package com.swd392.backend.invitation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.invitation.entity.InvitationEntity;
import com.swd392.backend.invitation.mapper.InvitationEntityMapper;
import com.swd392.backend.invitation.model.HostColor;
import com.swd392.backend.invitation.model.InvitationRequest;
import com.swd392.backend.invitation.model.InvitationStatus;
import com.swd392.backend.invitation.repository.InvitationRepository;
import com.swd392.backend.onlinegame.model.GameSource;
import com.swd392.backend.onlinegame.model.OnlineGame;
import com.swd392.backend.onlinegame.service.OnlineGameService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvitationServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-08T10:00:00.123Z");
    private static final Instant EXPIRES_AT = NOW.plus(Duration.ofMinutes(15));
    private static final Long HOST_ID = 1L;
    private static final Long JOINER_ID = 2L;
    private static final Long OTHER_ID = 3L;
    private static final Long INVITATION_ID = 10L;
    private static final Long GAME_ID = 100L;
    private static final String TOKEN = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQ";

    @Mock
    private InvitationRepository invitationRepository;

    @Spy
    private InvitationEntityMapper invitationEntityMapper = Mappers.getMapper(InvitationEntityMapper.class);

    @Mock
    private OnlineGameService onlineGameService;

    @Mock
    private Clock clock;

    @InjectMocks
    private InvitationServiceImpl invitationService;

    @BeforeEach
    void fixClock() {
        lenient().when(clock.instant()).thenReturn(NOW);
    }

    @Test
    void create_without_clock_settings_is_rated_30_plus_10_expiring_in_15_minutes_ok() {
        // given
        var request = request(null, null, null);
        when(invitationRepository.saveAndFlush(any(InvitationEntity.class))).thenAnswer(this::assignId);
        var captor = ArgumentCaptor.forClass(InvitationEntity.class);

        // when
        var created = invitationService.create(HOST_ID, request);

        // then
        verify(invitationRepository).saveAndFlush(captor.capture());
        var saved = captor.getValue();
        var invitation = created.getInvitation();
        assertAll(
                () -> assertThat(created.getToken()).matches("[A-Za-z0-9_-]{43}"),
                () -> assertThat(saved.getTokenHash()).isEqualTo(sha256(created.getToken())),
                () -> assertThat(saved.getHostAccountId()).isEqualTo(HOST_ID),
                () -> assertThat(saved.getHostColor()).isEqualTo("WHITE"),
                () -> assertThat(saved.getVariantCode()).isEqualTo("STANDARD"),
                () -> assertThat(saved.getModeCode()).isEqualTo("CLASSICAL"),
                () -> assertThat(saved.getCreatedAt()).isEqualTo(NOW),
                () -> assertThat(saved.getExpiresAt()).isEqualTo(EXPIRES_AT),
                () -> assertThat(invitation.isClockEnabled()).isTrue(),
                () -> assertThat(invitation.getInitialSeconds()).isEqualTo(1800),
                () -> assertThat(invitation.getIncrementSeconds()).isEqualTo(10),
                () -> assertThat(invitation.isRated()).isTrue(),
                () -> assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.OPEN));
    }

    @Test
    void create_tokens_are_unique_per_invitation_ok() {
        // given
        when(invitationRepository.saveAndFlush(any(InvitationEntity.class))).thenAnswer(this::assignId);

        // when
        var first = invitationService.create(HOST_ID, request(null, null, null));
        var second = invitationService.create(HOST_ID, request(null, null, null));

        // then
        assertThat(first.getToken()).isNotEqualTo(second.getToken());
    }

    @Test
    void create_with_changed_increment_is_unrated_ok() {
        // given
        when(invitationRepository.saveAndFlush(any(InvitationEntity.class))).thenAnswer(this::assignId);

        // when
        var created = invitationService.create(HOST_ID, request(true, 30, 5));

        // then
        var invitation = created.getInvitation();
        assertAll(
                () -> assertThat(invitation.getInitialSeconds()).isEqualTo(1800),
                () -> assertThat(invitation.getIncrementSeconds()).isEqualTo(5),
                () -> assertThat(invitation.isRated()).isFalse());
    }

    @Test
    void create_with_explicit_default_clock_is_rated_ok() {
        // given
        when(invitationRepository.saveAndFlush(any(InvitationEntity.class))).thenAnswer(this::assignId);

        // when
        var created = invitationService.create(HOST_ID, request(true, 30, 10));

        // then
        assertThat(created.getInvitation().isRated()).isTrue();
    }

    @Test
    void create_with_disabled_clock_is_unrated_without_timing_ok() {
        // given
        when(invitationRepository.saveAndFlush(any(InvitationEntity.class))).thenAnswer(this::assignId);

        // when
        var created = invitationService.create(HOST_ID, request(false, null, null));

        // then
        var invitation = created.getInvitation();
        assertAll(
                () -> assertThat(invitation.isClockEnabled()).isFalse(),
                () -> assertThat(invitation.getInitialSeconds()).isNull(),
                () -> assertThat(invitation.getIncrementSeconds()).isNull(),
                () -> assertThat(invitation.isRated()).isFalse());
    }

    @Test
    void create_with_disabled_clock_and_increment_ko() {
        // given
        var request = request(false, null, 10);

        // when / then
        assertThatThrownBy(() -> invitationService.create(HOST_ID, request))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.DISABLED_CLOCK_HAS_TIMING);
        verify(invitationRepository, never()).saveAndFlush(any());
    }

    @Test
    void claim_open_invitation_creates_pending_game_with_host_as_chosen_color_ok() {
        // given
        var entity = openInvitation("BLACK", true, 1800, 10);
        when(invitationRepository.findForUpdateByTokenHash(sha256(TOKEN))).thenReturn(Optional.of(entity));
        when(onlineGameService.createPendingGame(any())).thenReturn(gameWithId());
        when(onlineGameService.getGame(GAME_ID, JOINER_ID)).thenReturn(gameWithId());
        var gameCaptor = ArgumentCaptor.forClass(OnlineGame.class);

        // when
        var result = invitationService.claim(TOKEN, JOINER_ID);

        // then
        verify(onlineGameService).createPendingGame(gameCaptor.capture());
        verify(invitationRepository).saveAndFlush(entity);
        var game = gameCaptor.getValue();
        assertAll(
                () -> assertThat(result.getId()).isEqualTo(GAME_ID),
                () -> assertThat(game.getSource()).isEqualTo(GameSource.INVITE),
                () -> assertThat(game.getWhiteAccountId()).isEqualTo(JOINER_ID),
                () -> assertThat(game.getBlackAccountId()).isEqualTo(HOST_ID),
                () -> assertThat(game.isRated()).isTrue(),
                () -> assertThat(game.getInitialSeconds()).isEqualTo(1800),
                () -> assertThat(game.getIncrementSeconds()).isEqualTo(10),
                () -> assertThat(game.getCreatedAt()).isEqualTo(NOW),
                () -> assertThat(entity.getJoinerAccountId()).isEqualTo(JOINER_ID),
                () -> assertThat(entity.getGameId()).isEqualTo(GAME_ID),
                () -> assertThat(entity.getClaimedAt()).isEqualTo(NOW));
    }

    @Test
    void claim_random_color_assigns_host_and_joiner_opposite_colors_ok() {
        // given
        var entity = openInvitation("RANDOM", true, 1800, 10);
        when(invitationRepository.findForUpdateByTokenHash(sha256(TOKEN))).thenReturn(Optional.of(entity));
        when(onlineGameService.createPendingGame(any())).thenReturn(gameWithId());
        var gameCaptor = ArgumentCaptor.forClass(OnlineGame.class);

        // when
        invitationService.claim(TOKEN, JOINER_ID);

        // then
        verify(onlineGameService).createPendingGame(gameCaptor.capture());
        var game = gameCaptor.getValue();
        assertThat(List.of(game.getWhiteAccountId(), game.getBlackAccountId()))
                .containsExactlyInAnyOrder(HOST_ID, JOINER_ID);
    }

    @Test
    void claim_disabled_clock_creates_unrated_game_ok() {
        // given
        var entity = openInvitation("WHITE", false, null, null);
        when(invitationRepository.findForUpdateByTokenHash(sha256(TOKEN))).thenReturn(Optional.of(entity));
        when(onlineGameService.createPendingGame(any())).thenReturn(gameWithId());
        var gameCaptor = ArgumentCaptor.forClass(OnlineGame.class);

        // when
        invitationService.claim(TOKEN, JOINER_ID);

        // then
        verify(onlineGameService).createPendingGame(gameCaptor.capture());
        var game = gameCaptor.getValue();
        assertAll(
                () -> assertThat(game.getWhiteAccountId()).isEqualTo(HOST_ID),
                () -> assertThat(game.isRated()).isFalse(),
                () -> assertThat(game.isClockEnabled()).isFalse());
    }

    @Test
    void claim_unknown_token_ko() {
        // given
        when(invitationRepository.findForUpdateByTokenHash(any())).thenReturn(Optional.empty());

        // when / then
        assertClaimFails(AppErrorMessage.INVITATION_NOT_FOUND);
    }

    @Test
    void claim_by_host_ko() {
        // given
        var entity = openInvitation("WHITE", true, 1800, 10);
        when(invitationRepository.findForUpdateByTokenHash(sha256(TOKEN))).thenReturn(Optional.of(entity));

        // when / then
        assertThatThrownBy(() -> invitationService.claim(TOKEN, HOST_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.CANNOT_JOIN_OWN_INVITATION);
        verify(onlineGameService, never()).createPendingGame(any());
    }

    @Test
    void claim_at_exact_expiry_instant_ko() {
        // given
        var entity = openInvitation("WHITE", true, 1800, 10);
        when(invitationRepository.findForUpdateByTokenHash(sha256(TOKEN))).thenReturn(Optional.of(entity));
        when(clock.instant()).thenReturn(EXPIRES_AT);

        // when / then
        assertClaimFails(AppErrorMessage.INVITATION_EXPIRED);
    }

    @Test
    void claim_one_millisecond_before_expiry_ok() {
        // given
        var entity = openInvitation("WHITE", true, 1800, 10);
        when(invitationRepository.findForUpdateByTokenHash(sha256(TOKEN))).thenReturn(Optional.of(entity));
        when(clock.instant()).thenReturn(EXPIRES_AT.minusMillis(1));
        when(onlineGameService.createPendingGame(any())).thenReturn(gameWithId());
        when(onlineGameService.getGame(GAME_ID, JOINER_ID)).thenReturn(gameWithId());

        // when
        var result = invitationService.claim(TOKEN, JOINER_ID);

        // then
        assertThat(result.getId()).isEqualTo(GAME_ID);
    }

    @Test
    void claim_cancelled_invitation_ko() {
        // given
        var entity = openInvitation("WHITE", true, 1800, 10);
        entity.setCancelledAt(NOW);
        when(invitationRepository.findForUpdateByTokenHash(sha256(TOKEN))).thenReturn(Optional.of(entity));

        // when / then
        assertClaimFails(AppErrorMessage.INVITATION_CANCELLED);
    }

    @Test
    void claim_invitation_claimed_by_someone_else_is_full_ko() {
        // given
        var entity = claimedInvitation();
        when(invitationRepository.findForUpdateByTokenHash(sha256(TOKEN))).thenReturn(Optional.of(entity));

        // when / then
        assertThatThrownBy(() -> invitationService.claim(TOKEN, OTHER_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.INVITATION_FULL);
        verify(onlineGameService, never()).createPendingGame(any());
    }

    @Test
    void claim_retry_by_same_joiner_returns_existing_game_without_creating_another_ok() {
        // given
        var entity = claimedInvitation();
        when(invitationRepository.findForUpdateByTokenHash(sha256(TOKEN))).thenReturn(Optional.of(entity));
        when(onlineGameService.getGame(GAME_ID, JOINER_ID)).thenReturn(gameWithId());

        // when
        var result = invitationService.claim(TOKEN, JOINER_ID);

        // then
        assertThat(result.getId()).isEqualTo(GAME_ID);
        verify(onlineGameService, never()).createPendingGame(any());
        verify(invitationRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancel_open_invitation_sets_cancelled_at_ok() {
        // given
        var entity = openInvitation("WHITE", true, 1800, 10);
        when(invitationRepository.findForUpdateById(INVITATION_ID)).thenReturn(Optional.of(entity));
        when(invitationRepository.save(entity)).thenReturn(entity);

        // when
        var result = invitationService.cancel(INVITATION_ID, HOST_ID);

        // then
        assertAll(() -> assertThat(entity.getCancelledAt()).isEqualTo(NOW), () -> assertThat(result.getStatus())
                .isEqualTo(InvitationStatus.CANCELLED));
    }

    @Test
    void cancel_already_cancelled_invitation_is_idempotent_ok() {
        // given
        var cancelledAt = NOW.minusSeconds(30);
        var entity = openInvitation("WHITE", true, 1800, 10);
        entity.setCancelledAt(cancelledAt);
        when(invitationRepository.findForUpdateById(INVITATION_ID)).thenReturn(Optional.of(entity));

        // when
        var result = invitationService.cancel(INVITATION_ID, HOST_ID);

        // then
        assertAll(() -> assertThat(result.getCancelledAt()).isEqualTo(cancelledAt), () -> assertThat(result.getStatus())
                .isEqualTo(InvitationStatus.CANCELLED));
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void cancel_claimed_invitation_ko() {
        // given
        when(invitationRepository.findForUpdateById(INVITATION_ID)).thenReturn(Optional.of(claimedInvitation()));

        // when / then
        assertThatThrownBy(() -> invitationService.cancel(INVITATION_ID, HOST_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.INVITATION_ALREADY_CLAIMED);
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void cancel_by_non_host_is_not_found_ko() {
        // given
        var entity = openInvitation("WHITE", true, 1800, 10);
        when(invitationRepository.findForUpdateById(INVITATION_ID)).thenReturn(Optional.of(entity));

        // when / then
        assertThatThrownBy(() -> invitationService.cancel(INVITATION_ID, JOINER_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.INVITATION_NOT_FOUND);
        verify(invitationRepository, never()).save(any());
    }

    @Test
    void get_invitation_past_expiry_reports_expired_ok() {
        // given
        var entity = openInvitation("WHITE", true, 1800, 10);
        when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(entity));
        when(clock.instant()).thenReturn(EXPIRES_AT.plusSeconds(1));

        // when
        var result = invitationService.getInvitation(INVITATION_ID, HOST_ID);

        // then
        assertThat(result.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
    }

    @Test
    void get_claimed_invitation_reports_game_id_ok() {
        // given
        when(invitationRepository.findById(INVITATION_ID)).thenReturn(Optional.of(claimedInvitation()));

        // when
        var result = invitationService.getInvitation(INVITATION_ID, HOST_ID);

        // then
        assertAll(
                () -> assertThat(result.getStatus()).isEqualTo(InvitationStatus.CLAIMED),
                () -> assertThat(result.getGameId()).isEqualTo(GAME_ID));
    }

    private void assertClaimFails(AppErrorMessage expected) {
        assertThatThrownBy(() -> invitationService.claim(TOKEN, JOINER_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(expected);
        verify(onlineGameService, never()).createPendingGame(any());
    }

    private InvitationRequest request(Boolean clockEnabled, Integer initialMinutes, Integer incrementSeconds) {
        return InvitationRequest.builder()
                .withHostColor(HostColor.WHITE)
                .withClockEnabled(clockEnabled)
                .withInitialMinutes(initialMinutes)
                .withIncrementSeconds(incrementSeconds)
                .build();
    }

    private InvitationEntity openInvitation(
            String hostColor, boolean clockEnabled, Integer initialSeconds, Integer incrementSeconds) {
        return InvitationEntity.builder()
                .withId(INVITATION_ID)
                .withTokenHash(sha256(TOKEN))
                .withHostAccountId(HOST_ID)
                .withHostColor(hostColor)
                .withVariantCode("STANDARD")
                .withModeCode("CLASSICAL")
                .withClockEnabled(clockEnabled)
                .withInitialSeconds(initialSeconds)
                .withIncrementSeconds(incrementSeconds)
                .withCreatedAt(NOW)
                .withExpiresAt(EXPIRES_AT)
                .build();
    }

    private InvitationEntity claimedInvitation() {
        var entity = openInvitation("WHITE", true, 1800, 10);
        entity.setJoinerAccountId(JOINER_ID);
        entity.setGameId(GAME_ID);
        entity.setClaimedAt(NOW);

        return entity;
    }

    private OnlineGame gameWithId() {
        return OnlineGame.builder().withId(GAME_ID).build();
    }

    private InvitationEntity assignId(InvocationOnMock invocation) {
        InvitationEntity entity = invocation.getArgument(0);
        entity.setId(INVITATION_ID);

        return entity;
    }

    private byte[] sha256(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
