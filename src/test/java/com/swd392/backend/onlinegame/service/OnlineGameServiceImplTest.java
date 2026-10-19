package com.swd392.backend.onlinegame.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swd392.backend.account.service.AccountService;
import com.swd392.backend.chess.model.ChessColor;
import com.swd392.backend.chess.model.GameResult;
import com.swd392.backend.chess.service.ChessRulesServiceImpl;
import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.config.OnlineGameProperties;
import com.swd392.backend.onlinegame.entity.GameAbandonVoteEntity;
import com.swd392.backend.onlinegame.entity.GameAbandonVoteId;
import com.swd392.backend.onlinegame.entity.GameMoveEntity;
import com.swd392.backend.onlinegame.entity.GameMoveId;
import com.swd392.backend.onlinegame.entity.OnlineGameEntity;
import com.swd392.backend.onlinegame.mapper.GameMoveEntityMapper;
import com.swd392.backend.onlinegame.mapper.OnlineGameEntityMapper;
import com.swd392.backend.onlinegame.model.GameSource;
import com.swd392.backend.onlinegame.model.MoveSubmission;
import com.swd392.backend.onlinegame.model.OnlineGame;
import com.swd392.backend.onlinegame.model.OnlineGameStatus;
import com.swd392.backend.onlinegame.model.TerminationReason;
import com.swd392.backend.onlinegame.repository.GameAbandonVoteRepository;
import com.swd392.backend.onlinegame.repository.GameMoveRepository;
import com.swd392.backend.onlinegame.repository.OnlineGameRepository;
import com.swd392.backend.rating.model.RatedGameResult;
import com.swd392.backend.rating.service.RatingService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OnlineGameServiceImplTest {

    private static final Instant CLAIMED_AT = Instant.parse("2026-10-08T10:00:00.123Z");
    private static final Instant READY_DEADLINE = CLAIMED_AT.plusSeconds(180);
    private static final Instant NOW = CLAIMED_AT.plusSeconds(20);
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(30);
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final Long GAME_ID = 100L;
    private static final Long WHITE_ID = 1L;
    private static final Long BLACK_ID = 2L;
    private static final Long OUTSIDER_ID = 3L;
    private static final Instant TURN_STARTED_AT = NOW.minusSeconds(5);
    private static final long FULL_CLOCK_MS = 1_800_000L;
    private static final Duration RECONNECT_GRACE = Duration.ofMinutes(3);
    private static final Instant LATER = CLAIMED_AT.plus(Duration.ofMinutes(10));

    @Mock
    private OnlineGameRepository onlineGameRepository;

    @Spy
    private OnlineGameEntityMapper onlineGameEntityMapper = Mappers.getMapper(OnlineGameEntityMapper.class);

    @Mock
    private GameMoveRepository gameMoveRepository;

    @Spy
    private GameMoveEntityMapper gameMoveEntityMapper = Mappers.getMapper(GameMoveEntityMapper.class);

    @Mock
    private GameAbandonVoteRepository gameAbandonVoteRepository;

    @Spy
    private ChessRulesServiceImpl chessRulesService = new ChessRulesServiceImpl();

    @Mock
    private AccountService accountService;

    @Mock
    private RatingService ratingService;

    @Spy
    private OnlineGameProperties onlineGameProperties =
            new OnlineGameProperties(HEARTBEAT_TIMEOUT, HEARTBEAT_TIMEOUT, Duration.ofSeconds(5));

    @Mock
    private Clock clock;

    @InjectMocks
    private OnlineGameServiceImpl onlineGameService;

    @BeforeEach
    void fixClockAndNames() {
        lenient().when(clock.instant()).thenReturn(NOW);
        lenient()
                .when(accountService.getDisplayNames(anyCollection()))
                .thenReturn(Map.of(WHITE_ID, "Alex", BLACK_ID, "alex"));
        lenient()
                .when(onlineGameRepository.save(any(OnlineGameEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void create_pending_game_sets_ready_deadline_start_position_full_clocks_and_last_seen_ok() {
        // given
        var game = newGame(true, 1800, 10);
        when(onlineGameRepository.saveAndFlush(any(OnlineGameEntity.class))).thenAnswer(invocation -> {
            OnlineGameEntity entity = invocation.getArgument(0);
            entity.setId(GAME_ID);
            return entity;
        });
        var captor = ArgumentCaptor.forClass(OnlineGameEntity.class);

        // when
        var created = onlineGameService.createPendingGame(game);

        // then
        verify(onlineGameRepository).saveAndFlush(captor.capture());
        var saved = captor.getValue();
        assertAll(
                () -> assertThat(created.getId()).isEqualTo(GAME_ID),
                () -> assertThat(saved.getStatus()).isEqualTo("PENDING"),
                () -> assertThat(saved.getSource()).isEqualTo("INVITE"),
                () -> assertThat(saved.getCreatedAt()).isEqualTo(CLAIMED_AT),
                () -> assertThat(saved.getReadyDeadlineAt()).isEqualTo(READY_DEADLINE),
                () -> assertThat(saved.getInitialFen()).isEqualTo(START_FEN),
                () -> assertThat(saved.getCurrentFen()).isEqualTo(START_FEN),
                () -> assertThat(saved.getLastPly()).isZero(),
                () -> assertThat(saved.getWhiteRemainingMs()).isEqualTo(1_800_000L),
                () -> assertThat(saved.getBlackRemainingMs()).isEqualTo(1_800_000L),
                () -> assertThat(saved.getWhiteLastSeenAt()).isEqualTo(CLAIMED_AT),
                () -> assertThat(saved.getBlackLastSeenAt()).isEqualTo(CLAIMED_AT),
                () -> assertThat(saved.getStartedAt()).isNull());
    }

    @Test
    void create_pending_game_without_clock_has_no_remaining_time_ok() {
        // given
        var game = newGame(false, null, null);
        when(onlineGameRepository.saveAndFlush(any(OnlineGameEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        var captor = ArgumentCaptor.forClass(OnlineGameEntity.class);

        // when
        onlineGameService.createPendingGame(game);

        // then
        verify(onlineGameRepository).saveAndFlush(captor.capture());
        var saved = captor.getValue();
        assertAll(
                () -> assertThat(saved.isClockEnabled()).isFalse(),
                () -> assertThat(saved.getWhiteRemainingMs()).isNull(),
                () -> assertThat(saved.getBlackRemainingMs()).isNull());
    }

    @Test
    void get_game_for_black_participant_includes_names_and_viewer_color_ok() {
        // given
        when(onlineGameRepository.findById(GAME_ID)).thenReturn(Optional.of(pendingGame()));

        // when
        var game = onlineGameService.getGame(GAME_ID, BLACK_ID);

        // then
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.PENDING),
                () -> assertThat(game.getWhiteDisplayName()).isEqualTo("Alex"),
                () -> assertThat(game.getBlackDisplayName()).isEqualTo("alex"),
                () -> assertThat(game.getViewerColor()).isEqualTo(ChessColor.BLACK));
    }

    @Test
    void get_game_by_non_participant_is_not_found_ko() {
        // given
        when(onlineGameRepository.findById(GAME_ID)).thenReturn(Optional.of(pendingGame()));

        // when / then
        assertThatThrownBy(() -> onlineGameService.getGame(GAME_ID, OUTSIDER_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ONLINE_GAME_NOT_FOUND);
        verify(accountService, never()).getDisplayNames(anyCollection());
    }

    @Test
    void get_missing_game_is_not_found_ko() {
        // given
        when(onlineGameRepository.findById(GAME_ID)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> onlineGameService.getGame(GAME_ID, WHITE_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ONLINE_GAME_NOT_FOUND);
    }

    @Test
    void mark_ready_first_player_records_ready_and_stays_pending_ok() {
        // given
        var stored = pendingGame();
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        var game = onlineGameService.markReady(GAME_ID, WHITE_ID);

        // then
        verify(onlineGameRepository).save(stored);
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.PENDING),
                () -> assertThat(stored.getWhiteReadyAt()).isEqualTo(NOW),
                () -> assertThat(stored.getWhiteLastSeenAt()).isEqualTo(NOW),
                () -> assertThat(stored.getBlackReadyAt()).isNull(),
                () -> assertThat(stored.getStartedAt()).isNull(),
                () -> assertThat(game.getViewerColor()).isEqualTo(ChessColor.WHITE));
    }

    @Test
    void mark_ready_second_player_starts_game_and_white_turn_ok() {
        // given
        var stored = pendingGame();
        stored.setWhiteReadyAt(CLAIMED_AT.plusSeconds(5));
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        var game = onlineGameService.markReady(GAME_ID, BLACK_ID);

        // then
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.ACTIVE),
                () -> assertThat(stored.getStatus()).isEqualTo("ACTIVE"),
                () -> assertThat(stored.getBlackReadyAt()).isEqualTo(NOW),
                () -> assertThat(stored.getStartedAt()).isEqualTo(NOW),
                () -> assertThat(stored.getTurnStartedAt()).isEqualTo(NOW),
                () -> assertThat(stored.getEndedAt()).isNull(),
                () -> assertThat(stored.getTerminationReason()).isNull());
    }

    @Test
    void mark_ready_repeated_keeps_first_ready_time_ok() {
        // given
        var firstReady = CLAIMED_AT.plusSeconds(5);
        var stored = pendingGame();
        stored.setWhiteReadyAt(firstReady);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        var game = onlineGameService.markReady(GAME_ID, WHITE_ID);

        // then
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.PENDING),
                () -> assertThat(stored.getWhiteReadyAt()).isEqualTo(firstReady),
                () -> assertThat(stored.getStartedAt()).isNull());
    }

    @Test
    void mark_ready_at_deadline_cancels_instead_of_starting_ok() {
        // given
        var stored = pendingGame();
        stored.setWhiteReadyAt(CLAIMED_AT.plusSeconds(5));
        stored.setWhiteLastSeenAt(READY_DEADLINE.minusSeconds(1));
        when(clock.instant()).thenReturn(READY_DEADLINE);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        var game = onlineGameService.markReady(GAME_ID, BLACK_ID);

        // then
        verify(onlineGameRepository).save(stored);
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.CANCELLED),
                () -> assertThat(game.getTerminationReason()).isEqualTo(TerminationReason.PRE_START_CANCEL),
                () -> assertThat(stored.getEndedAt()).isEqualTo(READY_DEADLINE),
                () -> assertThat(stored.getBlackReadyAt()).isNull(),
                () -> assertThat(stored.getStartedAt()).isNull());
    }

    @Test
    void mark_ready_when_opponent_silent_for_heartbeat_timeout_cancels_ok() {
        // given
        var stored = pendingGame();
        stored.setBlackLastSeenAt(NOW.minus(HEARTBEAT_TIMEOUT));
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        var game = onlineGameService.markReady(GAME_ID, WHITE_ID);

        // then
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.CANCELLED),
                () -> assertThat(stored.getTerminationReason()).isEqualTo("PRE_START_CANCEL"),
                () -> assertThat(stored.getWhiteReadyAt()).isNull());
    }

    @Test
    void mark_ready_on_active_game_returns_state_without_saving_ok() {
        // given
        var stored = activeGame();
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        var game = onlineGameService.markReady(GAME_ID, BLACK_ID);

        // then
        verify(onlineGameRepository, never()).save(any());
        assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.ACTIVE);
    }

    @Test
    void mark_ready_by_non_participant_is_not_found_and_changes_nothing_ko() {
        // given
        var stored = pendingGame();
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when / then
        assertThatThrownBy(() -> onlineGameService.markReady(GAME_ID, OUTSIDER_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ONLINE_GAME_NOT_FOUND);
        verify(onlineGameRepository, never()).save(any());
    }

    @Test
    void cancel_pending_game_cancels_without_result_ok() {
        // given
        var stored = pendingGame();
        stored.setWhiteReadyAt(CLAIMED_AT.plusSeconds(5));
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        var game = onlineGameService.cancelPendingGame(GAME_ID, BLACK_ID);

        // then
        verify(onlineGameRepository).save(stored);
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.CANCELLED),
                () -> assertThat(stored.getEndedAt()).isEqualTo(NOW),
                () -> assertThat(stored.getTerminationReason()).isEqualTo("PRE_START_CANCEL"),
                () -> assertThat(stored.getStartedAt()).isNull());
    }

    @Test
    void cancel_already_cancelled_game_is_idempotent_ok() {
        // given
        var stored = pendingGame();
        stored.setStatus("CANCELLED");
        stored.setEndedAt(CLAIMED_AT.plusSeconds(10));
        stored.setTerminationReason("PRE_START_CANCEL");
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        var game = onlineGameService.cancelPendingGame(GAME_ID, WHITE_ID);

        // then
        verify(onlineGameRepository, never()).save(any());
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.CANCELLED),
                () -> assertThat(game.getEndedAt()).isEqualTo(CLAIMED_AT.plusSeconds(10)));
    }

    @Test
    void cancel_active_game_is_rejected_ko() {
        // given
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(activeGame()));

        // when / then
        assertThatThrownBy(() -> onlineGameService.cancelPendingGame(GAME_ID, WHITE_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ONLINE_GAME_ALREADY_STARTED);
        verify(onlineGameRepository, never()).save(any());
    }

    @Test
    void cancel_missing_game_is_not_found_ko() {
        // given
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> onlineGameService.cancelPendingGame(GAME_ID, WHITE_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ONLINE_GAME_NOT_FOUND);
    }

    @Test
    void heartbeat_records_last_seen_for_caller_only_ok() {
        // given
        var stored = pendingGame();
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        var game = onlineGameService.recordHeartbeat(GAME_ID, BLACK_ID);

        // then
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.PENDING),
                () -> assertThat(stored.getBlackLastSeenAt()).isEqualTo(NOW),
                () -> assertThat(stored.getWhiteLastSeenAt()).isEqualTo(CLAIMED_AT));
    }

    @Test
    void heartbeat_after_opponent_silence_cancels_pending_game_ok() {
        // given
        var stored = pendingGame();
        when(clock.instant()).thenReturn(CLAIMED_AT.plus(HEARTBEAT_TIMEOUT));
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        var game = onlineGameService.recordHeartbeat(GAME_ID, BLACK_ID);

        // then
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.CANCELLED),
                () -> assertThat(stored.getEndedAt()).isEqualTo(CLAIMED_AT.plus(HEARTBEAT_TIMEOUT)));
    }

    @Test
    void heartbeat_on_completed_game_changes_nothing_ok() {
        // given
        var stored = resignedByWhite();
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        var game = onlineGameService.recordHeartbeat(GAME_ID, WHITE_ID);

        // then
        verify(onlineGameRepository, never()).save(any());
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.COMPLETED),
                () -> assertThat(stored.getWhiteLastSeenAt()).isEqualTo(CLAIMED_AT));
    }

    @Test
    void find_games_due_queries_pending_games_against_deadline_and_heartbeat_cutoff_ok() {
        // given
        when(onlineGameRepository.findIdsDueForPreStartCancel("PENDING", NOW, NOW.minus(HEARTBEAT_TIMEOUT)))
                .thenReturn(List.of(GAME_ID));

        // when
        var due = onlineGameService.findGamesDueForPreStartCancel();

        // then
        assertThat(due).containsExactly(GAME_ID);
    }

    @Test
    void cancel_if_pre_start_due_cancels_game_past_deadline_ok() {
        // given
        var stored = pendingGame();
        when(clock.instant()).thenReturn(READY_DEADLINE.plusMillis(1));
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        onlineGameService.cancelIfPreStartDue(GAME_ID);

        // then
        verify(onlineGameRepository).save(stored);
        assertAll(
                () -> assertThat(stored.getStatus()).isEqualTo("CANCELLED"),
                () -> assertThat(stored.getTerminationReason()).isEqualTo("PRE_START_CANCEL"));
    }

    @Test
    void cancel_if_pre_start_due_leaves_game_started_meanwhile_ok() {
        // given
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(activeGame()));

        // when
        onlineGameService.cancelIfPreStartDue(GAME_ID);

        // then
        verify(onlineGameRepository, never()).save(any());
    }

    @Test
    void cancel_if_pre_start_due_leaves_recently_seen_game_ok() {
        // given
        var stored = pendingGame();
        when(onlineGameRepository.findForUpdateById(eq(GAME_ID))).thenReturn(Optional.of(stored));

        // when
        onlineGameService.cancelIfPreStartDue(GAME_ID);

        // then
        verify(onlineGameRepository, never()).save(any());
        assertThat(stored.getStatus()).isEqualTo("PENDING");
    }

    @Test
    void make_move_by_white_records_move_position_and_clock_with_increment_ok() {
        // given
        var stored = movableGame();
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));
        when(gameMoveRepository.findByIdGameIdOrderByIdPlyAsc(GAME_ID)).thenReturn(List.of());
        var captor = ArgumentCaptor.forClass(GameMoveEntity.class);

        // when
        var game = onlineGameService.makeMove(GAME_ID, WHITE_ID, submission(1, "e2e4"));

        // then
        verify(gameMoveRepository).save(captor.capture());
        var move = captor.getValue();
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.ACTIVE),
                () -> assertThat(game.getLastPly()).isEqualTo(1),
                () -> assertThat(stored.getCurrentFen())
                        .isEqualTo("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"),
                () -> assertThat(stored.getTurnStartedAt()).isEqualTo(NOW),
                () -> assertThat(stored.getWhiteRemainingMs()).isEqualTo(FULL_CLOCK_MS - 5_000L + 10_000L),
                () -> assertThat(stored.getBlackRemainingMs()).isEqualTo(FULL_CLOCK_MS),
                () -> assertThat(move.getId().getGameId()).isEqualTo(GAME_ID),
                () -> assertThat(move.getId().getPly()).isEqualTo(1),
                () -> assertThat(move.getPlayedByAccountId()).isEqualTo(WHITE_ID),
                () -> assertThat(move.getMoveUci()).isEqualTo("e2e4"),
                () -> assertThat(move.getSan()).isEqualTo("e4"),
                () -> assertThat(move.getResultingFen()).isEqualTo(stored.getCurrentFen()),
                () -> assertThat(move.getWhiteRemainingMs()).isEqualTo(stored.getWhiteRemainingMs()),
                () -> assertThat(move.getBlackRemainingMs()).isEqualTo(FULL_CLOCK_MS),
                () -> assertThat(move.getAcceptedAt()).isEqualTo(NOW));
    }

    @Test
    void make_move_without_clock_keeps_clocks_empty_ok() {
        // given
        var stored = movableGame();
        stored.setClockEnabled(false);
        stored.setInitialSeconds(null);
        stored.setIncrementSeconds(null);
        stored.setWhiteRemainingMs(null);
        stored.setBlackRemainingMs(null);
        stored.setTurnStartedAt(NOW.minusSeconds(86_400));
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));
        when(gameMoveRepository.findByIdGameIdOrderByIdPlyAsc(GAME_ID)).thenReturn(List.of());
        var captor = ArgumentCaptor.forClass(GameMoveEntity.class);

        // when
        onlineGameService.makeMove(GAME_ID, WHITE_ID, submission(1, "e2e4"));

        // then
        verify(gameMoveRepository).save(captor.capture());
        assertAll(
                () -> assertThat(stored.getWhiteRemainingMs()).isNull(),
                () -> assertThat(captor.getValue().getWhiteRemainingMs()).isNull(),
                () -> assertThat(captor.getValue().getBlackRemainingMs()).isNull());
    }

    @Test
    void make_move_checkmate_completes_game_with_outcome_and_reason_ok() {
        // given
        var stored = movableGame();
        stored.setLastPly(3);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));
        when(gameMoveRepository.findByIdGameIdOrderByIdPlyAsc(GAME_ID)).thenReturn(playedMoves("f2f3", "e7e5", "g2g4"));

        // when
        var game = onlineGameService.makeMove(GAME_ID, BLACK_ID, submission(4, "d8h4"));

        // then
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.COMPLETED),
                () -> assertThat(game.getOutcome()).isEqualTo(GameResult.BLACK_WIN),
                () -> assertThat(game.getTerminationReason()).isEqualTo(TerminationReason.CHECKMATE),
                () -> assertThat(game.getEndedAt()).isEqualTo(NOW),
                () -> assertThat(game.getLastPly()).isEqualTo(4));
    }

    @Test
    void make_move_completing_threefold_repetition_ends_in_draw_ok() {
        // given
        var stored = movableGame();
        stored.setLastPly(7);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));
        when(gameMoveRepository.findByIdGameIdOrderByIdPlyAsc(GAME_ID))
                .thenReturn(playedMoves("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1"));

        // when
        var game = onlineGameService.makeMove(GAME_ID, BLACK_ID, submission(8, "f6g8"));

        // then
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.COMPLETED),
                () -> assertThat(game.getOutcome()).isEqualTo(GameResult.DRAW),
                () -> assertThat(game.getTerminationReason()).isEqualTo(TerminationReason.REPETITION));
    }

    @Test
    void make_illegal_move_is_rejected_and_changes_nothing_ko() {
        // given
        var stored = movableGame();
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));
        when(gameMoveRepository.findByIdGameIdOrderByIdPlyAsc(GAME_ID)).thenReturn(List.of());

        // when / then
        assertThatThrownBy(() -> onlineGameService.makeMove(GAME_ID, WHITE_ID, submission(1, "e2e5")))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ILLEGAL_MOVE);
        verify(gameMoveRepository, never()).save(any());
        verify(onlineGameRepository, never()).save(any());
        assertAll(
                () -> assertThat(stored.getLastPly()).isZero(),
                () -> assertThat(stored.getCurrentFen()).isEqualTo(START_FEN),
                () -> assertThat(stored.getWhiteRemainingMs()).isEqualTo(FULL_CLOCK_MS));
    }

    @Test
    void make_move_out_of_turn_is_rejected_ko() {
        // given
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(movableGame()));

        // when / then
        assertThatThrownBy(() -> onlineGameService.makeMove(GAME_ID, BLACK_ID, submission(1, "e7e5")))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.NOT_YOUR_TURN);
        verify(gameMoveRepository, never()).save(any());
    }

    @Test
    void make_move_with_skipped_ply_is_rejected_ko() {
        // given
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(movableGame()));

        // when / then
        assertThatThrownBy(() -> onlineGameService.makeMove(GAME_ID, WHITE_ID, submission(2, "e2e4")))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.MOVE_OUT_OF_SEQUENCE);
        verify(gameMoveRepository, never()).save(any());
    }

    @Test
    void make_different_move_at_already_played_ply_is_rejected_ko() {
        // given
        var stored = movableGame();
        stored.setLastPly(1);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));
        when(gameMoveRepository.findById(new GameMoveId(GAME_ID, 1)))
                .thenReturn(Optional.of(playedMove(1, WHITE_ID, "e2e4")));

        // when / then
        assertThatThrownBy(() -> onlineGameService.makeMove(GAME_ID, WHITE_ID, submission(1, "d2d4")))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.MOVE_OUT_OF_SEQUENCE);
        verify(gameMoveRepository, never()).save(any());
    }

    @Test
    void make_move_retry_of_accepted_move_returns_current_state_without_saving_ok() {
        // given
        var stored = movableGame();
        stored.setLastPly(1);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));
        when(gameMoveRepository.findById(new GameMoveId(GAME_ID, 1)))
                .thenReturn(Optional.of(playedMove(1, WHITE_ID, "e2e4")));

        // when
        var game = onlineGameService.makeMove(GAME_ID, WHITE_ID, submission(1, "e2e4"));

        // then
        verify(gameMoveRepository, never()).save(any());
        verify(onlineGameRepository, never()).save(any());
        assertThat(game.getLastPly()).isEqualTo(1);
    }

    @Test
    void make_move_when_clock_reached_zero_is_rejected_and_game_stays_active_ko() {
        // given
        var stored = movableGame();
        stored.setWhiteRemainingMs(5_000L);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when / then
        assertThatThrownBy(() -> onlineGameService.makeMove(GAME_ID, WHITE_ID, submission(1, "e2e4")))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.TIME_EXPIRED);
        verify(gameMoveRepository, never()).save(any());
        assertThat(stored.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void make_move_in_pending_game_is_rejected_ko() {
        // given
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(pendingGame()));

        // when / then
        assertThatThrownBy(() -> onlineGameService.makeMove(GAME_ID, WHITE_ID, submission(1, "e2e4")))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ONLINE_GAME_NOT_ACTIVE);
        verify(gameMoveRepository, never()).save(any());
    }

    @Test
    void make_move_by_non_participant_is_not_found_ko() {
        // given
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(movableGame()));

        // when / then
        assertThatThrownBy(() -> onlineGameService.makeMove(GAME_ID, OUTSIDER_ID, submission(1, "e2e4")))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ONLINE_GAME_NOT_FOUND);
        verify(gameMoveRepository, never()).save(any());
    }

    @Test
    void heartbeat_on_active_game_after_flag_fall_records_timeout_ok() {
        // given
        var stored = movableGame();
        stored.setWhiteRemainingMs(4_000L);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        var game = onlineGameService.recordHeartbeat(GAME_ID, BLACK_ID);

        // then
        verify(onlineGameRepository).save(stored);
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.COMPLETED),
                () -> assertThat(game.getOutcome()).isEqualTo(GameResult.BLACK_WIN),
                () -> assertThat(game.getTerminationReason()).isEqualTo(TerminationReason.TIMEOUT));
    }

    @Test
    void heartbeat_on_active_game_with_time_left_records_presence_and_stays_active_ok() {
        // given
        var stored = movableGame();
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        var game = onlineGameService.recordHeartbeat(GAME_ID, WHITE_ID);

        // then
        verify(onlineGameRepository).save(stored);
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.ACTIVE),
                () -> assertThat(stored.getWhiteLastSeenAt()).isEqualTo(NOW),
                () -> assertThat(stored.getBlackLastSeenAt()).isEqualTo(CLAIMED_AT),
                () -> assertThat(stored.getWhiteRemainingMs()).isEqualTo(FULL_CLOCK_MS));
    }

    @Test
    void find_active_games_due_returns_only_games_whose_side_to_move_ran_out_ok() {
        // given
        var whiteFlagged = movableGame();
        whiteFlagged.setWhiteRemainingMs(5_000L);
        var blackToMoveWithTime = movableGame();
        blackToMoveWithTime.setId(GAME_ID + 1);
        blackToMoveWithTime.setLastPly(1);
        blackToMoveWithTime.setWhiteRemainingMs(1_000L);
        var blackFlagged = movableGame();
        blackFlagged.setId(GAME_ID + 2);
        blackFlagged.setLastPly(1);
        blackFlagged.setBlackRemainingMs(4_999L);
        when(onlineGameRepository.findActiveSweepCandidates("ACTIVE", NOW.minus(HEARTBEAT_TIMEOUT)))
                .thenReturn(List.of(whiteFlagged, blackToMoveWithTime, blackFlagged));

        // when
        var due = onlineGameService.findActiveGamesDue();

        // then
        assertThat(due).containsExactly(GAME_ID, GAME_ID + 2);
    }

    @Test
    void resolve_active_game_records_white_timeout_as_black_win_at_flag_fall_time_ok() {
        // given
        var stored = movableGame();
        stored.setWhiteRemainingMs(3_000L);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        onlineGameService.resolveActiveGame(GAME_ID);

        // then
        verify(onlineGameRepository).save(stored);
        assertAll(
                () -> assertThat(stored.getStatus()).isEqualTo("COMPLETED"),
                () -> assertThat(stored.getOutcome()).isEqualTo("BLACK_WIN"),
                () -> assertThat(stored.getTerminationReason()).isEqualTo("TIMEOUT"),
                () -> assertThat(stored.getEndedAt()).isEqualTo(TURN_STARTED_AT.plusSeconds(3)),
                () -> assertThat(stored.getWhiteRemainingMs()).isZero(),
                () -> assertThat(stored.getBlackRemainingMs()).isEqualTo(FULL_CLOCK_MS));
    }

    @Test
    void resolve_active_game_records_black_timeout_as_white_win_ok() {
        // given
        var stored = movableGame();
        stored.setLastPly(1);
        stored.setBlackRemainingMs(5_000L);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        onlineGameService.resolveActiveGame(GAME_ID);

        // then
        assertAll(
                () -> assertThat(stored.getOutcome()).isEqualTo("WHITE_WIN"),
                () -> assertThat(stored.getTerminationReason()).isEqualTo("TIMEOUT"),
                () -> assertThat(stored.getEndedAt()).isEqualTo(NOW),
                () -> assertThat(stored.getBlackRemainingMs()).isZero(),
                () -> assertThat(stored.getWhiteRemainingMs()).isEqualTo(FULL_CLOCK_MS));
    }

    @Test
    void resolve_active_game_is_a_loss_even_when_opponent_has_only_a_king_ok() {
        // given
        var stored = movableGame();
        stored.setCurrentFen("8/8/4k3/8/8/8/3QK3/8 w - - 0 60");
        stored.setLastPly(118);
        stored.setWhiteRemainingMs(1_000L);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        onlineGameService.resolveActiveGame(GAME_ID);

        // then
        assertAll(
                () -> assertThat(stored.getOutcome()).isEqualTo("BLACK_WIN"),
                () -> assertThat(stored.getTerminationReason()).isEqualTo("TIMEOUT"));
    }

    @Test
    void resolve_active_game_leaves_game_with_time_left_ok() {
        // given
        var stored = movableGame();
        stored.setWhiteRemainingMs(5_001L);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        onlineGameService.resolveActiveGame(GAME_ID);

        // then
        verify(onlineGameRepository, never()).save(any());
        assertThat(stored.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void resolve_active_game_never_times_out_game_without_clock_ok() {
        // given
        var stored = activeGame();
        stored.setTurnStartedAt(CLAIMED_AT);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        onlineGameService.resolveActiveGame(GAME_ID);

        // then
        verify(onlineGameRepository, never()).save(any());
        assertThat(stored.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void resolve_active_game_leaves_game_already_completed_by_a_move_ok() {
        // given
        var stored = movableGame();
        stored.setWhiteRemainingMs(1_000L);
        stored.setStatus("COMPLETED");
        stored.setOutcome("WHITE_WIN");
        stored.setTerminationReason("CHECKMATE");
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        onlineGameService.resolveActiveGame(GAME_ID);

        // then
        verify(onlineGameRepository, never()).save(any());
        assertAll(
                () -> assertThat(stored.getOutcome()).isEqualTo("WHITE_WIN"),
                () -> assertThat(stored.getTerminationReason()).isEqualTo("CHECKMATE"));
    }

    @Test
    void resolve_active_game_ignores_missing_game_ok() {
        // given
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.empty());

        // when
        onlineGameService.resolveActiveGame(GAME_ID);

        // then
        verify(onlineGameRepository, never()).save(any());
    }

    @Test
    void get_moves_returns_moves_in_ply_order_ok() {
        // given
        when(onlineGameRepository.findById(GAME_ID)).thenReturn(Optional.of(movableGame()));
        when(gameMoveRepository.findByIdGameIdOrderByIdPlyAsc(GAME_ID)).thenReturn(playedMoves("e2e4", "e7e5"));

        // when
        var moves = onlineGameService.getMoves(GAME_ID, BLACK_ID);

        // then
        assertAll(
                () -> assertThat(moves).extracting("ply").containsExactly(1, 2),
                () -> assertThat(moves).extracting("moveUci").containsExactly("e2e4", "e7e5"),
                () -> assertThat(moves).extracting("gameId").containsOnly(GAME_ID));
    }

    @Test
    void get_moves_by_non_participant_is_not_found_ko() {
        // given
        when(onlineGameRepository.findById(GAME_ID)).thenReturn(Optional.of(movableGame()));

        // when / then
        assertThatThrownBy(() -> onlineGameService.getMoves(GAME_ID, OUTSIDER_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ONLINE_GAME_NOT_FOUND);
        verify(gameMoveRepository, never()).findByIdGameIdOrderByIdPlyAsc(any());
    }

    @Test
    void resign_by_white_completes_game_as_black_win_ok() {
        // given
        var stored = movableGame();
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        var game = onlineGameService.resign(GAME_ID, WHITE_ID);

        // then
        verify(onlineGameRepository).save(stored);
        assertAll(
                () -> assertThat(stored.getStatus()).isEqualTo("COMPLETED"),
                () -> assertThat(stored.getOutcome()).isEqualTo("BLACK_WIN"),
                () -> assertThat(stored.getTerminationReason()).isEqualTo("RESIGNATION"),
                () -> assertThat(stored.getEndedAt()).isEqualTo(NOW),
                () -> assertThat(game.getOutcome()).isEqualTo(GameResult.BLACK_WIN),
                () -> assertThat(game.getViewerColor()).isEqualTo(ChessColor.WHITE));
    }

    @Test
    void resign_by_black_out_of_turn_completes_game_as_white_win_ok() {
        // given
        var stored = movableGame();
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        onlineGameService.resign(GAME_ID, BLACK_ID);

        // then
        assertAll(
                () -> assertThat(stored.getOutcome()).isEqualTo("WHITE_WIN"),
                () -> assertThat(stored.getTerminationReason()).isEqualTo("RESIGNATION"));
    }

    @Test
    void resign_retry_after_own_resignation_returns_game_without_saving_ok() {
        // given
        var stored = resignedByWhite();
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        var game = onlineGameService.resign(GAME_ID, WHITE_ID);

        // then
        verify(onlineGameRepository, never()).save(any());
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.COMPLETED),
                () -> assertThat(game.getTerminationReason()).isEqualTo(TerminationReason.RESIGNATION));
    }

    @Test
    void resign_after_opponent_resigned_is_rejected_ko() {
        // given
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(resignedByWhite()));

        // when / then
        assertThatThrownBy(() -> onlineGameService.resign(GAME_ID, BLACK_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ONLINE_GAME_NOT_ACTIVE);
        verify(onlineGameRepository, never()).save(any());
    }

    @Test
    void resign_in_pending_game_is_rejected_ko() {
        // given
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(pendingGame()));

        // when / then
        assertThatThrownBy(() -> onlineGameService.resign(GAME_ID, WHITE_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ONLINE_GAME_NOT_ACTIVE);
        verify(onlineGameRepository, never()).save(any());
    }

    @Test
    void resign_after_flag_fall_records_timeout_instead_ok() {
        // given
        var stored = movableGame();
        stored.setWhiteRemainingMs(4_000L);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        onlineGameService.resign(GAME_ID, BLACK_ID);

        // then
        verify(onlineGameRepository).save(stored);
        assertAll(
                () -> assertThat(stored.getOutcome()).isEqualTo("BLACK_WIN"),
                () -> assertThat(stored.getTerminationReason()).isEqualTo("TIMEOUT"),
                () -> assertThat(stored.getEndedAt()).isEqualTo(TURN_STARTED_AT.plusMillis(4_000L)));
    }

    @Test
    void resign_by_non_participant_is_not_found_ko() {
        // given
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(movableGame()));

        // when / then
        assertThatThrownBy(() -> onlineGameService.resign(GAME_ID, OUTSIDER_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ONLINE_GAME_NOT_FOUND);
        verify(onlineGameRepository, never()).save(any());
    }

    @Test
    void vote_to_abandon_first_vote_is_recorded_and_game_stays_active_ok() {
        // given
        var stored = movableGame();
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));
        when(gameAbandonVoteRepository.findByIdGameId(GAME_ID)).thenReturn(List.of(), List.of(vote(WHITE_ID)));
        var captor = ArgumentCaptor.forClass(GameAbandonVoteEntity.class);

        // when
        var game = onlineGameService.voteToAbandon(GAME_ID, WHITE_ID);

        // then
        verify(gameAbandonVoteRepository).save(captor.capture());
        var vote = captor.getValue();
        assertAll(
                () -> assertThat(vote.getId()).isEqualTo(new GameAbandonVoteId(GAME_ID, WHITE_ID)),
                () -> assertThat(vote.getVotedAt()).isEqualTo(NOW),
                () -> assertThat(stored.getStatus()).isEqualTo("ACTIVE"),
                () -> assertThat(stored.getOutcome()).isNull(),
                () -> assertThat(game.isWhiteAbandonVoted()).isTrue(),
                () -> assertThat(game.isBlackAbandonVoted()).isFalse());
    }

    @Test
    void vote_to_abandon_second_vote_completes_game_as_draw_ok() {
        // given
        var stored = movableGame();
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));
        when(gameAbandonVoteRepository.findByIdGameId(GAME_ID))
                .thenReturn(List.of(vote(WHITE_ID)), List.of(vote(WHITE_ID), vote(BLACK_ID)));

        // when
        var game = onlineGameService.voteToAbandon(GAME_ID, BLACK_ID);

        // then
        verify(gameAbandonVoteRepository).save(any(GameAbandonVoteEntity.class));
        verify(onlineGameRepository).save(stored);
        assertAll(
                () -> assertThat(stored.getStatus()).isEqualTo("COMPLETED"),
                () -> assertThat(stored.getOutcome()).isEqualTo("DRAW"),
                () -> assertThat(stored.getTerminationReason()).isEqualTo("MUTUAL_ABANDONMENT"),
                () -> assertThat(stored.getEndedAt()).isEqualTo(NOW),
                () -> assertThat(game.isWhiteAbandonVoted()).isTrue(),
                () -> assertThat(game.isBlackAbandonVoted()).isTrue());
    }

    @Test
    void vote_to_abandon_repeated_keeps_single_vote_without_saving_ok() {
        // given
        var stored = movableGame();
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));
        when(gameAbandonVoteRepository.findByIdGameId(GAME_ID)).thenReturn(List.of(vote(WHITE_ID)));

        // when
        var game = onlineGameService.voteToAbandon(GAME_ID, WHITE_ID);

        // then
        verify(gameAbandonVoteRepository, never()).save(any());
        verify(onlineGameRepository, never()).save(any());
        assertAll(() -> assertThat(stored.getStatus()).isEqualTo("ACTIVE"), () -> assertThat(game.isWhiteAbandonVoted())
                .isTrue());
    }

    @Test
    void vote_to_abandon_in_pending_game_is_rejected_ko() {
        // given
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(pendingGame()));

        // when / then
        assertThatThrownBy(() -> onlineGameService.voteToAbandon(GAME_ID, WHITE_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ONLINE_GAME_NOT_ACTIVE);
        verify(gameAbandonVoteRepository, never()).save(any());
    }

    @Test
    void vote_to_abandon_completed_game_is_rejected_ko() {
        // given
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(resignedByWhite()));

        // when / then
        assertThatThrownBy(() -> onlineGameService.voteToAbandon(GAME_ID, BLACK_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ONLINE_GAME_NOT_ACTIVE);
        verify(gameAbandonVoteRepository, never()).save(any());
    }

    @Test
    void vote_to_abandon_after_flag_fall_records_timeout_without_vote_ok() {
        // given
        var stored = movableGame();
        stored.setWhiteRemainingMs(4_000L);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));
        when(gameAbandonVoteRepository.findByIdGameId(GAME_ID)).thenReturn(List.of(vote(BLACK_ID)));

        // when
        onlineGameService.voteToAbandon(GAME_ID, WHITE_ID);

        // then
        verify(gameAbandonVoteRepository, never()).save(any());
        assertAll(
                () -> assertThat(stored.getOutcome()).isEqualTo("BLACK_WIN"),
                () -> assertThat(stored.getTerminationReason()).isEqualTo("TIMEOUT"));
    }

    @Test
    void vote_to_abandon_repeated_after_flag_fall_records_timeout_ok() {
        // given
        var stored = movableGame();
        stored.setWhiteRemainingMs(4_000L);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));
        when(gameAbandonVoteRepository.findByIdGameId(GAME_ID)).thenReturn(List.of(vote(WHITE_ID)));

        // when
        var game = onlineGameService.voteToAbandon(GAME_ID, WHITE_ID);

        // then
        verify(gameAbandonVoteRepository, never()).save(any());
        verify(onlineGameRepository).save(stored);
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.COMPLETED),
                () -> assertThat(stored.getOutcome()).isEqualTo("BLACK_WIN"),
                () -> assertThat(stored.getTerminationReason()).isEqualTo("TIMEOUT"));
    }

    @Test
    void get_game_shows_opponent_abandon_vote_ok() {
        // given
        when(onlineGameRepository.findById(GAME_ID)).thenReturn(Optional.of(movableGame()));
        when(gameAbandonVoteRepository.findByIdGameId(GAME_ID)).thenReturn(List.of(vote(BLACK_ID)));

        // when
        var game = onlineGameService.getGame(GAME_ID, WHITE_ID);

        // then
        assertAll(() -> assertThat(game.isWhiteAbandonVoted()).isFalse(), () -> assertThat(game.isBlackAbandonVoted())
                .isTrue());
    }

    @Test
    void heartbeat_reconnects_disconnected_caller_ok() {
        // given
        var stored = activeGame();
        stored.setWhiteLastSeenAt(NOW.minus(HEARTBEAT_TIMEOUT));
        stored.setWhiteDisconnectedAt(NOW.minus(HEARTBEAT_TIMEOUT));
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        var game = onlineGameService.recordHeartbeat(GAME_ID, WHITE_ID);

        // then
        verify(onlineGameRepository).save(stored);
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.ACTIVE),
                () -> assertThat(game.getWhiteDisconnectedAt()).isNull(),
                () -> assertThat(stored.getWhiteLastSeenAt()).isEqualTo(NOW),
                () -> assertThat(stored.getBlackDisconnectedAt()).isNull());
    }

    @Test
    void heartbeat_marks_opponent_silent_for_timeout_as_disconnected_since_last_seen_ok() {
        // given
        var stored = activeGame();
        stored.setBlackLastSeenAt(NOW.minus(HEARTBEAT_TIMEOUT));
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        var game = onlineGameService.recordHeartbeat(GAME_ID, WHITE_ID);

        // then
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.ACTIVE),
                () -> assertThat(game.getBlackDisconnectedAt()).isEqualTo(NOW.minus(HEARTBEAT_TIMEOUT)),
                () -> assertThat(game.getWhiteDisconnectedAt()).isNull());
    }

    @Test
    void heartbeat_by_player_returning_after_grace_forfeits_them_ok() {
        // given
        var stored = activeGame();
        var whiteLastSeen = LATER.minus(RECONNECT_GRACE).minusSeconds(1);
        stored.setWhiteLastSeenAt(whiteLastSeen);
        stored.setBlackLastSeenAt(LATER);
        when(clock.instant()).thenReturn(LATER);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        var game = onlineGameService.recordHeartbeat(GAME_ID, WHITE_ID);

        // then
        verify(onlineGameRepository).save(stored);
        assertAll(
                () -> assertThat(game.getStatus()).isEqualTo(OnlineGameStatus.COMPLETED),
                () -> assertThat(game.getOutcome()).isEqualTo(GameResult.BLACK_WIN),
                () -> assertThat(game.getTerminationReason()).isEqualTo(TerminationReason.DISCONNECT),
                () -> assertThat(game.getEndedAt()).isEqualTo(whiteLastSeen.plus(RECONNECT_GRACE)),
                () -> assertThat(game.getWhiteDisconnectedAt()).isEqualTo(whiteLastSeen),
                () -> assertThat(stored.getWhiteLastSeenAt()).isEqualTo(whiteLastSeen));
    }

    @Test
    void resolve_active_game_forfeits_player_at_exact_grace_deadline_even_without_clock_ok() {
        // given
        var stored = activeGame();
        stored.setWhiteLastSeenAt(LATER);
        stored.setBlackLastSeenAt(LATER.minus(RECONNECT_GRACE));
        when(clock.instant()).thenReturn(LATER);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        onlineGameService.resolveActiveGame(GAME_ID);

        // then
        verify(onlineGameRepository).save(stored);
        assertAll(
                () -> assertThat(stored.getStatus()).isEqualTo("COMPLETED"),
                () -> assertThat(stored.getOutcome()).isEqualTo("WHITE_WIN"),
                () -> assertThat(stored.getTerminationReason()).isEqualTo("DISCONNECT"),
                () -> assertThat(stored.getEndedAt()).isEqualTo(LATER),
                () -> assertThat(stored.getBlackDisconnectedAt()).isEqualTo(LATER.minus(RECONNECT_GRACE)),
                () -> assertThat(stored.getWhiteRemainingMs()).isNull());
    }

    @Test
    void resolve_active_game_just_before_grace_deadline_only_marks_disconnect_ok() {
        // given
        var stored = activeGame();
        var blackLastSeen = LATER.minus(RECONNECT_GRACE).plusMillis(1);
        stored.setWhiteLastSeenAt(LATER);
        stored.setBlackLastSeenAt(blackLastSeen);
        when(clock.instant()).thenReturn(LATER);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        onlineGameService.resolveActiveGame(GAME_ID);

        // then
        verify(onlineGameRepository).save(stored);
        assertAll(
                () -> assertThat(stored.getStatus()).isEqualTo("ACTIVE"),
                () -> assertThat(stored.getOutcome()).isNull(),
                () -> assertThat(stored.getBlackDisconnectedAt()).isEqualTo(blackLastSeen));
    }

    @Test
    void resolve_active_game_with_both_players_away_forfeits_the_earlier_grace_ok() {
        // given
        var stored = activeGame();
        var whiteLastSeen = LATER.minus(RECONNECT_GRACE).minusSeconds(10);
        stored.setWhiteLastSeenAt(whiteLastSeen);
        stored.setBlackLastSeenAt(LATER.minus(RECONNECT_GRACE).minusSeconds(5));
        when(clock.instant()).thenReturn(LATER);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        onlineGameService.resolveActiveGame(GAME_ID);

        // then
        assertAll(
                () -> assertThat(stored.getOutcome()).isEqualTo("BLACK_WIN"),
                () -> assertThat(stored.getTerminationReason()).isEqualTo("DISCONNECT"),
                () -> assertThat(stored.getEndedAt()).isEqualTo(whiteLastSeen.plus(RECONNECT_GRACE)));
    }

    @Test
    void resolve_active_game_with_flag_fall_before_grace_expiry_records_timeout_ok() {
        // given
        var stored = movableGame();
        stored.setTurnStartedAt(LATER.minusSeconds(200));
        stored.setWhiteRemainingMs(100_000L);
        stored.setWhiteLastSeenAt(LATER);
        stored.setBlackLastSeenAt(LATER.minus(RECONNECT_GRACE).minusSeconds(50));
        when(clock.instant()).thenReturn(LATER);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        onlineGameService.resolveActiveGame(GAME_ID);

        // then
        assertAll(
                () -> assertThat(stored.getOutcome()).isEqualTo("BLACK_WIN"),
                () -> assertThat(stored.getTerminationReason()).isEqualTo("TIMEOUT"),
                () -> assertThat(stored.getEndedAt()).isEqualTo(LATER.minusSeconds(100)),
                () -> assertThat(stored.getWhiteRemainingMs()).isZero());
    }

    @Test
    void resolve_active_game_with_grace_expiry_before_flag_fall_records_disconnect_and_stops_clock_ok() {
        // given
        var stored = movableGame();
        stored.setTurnStartedAt(LATER.minusSeconds(200));
        stored.setWhiteRemainingMs(150_000L);
        stored.setWhiteLastSeenAt(LATER);
        stored.setBlackLastSeenAt(LATER.minus(RECONNECT_GRACE).minusSeconds(100));
        when(clock.instant()).thenReturn(LATER);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        onlineGameService.resolveActiveGame(GAME_ID);

        // then
        assertAll(
                () -> assertThat(stored.getOutcome()).isEqualTo("WHITE_WIN"),
                () -> assertThat(stored.getTerminationReason()).isEqualTo("DISCONNECT"),
                () -> assertThat(stored.getEndedAt()).isEqualTo(LATER.minusSeconds(100)),
                () -> assertThat(stored.getWhiteRemainingMs()).isEqualTo(50_000L),
                () -> assertThat(stored.getBlackRemainingMs()).isEqualTo(FULL_CLOCK_MS));
    }

    @Test
    void resolve_active_game_with_flag_fall_and_grace_expiry_at_same_instant_records_timeout_ok() {
        // given
        var stored = movableGame();
        stored.setTurnStartedAt(LATER.minusSeconds(200));
        stored.setWhiteRemainingMs(100_000L);
        stored.setWhiteLastSeenAt(LATER);
        stored.setBlackLastSeenAt(LATER.minus(RECONNECT_GRACE).minusSeconds(100));
        when(clock.instant()).thenReturn(LATER);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        onlineGameService.resolveActiveGame(GAME_ID);

        // then
        assertAll(
                () -> assertThat(stored.getOutcome()).isEqualTo("BLACK_WIN"),
                () -> assertThat(stored.getTerminationReason()).isEqualTo("TIMEOUT"));
    }

    @Test
    void resolve_active_game_with_present_players_changes_nothing_ok() {
        // given
        var stored = activeGame();
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        onlineGameService.resolveActiveGame(GAME_ID);

        // then
        verify(onlineGameRepository, never()).save(any());
        assertThat(stored.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void find_active_games_due_returns_forfeits_and_unmarked_silences_only_ok() {
        // given
        var forfeitDue = activeGame();
        forfeitDue.setWhiteLastSeenAt(LATER);
        forfeitDue.setBlackLastSeenAt(LATER.minus(RECONNECT_GRACE));
        var newlySilent = activeGame();
        newlySilent.setId(GAME_ID + 1);
        newlySilent.setWhiteLastSeenAt(LATER);
        newlySilent.setBlackLastSeenAt(LATER.minus(HEARTBEAT_TIMEOUT));
        var alreadyMarked = activeGame();
        alreadyMarked.setId(GAME_ID + 2);
        alreadyMarked.setWhiteLastSeenAt(LATER);
        alreadyMarked.setBlackLastSeenAt(LATER.minusSeconds(60));
        alreadyMarked.setBlackDisconnectedAt(LATER.minusSeconds(60));
        var present = movableGame();
        present.setId(GAME_ID + 3);
        present.setTurnStartedAt(LATER);
        present.setWhiteLastSeenAt(LATER);
        present.setBlackLastSeenAt(LATER);
        when(clock.instant()).thenReturn(LATER);
        when(onlineGameRepository.findActiveSweepCandidates("ACTIVE", LATER.minus(HEARTBEAT_TIMEOUT)))
                .thenReturn(List.of(forfeitDue, newlySilent, alreadyMarked, present));

        // when
        var due = onlineGameService.findActiveGamesDue();

        // then
        assertThat(due).containsExactly(GAME_ID, GAME_ID + 1);
    }

    @Test
    void make_move_after_opponent_grace_expired_is_rejected_and_changes_nothing_ko() {
        // given
        var stored = movableGame();
        stored.setWhiteLastSeenAt(LATER.minusSeconds(1));
        stored.setBlackLastSeenAt(LATER.minus(RECONNECT_GRACE));
        when(clock.instant()).thenReturn(LATER);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when / then
        assertThatThrownBy(() -> onlineGameService.makeMove(GAME_ID, WHITE_ID, submission(1, "e2e4")))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.DISCONNECT_FORFEIT_DUE);
        verify(gameMoveRepository, never()).save(any());
        verify(onlineGameRepository, never()).save(any());
        assertAll(
                () -> assertThat(stored.getStatus()).isEqualTo("ACTIVE"),
                () -> assertThat(stored.getWhiteLastSeenAt()).isEqualTo(LATER.minusSeconds(1)));
    }

    @Test
    void make_move_reconnects_mover_and_marks_silent_opponent_ok() {
        // given
        var stored = movableGame();
        stored.setWhiteLastSeenAt(NOW.minusSeconds(40));
        stored.setWhiteDisconnectedAt(NOW.minusSeconds(40));
        stored.setBlackLastSeenAt(NOW.minus(HEARTBEAT_TIMEOUT));
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));
        when(gameMoveRepository.findByIdGameIdOrderByIdPlyAsc(GAME_ID)).thenReturn(List.of());

        // when
        var game = onlineGameService.makeMove(GAME_ID, WHITE_ID, submission(1, "e2e4"));

        // then
        assertAll(
                () -> assertThat(game.getLastPly()).isEqualTo(1),
                () -> assertThat(game.getWhiteDisconnectedAt()).isNull(),
                () -> assertThat(game.getWhiteLastSeenAt()).isEqualTo(NOW),
                () -> assertThat(game.getBlackDisconnectedAt()).isEqualTo(NOW.minus(HEARTBEAT_TIMEOUT)));
    }

    @Test
    void resign_after_opponent_grace_expired_records_disconnect_instead_ok() {
        // given
        var stored = activeGame();
        stored.setWhiteLastSeenAt(LATER.minusSeconds(1));
        stored.setBlackLastSeenAt(LATER.minus(RECONNECT_GRACE));
        when(clock.instant()).thenReturn(LATER);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        var game = onlineGameService.resign(GAME_ID, WHITE_ID);

        // then
        assertAll(
                () -> assertThat(game.getOutcome()).isEqualTo(GameResult.WHITE_WIN),
                () -> assertThat(game.getTerminationReason()).isEqualTo(TerminationReason.DISCONNECT));
    }

    @Test
    void resign_in_rated_game_applies_rating_for_the_result_once_ok() {
        // given
        var stored = ratedGame();
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));
        var captor = ArgumentCaptor.forClass(RatedGameResult.class);

        // when
        onlineGameService.resign(GAME_ID, WHITE_ID);

        // then
        verify(ratingService).applyGameResult(captor.capture());
        var result = captor.getValue();
        assertAll(
                () -> assertThat(result.getGameId()).isEqualTo(GAME_ID),
                () -> assertThat(result.getModeCode()).isEqualTo("CLASSICAL"),
                () -> assertThat(result.getWhiteAccountId()).isEqualTo(WHITE_ID),
                () -> assertThat(result.getBlackAccountId()).isEqualTo(BLACK_ID),
                () -> assertThat(result.getOutcome()).isEqualTo(GameResult.BLACK_WIN));
    }

    @Test
    void resign_in_unrated_game_applies_no_rating_ok() {
        // given
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(movableGame()));

        // when
        onlineGameService.resign(GAME_ID, WHITE_ID);

        // then
        verify(ratingService, never()).applyGameResult(any());
    }

    @Test
    void second_abandon_vote_in_rated_game_applies_draw_rating_ok() {
        // given
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(ratedGame()));
        when(gameAbandonVoteRepository.findByIdGameId(GAME_ID)).thenReturn(List.of(vote(WHITE_ID)));
        var captor = ArgumentCaptor.forClass(RatedGameResult.class);

        // when
        onlineGameService.voteToAbandon(GAME_ID, BLACK_ID);

        // then
        verify(ratingService).applyGameResult(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo(GameResult.DRAW);
    }

    @Test
    void resolve_active_game_timeout_in_rated_game_applies_rating_ok() {
        // given
        var stored = ratedGame();
        stored.setWhiteRemainingMs(1_000L);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));
        var captor = ArgumentCaptor.forClass(RatedGameResult.class);

        // when
        onlineGameService.resolveActiveGame(GAME_ID);

        // then
        verify(ratingService).applyGameResult(captor.capture());
        assertAll(
                () -> assertThat(stored.getTerminationReason()).isEqualTo("TIMEOUT"),
                () -> assertThat(captor.getValue().getOutcome()).isEqualTo(GameResult.BLACK_WIN));
    }

    @Test
    void first_abandon_vote_in_rated_game_applies_no_rating_ok() {
        // given
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(ratedGame()));

        // when
        onlineGameService.voteToAbandon(GAME_ID, WHITE_ID);

        // then
        verify(ratingService, never()).applyGameResult(any());
    }

    @Test
    void cancel_rated_pending_game_applies_no_rating_ok() {
        // given
        var stored = pendingGame();
        stored.setRated(true);
        when(onlineGameRepository.findForUpdateById(GAME_ID)).thenReturn(Optional.of(stored));

        // when
        onlineGameService.cancelPendingGame(GAME_ID, WHITE_ID);

        // then
        verify(ratingService, never()).applyGameResult(any());
    }

    private GameAbandonVoteEntity vote(Long accountId) {
        return GameAbandonVoteEntity.builder()
                .withId(new GameAbandonVoteId(GAME_ID, accountId))
                .withVotedAt(NOW.minusSeconds(1))
                .build();
    }

    private OnlineGameEntity resignedByWhite() {
        var game = movableGame();
        game.setStatus("COMPLETED");
        game.setEndedAt(NOW.minusSeconds(1));
        game.setOutcome("BLACK_WIN");
        game.setTerminationReason("RESIGNATION");

        return game;
    }

    private MoveSubmission submission(int ply, String move) {
        return MoveSubmission.builder().withPly(ply).withMove(move).build();
    }

    private List<GameMoveEntity> playedMoves(String... uciMoves) {
        return IntStream.range(0, uciMoves.length)
                .mapToObj(index -> playedMove(index + 1, index % 2 == 0 ? WHITE_ID : BLACK_ID, uciMoves[index]))
                .toList();
    }

    private GameMoveEntity playedMove(int ply, Long accountId, String uciMove) {
        return GameMoveEntity.builder()
                .withId(new GameMoveId(GAME_ID, ply))
                .withPlayedByAccountId(accountId)
                .withMoveUci(uciMove)
                .withResultingFen(START_FEN)
                .withAcceptedAt(NOW)
                .build();
    }

    private OnlineGameEntity movableGame() {
        var game = activeGame();
        game.setClockEnabled(true);
        game.setInitialSeconds(1800);
        game.setIncrementSeconds(10);
        game.setWhiteRemainingMs(FULL_CLOCK_MS);
        game.setBlackRemainingMs(FULL_CLOCK_MS);
        game.setTurnStartedAt(TURN_STARTED_AT);

        return game;
    }

    private OnlineGameEntity ratedGame() {
        var game = movableGame();
        game.setRated(true);
        game.setModeCode("CLASSICAL");

        return game;
    }

    private OnlineGame newGame(boolean clockEnabled, Integer initialSeconds, Integer incrementSeconds) {
        return OnlineGame.builder()
                .withSource(GameSource.INVITE)
                .withWhiteAccountId(WHITE_ID)
                .withBlackAccountId(BLACK_ID)
                .withVariantCode("STANDARD")
                .withModeCode("CLASSICAL")
                .withRated(clockEnabled)
                .withClockEnabled(clockEnabled)
                .withInitialSeconds(initialSeconds)
                .withIncrementSeconds(incrementSeconds)
                .withCreatedAt(CLAIMED_AT)
                .build();
    }

    private OnlineGameEntity pendingGame() {
        return OnlineGameEntity.builder()
                .withId(GAME_ID)
                .withSource("INVITE")
                .withWhiteAccountId(WHITE_ID)
                .withBlackAccountId(BLACK_ID)
                .withStatus("PENDING")
                .withCreatedAt(CLAIMED_AT)
                .withReadyDeadlineAt(READY_DEADLINE)
                .withWhiteLastSeenAt(CLAIMED_AT)
                .withBlackLastSeenAt(CLAIMED_AT)
                .withInitialFen(START_FEN)
                .withCurrentFen(START_FEN)
                .build();
    }

    private OnlineGameEntity activeGame() {
        var game = pendingGame();
        game.setStatus("ACTIVE");
        game.setWhiteReadyAt(CLAIMED_AT.plusSeconds(5));
        game.setBlackReadyAt(CLAIMED_AT.plusSeconds(6));
        game.setStartedAt(CLAIMED_AT.plusSeconds(6));

        return game;
    }
}
