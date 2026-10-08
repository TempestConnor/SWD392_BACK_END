package com.swd392.backend.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
class AccountSqlServerIntegrationTest {

    private static final String TEST_EMAIL_DOMAIN = "@f01-it.test";
    private static final String PASSWORD = "correct-horse";

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @AfterEach
    void deleteTestAccounts() {
        var testAccounts = "SELECT account_id FROM Account WHERE email LIKE ?";
        jdbcTemplate.update(
                "DELETE FROM PlayerRating WHERE account_id IN (" + testAccounts + ")", "%" + TEST_EMAIL_DOMAIN);
        jdbcTemplate.update("DELETE FROM Account WHERE email LIKE ?", "%" + TEST_EMAIL_DOMAIN);
    }

    @Test
    void signup_intermediate_persists_hashed_account_and_800_classical_rating_ok() throws Exception {
        // given
        var email = uniqueEmail();
        var displayName = uniqueName("Int");

        // when
        var response = signUp(email, displayName, "INTERMEDIATE");

        // then
        var accountId = ((Number) JsonPath.read(response.body(), "$.account.id")).longValue();
        var storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM Account WHERE account_id = ?", String.class, accountId);
        var rating = jdbcTemplate.queryForMap(
                "SELECT mode_code, rating, starting_rating, games_played FROM PlayerRating WHERE account_id = ?",
                accountId);
        assertAll(
                () -> assertThat(response.statusCode()).isEqualTo(201),
                () -> assertThat((String) JsonPath.read(response.body(), "$.accessToken"))
                        .isNotBlank(),
                () -> assertThat(storedHash).startsWith("$2").isNotEqualTo(PASSWORD),
                () -> assertThat(rating)
                        .containsEntry("mode_code", "CLASSICAL")
                        .containsEntry("rating", 800)
                        .containsEntry("starting_rating", 800)
                        .containsEntry("games_played", 0));
    }

    @Test
    void signup_display_names_differing_only_in_case_are_distinct_ok() throws Exception {
        // given
        var suffix = shortId();

        // when
        var upper = signUp(uniqueEmail(), "Alex" + suffix, "BEGINNER");
        var lower = signUp(uniqueEmail(), "alex" + suffix, "BEGINNER");

        // then
        assertAll(() -> assertThat(upper.statusCode()).isEqualTo(201), () -> assertThat(lower.statusCode())
                .isEqualTo(201));
    }

    @Test
    void signup_email_differing_only_in_case_returns_409_ko() throws Exception {
        // given
        var email = uniqueEmail();
        signUp(email, uniqueName("First"), "BEGINNER");

        // when
        var duplicate = signUp(email.toUpperCase(), uniqueName("Second"), "BEGINNER");

        // then
        assertAll(
                () -> assertThat(duplicate.statusCode()).isEqualTo(409),
                () -> assertThat((String) JsonPath.read(duplicate.body(), "$.errorCode"))
                        .isEqualTo("EMAIL_ALREADY_REGISTERED"),
                () -> assertThat(countAccounts(email)).isEqualTo(1));
    }

    @Test
    void signup_duplicate_display_name_returns_409_ko() throws Exception {
        // given
        var displayName = uniqueName("Taken");
        signUp(uniqueEmail(), displayName, "BEGINNER");

        // when
        var duplicate = signUp(uniqueEmail(), displayName, "BEGINNER");

        // then
        assertAll(() -> assertThat(duplicate.statusCode()).isEqualTo(409), () -> assertThat(
                        (String) JsonPath.read(duplicate.body(), "$.errorCode"))
                .isEqualTo("DISPLAY_NAME_TAKEN"));
    }

    @Test
    void concurrent_signups_with_same_email_create_exactly_one_account_ok() throws Exception {
        // given
        var email = uniqueEmail();
        var names = List.of(uniqueName("RaceA"), uniqueName("RaceB"), uniqueName("RaceC"), uniqueName("RaceD"));
        var start = new CountDownLatch(1);

        // when
        List<Integer> statuses;
        try (var executor = Executors.newFixedThreadPool(names.size())) {
            var futures = names.stream()
                    .map(name -> executor.submit(() -> {
                        start.await();
                        return signUp(email, name, "ADVANCED").statusCode();
                    }))
                    .toList();
            start.countDown();
            statuses = futures.stream().map(this::await).toList();
        }

        // then
        var ratingRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM PlayerRating r JOIN Account a ON a.account_id = r.account_id WHERE a.email = ?",
                Integer.class,
                email);
        assertAll(
                () -> assertThat(statuses).containsOnly(201, 409),
                () -> assertThat(statuses).filteredOn(status -> status == 201).hasSize(1),
                () -> assertThat(countAccounts(email)).isEqualTo(1),
                () -> assertThat(ratingRows).isEqualTo(1));
    }

    @Test
    void signin_with_different_email_case_then_me_returns_account_ok() throws Exception {
        // given
        var email = uniqueEmail();
        var displayName = uniqueName("Me");
        signUp(email, displayName, "EXPERT");

        // when
        var signIn = post(
                "/api/accounts/signin",
                """
                {"email":"%s","password":"%s"}""".formatted(email.toUpperCase(), PASSWORD));
        String token = JsonPath.read(signIn.body(), "$.accessToken");
        var me = httpClient.send(
                HttpRequest.newBuilder(uri("/api/accounts/me"))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // then
        assertAll(
                () -> assertThat(signIn.statusCode()).isEqualTo(200),
                () -> assertThat(me.statusCode()).isEqualTo(200),
                () -> assertThat((String) JsonPath.read(me.body(), "$.displayName"))
                        .isEqualTo(displayName),
                () -> assertThat((Integer) JsonPath.read(me.body(), "$.ratings[0].rating"))
                        .isEqualTo(1600));
    }

    @Test
    void signin_wrong_password_returns_401_ko() throws Exception {
        // given
        var email = uniqueEmail();
        signUp(email, uniqueName("Wrong"), "BEGINNER");

        // when
        var signIn = post(
                "/api/accounts/signin",
                """
                {"email":"%s","password":"not-the-password"}""".formatted(email));

        // then
        assertAll(() -> assertThat(signIn.statusCode()).isEqualTo(401), () -> assertThat(
                        (String) JsonPath.read(signIn.body(), "$.errorCode"))
                .isEqualTo("INVALID_CREDENTIALS"));
    }

    private HttpResponse<String> signUp(String email, String displayName, String skill) throws Exception {
        return post(
                "/api/accounts/signup",
                """
                {"email":"%s","password":"%s","displayName":"%s","signupSkill":"%s"}"""
                        .formatted(email, PASSWORD, displayName, skill));
    }

    private HttpResponse<String> post(String path, String json) throws Exception {
        var request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private Integer countAccounts(String email) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM Account WHERE email = ?", Integer.class, email);
    }

    private Integer await(java.util.concurrent.Future<Integer> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String uniqueEmail() {
        return "player-" + shortId() + TEST_EMAIL_DOMAIN;
    }

    private String uniqueName(String prefix) {
        return prefix + shortId();
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
