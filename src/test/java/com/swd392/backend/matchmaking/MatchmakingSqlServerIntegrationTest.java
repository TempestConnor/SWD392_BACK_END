package com.swd392.backend.matchmaking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.jayway.jsonpath.JsonPath;
import com.swd392.backend.matchmaking.model.QueuePairing;
import com.swd392.backend.matchmaking.service.MatchmakingService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

// Relies on the CLASSICAL/STANDARD MatchmakingConfig row from db/migrations/002 (10 s heartbeat + 20 s grace;
// gap 100, +50 every 30 s, max 400). Pairing is driven directly; the background workers are disabled in tests.
@EnabledIfEnvironmentVariable(named = "CHESS_TEST_DB_URL", matches = ".+")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true"
        })
class MatchmakingSqlServerIntegrationTest {

    private static final String TEST_EMAIL_DOMAIN = "@f05-it.test";
    private static final String PASSWORD = "correct-horse";
    private static final String TEST_ACCOUNTS = "SELECT account_id FROM Account WHERE email LIKE ?";
    private static final String JOIN_BODY = """
            {"mode":"CLASSICAL","variant":"STANDARD"}""";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MatchmakingService matchmakingService;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @AfterEach
    void deleteTestData() {
        var pattern = "%" + TEST_EMAIL_DOMAIN;
        jdbcTemplate.update("DELETE FROM QueueRequest WHERE account_id IN (" + TEST_ACCOUNTS + ")", pattern);
        jdbcTemplate.update(
                "DELETE FROM OnlineGame WHERE white_account_id IN (" + TEST_ACCOUNTS + ")" + " OR black_account_id IN ("
                        + TEST_ACCOUNTS + ")",
                pattern,
                pattern);
        jdbcTemplate.update("DELETE FROM PlayerRating WHERE account_id IN (" + TEST_ACCOUNTS + ")", pattern);
        jdbcTemplate.update("DELETE FROM Account WHERE email LIKE ?", pattern);
    }

    @Test
    void join_persists_open_request_with_rating_captured_at_entry_ok() throws Exception {
        // given
        var player = signUp();
        var rating = currentRating(player);

        // when
        var join = join(player);
        jdbcTemplate.update(
                "UPDATE PlayerRating SET rating = rating + 50 WHERE account_id = ? AND mode_code = 'CLASSICAL'",
                player.accountId());
        var heartbeat = post(player, "/api/matchmaking/heartbeat", "");

        // then
        var requestId = id(join.body(), "$.id");
        var row = jdbcTemplate.queryForMap("SELECT * FROM QueueRequest WHERE request_id = ?", requestId);
        assertAll(
                () -> assertThat(join.statusCode()).isEqualTo(200),
                () -> assertThat((String) JsonPath.read(join.body(), "$.status"))
                        .isEqualTo("QUEUED"),
                () -> assertThat((Integer) JsonPath.read(join.body(), "$.heartbeatIntervalSeconds"))
                        .isEqualTo(10),
                () -> assertThat(row)
                        .containsEntry("account_id", player.accountId())
                        .containsEntry("mode_code", "CLASSICAL")
                        .containsEntry("variant_code", "STANDARD")
                        .containsEntry("rating_at_entry", rating)
                        .containsEntry("left_at", null)
                        .containsEntry("exit_reason", null),
                () -> assertThat(heartbeat.statusCode()).isEqualTo(200),
                () -> assertThat((Integer) JsonPath.read(heartbeat.body(), "$.ratingAtEntry"))
                        .isEqualTo(rating));
    }

    @Test
    void concurrent_joins_from_several_tabs_leave_one_open_request_ok() throws Exception {
        // given
        var player = signUp();

        // when
        var responses = runConcurrently(Collections.<Callable<HttpResponse<String>>>nCopies(6, () -> join(player)));

        // then
        var requestIds =
                responses.stream().map(response -> id(response.body(), "$.id")).toList();
        assertAll(
                () -> assertThat(responses).allSatisfy(response -> assertThat(response.statusCode())
                        .isEqualTo(200)),
                () -> assertThat(requestIds).containsOnly(requestIds.get(0)),
                () -> assertThat(countRequests(player)).isEqualTo(1),
                () -> assertThat(countOpenRequests(player)).isEqualTo(1));
    }

    @Test
    void cancel_leaves_queue_immediately_and_repeat_is_harmless_ok() throws Exception {
        // given
        var player = signUp();
        var requestId = id(join(player).body(), "$.id");

        // when
        var cancel = post(player, "/api/matchmaking/cancel", "");
        var repeat = post(player, "/api/matchmaking/cancel", "");
        var rejoin = join(player);

        // then
        var row = jdbcTemplate.queryForMap("SELECT * FROM QueueRequest WHERE request_id = ?", requestId);
        assertAll(
                () -> assertThat(cancel.statusCode()).isEqualTo(200),
                () -> assertThat((String) JsonPath.read(cancel.body(), "$.status"))
                        .isEqualTo("CANCELLED"),
                () -> assertThat(repeat.statusCode()).isEqualTo(200),
                () -> assertThat(id(repeat.body(), "$.id")).isEqualTo(requestId),
                () -> assertThat(row).containsEntry("exit_reason", "CANCELLED").containsEntry("game_id", null),
                () -> assertThat(row.get("left_at")).isNotNull(),
                () -> assertThat(id(rejoin.body(), "$.id")).isNotEqualTo(requestId),
                () -> assertThat(countRequests(player)).isEqualTo(2),
                () -> assertThat(countOpenRequests(player)).isEqualTo(1));
    }

    @Test
    void silent_request_times_out_at_heartbeat_plus_grace_ok() throws Exception {
        // given
        var player = signUp();
        var requestId = id(join(player).body(), "$.id");
        jdbcTemplate.update(
                "UPDATE QueueRequest SET joined_at = DATEADD(SECOND, -31, joined_at),"
                        + " heartbeat_at = DATEADD(SECOND, -31, heartbeat_at) WHERE request_id = ?",
                requestId);

        // when
        var due = matchmakingService.findRequestsDueForTimeout();
        due.forEach(matchmakingService::timeOutIfDue);
        var heartbeat = post(player, "/api/matchmaking/heartbeat", "");

        // then
        var row = jdbcTemplate.queryForMap("SELECT * FROM QueueRequest WHERE request_id = ?", requestId);
        var heartbeatAt = ((Timestamp) row.get("heartbeat_at")).toInstant();
        var leftAt = ((Timestamp) row.get("left_at")).toInstant();
        assertAll(
                () -> assertThat(due).contains(requestId),
                () -> assertThat(row).containsEntry("exit_reason", "TIMED_OUT"),
                () -> assertThat(Duration.between(heartbeatAt, leftAt)).isEqualTo(Duration.ofSeconds(30)),
                () -> assertThat((String) JsonPath.read(heartbeat.body(), "$.status"))
                        .isEqualTo("TIMED_OUT"),
                () -> assertThat(countOpenRequests(player)).isZero());
    }

    @Test
    void cancel_racing_join_never_leaves_two_open_requests_ok() throws Exception {
        // given
        var player = signUp();
        join(player);

        // when
        var responses = runConcurrently(List.<Callable<HttpResponse<String>>>of(
                () -> post(player, "/api/matchmaking/cancel", ""),
                () -> join(player),
                () -> post(player, "/api/matchmaking/cancel", ""),
                () -> join(player)));

        // then
        assertAll(
                () -> assertThat(responses).allSatisfy(response -> assertThat(response.statusCode())
                        .isEqualTo(200)),
                () -> assertThat(countOpenRequests(player)).isLessThanOrEqualTo(1));
    }

    @Test
    void pair_consumes_both_requests_and_creates_one_pending_rated_game_ok() throws Exception {
        // given
        var first = signUp();
        var second = signUp();
        var firstId = id(join(first).body(), "$.id");
        var secondId = id(join(second).body(), "$.id");

        // when
        var candidates = matchmakingService.findPairingCandidates();
        var gameId = matchmakingService.pair(firstId, secondId).orElseThrow();
        var heartbeat = post(first, "/api/matchmaking/heartbeat", "");
        var game = get(second, "/api/online-games/" + gameId);

        // then
        var gameRow = jdbcTemplate.queryForMap("SELECT * FROM OnlineGame WHERE game_id = ?", gameId);
        var requestRows =
                jdbcTemplate.queryForList("SELECT * FROM QueueRequest WHERE request_id IN (?, ?)", firstId, secondId);
        assertAll(
                () -> assertThat(candidates)
                        .extracting(QueuePairing::getFirstRequestId, QueuePairing::getSecondRequestId)
                        .contains(tuple(firstId, secondId)),
                () -> assertThat(gameRow)
                        .containsEntry("source", "MATCHMAKING")
                        .containsEntry("status", "PENDING")
                        .containsEntry("is_rated", true)
                        .containsEntry("mode_code", "CLASSICAL")
                        .containsEntry("variant_code", "STANDARD")
                        .containsEntry("initial_seconds", 1800)
                        .containsEntry("increment_seconds", 10),
                () -> assertThat(List.of(gameRow.get("white_account_id"), gameRow.get("black_account_id")))
                        .containsExactlyInAnyOrder(first.accountId(), second.accountId()),
                () -> assertThat(requestRows).hasSize(2).allSatisfy(row -> assertThat(row)
                        .containsEntry("exit_reason", "MATCHED")
                        .containsEntry("game_id", gameId)),
                () -> assertThat((String) JsonPath.read(heartbeat.body(), "$.status"))
                        .isEqualTo("MATCHED"),
                () -> assertThat(id(heartbeat.body(), "$.gameId")).isEqualTo(gameId),
                () -> assertThat(game.statusCode()).isEqualTo(200),
                () -> assertThat((String) JsonPath.read(game.body(), "$.status"))
                        .isEqualTo("PENDING"));
    }

    @Test
    void competing_pairings_match_each_request_at_most_once_ok() throws Exception {
        // given
        var a = id(join(signUp()).body(), "$.id");
        var b = id(join(signUp()).body(), "$.id");
        var c = id(join(signUp()).body(), "$.id");

        // when
        var results = runConcurrently(List.<Callable<Optional<Long>>>of(
                () -> matchmakingService.pair(a, b),
                () -> matchmakingService.pair(c, a),
                () -> matchmakingService.pair(b, c),
                () -> matchmakingService.pair(a, b),
                () -> matchmakingService.pair(b, c)));

        // then
        var gameIds = results.stream().flatMap(Optional::stream).distinct().toList();
        var matched = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM QueueRequest WHERE request_id IN (?, ?, ?) AND exit_reason = 'MATCHED'",
                Integer.class,
                a,
                b,
                c);
        assertAll(
                () -> assertThat(gameIds).hasSize(1),
                () -> assertThat(countTestGames()).isEqualTo(1),
                () -> assertThat(matched).isEqualTo(2));
    }

    @Test
    void cancel_racing_pairing_never_matches_a_cancelled_request_ok() throws Exception {
        // given
        var leaver = signUp();
        var leaverRequest = id(join(leaver).body(), "$.id");
        var otherRequest = id(join(signUp()).body(), "$.id");

        // when
        var results = runConcurrently(List.<Callable<Object>>of(
                () -> post(leaver, "/api/matchmaking/cancel", ""),
                () -> matchmakingService.pair(leaverRequest, otherRequest)));

        // then
        var cancel = (HttpResponse<?>) results.get(0);
        var cancelStatus = (String) JsonPath.read((String) cancel.body(), "$.status");
        var leaverRow = jdbcTemplate.queryForMap("SELECT * FROM QueueRequest WHERE request_id = ?", leaverRequest);
        var otherRow = jdbcTemplate.queryForMap("SELECT * FROM QueueRequest WHERE request_id = ?", otherRequest);
        var expectedOtherExit = "MATCHED".equals(cancelStatus) ? "MATCHED" : null;
        var expectedGames = "MATCHED".equals(cancelStatus) ? 1 : 0;
        assertAll(
                () -> assertThat(cancel.statusCode()).isEqualTo(200),
                () -> assertThat(cancelStatus).isIn("CANCELLED", "MATCHED"),
                () -> assertThat(leaverRow).containsEntry("exit_reason", cancelStatus),
                () -> assertThat(otherRow).containsEntry("exit_reason", expectedOtherExit),
                () -> assertThat(countTestGames()).isEqualTo(expectedGames));
    }

    @Test
    void ratings_beyond_maximum_gap_are_never_paired_ko() throws Exception {
        // given
        var beginnerRequest = id(join(signUp("BEGINNER")).body(), "$.id");
        var expertRequest = id(join(signUp("EXPERT")).body(), "$.id");
        jdbcTemplate.update(
                "UPDATE QueueRequest SET joined_at = DATEADD(HOUR, -1, joined_at) WHERE request_id IN (?, ?)",
                beginnerRequest,
                expertRequest);

        // when
        var candidates = matchmakingService.findPairingCandidates();
        var gameId = matchmakingService.pair(beginnerRequest, expertRequest);

        // then
        assertAll(
                () -> assertThat(candidates)
                        .flatExtracting(QueuePairing::getFirstRequestId, QueuePairing::getSecondRequestId)
                        .doesNotContain(beginnerRequest, expertRequest),
                () -> assertThat(gameId).isEmpty(),
                () -> assertThat(countTestGames()).isZero());
    }

    private record Player(long accountId, String accessToken) {}

    private Player signUp() throws Exception {
        return signUp("BEGINNER");
    }

    private Player signUp(String skill) throws Exception {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var response = post(
                null,
                "/api/accounts/signup",
                """
                {"email":"player-%s%s","password":"%s","displayName":"F05%s","signupSkill":"%s"}"""
                        .formatted(suffix, TEST_EMAIL_DOMAIN, PASSWORD, suffix, skill));

        return new Player(id(response.body(), "$.account.id"), JsonPath.read(response.body(), "$.accessToken"));
    }

    private HttpResponse<String> join(Player player) throws Exception {
        return post(player, "/api/matchmaking/join", JOIN_BODY);
    }

    private HttpResponse<String> post(Player player, String path, String json) throws Exception {
        var builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (player != null) {
            builder.header("Authorization", "Bearer " + player.accessToken());
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(Player player, String path) throws Exception {
        var request = HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + player.accessToken())
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private <T> List<T> runConcurrently(List<Callable<T>> actions) {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(actions.size())) {
            var futures = actions.stream()
                    .map(action -> executor.submit(() -> {
                        start.await();
                        return action.call();
                    }))
                    .toList();
            start.countDown();

            return futures.stream().map(this::await).toList();
        }
    }

    private <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Integer currentRating(Player player) {
        return jdbcTemplate.queryForObject(
                "SELECT rating FROM PlayerRating WHERE account_id = ? AND mode_code = 'CLASSICAL'",
                Integer.class,
                player.accountId());
    }

    private Integer countTestGames() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM OnlineGame WHERE white_account_id IN (" + TEST_ACCOUNTS + ")",
                Integer.class,
                "%" + TEST_EMAIL_DOMAIN);
    }

    private Integer countRequests(Player player) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM QueueRequest WHERE account_id = ?", Integer.class, player.accountId());
    }

    private Integer countOpenRequests(Player player) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM QueueRequest WHERE account_id = ? AND left_at IS NULL",
                Integer.class,
                player.accountId());
    }

    private long id(String body, String path) {
        return ((Number) JsonPath.read(body, path)).longValue();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
