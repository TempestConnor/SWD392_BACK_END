package com.swd392.backend.onlinegame;

import static org.assertj.core.api.Assertions.assertThat;
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
class OnlineGameEndingSqlServerIntegrationTest {

    private static final String TEST_EMAIL_DOMAIN = "@f10-it.test";
    private static final String PASSWORD = "correct-horse";
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
        jdbcTemplate.update("DELETE FROM GameAbandonVote WHERE game_id IN (" + TEST_GAMES + ")", pattern, pattern);
        jdbcTemplate.update("DELETE FROM RatingEvent WHERE game_id IN (" + TEST_GAMES + ")", pattern, pattern);
        jdbcTemplate.update("DELETE FROM GameMove WHERE game_id IN (" + TEST_GAMES + ")", pattern, pattern);
        jdbcTemplate.update("DELETE FROM Invitation WHERE host_account_id IN (" + TEST_ACCOUNTS + ")", pattern);
        jdbcTemplate.update("DELETE FROM OnlineGame WHERE game_id IN (" + TEST_GAMES + ")", pattern, pattern);
        jdbcTemplate.update("DELETE FROM PlayerRating WHERE account_id IN (" + TEST_ACCOUNTS + ")", pattern);
        jdbcTemplate.update("DELETE FROM Account WHERE email LIKE ?", pattern);
    }

    @Test
    void white_resignation_stores_black_win_and_rejects_later_moves_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = startedGame(white, black);
        move(white, gameId, 1, "e2e4");

        // when
        var resign = post(white, gamePath(gameId, "/resign"));
        var retry = post(white, gamePath(gameId, "/resign"));
        var lateMove = move(black, gameId, 2, "e7e5");

        // then
        assertAll(
                () -> assertThat(resign.statusCode()).isEqualTo(200),
                () -> assertThat((String) JsonPath.read(resign.body(), "$.outcome"))
                        .isEqualTo("BLACK_WIN"),
                () -> assertThat(retry.statusCode()).isEqualTo(200),
                () -> assertThat(lateMove.statusCode()).isEqualTo(409),
                () -> assertThat((String) JsonPath.read(lateMove.body(), "$.errorCode"))
                        .isEqualTo("ONLINE_GAME_NOT_ACTIVE"),
                () -> assertThat(gameRow(gameId))
                        .containsEntry("status", "COMPLETED")
                        .containsEntry("outcome", "BLACK_WIN")
                        .containsEntry("termination_reason", "RESIGNATION")
                        .containsEntry("last_ply", 1),
                () -> assertThat(gameRow(gameId).get("ended_at")).isNotNull());
    }

    @Test
    void one_vote_keeps_game_active_and_second_vote_draws_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = startedGame(white, black);

        // when
        var first = post(white, gamePath(gameId, "/abandon"));
        var repeated = post(white, gamePath(gameId, "/abandon"));
        var afterFirst = gameRow(gameId);
        var opponentView = get(black, gamePath(gameId, ""));
        var moveAfterVote = move(white, gameId, 1, "e2e4");
        var second = post(black, gamePath(gameId, "/abandon"));

        // then
        assertAll(
                () -> assertThat(first.statusCode()).isEqualTo(200),
                () -> assertThat(repeated.statusCode()).isEqualTo(200),
                () -> assertThat(afterFirst).containsEntry("status", "ACTIVE"),
                () -> assertThat((Boolean) JsonPath.read(opponentView.body(), "$.white.abandonVoted"))
                        .isTrue(),
                () -> assertThat((Boolean) JsonPath.read(opponentView.body(), "$.black.abandonVoted"))
                        .isFalse(),
                () -> assertThat(moveAfterVote.statusCode()).isEqualTo(200),
                () -> assertThat(second.statusCode()).isEqualTo(200),
                () -> assertThat((String) JsonPath.read(second.body(), "$.terminationReason"))
                        .isEqualTo("MUTUAL_ABANDONMENT"),
                () -> assertThat(gameRow(gameId))
                        .containsEntry("status", "COMPLETED")
                        .containsEntry("outcome", "DRAW")
                        .containsEntry("termination_reason", "MUTUAL_ABANDONMENT"),
                () -> assertThat(voteRows(gameId)).hasSize(2));
    }

    @Test
    void simultaneous_votes_complete_one_draw_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = startedGame(white, black);

        // when
        var statuses = runConcurrently(List.of(
                () -> post(white, gamePath(gameId, "/abandon")).statusCode(),
                () -> post(black, gamePath(gameId, "/abandon")).statusCode()));

        // then
        assertAll(
                () -> assertThat(statuses).containsExactly(200, 200),
                () -> assertThat(voteRows(gameId)).hasSize(2),
                () -> assertThat(gameRow(gameId))
                        .containsEntry("status", "COMPLETED")
                        .containsEntry("outcome", "DRAW")
                        .containsEntry("termination_reason", "MUTUAL_ABANDONMENT"));
    }

    @Test
    void simultaneous_resignations_complete_game_once_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = startedGame(white, black);

        // when
        var statuses = runConcurrently(List.of(
                () -> post(white, gamePath(gameId, "/resign")).statusCode(),
                () -> post(black, gamePath(gameId, "/resign")).statusCode()));

        // then
        var game = gameRow(gameId);
        assertAll(
                () -> assertThat(statuses).containsExactlyInAnyOrder(200, 409),
                () -> assertThat(game)
                        .containsEntry("status", "COMPLETED")
                        .containsEntry("termination_reason", "RESIGNATION"),
                () -> assertThat(game.get("outcome")).isIn("WHITE_WIN", "BLACK_WIN"));
    }

    @Test
    void vote_before_game_starts_is_rejected_and_stores_nothing_ko() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = claimedGame(white, black);

        // when
        var vote = post(white, gamePath(gameId, "/abandon"));
        var resign = post(black, gamePath(gameId, "/resign"));

        // then
        assertAll(
                () -> assertThat(vote.statusCode()).isEqualTo(409),
                () -> assertThat(resign.statusCode()).isEqualTo(409),
                () -> assertThat(voteRows(gameId)).isEmpty(),
                () -> assertThat(gameRow(gameId)).containsEntry("status", "PENDING"));
    }

    private record Player(long accountId, String accessToken) {}

    private Player signUp() throws Exception {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var response = post(
                null,
                "/api/accounts/signup",
                """
                {"email":"player-%s%s","password":"%s","displayName":"F10%s","signupSkill":"BEGINNER"}"""
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
        post(white, gamePath(gameId, "/ready"));
        post(black, gamePath(gameId, "/ready"));

        return gameId;
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

    private List<Map<String, Object>> voteRows(long gameId) {
        return jdbcTemplate.queryForList("SELECT * FROM GameAbandonVote WHERE game_id = ?", gameId);
    }

    private HttpResponse<String> get(Player player, String path) throws Exception {
        var request = HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + player.accessToken())
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
