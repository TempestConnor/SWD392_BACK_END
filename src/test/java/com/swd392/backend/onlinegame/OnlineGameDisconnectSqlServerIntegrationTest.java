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
class OnlineGameDisconnectSqlServerIntegrationTest {

    private static final String TEST_EMAIL_DOMAIN = "@f11-it.test";
    private static final String PASSWORD = "correct-horse";
    private static final String TEST_ACCOUNTS = "SELECT account_id FROM Account WHERE email LIKE ?";
    private static final String TEST_GAMES = "SELECT game_id FROM OnlineGame WHERE white_account_id IN ("
            + TEST_ACCOUNTS + ") OR black_account_id IN (" + TEST_ACCOUNTS + ")";
    private static final String AGE_GAME =
            """
            DECLARE @seconds int = ?;
            UPDATE OnlineGame SET
              created_at = DATEADD(second, -@seconds, created_at),
              ready_deadline_at = DATEADD(second, -@seconds, ready_deadline_at),
              white_ready_at = DATEADD(second, -@seconds, white_ready_at),
              black_ready_at = DATEADD(second, -@seconds, black_ready_at),
              started_at = DATEADD(second, -@seconds, started_at),
              turn_started_at = DATEADD(second, -@seconds, turn_started_at),
              white_last_seen_at = DATEADD(second, -@seconds, white_last_seen_at),
              black_last_seen_at = DATEADD(second, -@seconds, black_last_seen_at)
            WHERE game_id = ?""";
    private static final int PAST_GRACE_SECONDS = 181;

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
        jdbcTemplate.update("DELETE FROM GameAbandonVote WHERE game_id IN (" + TEST_GAMES + ")", pattern, pattern);
        jdbcTemplate.update("DELETE FROM RatingEvent WHERE game_id IN (" + TEST_GAMES + ")", pattern, pattern);
        jdbcTemplate.update("DELETE FROM GameMove WHERE game_id IN (" + TEST_GAMES + ")", pattern, pattern);
        jdbcTemplate.update("DELETE FROM Invitation WHERE host_account_id IN (" + TEST_ACCOUNTS + ")", pattern);
        jdbcTemplate.update("DELETE FROM OnlineGame WHERE game_id IN (" + TEST_GAMES + ")", pattern, pattern);
        jdbcTemplate.update("DELETE FROM PlayerRating WHERE account_id IN (" + TEST_ACCOUNTS + ")", pattern);
        jdbcTemplate.update("DELETE FROM Account WHERE email LIKE ?", pattern);
    }

    @Test
    void player_absent_past_grace_forfeits_unrated_game_without_clock_once_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = startedGame(white, black, """
                {"hostColor":"WHITE","clockEnabled":false}""");
        ageGame(gameId, PAST_GRACE_SECONDS);
        markWhiteSeenNow(gameId);

        // when
        var due = onlineGameService.findActiveGamesDue();
        onlineGameService.resolveActiveGame(gameId);
        onlineGameService.resolveActiveGame(gameId);
        var lateHeartbeat = post(black, gamePath(gameId, "/heartbeat"));
        var lateMove = move(white, gameId, 1, "e2e4");

        // then
        var game = gameRow(gameId);
        assertAll(
                () -> assertThat(due).contains(gameId),
                () -> assertThat(game)
                        .containsEntry("status", "COMPLETED")
                        .containsEntry("outcome", "WHITE_WIN")
                        .containsEntry("termination_reason", "DISCONNECT")
                        .containsEntry("is_rated", false)
                        .containsEntry("clock_enabled", false),
                () -> assertThat(game.get("black_disconnected_at")).isNotNull(),
                () -> assertThat(game.get("white_disconnected_at")).isNull(),
                () -> assertThat(lateHeartbeat.statusCode()).isEqualTo(200),
                () -> assertThat((String) JsonPath.read(lateHeartbeat.body(), "$.terminationReason"))
                        .isEqualTo("DISCONNECT"),
                () -> assertThat(lateMove.statusCode()).isEqualTo(409));
    }

    @Test
    void player_reconnecting_within_grace_keeps_rated_game_active_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = startedGame(white, black, """
                {"hostColor":"WHITE"}""");
        ageGame(gameId, 60);

        // when
        var whiteHeartbeat = post(white, gamePath(gameId, "/heartbeat"));
        var markedRow = gameRow(gameId);
        var blackHeartbeat = post(black, gamePath(gameId, "/heartbeat"));
        var move = move(white, gameId, 1, "e2e4");

        // then
        var game = gameRow(gameId);
        assertAll(
                () -> assertThat(whiteHeartbeat.statusCode()).isEqualTo(200),
                () -> assertThat((String) JsonPath.read(whiteHeartbeat.body(), "$.black.disconnectedAt"))
                        .isNotNull(),
                () -> assertThat(markedRow.get("black_disconnected_at")).isNotNull(),
                () -> assertThat((Object) JsonPath.read(blackHeartbeat.body(), "$.black.disconnectedAt"))
                        .isNull(),
                () -> assertThat(move.statusCode()).isEqualTo(200),
                () -> assertThat(game).containsEntry("status", "ACTIVE").containsEntry("last_ply", 1),
                () -> assertThat(game.get("black_disconnected_at")).isNull(),
                () -> assertThat(game.get("white_disconnected_at")).isNull());
    }

    @Test
    void simultaneous_sweep_and_heartbeat_record_one_forfeit_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = startedGame(white, black, """
                {"hostColor":"WHITE"}""");
        ageGame(gameId, PAST_GRACE_SECONDS);
        markWhiteSeenNow(gameId);

        // when
        var statuses = runConcurrently(
                List.of(() -> post(white, gamePath(gameId, "/heartbeat")).statusCode(), () -> {
                    onlineGameService.resolveActiveGame(gameId);
                    return 200;
                }));

        // then
        assertAll(() -> assertThat(statuses).containsExactly(200, 200), () -> assertThat(gameRow(gameId))
                .containsEntry("status", "COMPLETED")
                .containsEntry("outcome", "WHITE_WIN")
                .containsEntry("termination_reason", "DISCONNECT"));
    }

    private record Player(long accountId, String accessToken) {}

    private Player signUp() throws Exception {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var response = post(
                null,
                "/api/accounts/signup",
                """
                {"email":"player-%s%s","password":"%s","displayName":"F11%s","signupSkill":"BEGINNER"}"""
                        .formatted(suffix, TEST_EMAIL_DOMAIN, PASSWORD, suffix));

        return new Player(id(response.body(), "$.account.id"), JsonPath.read(response.body(), "$.accessToken"));
    }

    private long startedGame(Player white, Player black, String invitation) throws Exception {
        var created = post(white, "/api/invitations", invitation);
        var token = (String) JsonPath.read(created.body(), "$.token");
        var claim = post(black, "/api/invitations/claim", """
                {"token":"%s"}""".formatted(token));
        var gameId = id(claim.body(), "$.id");
        post(white, gamePath(gameId, "/ready"));
        post(black, gamePath(gameId, "/ready"));

        return gameId;
    }

    // Moves every timestamp back together so the schema's ordering checks still hold.
    private void ageGame(long gameId, int seconds) {
        jdbcTemplate.update(AGE_GAME, seconds, gameId);
    }

    private void markWhiteSeenNow(long gameId) {
        jdbcTemplate.update("UPDATE OnlineGame SET white_last_seen_at = SYSUTCDATETIME() WHERE game_id = ?", gameId);
    }

    private HttpResponse<String> move(Player player, long gameId, int ply, String move) throws Exception {
        return post(
                player, gamePath(gameId, "/moves"), """
                {"ply":%d,"move":"%s"}""".formatted(ply, move));
    }

    private String gamePath(long gameId, String action) {
        return "/api/online-games/" + gameId + action;
    }

    private Map<String, Object> gameRow(long gameId) {
        return jdbcTemplate.queryForMap("SELECT * FROM OnlineGame WHERE game_id = ?", gameId);
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
