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
class OnlineGameMoveSqlServerIntegrationTest {

    private static final String TEST_EMAIL_DOMAIN = "@f08-it.test";
    private static final String PASSWORD = "correct-horse";
    private static final String TEST_ACCOUNTS = "SELECT account_id FROM Account WHERE email LIKE ?";
    private static final String TEST_GAMES = "SELECT game_id FROM OnlineGame WHERE white_account_id IN ("
            + TEST_ACCOUNTS + ") OR black_account_id IN (" + TEST_ACCOUNTS + ")";

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
        jdbcTemplate.update("DELETE FROM GameMove WHERE game_id IN (" + TEST_GAMES + ")", pattern, pattern);
        jdbcTemplate.update("DELETE FROM Invitation WHERE host_account_id IN (" + TEST_ACCOUNTS + ")", pattern);
        jdbcTemplate.update("DELETE FROM OnlineGame WHERE game_id IN (" + TEST_GAMES + ")", pattern, pattern);
        jdbcTemplate.update("DELETE FROM PlayerRating WHERE account_id IN (" + TEST_ACCOUNTS + ")", pattern);
        jdbcTemplate.update("DELETE FROM Account WHERE email LIKE ?", pattern);
    }

    @Test
    void checkmate_sequence_persists_moves_in_order_and_completes_game_once_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = startedGame(white, black);

        // when
        move(white, gameId, 1, "f2f3");
        move(black, gameId, 2, "e7e5");
        move(white, gameId, 3, "g2g4");
        var mate = move(black, gameId, 4, "d8h4");
        var history = get(white, "/api/online-games/" + gameId + "/moves");

        // then
        var game = gameRow(gameId);
        var moves = moveRows(gameId);
        assertAll(
                () -> assertThat(mate.statusCode()).isEqualTo(200),
                () -> assertThat((String) JsonPath.read(mate.body(), "$.outcome"))
                        .isEqualTo("BLACK_WIN"),
                () -> assertThat(game)
                        .containsEntry("status", "COMPLETED")
                        .containsEntry("outcome", "BLACK_WIN")
                        .containsEntry("termination_reason", "CHECKMATE")
                        .containsEntry("last_ply", 4),
                () -> assertThat(game.get("ended_at")).isNotNull(),
                () -> assertThat(moves)
                        .extracting(row -> row.get("move_uci"))
                        .containsExactly("f2f3", "e7e5", "g2g4", "d8h4"),
                () -> assertThat(moves.getLast()).containsEntry("san", "Qh4#"),
                () -> assertThat(moves.getLast().get("resulting_fen")).isEqualTo(game.get("current_fen")),
                () -> assertThat((List<String>) JsonPath.read(history.body(), "$[*].move"))
                        .containsExactly("f2f3", "e7e5", "g2g4", "d8h4"));
    }

    @Test
    void accepted_move_updates_clocks_and_turn_start_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = startedGame(white, black);

        // when
        move(white, gameId, 1, "e2e4");

        // then
        var game = gameRow(gameId);
        var move = moveRows(gameId).getFirst();
        assertAll(
                () -> assertThat(((Number) game.get("white_remaining_ms")).longValue())
                        .isGreaterThan(1_800_000L)
                        .isLessThanOrEqualTo(1_810_000L),
                () -> assertThat(((Number) game.get("black_remaining_ms")).longValue())
                        .isEqualTo(1_800_000L),
                () -> assertThat(move.get("white_remaining_ms")).isEqualTo(game.get("white_remaining_ms")),
                () -> assertThat(move.get("accepted_at")).isEqualTo(game.get("turn_started_at")));
    }

    @Test
    void concurrent_moves_at_same_ply_accept_exactly_one_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = startedGame(white, black);

        // when
        var statuses = runConcurrently(
                List.of(() -> move(white, gameId, 1, "e2e4").statusCode(), () -> move(white, gameId, 1, "d2d4")
                        .statusCode()));

        // then
        assertAll(
                () -> assertThat(statuses).containsExactlyInAnyOrder(200, 409),
                () -> assertThat(moveRows(gameId)).hasSize(1),
                () -> assertThat(gameRow(gameId)).containsEntry("last_ply", 1));
    }

    @Test
    void retry_of_accepted_move_returns_200_and_stores_it_once_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = startedGame(white, black);
        move(white, gameId, 1, "e2e4");

        // when
        var retry = move(white, gameId, 1, "e2e4");

        // then
        assertAll(
                () -> assertThat(retry.statusCode()).isEqualTo(200),
                () -> assertThat(moveRows(gameId)).hasSize(1),
                () -> assertThat(gameRow(gameId)).containsEntry("last_ply", 1));
    }

    @Test
    void illegal_move_changes_no_rows_ko() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = startedGame(white, black);
        var before = gameRow(gameId);

        // when
        var illegal = move(white, gameId, 1, "e2e5");

        // then
        var after = gameRow(gameId);
        assertAll(
                () -> assertThat(illegal.statusCode()).isEqualTo(400),
                () -> assertThat((String) JsonPath.read(illegal.body(), "$.errorCode"))
                        .isEqualTo("ILLEGAL_MOVE"),
                () -> assertThat(moveRows(gameId)).isEmpty(),
                () -> assertThat(after.get("current_fen")).isEqualTo(before.get("current_fen")),
                () -> assertThat(after.get("white_remaining_ms")).isEqualTo(before.get("white_remaining_ms")),
                () -> assertThat(after.get("turn_started_at")).isEqualTo(before.get("turn_started_at")));
    }

    @Test
    void move_before_game_starts_is_rejected_ko() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = claimedGame(white, black);

        // when
        var early = move(white, gameId, 1, "e2e4");

        // then
        assertAll(
                () -> assertThat(early.statusCode()).isEqualTo(409),
                () -> assertThat((String) JsonPath.read(early.body(), "$.errorCode"))
                        .isEqualTo("ONLINE_GAME_NOT_ACTIVE"),
                () -> assertThat(moveRows(gameId)).isEmpty());
    }

    @Test
    void late_move_is_rejected_and_sweep_records_timeout_once_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = startedGame(white, black);
        shiftTurnStartBack(gameId, 1_801);

        // when
        var late = move(white, gameId, 1, "e2e4");
        var afterRejection = gameRow(gameId);
        var due = onlineGameService.findGamesDueForTimeout();
        onlineGameService.completeIfTimedOut(gameId);
        onlineGameService.completeIfTimedOut(gameId);

        // then
        var game = gameRow(gameId);
        assertAll(
                () -> assertThat(late.statusCode()).isEqualTo(409),
                () -> assertThat((String) JsonPath.read(late.body(), "$.errorCode"))
                        .isEqualTo("TIME_EXPIRED"),
                () -> assertThat(afterRejection).containsEntry("status", "ACTIVE"),
                () -> assertThat(due).contains(gameId),
                () -> assertThat(game)
                        .containsEntry("status", "COMPLETED")
                        .containsEntry("outcome", "BLACK_WIN")
                        .containsEntry("termination_reason", "TIMEOUT")
                        .containsEntry("white_remaining_ms", 0L)
                        .containsEntry("last_ply", 0),
                () -> assertThat(msFromTurnStartToEnd(gameId)).isEqualTo(1_800_000L),
                () -> assertThat(moveRows(gameId)).isEmpty());
    }

    @Test
    void heartbeat_after_flag_fall_returns_timeout_result_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = startedGame(white, black);
        move(white, gameId, 1, "e2e4");
        shiftTurnStartBack(gameId, 1_801);

        // when
        var heartbeat = post(white, "/api/online-games/" + gameId + "/heartbeat", "");

        // then
        assertAll(
                () -> assertThat(heartbeat.statusCode()).isEqualTo(200),
                () -> assertThat((String) JsonPath.read(heartbeat.body(), "$.outcome"))
                        .isEqualTo("WHITE_WIN"),
                () -> assertThat((String) JsonPath.read(heartbeat.body(), "$.terminationReason"))
                        .isEqualTo("TIMEOUT"),
                () -> assertThat(gameRow(gameId))
                        .containsEntry("status", "COMPLETED")
                        .containsEntry("black_remaining_ms", 0L));
    }

    private record Player(long accountId, String accessToken) {}

    private Player signUp() throws Exception {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var response = post(
                null,
                "/api/accounts/signup",
                """
                {"email":"player-%s%s","password":"%s","displayName":"F08%s","signupSkill":"BEGINNER"}"""
                        .formatted(suffix, TEST_EMAIL_DOMAIN, PASSWORD, suffix));

        return new Player(id(response.body(), "$.account.id"), JsonPath.read(response.body(), "$.accessToken"));
    }

    private long claimedGame(Player white, Player black) throws Exception {
        var created = post(white, "/api/invitations", """
                {"hostColor":"WHITE"}""");
        var token = (String) JsonPath.read(created.body(), "$.token");
        var claim = post(black, "/api/invitations/claim", """
                {"token":"%s"}""".formatted(token));

        return id(claim.body(), "$.id");
    }

    private long startedGame(Player white, Player black) throws Exception {
        var gameId = claimedGame(white, black);
        post(white, "/api/online-games/" + gameId + "/ready", "");
        post(black, "/api/online-games/" + gameId + "/ready", "");

        return gameId;
    }

    private HttpResponse<String> move(Player player, long gameId, int ply, String move) throws Exception {
        return post(
                player,
                "/api/online-games/" + gameId + "/moves",
                """
                {"ply":%d,"move":"%s"}""".formatted(ply, move));
    }

    // Shifts in SQL so the stored UTC value is moved without converting through the JVM time zone.
    private void shiftTurnStartBack(long gameId, int seconds) {
        jdbcTemplate.update(
                "UPDATE OnlineGame SET turn_started_at = DATEADD(second, ?, turn_started_at) WHERE game_id = ?",
                -seconds,
                gameId);
    }

    private Long msFromTurnStartToEnd(long gameId) {
        return jdbcTemplate.queryForObject(
                "SELECT DATEDIFF_BIG(millisecond, turn_started_at, ended_at) FROM OnlineGame WHERE game_id = ?",
                Long.class,
                gameId);
    }

    private Map<String, Object> gameRow(long gameId) {
        return jdbcTemplate.queryForMap("SELECT * FROM OnlineGame WHERE game_id = ?", gameId);
    }

    private List<Map<String, Object>> moveRows(long gameId) {
        return jdbcTemplate.queryForList("SELECT * FROM GameMove WHERE game_id = ? ORDER BY ply", gameId);
    }

    private HttpResponse<String> get(Player player, String path) throws Exception {
        var request = HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + player.accessToken())
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
