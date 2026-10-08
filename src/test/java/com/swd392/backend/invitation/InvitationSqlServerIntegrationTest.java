package com.swd392.backend.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.List;
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
class InvitationSqlServerIntegrationTest {

    private static final String TEST_EMAIL_DOMAIN = "@f04-it.test";
    private static final String PASSWORD = "correct-horse";
    private static final String TEST_ACCOUNTS = "SELECT account_id FROM Account WHERE email LIKE ?";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void claim_default_invitation_persists_rated_pending_game_with_host_color_ok() throws Exception {
        // given
        var host = signUp();
        var joiner = signUp();
        var created = createInvitation(host, """
                {"hostColor":"BLACK"}""");
        var token = (String) JsonPath.read(created.body(), "$.token");
        var invitationId = id(created.body(), "$.invitation.id");

        // when
        var claim = claim(joiner, token);

        // then
        var gameId = id(claim.body(), "$.id");
        var game = jdbcTemplate.queryForMap("SELECT * FROM OnlineGame WHERE game_id = ?", gameId);
        var invitation = jdbcTemplate.queryForMap("SELECT * FROM Invitation WHERE invitation_id = ?", invitationId);
        var createdAt = ((Timestamp) game.get("created_at")).toInstant();
        var deadline = ((Timestamp) game.get("ready_deadline_at")).toInstant();
        var hostView = get(host, "/api/online-games/" + gameId);
        var invitationView = get(host, "/api/invitations/" + invitationId);
        assertAll(
                () -> assertThat(created.statusCode()).isEqualTo(201),
                () -> assertThat(claim.statusCode()).isEqualTo(200),
                () -> assertThat((String) JsonPath.read(claim.body(), "$.viewerColor"))
                        .isEqualTo("WHITE"),
                () -> assertThat(game)
                        .containsEntry("source", "INVITE")
                        .containsEntry("status", "PENDING")
                        .containsEntry("is_rated", true)
                        .containsEntry("initial_seconds", 1800)
                        .containsEntry("increment_seconds", 10)
                        .containsEntry("white_account_id", joiner.accountId())
                        .containsEntry("black_account_id", host.accountId())
                        .containsEntry("white_remaining_ms", 1_800_000L)
                        .containsEntry("last_ply", 0),
                () -> assertThat(Duration.between(createdAt, deadline)).isEqualTo(Duration.ofMinutes(3)),
                () -> assertThat(invitation)
                        .containsEntry("joiner_account_id", joiner.accountId())
                        .containsEntry("game_id", gameId)
                        .containsEntry("host_color", "BLACK"),
                () -> assertThat(invitation.get("claimed_at")).isEqualTo(game.get("created_at")),
                () -> assertThat((String) JsonPath.read(hostView.body(), "$.viewerColor"))
                        .isEqualTo("BLACK"),
                () -> assertThat((String) JsonPath.read(invitationView.body(), "$.status"))
                        .isEqualTo("CLAIMED"),
                () -> assertThat(id(invitationView.body(), "$.gameId")).isEqualTo(gameId));
    }

    @Test
    void concurrent_claims_yield_exactly_one_joiner_ok() throws Exception {
        // given
        var host = signUp();
        var joiners = List.of(signUp(), signUp(), signUp(), signUp());
        var created = createInvitation(host, """
                {"hostColor":"RANDOM"}""");
        var token = (String) JsonPath.read(created.body(), "$.token");

        // when
        var statuses = runConcurrently(joiners.stream()
                .map(joiner -> (Callable<Integer>) () -> claim(joiner, token).statusCode())
                .toList());

        // then
        var games = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM OnlineGame WHERE white_account_id = ? OR black_account_id = ?",
                Integer.class,
                host.accountId(),
                host.accountId());
        assertAll(
                () -> assertThat(statuses).filteredOn(status -> status == 200).hasSize(1),
                () -> assertThat(statuses).filteredOn(status -> status == 409).hasSize(3),
                () -> assertThat(games).isEqualTo(1));
    }

    @Test
    void claim_racing_cancel_leaves_exactly_one_outcome_ok() throws Exception {
        // given
        var host = signUp();
        var joiner = signUp();
        var created = createInvitation(host, """
                {"hostColor":"WHITE"}""");
        var token = (String) JsonPath.read(created.body(), "$.token");
        var invitationId = id(created.body(), "$.invitation.id");

        // when
        var statuses = runConcurrently(List.of(() -> claim(joiner, token).statusCode(), () -> post(
                        host, "/api/invitations/" + invitationId + "/cancel", "")
                .statusCode()));

        // then
        var row = jdbcTemplate.queryForMap(
                "SELECT claimed_at, cancelled_at FROM Invitation WHERE invitation_id = ?", invitationId);
        var claimed = row.get("claimed_at") != null;
        var cancelled = row.get("cancelled_at") != null;
        var games = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM OnlineGame WHERE white_account_id = ?", Integer.class, host.accountId());
        assertAll(
                () -> assertThat(claimed ^ cancelled).isTrue(),
                () -> assertThat(statuses).filteredOn(status -> status == 200).hasSize(1),
                () -> assertThat(games).isEqualTo(claimed ? 1 : 0));
    }

    @Test
    void claim_changed_clock_invitation_creates_unrated_game_ok() throws Exception {
        // given
        var host = signUp();
        var joiner = signUp();
        var created = createInvitation(
                host, """
                {"hostColor":"WHITE","initialMinutes":5,"incrementSeconds":0}""");
        var token = (String) JsonPath.read(created.body(), "$.token");

        // when
        var claim = claim(joiner, token);

        // then
        var game = jdbcTemplate.queryForMap("SELECT * FROM OnlineGame WHERE game_id = ?", id(claim.body(), "$.id"));
        assertAll(
                () -> assertThat((Boolean) JsonPath.read(created.body(), "$.invitation.rated"))
                        .isFalse(),
                () -> assertThat(game)
                        .containsEntry("is_rated", false)
                        .containsEntry("initial_seconds", 300)
                        .containsEntry("increment_seconds", 0)
                        .containsEntry("white_account_id", host.accountId()));
    }

    @Test
    void claim_disabled_clock_invitation_creates_unrated_game_without_clocks_ok() throws Exception {
        // given
        var host = signUp();
        var joiner = signUp();
        var created = createInvitation(host, """
                {"hostColor":"WHITE","clockEnabled":false}""");
        var token = (String) JsonPath.read(created.body(), "$.token");

        // when
        var claim = claim(joiner, token);

        // then
        var game = jdbcTemplate.queryForMap("SELECT * FROM OnlineGame WHERE game_id = ?", id(claim.body(), "$.id"));
        assertAll(
                () -> assertThat(game).containsEntry("is_rated", false).containsEntry("clock_enabled", false),
                () -> assertThat(game.get("white_remaining_ms")).isNull(),
                () -> assertThat(game.get("initial_seconds")).isNull());
    }

    @Test
    void claim_expired_invitation_returns_410_and_creates_no_game_ko() throws Exception {
        // given
        var host = signUp();
        var joiner = signUp();
        var created = createInvitation(host, """
                {"hostColor":"WHITE"}""");
        var token = (String) JsonPath.read(created.body(), "$.token");
        jdbcTemplate.update(
                "UPDATE Invitation SET created_at = DATEADD(minute, -16, created_at),"
                        + " expires_at = DATEADD(minute, -16, expires_at) WHERE invitation_id = ?",
                id(created.body(), "$.invitation.id"));

        // when
        var claim = claim(joiner, token);

        // then
        assertAll(
                () -> assertThat(claim.statusCode()).isEqualTo(410),
                () -> assertThat((String) JsonPath.read(claim.body(), "$.errorCode"))
                        .isEqualTo("INVITATION_EXPIRED"),
                () -> assertThat(countGames(host)).isZero());
    }

    @Test
    void claim_cancelled_invitation_returns_410_ko() throws Exception {
        // given
        var host = signUp();
        var joiner = signUp();
        var created = createInvitation(host, """
                {"hostColor":"WHITE"}""");
        var token = (String) JsonPath.read(created.body(), "$.token");
        var cancel = post(host, "/api/invitations/" + id(created.body(), "$.invitation.id") + "/cancel", "");

        // when
        var claim = claim(joiner, token);

        // then
        assertAll(
                () -> assertThat(cancel.statusCode()).isEqualTo(200),
                () -> assertThat((String) JsonPath.read(cancel.body(), "$.status"))
                        .isEqualTo("CANCELLED"),
                () -> assertThat(claim.statusCode()).isEqualTo(410),
                () -> assertThat((String) JsonPath.read(claim.body(), "$.errorCode"))
                        .isEqualTo("INVITATION_CANCELLED"),
                () -> assertThat(countGames(host)).isZero());
    }

    @Test
    void host_claiming_own_invitation_returns_409_ko() throws Exception {
        // given
        var host = signUp();
        var created = createInvitation(host, """
                {"hostColor":"WHITE"}""");
        var token = (String) JsonPath.read(created.body(), "$.token");

        // when
        var claim = claim(host, token);

        // then
        assertAll(
                () -> assertThat(claim.statusCode()).isEqualTo(409),
                () -> assertThat((String) JsonPath.read(claim.body(), "$.errorCode"))
                        .isEqualTo("CANNOT_JOIN_OWN_INVITATION"),
                () -> assertThat(countGames(host)).isZero());
    }

    private record Player(long accountId, String accessToken) {}

    private Player signUp() throws Exception {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var response = post(
                null,
                "/api/accounts/signup",
                """
                {"email":"player-%s%s","password":"%s","displayName":"F04%s","signupSkill":"BEGINNER"}"""
                        .formatted(suffix, TEST_EMAIL_DOMAIN, PASSWORD, suffix));

        return new Player(id(response.body(), "$.account.id"), JsonPath.read(response.body(), "$.accessToken"));
    }

    private HttpResponse<String> createInvitation(Player host, String json) throws Exception {
        return post(host, "/api/invitations", json);
    }

    private HttpResponse<String> claim(Player joiner, String token) throws Exception {
        return post(joiner, "/api/invitations/claim", """
                {"token":"%s"}""".formatted(token));
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

    private Integer countGames(Player player) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM OnlineGame WHERE white_account_id = ? OR black_account_id = ?",
                Integer.class,
                player.accountId(),
                player.accountId());
    }

    private long id(String body, String path) {
        return ((Number) JsonPath.read(body, path)).longValue();
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
