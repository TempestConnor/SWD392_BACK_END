package com.swd392.backend.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swd392.backend.account.entity.AccountEntity;
import com.swd392.backend.account.mapper.AccountEntityMapper;
import com.swd392.backend.account.model.AccountRegistration;
import com.swd392.backend.account.model.Credentials;
import com.swd392.backend.account.model.SkillLevel;
import com.swd392.backend.account.repository.AccountRepository;
import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.rating.model.PlayerRating;
import com.swd392.backend.rating.service.RatingService;
import com.swd392.backend.security.model.AccessToken;
import com.swd392.backend.security.service.TokenService;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    private static final String EMAIL = "player@example.com";
    private static final String PASSWORD = "correct-horse";
    private static final String PASSWORD_HASH = "$2a$12$hash";
    private static final String DISPLAY_NAME = "Alex";
    private static final Long ACCOUNT_ID = 42L;
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-08T12:00:00Z");

    @Mock
    private AccountRepository accountRepository;

    @Spy
    private AccountEntityMapper accountEntityMapper = Mappers.getMapper(AccountEntityMapper.class);

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RatingService ratingService;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private AccountServiceImpl accountService;

    @Test
    void register_intermediate_initializes_800_rating_and_issues_token_ok() {
        // given
        var registration = registration(PASSWORD, SkillLevel.INTERMEDIATE);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD_HASH);
        when(accountRepository.saveAndFlush(any(AccountEntity.class))).thenAnswer(invocation -> {
            AccountEntity entity = invocation.getArgument(0);
            entity.setId(ACCOUNT_ID);
            return entity;
        });
        when(ratingService.initializeRatings(ACCOUNT_ID, 800)).thenReturn(List.of(classicalRating(800)));
        when(tokenService.issueAccessToken(eq("42"), anyCollection())).thenReturn(token());
        var savedCaptor = ArgumentCaptor.forClass(AccountEntity.class);

        // when
        var result = accountService.register(registration);

        // then
        verify(accountRepository).saveAndFlush(savedCaptor.capture());
        var saved = savedCaptor.getValue();
        assertAll(
                () -> assertThat(saved.getPasswordHash()).isEqualTo(PASSWORD_HASH),
                () -> assertThat(saved.getEmail()).isEqualTo(EMAIL),
                () -> assertThat(saved.getDisplayName()).isEqualTo(DISPLAY_NAME),
                () -> assertThat(saved.getSignupSkill()).isEqualTo("INTERMEDIATE"),
                () -> assertThat(result.getAccessToken()).isEqualTo("token-value"),
                () -> assertThat(result.getExpiresAt()).isEqualTo(EXPIRES_AT),
                () -> assertThat(result.getAccount().getId()).isEqualTo(ACCOUNT_ID),
                () -> assertThat(result.getAccount().getRatings())
                        .extracting(PlayerRating::getRating)
                        .containsExactly(800));
    }

    @Test
    void register_existing_email_ko() {
        // given
        var registration = registration(PASSWORD, SkillLevel.BEGINNER);
        when(accountRepository.existsByEmail(EMAIL)).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> accountService.register(registration))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.EMAIL_ALREADY_REGISTERED);
        verify(accountRepository, never()).saveAndFlush(any());
        verify(ratingService, never()).initializeRatings(any(), anyInt());
    }

    @Test
    void register_existing_display_name_ko() {
        // given
        var registration = registration(PASSWORD, SkillLevel.BEGINNER);
        when(accountRepository.existsByDisplayName(DISPLAY_NAME)).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> accountService.register(registration))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.DISPLAY_NAME_TAKEN);
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_password_over_72_utf8_bytes_ko() {
        // given
        var registration = registration("é".repeat(37), SkillLevel.BEGINNER);

        // when / then
        assertThatThrownBy(() -> accountService.register(registration))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.PASSWORD_TOO_LONG);
        verify(passwordEncoder, never()).encode(anyString());
        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void register_concurrent_email_unique_violation_maps_to_conflict_ko() {
        // given
        var registration = registration(PASSWORD, SkillLevel.BEGINNER);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD_HASH);
        when(accountRepository.saveAndFlush(any(AccountEntity.class)))
                .thenThrow(uniqueViolation("Violation of UNIQUE KEY constraint 'UQ_Account_email'."));

        // when / then
        assertThatThrownBy(() -> accountService.register(registration))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.EMAIL_ALREADY_REGISTERED);
        verify(ratingService, never()).initializeRatings(any(), anyInt());
    }

    @Test
    void register_concurrent_display_name_unique_violation_maps_to_conflict_ko() {
        // given
        var registration = registration(PASSWORD, SkillLevel.BEGINNER);
        when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD_HASH);
        when(accountRepository.saveAndFlush(any(AccountEntity.class)))
                .thenThrow(uniqueViolation("Violation of UNIQUE KEY constraint 'UQ_Account_name'."));

        // when / then
        assertThatThrownBy(() -> accountService.register(registration))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.DISPLAY_NAME_TAKEN);
    }

    @Test
    void register_unrelated_integrity_violation_is_rethrown_ko() {
        // given
        var registration = registration(PASSWORD, SkillLevel.BEGINNER);
        var violation = uniqueViolation("The INSERT statement conflicted with the CHECK constraint 'CK_Account_name'.");
        when(passwordEncoder.encode(PASSWORD)).thenReturn(PASSWORD_HASH);
        when(accountRepository.saveAndFlush(any(AccountEntity.class))).thenThrow(violation);

        // when / then
        assertThatThrownBy(() -> accountService.register(registration)).isSameAs(violation);
    }

    @Test
    void sign_in_ok() {
        // given
        var credentials = new Credentials(EMAIL, PASSWORD);
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(storedAccount()));
        when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(ratingService.getRatings(ACCOUNT_ID)).thenReturn(List.of(classicalRating(1200)));
        when(tokenService.issueAccessToken(eq("42"), anyCollection())).thenReturn(token());

        // when
        var result = accountService.signIn(credentials);

        // then
        assertAll(
                () -> assertThat(result.getAccessToken()).isEqualTo("token-value"),
                () -> assertThat(result.getAccount().getDisplayName()).isEqualTo(DISPLAY_NAME),
                () -> assertThat(result.getAccount().getRatings())
                        .extracting(PlayerRating::getRating)
                        .containsExactly(1200));
    }

    @Test
    void sign_in_admin_token_carries_admin_role_ok() {
        // given
        var credentials = new Credentials(EMAIL, PASSWORD);
        var admin = storedAccount();
        admin.setRoles(Set.of("ADMIN"));
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches(PASSWORD, PASSWORD_HASH)).thenReturn(true);
        when(tokenService.issueAccessToken(eq("42"), anyCollection())).thenReturn(token());

        // when
        accountService.signIn(credentials);

        // then
        verify(tokenService).issueAccessToken("42", List.of("ADMIN"));
    }

    @Test
    void sign_in_unknown_email_ko() {
        // given
        var credentials = new Credentials(EMAIL, PASSWORD);
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> accountService.signIn(credentials))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.INVALID_CREDENTIALS);
        verify(tokenService, never()).issueAccessToken(anyString(), anyCollection());
    }

    @Test
    void sign_in_wrong_password_ko() {
        // given
        var credentials = new Credentials(EMAIL, "wrong-password");
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(storedAccount()));
        when(passwordEncoder.matches("wrong-password", PASSWORD_HASH)).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> accountService.signIn(credentials))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.INVALID_CREDENTIALS);
        verify(tokenService, never()).issueAccessToken(anyString(), anyCollection());
    }

    @Test
    void get_current_account_ok() {
        // given
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.of(storedAccount()));
        when(ratingService.getRatings(ACCOUNT_ID)).thenReturn(List.of(classicalRating(800)));

        // when
        var result = accountService.getCurrentAccount(ACCOUNT_ID);

        // then
        assertAll(
                () -> assertThat(result.getEmail()).isEqualTo(EMAIL),
                () -> assertThat(result.getSignupSkill()).isEqualTo(SkillLevel.INTERMEDIATE),
                () -> assertThat(result.getRatings()).hasSize(1));
    }

    @Test
    void get_current_account_not_found_ko() {
        // given
        when(accountRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> accountService.getCurrentAccount(ACCOUNT_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.CURRENT_ACCOUNT_NOT_FOUND);
    }

    private AccountRegistration registration(String password, SkillLevel skill) {
        return new AccountRegistration(EMAIL, password, DISPLAY_NAME, skill);
    }

    private AccountEntity storedAccount() {
        return AccountEntity.builder()
                .withId(ACCOUNT_ID)
                .withEmail(EMAIL)
                .withDisplayName(DISPLAY_NAME)
                .withPasswordHash(PASSWORD_HASH)
                .withSignupSkill("INTERMEDIATE")
                .build();
    }

    private PlayerRating classicalRating(int rating) {
        return new PlayerRating(ACCOUNT_ID, "CLASSICAL", rating, rating, 0);
    }

    private AccessToken token() {
        return new AccessToken("token-value", EXPIRES_AT);
    }

    private DataIntegrityViolationException uniqueViolation(String sqlMessage) {
        return new DataIntegrityViolationException("could not execute statement", new SQLException(sqlMessage));
    }
}
