package com.swd392.backend.account.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swd392.backend.account.mapper.AccountMapperImpl;
import com.swd392.backend.account.model.Account;
import com.swd392.backend.account.model.AccountRegistration;
import com.swd392.backend.account.model.AuthenticatedAccount;
import com.swd392.backend.account.model.SkillLevel;
import com.swd392.backend.account.service.AccountService;
import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.config.SecurityConfig;
import com.swd392.backend.rating.model.PlayerRating;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, AccountMapperImpl.class})
class AccountControllerTest {

    private static final String SIGNUP_URL = "/api/accounts/signup";
    private static final String SIGNIN_URL = "/api/accounts/signin";
    private static final String ME_URL = "/api/accounts/me";
    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @Test
    void signup_valid_request_returns_201_with_token_and_rating_ok() throws Exception {
        // given
        var body =
                """
                {"email":"player@example.com","password":"correct-horse","displayName":"Alex","signupSkill":"INTERMEDIATE"}
                """;
        when(accountService.register(any())).thenReturn(authenticated());

        // when / then
        mockMvc.perform(post(SIGNUP_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("token-value"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.account.id").value(42))
                .andExpect(jsonPath("$.account.displayName").value("Alex"))
                .andExpect(jsonPath("$.account.signupSkill").value("INTERMEDIATE"))
                .andExpect(jsonPath("$.account.ratings[0].mode").value("CLASSICAL"))
                .andExpect(jsonPath("$.account.ratings[0].rating").value(800))
                .andExpect(jsonPath("$.account.passwordHash").doesNotExist());
    }

    @Test
    void signup_trims_email_and_display_name_before_service_ok() throws Exception {
        // given
        var body =
                """
                {"email":"  player@example.com ","password":"  spaced pass  ","displayName":"  Alex Smith ","signupSkill":"EXPERT"}
                """;
        when(accountService.register(any())).thenReturn(authenticated());
        var captor = ArgumentCaptor.forClass(AccountRegistration.class);

        // when
        mockMvc.perform(post(SIGNUP_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        // then
        verify(accountService).register(captor.capture());
        var registration = captor.getValue();
        assertAll(
                () -> assertThat(registration.getEmail()).isEqualTo("player@example.com"),
                () -> assertThat(registration.getDisplayName()).isEqualTo("Alex Smith"),
                () -> assertThat(registration.getPassword()).isEqualTo("  spaced pass  "),
                () -> assertThat(registration.getSignupSkill()).isEqualTo(SkillLevel.EXPERT));
    }

    @Test
    void signup_invalid_fields_returns_400_problem_with_violations_ko() throws Exception {
        // given
        var body =
                """
                {"email":"not-an-email","password":"short","displayName":"Al  ex","signupSkill":"GRANDMASTER"}
                """;

        // when / then
        mockMvc.perform(post(SIGNUP_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[*].field")
                        .value(containsInAnyOrder("email", "password", "displayName", "signupSkill")));
        verify(accountService, never()).register(any());
    }

    @Test
    void signup_display_name_with_tab_returns_400_ko() throws Exception {
        // given
        var body =
                """
                {"email":"player@example.com","password":"correct-horse","displayName":"Al\\tex","signupSkill":"BEGINNER"}
                """;

        // when / then
        mockMvc.perform(post(SIGNUP_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("displayName"));
        verify(accountService, never()).register(any());
    }

    @Test
    void signup_password_over_72_characters_returns_400_ko() throws Exception {
        // given
        var body =
                """
                {"email":"player@example.com","password":"%s","displayName":"Alex","signupSkill":"BEGINNER"}
                """
                        .formatted("a".repeat(73));

        // when / then
        mockMvc.perform(post(SIGNUP_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("password"));
    }

    @Test
    void signup_duplicate_email_returns_409_problem_ko() throws Exception {
        // given
        var body =
                """
                {"email":"player@example.com","password":"correct-horse","displayName":"Alex","signupSkill":"BEGINNER"}
                """;
        when(accountService.register(any())).thenThrow(new AppException(AppErrorMessage.EMAIL_ALREADY_REGISTERED));

        // when / then
        mockMvc.perform(post(SIGNUP_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void signin_invalid_credentials_returns_401_problem_ko() throws Exception {
        // given
        var body = """
                {"email":"player@example.com","password":"wrong-password"}
                """;
        when(accountService.signIn(any())).thenThrow(new AppException(AppErrorMessage.INVALID_CREDENTIALS));

        // when / then
        mockMvc.perform(post(SIGNIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void signin_ok() throws Exception {
        // given
        var body = """
                {"email":"player@example.com","password":"correct-horse"}
                """;
        when(accountService.signIn(any())).thenReturn(authenticated());

        // when / then
        mockMvc.perform(post(SIGNIN_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token-value"));
    }

    @Test
    void me_without_token_returns_401_problem_ko() throws Exception {
        // when / then
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        verify(accountService, never()).getCurrentAccount(any());
    }

    @Test
    void me_with_malformed_token_returns_401_ko() throws Exception {
        // when / then
        mockMvc.perform(get(ME_URL).header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    void me_with_jwt_returns_current_account_ok() throws Exception {
        // given
        when(accountService.getCurrentAccount(42L)).thenReturn(account());

        // when / then
        mockMvc.perform(get(ME_URL).with(jwt().jwt(token -> token.subject("42"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.email").value("player@example.com"));
    }

    private Account account() {
        return Account.builder()
                .withId(42L)
                .withEmail("player@example.com")
                .withDisplayName("Alex")
                .withPasswordHash("$2a$12$hash")
                .withSignupSkill(SkillLevel.INTERMEDIATE)
                .withRatings(List.of(new PlayerRating(42L, "CLASSICAL", 800, 800, 0)))
                .build();
    }

    private AuthenticatedAccount authenticated() {
        return new AuthenticatedAccount(account(), "token-value", Instant.parse("2026-10-08T12:00:00Z"));
    }
}
