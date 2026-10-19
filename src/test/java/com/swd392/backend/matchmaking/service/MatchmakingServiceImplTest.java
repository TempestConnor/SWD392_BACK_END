package com.swd392.backend.matchmaking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.matchmaking.entity.MatchmakingConfigEntity;
import com.swd392.backend.matchmaking.entity.MatchmakingConfigId;
import com.swd392.backend.matchmaking.entity.QueueRequestEntity;
import com.swd392.backend.matchmaking.mapper.QueueRequestEntityMapper;
import com.swd392.backend.matchmaking.model.QueueJoinRequest;
import com.swd392.backend.matchmaking.model.QueuePairing;
import com.swd392.backend.matchmaking.model.QueueStatus;
import com.swd392.backend.matchmaking.repository.MatchmakingConfigRepository;
import com.swd392.backend.matchmaking.repository.QueueRequestRepository;
import com.swd392.backend.onlinegame.model.GameSource;
import com.swd392.backend.onlinegame.model.OnlineGame;
import com.swd392.backend.onlinegame.service.OnlineGameService;
import com.swd392.backend.rating.model.PlayerRating;
import com.swd392.backend.rating.service.RatingService;
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
class MatchmakingServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00.123Z");
    private static final Duration SILENCE_LIMIT = Duration.ofSeconds(30);
    private static final Long ACCOUNT_ID = 1L;
    private static final Long REQUEST_ID = 10L;
    private static final Long NEW_REQUEST_ID = 11L;
    private static final Long GAME_ID = 100L;
    private static final String CLASSICAL = "CLASSICAL";
    private static final String STANDARD = "STANDARD";
    private static final MatchmakingConfigId CONFIG_ID = new MatchmakingConfigId(CLASSICAL, STANDARD);

    @Mock
    private QueueRequestRepository queueRequestRepository;

    @Spy
    private QueueRequestEntityMapper queueRequestEntityMapper = Mappers.getMapper(QueueRequestEntityMapper.class);

    @Mock
    private MatchmakingConfigRepository matchmakingConfigRepository;

    @Mock
    private RatingService ratingService;

    @Mock
    private OnlineGameService onlineGameService;

    @Mock
    private Clock clock;

    @InjectMocks
    private MatchmakingServiceImpl matchmakingService;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(NOW);
        lenient().when(matchmakingConfigRepository.findById(CONFIG_ID)).thenReturn(Optional.of(config()));
        lenient().when(ratingService.isModeEnabled(CLASSICAL)).thenReturn(true);
        lenient()
                .when(ratingService.getRatingForUpdate(ACCOUNT_ID, CLASSICAL))
                .thenReturn(new PlayerRating(ACCOUNT_ID, CLASSICAL, 1234, 1200, 3));
        lenient()
                .when(queueRequestRepository.save(any(QueueRequestEntity.class)))
                .thenAnswer(this::returnArgument);
    }

    @Test
    void join_creates_open_request_with_current_rating_ok() {
        // given
        when(queueRequestRepository.findForUpdateByAccountIdAndLeftAtIsNull(ACCOUNT_ID))
                .thenReturn(Optional.empty());
        when(queueRequestRepository.saveAndFlush(any(QueueRequestEntity.class))).thenAnswer(this::assignNewId);
        var captor = ArgumentCaptor.forClass(QueueRequestEntity.class);

        // when
        var queued = matchmakingService.join(ACCOUNT_ID, joinRequest());

        // then
        verify(queueRequestRepository).saveAndFlush(captor.capture());
        var saved = captor.getValue();
        assertAll(
                () -> assertThat(saved.getAccountId()).isEqualTo(ACCOUNT_ID),
                () -> assertThat(saved.getModeCode()).isEqualTo(CLASSICAL),
                () -> assertThat(saved.getVariantCode()).isEqualTo(STANDARD),
                () -> assertThat(saved.getRatingAtEntry()).isEqualTo(1234),
                () -> assertThat(saved.getJoinedAt()).isEqualTo(NOW),
                () -> assertThat(saved.getHeartbeatAt()).isEqualTo(NOW),
                () -> assertThat(saved.getLeftAt()).isNull(),
                () -> assertThat(saved.getExitReason()).isNull(),
                () -> assertThat(queued.getId()).isEqualTo(NEW_REQUEST_ID),
                () -> assertThat(queued.getStatus()).isEqualTo(QueueStatus.QUEUED),
                () -> assertThat(queued.getHeartbeatIntervalSeconds()).isEqualTo(10));
    }

    @Test
    void join_when_already_queued_returns_same_request_and_refreshes_heartbeat_ok() {
        // given
        var open = openRequest(NOW.minusSeconds(29));
        when(queueRequestRepository.findForUpdateByAccountIdAndLeftAtIsNull(ACCOUNT_ID))
                .thenReturn(Optional.of(open));

        // when
        var queued = matchmakingService.join(ACCOUNT_ID, joinRequest());

        // then
        assertAll(
                () -> assertThat(queued.getId()).isEqualTo(REQUEST_ID),
                () -> assertThat(queued.getStatus()).isEqualTo(QueueStatus.QUEUED),
                () -> assertThat(queued.getRatingAtEntry()).isEqualTo(1100),
                () -> assertThat(open.getHeartbeatAt()).isEqualTo(NOW),
                () -> assertThat(open.getJoinedAt()).isEqualTo(NOW.minusSeconds(60)));
        verify(queueRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void join_with_silent_open_request_times_it_out_and_creates_new_one_ok() {
        // given
        var stale = openRequest(NOW.minus(SILENCE_LIMIT));
        when(queueRequestRepository.findForUpdateByAccountIdAndLeftAtIsNull(ACCOUNT_ID))
                .thenReturn(Optional.of(stale));
        when(queueRequestRepository.saveAndFlush(any(QueueRequestEntity.class))).thenAnswer(this::assignNewId);

        // when
        var queued = matchmakingService.join(ACCOUNT_ID, joinRequest());

        // then
        assertAll(
                () -> assertThat(stale.getExitReason()).isEqualTo("TIMED_OUT"),
                () -> assertThat(stale.getLeftAt()).isEqualTo(NOW),
                () -> assertThat(queued.getId()).isEqualTo(NEW_REQUEST_ID),
                () -> assertThat(queued.getRatingAtEntry()).isEqualTo(1234),
                () -> assertThat(queued.getStatus()).isEqualTo(QueueStatus.QUEUED));
    }

    @Test
    void join_without_matchmaking_config_is_unavailable_ko() {
        // given
        when(matchmakingConfigRepository.findById(CONFIG_ID)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> matchmakingService.join(ACCOUNT_ID, joinRequest()))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.MATCHMAKING_UNAVAILABLE);
        verify(ratingService, never()).getRatingForUpdate(anyLong(), anyString());
        verify(queueRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void join_with_disabled_mode_is_unavailable_ko() {
        // given
        when(ratingService.isModeEnabled(CLASSICAL)).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> matchmakingService.join(ACCOUNT_ID, joinRequest()))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.MATCHMAKING_UNAVAILABLE);
        verify(queueRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void heartbeat_refreshes_open_request_ok() {
        // given
        var open = openRequest(NOW.minusSeconds(10));
        when(queueRequestRepository.findForUpdateByAccountIdAndLeftAtIsNull(ACCOUNT_ID))
                .thenReturn(Optional.of(open));

        // when
        var queued = matchmakingService.heartbeat(ACCOUNT_ID);

        // then
        assertAll(
                () -> assertThat(queued.getStatus()).isEqualTo(QueueStatus.QUEUED),
                () -> assertThat(queued.getHeartbeatAt()).isEqualTo(NOW),
                () -> assertThat(open.getLeftAt()).isNull());
    }

    @Test
    void heartbeat_exactly_at_silence_limit_times_out_at_deadline_ok() {
        // given
        var lastContact = NOW.minus(SILENCE_LIMIT);
        var open = openRequest(lastContact);
        when(queueRequestRepository.findForUpdateByAccountIdAndLeftAtIsNull(ACCOUNT_ID))
                .thenReturn(Optional.of(open));

        // when
        var closed = matchmakingService.heartbeat(ACCOUNT_ID);

        // then
        assertAll(
                () -> assertThat(closed.getStatus()).isEqualTo(QueueStatus.TIMED_OUT),
                () -> assertThat(closed.getLeftAt()).isEqualTo(lastContact.plus(SILENCE_LIMIT)),
                () -> assertThat(closed.getHeartbeatAt()).isEqualTo(lastContact),
                () -> assertThat(closed.getGameId()).isNull());
    }

    @Test
    void heartbeat_after_match_returns_matched_request_with_game_ok() {
        // given
        var matched = openRequest(NOW.minusSeconds(5));
        matched.setLeftAt(NOW.minusSeconds(1));
        matched.setExitReason("MATCHED");
        matched.setGameId(GAME_ID);
        when(queueRequestRepository.findForUpdateByAccountIdAndLeftAtIsNull(ACCOUNT_ID))
                .thenReturn(Optional.empty());
        when(queueRequestRepository.findFirstByAccountIdOrderByIdDesc(ACCOUNT_ID))
                .thenReturn(Optional.of(matched));

        // when
        var latest = matchmakingService.heartbeat(ACCOUNT_ID);

        // then
        assertAll(
                () -> assertThat(latest.getStatus()).isEqualTo(QueueStatus.MATCHED),
                () -> assertThat(latest.getGameId()).isEqualTo(GAME_ID));
        verify(queueRequestRepository, never()).save(any());
    }

    @Test
    void heartbeat_when_never_queued_is_not_found_ko() {
        // given
        when(queueRequestRepository.findForUpdateByAccountIdAndLeftAtIsNull(ACCOUNT_ID))
                .thenReturn(Optional.empty());
        when(queueRequestRepository.findFirstByAccountIdOrderByIdDesc(ACCOUNT_ID))
                .thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> matchmakingService.heartbeat(ACCOUNT_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.QUEUE_REQUEST_NOT_FOUND);
    }

    @Test
    void cancel_closes_open_request_immediately_ok() {
        // given
        var open = openRequest(NOW.minusSeconds(5));
        when(queueRequestRepository.findForUpdateByAccountIdAndLeftAtIsNull(ACCOUNT_ID))
                .thenReturn(Optional.of(open));

        // when
        var cancelled = matchmakingService.cancel(ACCOUNT_ID);

        // then
        verify(queueRequestRepository).save(open);
        assertAll(
                () -> assertThat(cancelled.getStatus()).isEqualTo(QueueStatus.CANCELLED),
                () -> assertThat(cancelled.getLeftAt()).isEqualTo(NOW),
                () -> assertThat(open.getExitReason()).isEqualTo("CANCELLED"),
                () -> assertThat(open.getGameId()).isNull());
    }

    @Test
    void cancel_repeated_returns_closed_request_unchanged_ok() {
        // given
        var cancelled = openRequest(NOW.minusSeconds(5));
        cancelled.setLeftAt(NOW.minusSeconds(2));
        cancelled.setExitReason("CANCELLED");
        when(queueRequestRepository.findForUpdateByAccountIdAndLeftAtIsNull(ACCOUNT_ID))
                .thenReturn(Optional.empty());
        when(queueRequestRepository.findFirstByAccountIdOrderByIdDesc(ACCOUNT_ID))
                .thenReturn(Optional.of(cancelled));

        // when
        var latest = matchmakingService.cancel(ACCOUNT_ID);

        // then
        assertAll(
                () -> assertThat(latest.getStatus()).isEqualTo(QueueStatus.CANCELLED),
                () -> assertThat(latest.getLeftAt()).isEqualTo(NOW.minusSeconds(2)));
        verify(queueRequestRepository, never()).save(any());
    }

    @Test
    void cancel_after_silence_limit_records_timeout_instead_ok() {
        // given
        var lastContact = NOW.minusSeconds(45);
        var open = openRequest(lastContact);
        when(queueRequestRepository.findForUpdateByAccountIdAndLeftAtIsNull(ACCOUNT_ID))
                .thenReturn(Optional.of(open));

        // when
        var closed = matchmakingService.cancel(ACCOUNT_ID);

        // then
        assertAll(
                () -> assertThat(closed.getStatus()).isEqualTo(QueueStatus.TIMED_OUT),
                () -> assertThat(closed.getLeftAt()).isEqualTo(lastContact.plus(SILENCE_LIMIT)));
    }

    @Test
    void find_requests_due_for_timeout_uses_heartbeat_plus_grace_cutoff_ok() {
        // given
        when(matchmakingConfigRepository.findAll()).thenReturn(List.of(config()));
        when(queueRequestRepository.findOpenIdsSilentSince(CLASSICAL, STANDARD, NOW.minus(SILENCE_LIMIT)))
                .thenReturn(List.of(REQUEST_ID));

        // when
        var due = matchmakingService.findRequestsDueForTimeout();

        // then
        assertThat(due).containsExactly(REQUEST_ID);
    }

    @Test
    void time_out_if_due_closes_silent_request_ok() {
        // given
        var open = openRequest(NOW.minusSeconds(31));
        when(queueRequestRepository.findForUpdateById(REQUEST_ID)).thenReturn(Optional.of(open));

        // when
        matchmakingService.timeOutIfDue(REQUEST_ID);

        // then
        verify(queueRequestRepository).save(open);
        assertAll(() -> assertThat(open.getExitReason()).isEqualTo("TIMED_OUT"), () -> assertThat(open.getLeftAt())
                .isEqualTo(NOW.minusSeconds(1)));
    }

    @Test
    void time_out_if_due_leaves_request_refreshed_after_candidate_scan_ok() {
        // given
        var open = openRequest(NOW.minusSeconds(1));
        when(queueRequestRepository.findForUpdateById(REQUEST_ID)).thenReturn(Optional.of(open));

        // when
        matchmakingService.timeOutIfDue(REQUEST_ID);

        // then
        assertThat(open.getLeftAt()).isNull();
        verify(queueRequestRepository, never()).save(any());
    }

    @Test
    void time_out_if_due_ignores_request_closed_meanwhile_ok() {
        // given
        var cancelled = openRequest(NOW.minusSeconds(60));
        cancelled.setLeftAt(NOW.minusSeconds(50));
        cancelled.setExitReason("CANCELLED");
        when(queueRequestRepository.findForUpdateById(REQUEST_ID)).thenReturn(Optional.of(cancelled));

        // when
        matchmakingService.timeOutIfDue(REQUEST_ID);

        // then
        assertThat(cancelled.getExitReason()).isEqualTo("CANCELLED");
        verify(queueRequestRepository, never()).save(any());
    }

    @Test
    void find_pairing_candidates_pairs_requests_exactly_at_initial_gap_ok() {
        // given
        givenWaiting(waiting(21L, 2L, 1000, NOW), waiting(22L, 3L, 1100, NOW));

        // when
        var pairings = matchmakingService.findPairingCandidates();

        // then
        assertThat(pairings)
                .extracting(QueuePairing::getFirstRequestId, QueuePairing::getSecondRequestId)
                .containsExactly(tuple(21L, 22L));
    }

    @Test
    void find_pairing_candidates_above_initial_gap_leaves_new_requests_queued_ko() {
        // given
        givenWaiting(waiting(21L, 2L, 1000, NOW), waiting(22L, 3L, 1101, NOW));

        // when
        var pairings = matchmakingService.findPairingCandidates();

        // then
        assertThat(pairings).isEmpty();
    }

    @Test
    void find_pairing_candidates_widens_gap_after_full_step_interval_ok() {
        // given
        var joinedAt = NOW.minusSeconds(30);
        givenWaiting(waiting(21L, 2L, 1000, joinedAt), waiting(22L, 3L, 1150, joinedAt));

        // when
        var pairings = matchmakingService.findPairingCandidates();

        // then
        assertThat(pairings).hasSize(1);
    }

    @Test
    void find_pairing_candidates_before_full_step_interval_keeps_initial_gap_ko() {
        // given
        var joinedAt = NOW.minusMillis(29_999);
        givenWaiting(waiting(21L, 2L, 1000, joinedAt), waiting(22L, 3L, 1150, joinedAt));

        // when
        var pairings = matchmakingService.findPairingCandidates();

        // then
        assertThat(pairings).isEmpty();
    }

    @Test
    void find_pairing_candidates_requires_difference_within_both_gaps_ko() {
        // given
        var longWaiter = waiting(21L, 2L, 1000, NOW.minus(Duration.ofMinutes(10)));
        var newcomer = waiting(22L, 3L, 1300, NOW);
        givenWaiting(longWaiter, newcomer);

        // when
        var pairings = matchmakingService.findPairingCandidates();

        // then
        assertThat(pairings).isEmpty();
    }

    @Test
    void find_pairing_candidates_never_exceeds_maximum_gap_ko() {
        // given
        var joinedAt = NOW.minus(Duration.ofHours(1));
        givenWaiting(waiting(21L, 2L, 1000, joinedAt), waiting(22L, 3L, 1401, joinedAt));

        // when
        var pairings = matchmakingService.findPairingCandidates();

        // then
        assertThat(pairings).isEmpty();
    }

    @Test
    void find_pairing_candidates_gives_oldest_request_the_closest_rating_ok() {
        // given
        givenWaiting(
                waiting(21L, 2L, 1000, NOW.minusSeconds(4)),
                waiting(22L, 3L, 1090, NOW.minusSeconds(3)),
                waiting(23L, 4L, 1020, NOW.minusSeconds(2)),
                waiting(24L, 5L, 1080, NOW.minusSeconds(1)));

        // when
        var pairings = matchmakingService.findPairingCandidates();

        // then
        assertThat(pairings)
                .extracting(QueuePairing::getFirstRequestId, QueuePairing::getSecondRequestId)
                .containsExactly(tuple(21L, 23L), tuple(22L, 24L));
    }

    @Test
    void find_pairing_candidates_skips_request_past_silence_limit_ko() {
        // given
        var silent = waiting(22L, 3L, 1000, NOW.minusSeconds(60));
        silent.setHeartbeatAt(NOW.minus(SILENCE_LIMIT));
        givenWaiting(waiting(21L, 2L, 1000, NOW), silent);

        // when
        var pairings = matchmakingService.findPairingCandidates();

        // then
        assertThat(pairings).isEmpty();
    }

    @Test
    void find_pairing_candidates_with_disabled_mode_pairs_nothing_ko() {
        // given
        when(matchmakingConfigRepository.findAll()).thenReturn(List.of(config()));
        when(ratingService.isModeEnabled(CLASSICAL)).thenReturn(false);

        // when
        var pairings = matchmakingService.findPairingCandidates();

        // then
        assertThat(pairings).isEmpty();
        verify(queueRequestRepository, never())
                .findByModeCodeAndVariantCodeAndLeftAtIsNullOrderByJoinedAtAscIdAsc(anyString(), anyString());
    }

    @Test
    void pair_consumes_both_requests_and_creates_one_rated_pending_game_ok() {
        // given
        var first = waiting(21L, 2L, 1000, NOW.minusSeconds(5));
        var second = waiting(22L, 3L, 1050, NOW.minusSeconds(2));
        givenLocked(first, second);
        when(onlineGameService.createPendingGame(any(OnlineGame.class))).thenAnswer(this::assignGameId);
        var captor = ArgumentCaptor.forClass(OnlineGame.class);

        // when
        var gameId = matchmakingService.pair(22L, 21L);

        // then
        var locks = inOrder(queueRequestRepository);
        locks.verify(queueRequestRepository).findForUpdateById(21L);
        locks.verify(queueRequestRepository).findForUpdateById(22L);
        verify(onlineGameService).createPendingGame(captor.capture());
        var game = captor.getValue();
        assertAll(
                () -> assertThat(gameId).contains(GAME_ID),
                () -> assertThat(game.getSource()).isEqualTo(GameSource.MATCHMAKING),
                () -> assertThat(List.of(game.getWhiteAccountId(), game.getBlackAccountId()))
                        .containsExactlyInAnyOrder(2L, 3L),
                () -> assertThat(game.getModeCode()).isEqualTo(CLASSICAL),
                () -> assertThat(game.getVariantCode()).isEqualTo(STANDARD),
                () -> assertThat(game.isRated()).isTrue(),
                () -> assertThat(game.isClockEnabled()).isTrue(),
                () -> assertThat(game.getInitialSeconds()).isEqualTo(1800),
                () -> assertThat(game.getIncrementSeconds()).isEqualTo(10),
                () -> assertThat(game.getCreatedAt()).isEqualTo(NOW),
                () -> assertThat(List.of(first, second)).allSatisfy(request -> {
                    assertThat(request.getExitReason()).isEqualTo("MATCHED");
                    assertThat(request.getLeftAt()).isEqualTo(NOW);
                    assertThat(request.getGameId()).isEqualTo(GAME_ID);
                }));
        verify(queueRequestRepository).save(first);
        verify(queueRequestRepository).save(second);
    }

    @Test
    void pair_when_request_closed_meanwhile_creates_no_game_ko() {
        // given
        var first = waiting(21L, 2L, 1000, NOW);
        var cancelled = waiting(22L, 3L, 1000, NOW);
        cancelled.setLeftAt(NOW.minusMillis(1));
        cancelled.setExitReason("CANCELLED");
        givenLocked(first, cancelled);

        // when
        var gameId = matchmakingService.pair(21L, 22L);

        // then
        assertAll(
                () -> assertThat(gameId).isEmpty(),
                () -> assertThat(first.getLeftAt()).isNull(),
                () -> assertThat(cancelled.getExitReason()).isEqualTo("CANCELLED"));
        verify(onlineGameService, never()).createPendingGame(any());
        verify(queueRequestRepository, never()).save(any());
    }

    @Test
    void pair_with_silent_request_records_timeout_and_creates_no_game_ko() {
        // given
        var first = waiting(21L, 2L, 1000, NOW);
        var silent = waiting(22L, 3L, 1000, NOW.minusSeconds(60));
        silent.setHeartbeatAt(NOW.minus(SILENCE_LIMIT));
        givenLocked(first, silent);

        // when
        var gameId = matchmakingService.pair(21L, 22L);

        // then
        assertAll(
                () -> assertThat(gameId).isEmpty(),
                () -> assertThat(first.getLeftAt()).isNull(),
                () -> assertThat(silent.getExitReason()).isEqualTo("TIMED_OUT"),
                () -> assertThat(silent.getLeftAt()).isEqualTo(NOW));
        verify(onlineGameService, never()).createPendingGame(any());
        verify(queueRequestRepository).save(silent);
    }

    @Test
    void pair_when_ratings_not_compatible_creates_no_game_ko() {
        // given
        var first = waiting(21L, 2L, 1000, NOW);
        var second = waiting(22L, 3L, 1300, NOW);
        givenLocked(first, second);

        // when
        var gameId = matchmakingService.pair(21L, 22L);

        // then
        assertAll(
                () -> assertThat(gameId).isEmpty(),
                () -> assertThat(first.getLeftAt()).isNull(),
                () -> assertThat(second.getLeftAt()).isNull());
        verify(onlineGameService, never()).createPendingGame(any());
    }

    private void givenWaiting(QueueRequestEntity... requests) {
        when(matchmakingConfigRepository.findAll()).thenReturn(List.of(config()));
        when(queueRequestRepository.findByModeCodeAndVariantCodeAndLeftAtIsNullOrderByJoinedAtAscIdAsc(
                        CLASSICAL, STANDARD))
                .thenReturn(List.of(requests));
    }

    private void givenLocked(QueueRequestEntity first, QueueRequestEntity second) {
        when(queueRequestRepository.findForUpdateById(first.getId())).thenReturn(Optional.of(first));
        lenient().when(queueRequestRepository.findForUpdateById(second.getId())).thenReturn(Optional.of(second));
    }

    private QueueRequestEntity waiting(Long requestId, Long accountId, int rating, Instant joinedAt) {
        return QueueRequestEntity.builder()
                .withId(requestId)
                .withAccountId(accountId)
                .withModeCode(CLASSICAL)
                .withVariantCode(STANDARD)
                .withRatingAtEntry(rating)
                .withJoinedAt(joinedAt)
                .withHeartbeatAt(NOW)
                .build();
    }

    private OnlineGame assignGameId(InvocationOnMock invocation) {
        var game = (OnlineGame) invocation.getArgument(0);
        game.setId(GAME_ID);

        return game;
    }

    private QueueJoinRequest joinRequest() {
        return new QueueJoinRequest(CLASSICAL, STANDARD);
    }

    private MatchmakingConfigEntity config() {
        return new MatchmakingConfigEntity(CONFIG_ID, 100, 50, 30, 400, 10, 20, 180);
    }

    private QueueRequestEntity openRequest(Instant heartbeatAt) {
        return QueueRequestEntity.builder()
                .withId(REQUEST_ID)
                .withAccountId(ACCOUNT_ID)
                .withModeCode(CLASSICAL)
                .withVariantCode(STANDARD)
                .withRatingAtEntry(1100)
                .withJoinedAt(NOW.minusSeconds(60))
                .withHeartbeatAt(heartbeatAt)
                .build();
    }

    private QueueRequestEntity assignNewId(InvocationOnMock invocation) {
        var entity = (QueueRequestEntity) invocation.getArgument(0);
        if (entity.getId() == null) {
            entity.setId(NEW_REQUEST_ID);
        }

        return entity;
    }

    private QueueRequestEntity returnArgument(InvocationOnMock invocation) {
        return invocation.getArgument(0);
    }
}
