package com.swd392.backend.rating;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.jayway.jsonpath.JsonPath;
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
class RatingSqlServerIntegrationTest {

    private static final String TEST_EMAIL_DOMAIN = "@f12-it.test";
    private static final String PASSWORD = "correct-horse";
    private static final int BEGINNER_RATING = 400;
    private static final String TEST_ACCOUNTS = "SELECT account_id FROM Account WHERE email LIKE ?";
    private static final String TEST_GAMES = "SELECT game_id FROM OnlineGame WHERE white_account_id IN ("
            + TEST_ACCOUNTS + ") OR black_account_id IN (" + TEST_ACCOUNTS + ")";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @AfterEach
    void deleteTestData() {
        var pattern = "%" + TEST_EMAIL_DOMAIN;
        jdbcTemplate.update("DELETE FROM RatingEvent WHERE game_id IN (" + TEST_GAMES + ")", pattern, pattern);
        jdbcTemplate.update("DELETE FROM GameAbandonVote WHERE game_id IN (" + TEST_GAMES + ")", pattern, pattern);
        jdbcTemplate.update("DELETE FROM GameMove WHERE game_id IN (" + TEST_GAMES + ")", pattern, pattern);
        jdbcTemplate.update("DELETE FROM Invitation WHERE host_account_id IN (" + TEST_ACCOUNTS + ")", pattern);
        jdbcTemplate.update("DELETE FROM OnlineGame WHERE game_id IN (" + TEST_GAMES + ")", pattern, pattern);
        jdbcTemplate.update("DELETE FROM PlayerRating WHERE account_id IN (" + TEST_ACCOUNTS + ")", pattern);
        jdbcTemplate.update("DELETE FROM Account WHERE email LIKE ?", pattern);
    }

    @Test
    void rated_resignation_applies_both_ratings_once_even_when_retried_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = startedGame(white, black, "");

        // when
        var resign = post(white, gamePath(gameId, "/resign"));
        var retry = post(white, gamePath(gameId, "/resign"));
        var heartbeat = post(black, gamePath(gameId, "/heartbeat"));

        // then
        assertAll(
                () -> assertThat(resign.statusCode()).isEqualTo(200),
                () -> assertThat(retry.statusCode()).isEqualTo(200),
                () -> assertThat(heartbeat.statusCode()).isEqualTo(200),
                () -> assertThat(eventRows(gameId))
                        .extracting(
                                row -> row.get("account_id"),
                                row -> row.get("mode_code"),
                                row -> row.get("rating_before"),
                                row -> row.get("rating_delta"),
                                row -> row.get("rating_after"),
                                row -> row.get("k_factor_used"))
                        .containsExactlyInAnyOrder(
                                tuple(white.accountId(), "CLASSICAL", 400, -10, 390, 20),
                                tuple(black.accountId(), "CLASSICAL", 400, 10, 410, 20)),
                () -> assertThat(ratingRow(white)).containsEntry("rating", 390).containsEntry("games_played", 1),
                () -> assertThat(ratingRow(black)).containsEntry("rating", 410).containsEntry("games_played", 1));
    }

    @Test
    void rated_mutual_abandonment_records_two_zero_delta_events_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = startedGame(white, black, "");

        // when
        post(white, gamePath(gameId, "/abandon"));
        var second = post(black, gamePath(gameId, "/abandon"));

        // then
        assertAll(
                () -> assertThat((String) JsonPath.read(second.body(), "$.outcome"))
                        .isEqualTo("DRAW"),
                () -> assertThat(eventRows(gameId))
                        .extracting(
                                row -> row.get("rating_before"),
                                row -> row.get("rating_delta"),
                                row -> row.get("rating_after"))
                        .containsExactly(
                                tuple(BEGINNER_RATING, 0, BEGINNER_RATING), tuple(BEGINNER_RATING, 0, BEGINNER_RATING)),
                () -> assertThat(ratingRow(white))
                        .containsEntry("rating", BEGINNER_RATING)
                        .containsEntry("games_played", 1));
    }

    @Test
    void unrated_invite_game_creates_no_rating_events_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = startedGame(white, black, ",\"initialMinutes\":10,\"incrementSeconds\":5");

        // when
        var resign = post(white, gamePath(gameId, "/resign"));

        // then
        assertAll(
                () -> assertThat(resign.statusCode()).isEqualTo(200),
                () -> assertThat(gameRow(gameId)).containsEntry("is_rated", false),
                () -> assertThat(eventRows(gameId)).isEmpty(),
                () -> assertThat(ratingRow(white))
                        .containsEntry("rating", BEGINNER_RATING)
                        .containsEntry("games_played", 0));
    }

    @Test
    void cancelled_rated_game_creates_no_rating_events_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = claimedGame(white, black, "");

        // when
        var cancel = post(white, gamePath(gameId, "/cancel"));

        // then
        assertAll(
                () -> assertThat(cancel.statusCode()).isEqualTo(200),
                () -> assertThat(eventRows(gameId)).isEmpty(),
                () -> assertThat(ratingRow(black)).containsEntry("rating", BEGINNER_RATING));
    }

    @Test
    void simultaneous_resignations_rate_the_game_once_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = startedGame(white, black, "");

        // when
        runConcurrently(List.of(
                () -> post(white, gamePath(gameId, "/resign")).statusCode(),
                () -> post(black, gamePath(gameId, "/resign")).statusCode()));

        // then
        var events = eventRows(gameId);
        assertAll(
                () -> assertThat(events).hasSize(2),
                () -> assertThat(events)
                        .extracting(row -> row.get("rating_delta"))
                        .containsExactlyInAnyOrder(10, -10),
                () -> assertThat(ratingRow(white).get("games_played")).isEqualTo(1),
                () -> assertThat(ratingRow(black).get("games_played")).isEqualTo(1));
    }

    @Test
    void simultaneous_games_of_one_player_both_apply_without_lost_update_ok() throws Exception {
        // given
        var shared = signUp();
        var first = signUp();
        var second = signUp();
        var firstGame = startedGame(shared, first, "");
        var secondGame = startedGame(shared, second, "");

        // when
        var statuses = runConcurrently(List.of(
                () -> post(shared, gamePath(firstGame, "/resign")).statusCode(),
                () -> post(shared, gamePath(secondGame, "/resign")).statusCode()));

        // then
        var sharedEvents = jdbcTemplate.queryForList(
                "SELECT rating_before, rating_after FROM RatingEvent WHERE account_id = ? ORDER BY rating_before DESC",
                shared.accountId());
        assertAll(
                () -> assertThat(statuses).containsExactly(200, 200),
                () -> assertThat(sharedEvents)
                        .extracting(row -> row.get("rating_before"), row -> row.get("rating_after"))
                        .containsExactly(tuple(400, 390), tuple(390, 380)),
                () -> assertThat(ratingRow(shared)).containsEntry("rating", 380).containsEntry("games_played", 2));
    }

    private record Player(long accountId, String accessToken) {}

    private Player signUp() throws Exception {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var response = post(
                null,
                "/api/accounts/signup",
                """
                {"email":"player-%s%s","password":"%s","displayName":"F12%s","signupSkill":"BEGINNER"}"""
                        .formatted(suffix, TEST_EMAIL_DOMAIN, PASSWORD, suffix));

        return new Player(id(response.body(), "$.account.id"), JsonPath.read(response.body(), "$.accessToken"));
    }

    private long claimedGame(Player white, Player black, String clockJson) throws Exception {
        var created =
                post(white, "/api/invitations", """
                {"hostColor":"WHITE"%s}""".formatted(clockJson));
        var token = (String) JsonPath.read(created.body(), "$.token");
        var claim = post(black, "/api/invitations/claim", """
                {"token":"%s"}""".formatted(token));

        return id(claim.body(), "$.id");
    }

    private long startedGame(Player white, Player black, String clockJson) throws Exception {
        var gameId = claimedGame(white, black, clockJson);
        post(white, gamePath(gameId, "/ready"));
        post(black, gamePath(gameId, "/ready"));

        return gameId;
    }

    private String gamePath(long gameId, String action) {
        return "/api/online-games/" + gameId + action;
    }

    private Map<String, Object> gameRow(long gameId) {
        return jdbcTemplate.queryForMap("SELECT * FROM OnlineGame WHERE game_id = ?", gameId);
    }

    private List<Map<String, Object>> eventRows(long gameId) {
        return jdbcTemplate.queryForList("SELECT * FROM RatingEvent WHERE game_id = ? ORDER BY account_id", gameId);
    }

    private Map<String, Object> ratingRow(Player player) {
        return jdbcTemplate.queryForMap(
                "SELECT rating, games_played FROM PlayerRating WHERE account_id = ? AND mode_code = 'CLASSICAL'",
                player.accountId());
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
