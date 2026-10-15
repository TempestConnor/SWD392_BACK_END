package com.swd392.backend.onlinegame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.jayway.jsonpath.JsonPath;
import com.swd392.backend.onlinegame.service.OnlineGameService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
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

@EnabledIfEnvironmentVariable(named = "CHESS_TEST_DB_URL", matches = ".+")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=true"
        })
class OnlineGameReadySqlServerIntegrationTest {

    private static final String TEST_EMAIL_DOMAIN = "@f07-it.test";
    private static final String PASSWORD = "correct-horse";
    private static final String TEST_ACCOUNTS = "SELECT account_id FROM Account WHERE email LIKE ?";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OnlineGameService onlineGameService;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @AfterEach
    void deleteTestData() {
        var pattern = "%" + TEST_EMAIL_DOMAIN;
        jdbcTemplate.update("DELETE FROM Invitation WHERE host_account_id IN (" + TEST_ACCOUNTS + ")", pattern);
        jdbcTemplate.update(
                "DELETE FROM OnlineGame WHERE white_account_id IN (" + TEST_ACCOUNTS + ") OR black_account_id IN ("
                        + TEST_ACCOUNTS + ")",
                pattern,
                pattern);
        jdbcTemplate.update("DELETE FROM PlayerRating WHERE account_id IN (" + TEST_ACCOUNTS + ")", pattern);
        jdbcTemplate.update("DELETE FROM Account WHERE email LIKE ?", pattern);
    }

    @Test
    void both_players_ready_start_game_once_ok() throws Exception {
        // given
        var host = signUp();
        var joiner = signUp();
        var gameId = claimedGame(host, joiner);

        // when
        var first = post(host, gamePath(gameId, "ready"));
        var second = post(joiner, gamePath(gameId, "ready"));
        var retry = post(host, gamePath(gameId, "ready"));

        // then
        var game = gameRow(gameId);
        assertAll(
                () -> assertThat((String) JsonPath.read(first.body(), "$.status"))
                        .isEqualTo("PENDING"),
                () -> assertThat((String) JsonPath.read(second.body(), "$.status"))
                        .isEqualTo("ACTIVE"),
                () -> assertThat(retry.statusCode()).isEqualTo(200),
                () -> assertThat(game).containsEntry("status", "ACTIVE"),
                () -> assertThat(game.get("started_at")).isNotNull(),
                () -> assertThat(game.get("turn_started_at")).isEqualTo(game.get("started_at")),
                () -> assertThat(game.get("white_ready_at")).isNotNull(),
                () -> assertThat(game.get("black_ready_at")).isNotNull());
    }

    @Test
    void concurrent_ready_from_both_players_starts_game_ok() throws Exception {
        // given
        var host = signUp();
        var joiner = signUp();
        var gameId = claimedGame(host, joiner);

        // when
        var statuses = runConcurrently(List.of(
                () -> post(host, gamePath(gameId, "ready")).statusCode(),
                () -> post(joiner, gamePath(gameId, "ready")).statusCode()));

        // then
        var game = gameRow(gameId);
        assertAll(
                () -> assertThat(statuses).containsOnly(200),
                () -> assertThat(game).containsEntry("status", "ACTIVE"),
                () -> assertThat(game.get("started_at")).isNotNull());
    }

    @Test
    void second_ready_racing_cancel_leaves_exactly_one_outcome_ok() throws Exception {
        // given
        var host = signUp();
        var joiner = signUp();
        var gameId = claimedGame(host, joiner);
        post(host, gamePath(gameId, "ready"));

        // when
        runConcurrently(List.of(
                () -> post(joiner, gamePath(gameId, "ready")).statusCode(),
                () -> post(host, gamePath(gameId, "cancel")).statusCode()));

        // then
        var game = gameRow(gameId);
        var started = "ACTIVE".equals(game.get("status")) && game.get("started_at") != null;
        var cancelled = "CANCELLED".equals(game.get("status"))
                && game.get("started_at") == null
                && "PRE_START_CANCEL".equals(game.get("termination_reason"));
        assertThat(started ^ cancelled).isTrue();
    }

    @Test
    void ready_after_deadline_cancels_game_without_result_ok() throws Exception {
        // given
        var host = signUp();
        var joiner = signUp();
        var gameId = claimedGame(host, joiner);
        post(host, gamePath(gameId, "ready"));
        shiftGameIntoPast(gameId, 181);

        // when
        var ready = post(joiner, gamePath(gameId, "ready"));

        // then
        var game = gameRow(gameId);
        assertAll(
                () -> assertThat(ready.statusCode()).isEqualTo(200),
                () -> assertThat((String) JsonPath.read(ready.body(), "$.status"))
                        .isEqualTo("CANCELLED"),
                () -> assertThat(game)
                        .containsEntry("status", "CANCELLED")
                        .containsEntry("termination_reason", "PRE_START_CANCEL"),
                () -> assertThat(game.get("outcome")).isNull(),
                () -> assertThat(game.get("started_at")).isNull(),
                () -> assertThat(game.get("ended_at")).isNotNull());
    }

    @Test
    void sweeper_cancels_game_with_silent_participant_ok() throws Exception {
        // given
        var host = signUp();
        var joiner = signUp();
        var gameId = claimedGame(host, joiner);
        shiftGameIntoPast(gameId, 31);

        // when
        var due = onlineGameService.findGamesDueForPreStartCancel();
        due.forEach(onlineGameService::cancelIfPreStartDue);

        // then
        var game = gameRow(gameId);
        assertAll(() -> assertThat(due).contains(gameId), () -> assertThat(game)
                .containsEntry("status", "CANCELLED")
                .containsEntry("termination_reason", "PRE_START_CANCEL"));
    }

    @Test
    void sweeper_ignores_game_with_recent_heartbeats_ok() throws Exception {
        // given
        var host = signUp();
        var joiner = signUp();
        var gameId = claimedGame(host, joiner);
        shiftGameIntoPast(gameId, 20);
        post(host, gamePath(gameId, "heartbeat"));
        post(joiner, gamePath(gameId, "heartbeat"));
        shiftGameIntoPast(gameId, 20);

        // when
        var due = onlineGameService.findGamesDueForPreStartCancel();

        // then
        assertAll(() -> assertThat(due).doesNotContain(gameId), () -> assertThat(gameRow(gameId))
                .containsEntry("status", "PENDING"));
    }

    @Test
    void cancel_started_game_returns_409_and_keeps_it_active_ko() throws Exception {
        // given
        var host = signUp();
        var joiner = signUp();
        var gameId = claimedGame(host, joiner);
        post(host, gamePath(gameId, "ready"));
        post(joiner, gamePath(gameId, "ready"));

        // when
        var cancel = post(host, gamePath(gameId, "cancel"));

        // then
        assertAll(
                () -> assertThat(cancel.statusCode()).isEqualTo(409),
                () -> assertThat((String) JsonPath.read(cancel.body(), "$.errorCode"))
                        .isEqualTo("ONLINE_GAME_ALREADY_STARTED"),
                () -> assertThat(gameRow(gameId)).containsEntry("status", "ACTIVE"));
    }

    @Test
    void ready_by_non_participant_returns_404_ko() throws Exception {
        // given
        var host = signUp();
        var joiner = signUp();
        var outsider = signUp();
        var gameId = claimedGame(host, joiner);

        // when
        var ready = post(outsider, gamePath(gameId, "ready"));

        // then
        assertAll(
                () -> assertThat(ready.statusCode()).isEqualTo(404),
                () -> assertThat(gameRow(gameId).get("white_ready_at")).isNull(),
                () -> assertThat(gameRow(gameId).get("black_ready_at")).isNull());
    }

    private record Player(long accountId, String accessToken) {}

    private Player signUp() throws Exception {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var response = post(
                null,
                "/api/accounts/signup",
                """
                {"email":"player-%s%s","password":"%s","displayName":"F07%s","signupSkill":"BEGINNER"}"""
                        .formatted(suffix, TEST_EMAIL_DOMAIN, PASSWORD, suffix));

        return new Player(id(response.body(), "$.account.id"), JsonPath.read(response.body(), "$.accessToken"));
    }

    private long claimedGame(Player host, Player joiner) throws Exception {
        var created = post(host, "/api/invitations", """
                {"hostColor":"WHITE"}""");
        var token = (String) JsonPath.read(created.body(), "$.token");
        var claim = post(joiner, "/api/invitations/claim", """
                {"token":"%s"}""".formatted(token));

        return id(claim.body(), "$.id");
    }

    // Moves every recorded instant back so the game looks claimed (and last seen) that many seconds ago.
    private void shiftGameIntoPast(long gameId, int seconds) {
        jdbcTemplate.update(
                "UPDATE OnlineGame SET created_at = DATEADD(second, -?, created_at),"
                        + " ready_deadline_at = DATEADD(second, -?, ready_deadline_at),"
                        + " white_ready_at = DATEADD(second, -?, white_ready_at),"
                        + " black_ready_at = DATEADD(second, -?, black_ready_at),"
                        + " white_last_seen_at = DATEADD(second, -?, white_last_seen_at),"
                        + " black_last_seen_at = DATEADD(second, -?, black_last_seen_at)"
                        + " WHERE game_id = ?",
                seconds,
                seconds,
                seconds,
                seconds,
                seconds,
                seconds,
                gameId);
    }

    private Map<String, Object> gameRow(long gameId) {
        return jdbcTemplate.queryForMap("SELECT * FROM OnlineGame WHERE game_id = ?", gameId);
    }

    private String gamePath(long gameId, String action) {
        return "/api/online-games/" + gameId + "/" + action;
    }

    private HttpResponse<String> post(Player player, String path) throws Exception {
        return post(player, path, "");
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

    private List<Integer> runConcurrently(List<Callable<Integer>> actions) {
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

    private Integer await(Future<Integer> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private long id(String body, String path) {
        return ((Number) JsonPath.read(body, path)).longValue();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
