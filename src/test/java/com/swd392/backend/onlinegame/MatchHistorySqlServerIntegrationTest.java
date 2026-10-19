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
import java.util.UUID;
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
class MatchHistorySqlServerIntegrationTest {

    private static final String TEST_EMAIL_DOMAIN = "@f13-it.test";
    private static final String PASSWORD = "correct-horse";
    private static final String TEST_ACCOUNTS = "SELECT account_id FROM Account WHERE email LIKE ?";
    private static final String TEST_GAMES = "SELECT game_id FROM OnlineGame WHERE white_account_id IN ("
            + TEST_ACCOUNTS + ") OR black_account_id IN (" + TEST_ACCOUNTS + ")";
    private static final String RATED_INVITE = """
            {"hostColor":"WHITE"}""";
    private static final String UNRATED_INVITE = """
            {"hostColor":"WHITE","clockEnabled":false}""";
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
        jdbcTemplate.update("DELETE FROM RatingEvent WHERE game_id IN (" + TEST_GAMES + ")", pattern, pattern);
        jdbcTemplate.update("DELETE FROM GameAbandonVote WHERE game_id IN (" + TEST_GAMES + ")", pattern, pattern);
        jdbcTemplate.update("DELETE FROM GameMove WHERE game_id IN (" + TEST_GAMES + ")", pattern, pattern);
        jdbcTemplate.update("DELETE FROM Invitation WHERE host_account_id IN (" + TEST_ACCOUNTS + ")", pattern);
        jdbcTemplate.update("DELETE FROM OnlineGame WHERE game_id IN (" + TEST_GAMES + ")", pattern, pattern);
        jdbcTemplate.update("DELETE FROM PlayerRating WHERE account_id IN (" + TEST_ACCOUNTS + ")", pattern);
        jdbcTemplate.update("DELETE FROM Account WHERE email LIKE ?", pattern);
    }

    @Test
    void history_lists_only_completed_games_newest_first_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var outsider = signUp();
        var cancelled = claimedGame(white, black, RATED_INVITE);
        post(white, gamePath(cancelled, "/cancel"));
        var active = startedGame(white, black, RATED_INVITE);
        var older = startedGame(white, black, UNRATED_INVITE);
        post(white, gamePath(older, "/resign"));
        var newer = startedGame(white, black, UNRATED_INVITE);
        post(black, gamePath(newer, "/resign"));
        startedGame(white, outsider, UNRATED_INVITE);

        // when
        var history = get(white, "/api/match-history");
        var paged = get(white, "/api/match-history?page=1&size=1");

        // then
        assertAll(
                () -> assertThat(history.statusCode()).isEqualTo(200),
                () -> assertThat(longs(history.body(), "$.content[*].gameId")).containsExactly(newer, older),
                () -> assertThat(longs(history.body(), "$.content[*].gameId")).doesNotContain(cancelled, active),
                () -> assertThat((Integer) JsonPath.read(history.body(), "$.totalElements"))
                        .isEqualTo(2),
                () -> assertThat(longs(paged.body(), "$.content[*].gameId")).containsExactly(older),
                () -> assertThat((Boolean) JsonPath.read(paged.body(), "$.last"))
                        .isTrue());
    }

    @Test
    void completed_unrated_invite_is_shown_without_rating_change_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = startedGame(white, black, UNRATED_INVITE);
        post(white, gamePath(gameId, "/resign"));

        // when
        var history = get(black, "/api/match-history");

        // then
        assertAll(
                () -> assertThat(id(history.body(), "$.content[0].gameId")).isEqualTo(gameId),
                () -> assertThat((Boolean) JsonPath.read(history.body(), "$.content[0].rated"))
                        .isFalse(),
                () -> assertThat((Boolean) JsonPath.read(history.body(), "$.content[0].clock.enabled"))
                        .isFalse(),
                () -> assertThat((String) JsonPath.read(history.body(), "$.content[0].result"))
                        .isEqualTo("WIN"),
                () -> assertThat((String) JsonPath.read(history.body(), "$.content[0].terminationReason"))
                        .isEqualTo("RESIGNATION"),
                () -> assertThat((Object) JsonPath.read(history.body(), "$.content[0].ratingChange"))
                        .isNull());
    }

    @Test
    void rated_game_detail_shows_moves_and_both_rating_events_from_each_side_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = startedGame(white, black, RATED_INVITE);
        move(white, gameId, 1, "e2e4");
        move(black, gameId, 2, "e7e5");
        post(black, gamePath(gameId, "/resign"));

        // when
        var whiteDetail = get(white, "/api/match-history/" + gameId);
        var blackDetail = get(black, "/api/match-history/" + gameId);
        var blackList = get(black, "/api/match-history");

        // then
        assertAll(
                () -> assertThat(whiteDetail.statusCode()).isEqualTo(200),
                () -> assertThat((String) JsonPath.read(whiteDetail.body(), "$.result"))
                        .isEqualTo("WIN"),
                () -> assertThat((String) JsonPath.read(blackDetail.body(), "$.result"))
                        .isEqualTo("LOSS"),
                () -> assertThat((String) JsonPath.read(whiteDetail.body(), "$.outcome"))
                        .isEqualTo("WHITE_WIN"),
                () -> assertThat((List<String>) JsonPath.read(whiteDetail.body(), "$.moves[*].move"))
                        .containsExactly("e2e4", "e7e5"),
                () -> assertThat((Integer) JsonPath.read(whiteDetail.body(), "$.plyCount"))
                        .isEqualTo(2),
                () -> assertThat((Integer) JsonPath.read(whiteDetail.body(), "$.whiteRatingChange.ratingDelta"))
                        .isEqualTo(10),
                () -> assertThat((Integer) JsonPath.read(whiteDetail.body(), "$.blackRatingChange.ratingDelta"))
                        .isEqualTo(-10),
                () -> assertThat((Integer) JsonPath.read(whiteDetail.body(), "$.blackRatingChange.ratingAfter"))
                        .isEqualTo(390),
                () -> assertThat((Integer) JsonPath.read(blackList.body(), "$.content[0].ratingChange.ratingDelta"))
                        .isEqualTo(-10));
    }

    @Test
    void disconnect_forfeit_shows_perspective_result_and_reason_ok() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var gameId = startedGame(white, black, UNRATED_INVITE);
        ageGame(gameId, PAST_GRACE_SECONDS);
        jdbcTemplate.update("UPDATE OnlineGame SET white_last_seen_at = SYSUTCDATETIME() WHERE game_id = ?", gameId);
        onlineGameService.resolveActiveGame(gameId);

        // when
        var whiteHistory = get(white, "/api/match-history");
        var blackHistory = get(black, "/api/match-history");

        // then
        assertAll(
                () -> assertThat((String) JsonPath.read(whiteHistory.body(), "$.content[0].result"))
                        .isEqualTo("WIN"),
                () -> assertThat((String) JsonPath.read(blackHistory.body(), "$.content[0].result"))
                        .isEqualTo("LOSS"),
                () -> assertThat((String) JsonPath.read(blackHistory.body(), "$.content[0].terminationReason"))
                        .isEqualTo("DISCONNECT"),
                () -> assertThat((String) JsonPath.read(blackHistory.body(), "$.content[0].opponent.displayName"))
                        .startsWith("F13"));
    }

    @Test
    void history_detail_is_refused_for_active_and_foreign_games_ko() throws Exception {
        // given
        var white = signUp();
        var black = signUp();
        var outsider = signUp();
        var active = startedGame(white, black, RATED_INVITE);
        var finished = startedGame(white, black, UNRATED_INVITE);
        post(white, gamePath(finished, "/resign"));

        // when
        var activeDetail = get(white, "/api/match-history/" + active);
        var foreignDetail = get(outsider, "/api/match-history/" + finished);
        var outsiderList = get(outsider, "/api/match-history");

        // then
        assertAll(
                () -> assertThat(activeDetail.statusCode()).isEqualTo(404),
                () -> assertThat((String) JsonPath.read(activeDetail.body(), "$.errorCode"))
                        .isEqualTo("GAME_NOT_IN_HISTORY"),
                () -> assertThat(foreignDetail.statusCode()).isEqualTo(404),
                () -> assertThat((String) JsonPath.read(foreignDetail.body(), "$.errorCode"))
                        .isEqualTo("ONLINE_GAME_NOT_FOUND"),
                () -> assertThat((Integer) JsonPath.read(outsiderList.body(), "$.totalElements"))
                        .isZero());
    }

    private record Player(long accountId, String accessToken) {}

    private Player signUp() throws Exception {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var response = post(
                null,
                "/api/accounts/signup",
                """
                {"email":"player-%s%s","password":"%s","displayName":"F13%s","signupSkill":"BEGINNER"}"""
                        .formatted(suffix, TEST_EMAIL_DOMAIN, PASSWORD, suffix));

        return new Player(id(response.body(), "$.account.id"), JsonPath.read(response.body(), "$.accessToken"));
    }

    private long claimedGame(Player white, Player black, String invitation) throws Exception {
        var created = post(white, "/api/invitations", invitation);
        var token = (String) JsonPath.read(created.body(), "$.token");
        var claim = post(black, "/api/invitations/claim", """
                {"token":"%s"}""".formatted(token));

        return id(claim.body(), "$.id");
    }

    private long startedGame(Player white, Player black, String invitation) throws Exception {
        var gameId = claimedGame(white, black, invitation);
        post(white, gamePath(gameId, "/ready"));
        post(black, gamePath(gameId, "/ready"));

        return gameId;
    }

    // Moves every timestamp back together so the schema's ordering checks still hold.
    private void ageGame(long gameId, int seconds) {
        jdbcTemplate.update(AGE_GAME, seconds, gameId);
    }

    private HttpResponse<String> move(Player player, long gameId, int ply, String move) throws Exception {
        return post(
                player, gamePath(gameId, "/moves"), """
                {"ply":%d,"move":"%s"}""".formatted(ply, move));
    }

    private String gamePath(long gameId, String action) {
        return "/api/online-games/" + gameId + action;
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

    private List<Long> longs(String body, String path) {
        var values = JsonPath.<List<Number>>read(body, path);

        return values.stream().map(Number::longValue).toList();
    }

    private long id(String body, String path) {
        return ((Number) JsonPath.read(body, path)).longValue();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
