package com.swd392.backend.onlinegame.service;

import com.swd392.backend.account.service.AccountService;
import com.swd392.backend.chess.model.ChessColor;
import com.swd392.backend.chess.model.ChessPosition;
import com.swd392.backend.chess.model.GameResult;
import com.swd392.backend.chess.model.GameStatus;
import com.swd392.backend.chess.service.ChessRulesService;
import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.config.OnlineGameProperties;
import com.swd392.backend.onlinegame.entity.GameMoveEntity;
import com.swd392.backend.onlinegame.entity.GameMoveId;
import com.swd392.backend.onlinegame.entity.OnlineGameEntity;
import com.swd392.backend.onlinegame.mapper.GameMoveEntityMapper;
import com.swd392.backend.onlinegame.mapper.OnlineGameEntityMapper;
import com.swd392.backend.onlinegame.model.GameMove;
import com.swd392.backend.onlinegame.model.MoveSubmission;
import com.swd392.backend.onlinegame.model.OnlineGame;
import com.swd392.backend.onlinegame.model.OnlineGameStatus;
import com.swd392.backend.onlinegame.model.TerminationReason;
import com.swd392.backend.onlinegame.repository.GameMoveRepository;
import com.swd392.backend.onlinegame.repository.OnlineGameRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OnlineGameServiceImpl implements OnlineGameService {

    private static final Duration READY_WINDOW = Duration.ofMinutes(3);
    private static final long MILLIS_PER_SECOND = 1000L;
    private static final int NO_MOVES_PLAYED = 0;
    private static final String PENDING = OnlineGameStatus.PENDING.name();
    private static final String ACTIVE = OnlineGameStatus.ACTIVE.name();
    private static final String CANCEL_EXPLICIT = "EXPLICIT";
    private static final String CANCEL_READY_DEADLINE = "READY_DEADLINE";
    private static final String CANCEL_HEARTBEAT_LOST = "HEARTBEAT_LOST";

    private final OnlineGameRepository onlineGameRepository;
    private final OnlineGameEntityMapper onlineGameEntityMapper;
    private final GameMoveRepository gameMoveRepository;
    private final GameMoveEntityMapper gameMoveEntityMapper;
    private final ChessRulesService chessRulesService;
    private final AccountService accountService;
    private final OnlineGameProperties onlineGameProperties;
    private final Clock clock;

    @Override
    public OnlineGame createPendingGame(OnlineGame game) {
        ChessPosition initialPosition = chessRulesService.replay(List.of());
        Long remainingMs = game.isClockEnabled() ? game.getInitialSeconds() * MILLIS_PER_SECOND : null;
        game.setStatus(OnlineGameStatus.PENDING);
        game.setReadyDeadlineAt(game.getCreatedAt().plus(READY_WINDOW));
        game.setInitialFen(initialPosition.getFen());
        game.setCurrentFen(initialPosition.getFen());
        game.setLastPly(NO_MOVES_PLAYED);
        game.setWhiteRemainingMs(remainingMs);
        game.setBlackRemainingMs(remainingMs);
        game.setWhiteLastSeenAt(game.getCreatedAt());
        game.setBlackLastSeenAt(game.getCreatedAt());

        OnlineGameEntity entity = onlineGameEntityMapper.toEntity(game);
        OnlineGameEntity saved = onlineGameRepository.saveAndFlush(entity);
        log.info(
                "[ONLINE_GAME] - ACTION: createPendingGame: gameId: {}, source: {}, rated: {}",
                saved.getId(),
                saved.getSource(),
                saved.isRated());

        return onlineGameEntityMapper.toModel(saved);
    }

    @Override
    public OnlineGame getGame(Long gameId, Long accountId) {
        OnlineGameEntity entity = onlineGameRepository
                .findById(gameId)
                .filter(found -> isParticipant(found, accountId))
                .orElseThrow(() -> new AppException(AppErrorMessage.ONLINE_GAME_NOT_FOUND));

        return toViewerGame(entity, accountId);
    }

    @Override
    public OnlineGame markReady(Long gameId, Long accountId) {
        OnlineGameEntity entity = lockParticipantGame(gameId, accountId);
        if (!isPending(entity)) {
            return toViewerGame(entity, accountId);
        }
        Instant now = now();
        recordSeen(entity, accountId, now);
        boolean cancelled = cancelIfDue(entity, now);
        if (!cancelled) {
            recordReady(entity, accountId, now);
            startIfBothReady(entity, now);
        }
        OnlineGameEntity saved = onlineGameRepository.save(entity);

        return toViewerGame(saved, accountId);
    }

    @Override
    public OnlineGame cancelPendingGame(Long gameId, Long accountId) {
        OnlineGameEntity entity = lockParticipantGame(gameId, accountId);
        OnlineGameStatus status = OnlineGameStatus.valueOf(entity.getStatus());
        if (status == OnlineGameStatus.CANCELLED) {
            return toViewerGame(entity, accountId);
        }
        if (status != OnlineGameStatus.PENDING) {
            throw new AppException(AppErrorMessage.ONLINE_GAME_ALREADY_STARTED);
        }

        cancel(entity, now(), CANCEL_EXPLICIT);
        OnlineGameEntity saved = onlineGameRepository.save(entity);

        return toViewerGame(saved, accountId);
    }

    @Override
    public OnlineGame recordHeartbeat(Long gameId, Long accountId) {
        OnlineGameEntity entity = lockParticipantGame(gameId, accountId);
        Instant now = now();
        if (isPending(entity)) {
            recordSeen(entity, accountId, now);
            cancelIfDue(entity, now);
        } else if (!completeIfFlagFallen(entity, now)) {
            return toViewerGame(entity, accountId);
        }
        OnlineGameEntity saved = onlineGameRepository.save(entity);

        return toViewerGame(saved, accountId);
    }

    @Override
    public OnlineGame makeMove(Long gameId, Long accountId, MoveSubmission submission) {
        OnlineGameEntity entity = lockParticipantGame(gameId, accountId);
        if (isAcceptedRetry(entity, accountId, submission)) {
            return toViewerGame(entity, accountId);
        }
        requireMoveAllowed(entity, accountId, submission);
        Instant now = now();
        boolean whiteMoves = isWhite(entity, accountId);
        Long moverRemainingMs = moverRemainingAfterMove(entity, now);
        List<String> playedMoves = gameMoveRepository.findByIdGameIdOrderByIdPlyAsc(gameId).stream()
                .map(GameMoveEntity::getMoveUci)
                .toList();
        ChessPosition position = chessRulesService.play(playedMoves, submission.getMove());

        applyMove(entity, position, now);
        if (whiteMoves) {
            entity.setWhiteRemainingMs(moverRemainingMs);
        } else {
            entity.setBlackRemainingMs(moverRemainingMs);
        }
        GameMove move = GameMove.builder()
                .withGameId(gameId)
                .withPly(submission.getPly())
                .withPlayedByAccountId(accountId)
                .withMoveUci(submission.getMove())
                .withSan(position.getLastMoveSan())
                .withResultingFen(position.getFen())
                .withWhiteRemainingMs(entity.getWhiteRemainingMs())
                .withBlackRemainingMs(entity.getBlackRemainingMs())
                .withAcceptedAt(now)
                .build();
        gameMoveRepository.save(gameMoveEntityMapper.toEntity(move));
        finishIfOver(entity, position, now);
        OnlineGameEntity saved = onlineGameRepository.save(entity);
        log.info(
                "[ONLINE_GAME] - ACTION: makeMove: gameId: {}, ply: {}, accountId: {}",
                gameId,
                submission.getPly(),
                accountId);

        return toViewerGame(saved, accountId);
    }

    @Override
    public List<GameMove> getMoves(Long gameId, Long accountId) {
        onlineGameRepository
                .findById(gameId)
                .filter(found -> isParticipant(found, accountId))
                .orElseThrow(() -> new AppException(AppErrorMessage.ONLINE_GAME_NOT_FOUND));
        List<GameMoveEntity> moves = gameMoveRepository.findByIdGameIdOrderByIdPlyAsc(gameId);

        return gameMoveEntityMapper.toModels(moves);
    }

    @Override
    public List<Long> findGamesDueForPreStartCancel() {
        Instant now = now();

        return onlineGameRepository.findIdsDueForPreStartCancel(PENDING, now, silentSince(now));
    }

    @Override
    public void cancelIfPreStartDue(Long gameId) {
        onlineGameRepository.findForUpdateById(gameId).filter(this::isPending).ifPresent(entity -> {
            if (cancelIfDue(entity, now())) {
                onlineGameRepository.save(entity);
            }
        });
    }

    @Override
    public List<Long> findGamesDueForTimeout() {
        Instant now = now();

        return onlineGameRepository.findByStatusAndClockEnabledTrue(ACTIVE).stream()
                .filter(entity -> isFlagFallen(entity, now))
                .map(OnlineGameEntity::getId)
                .toList();
    }

    @Override
    public void completeIfTimedOut(Long gameId) {
        onlineGameRepository.findForUpdateById(gameId).ifPresent(entity -> {
            if (completeIfFlagFallen(entity, now())) {
                onlineGameRepository.save(entity);
            }
        });
    }

    private OnlineGameEntity lockParticipantGame(Long gameId, Long accountId) {
        return onlineGameRepository
                .findForUpdateById(gameId)
                .filter(found -> isParticipant(found, accountId))
                .orElseThrow(() -> new AppException(AppErrorMessage.ONLINE_GAME_NOT_FOUND));
    }

    // BR-MOVE-008: resending the move already accepted at that ply (e.g. after a timeout) returns the current state.
    private boolean isAcceptedRetry(OnlineGameEntity entity, Long accountId, MoveSubmission submission) {
        boolean alreadyPlayedPly = submission.getPly() <= entity.getLastPly();
        if (!alreadyPlayedPly) {
            return false;
        }
        GameMoveId moveId = new GameMoveId(entity.getId(), submission.getPly());

        return gameMoveRepository
                .findById(moveId)
                .filter(move -> accountId.equals(move.getPlayedByAccountId()))
                .filter(move -> submission.getMove().equals(move.getMoveUci()))
                .isPresent();
    }

    private void requireMoveAllowed(OnlineGameEntity entity, Long accountId, MoveSubmission submission) {
        if (!ACTIVE.equals(entity.getStatus())) {
            throw new AppException(AppErrorMessage.ONLINE_GAME_NOT_ACTIVE);
        }
        if (submission.getPly() != entity.getLastPly() + 1) {
            throw new AppException(AppErrorMessage.MOVE_OUT_OF_SEQUENCE);
        }
        if (isWhiteToMove(entity) != isWhite(entity, accountId)) {
            throw new AppException(AppErrorMessage.NOT_YOUR_TURN);
        }
    }

    // A move arriving once the mover's flag has fallen is rejected and changes no rows (decision 2026-10-15);
    // the TIMEOUT result is recorded separately by completeIfFlagFallen.
    private Long moverRemainingAfterMove(OnlineGameEntity entity, Instant now) {
        if (!entity.isClockEnabled()) {
            return null;
        }
        long remainingAtMoveMs = sideToMoveRemainingMs(entity, now);
        if (remainingAtMoveMs <= 0) {
            throw new AppException(AppErrorMessage.TIME_EXPIRED);
        }

        return remainingAtMoveMs + entity.getIncrementSeconds() * MILLIS_PER_SECOND;
    }

    // BR-GAME-014/F09: the side to move loses on time once its clock reaches zero, whatever material the
    // opponent has left (decision 2026-10-15). ended_at is the flag-fall instant, not the detection time.
    private boolean completeIfFlagFallen(OnlineGameEntity entity, Instant now) {
        if (!ACTIVE.equals(entity.getStatus()) || !isFlagFallen(entity, now)) {
            return false;
        }
        boolean whiteFlagged = isWhiteToMove(entity);
        long flaggedRemainingMs = whiteFlagged ? entity.getWhiteRemainingMs() : entity.getBlackRemainingMs();
        if (whiteFlagged) {
            entity.setWhiteRemainingMs(0L);
        } else {
            entity.setBlackRemainingMs(0L);
        }
        GameResult outcome = whiteFlagged ? GameResult.BLACK_WIN : GameResult.WHITE_WIN;
        entity.setStatus(OnlineGameStatus.COMPLETED.name());
        entity.setEndedAt(entity.getTurnStartedAt().plusMillis(flaggedRemainingMs));
        entity.setOutcome(outcome.name());
        entity.setTerminationReason(TerminationReason.TIMEOUT.name());
        log.info(
                "[ONLINE_GAME] - ACTION: completeGame: gameId: {}, outcome: {}, reason: {}",
                entity.getId(),
                entity.getOutcome(),
                TerminationReason.TIMEOUT);

        return true;
    }

    // A game without a clock never times out.
    private boolean isFlagFallen(OnlineGameEntity entity, Instant now) {
        return entity.isClockEnabled() && sideToMoveRemainingMs(entity, now) <= 0;
    }

    private long sideToMoveRemainingMs(OnlineGameEntity entity, Instant now) {
        long elapsedMs = Duration.between(entity.getTurnStartedAt(), now).toMillis();
        long remainingMs = isWhiteToMove(entity) ? entity.getWhiteRemainingMs() : entity.getBlackRemainingMs();

        return remainingMs - elapsedMs;
    }

    private void applyMove(OnlineGameEntity entity, ChessPosition position, Instant now) {
        entity.setCurrentFen(position.getFen());
        entity.setLastPly(position.getPly());
        entity.setTurnStartedAt(now);
    }

    // Online games end on the same library-detected outcomes as CPU games (decision 2026-10-15).
    private void finishIfOver(OnlineGameEntity entity, ChessPosition position, Instant now) {
        if (position.getStatus() == GameStatus.ACTIVE) {
            return;
        }
        TerminationReason reason = toTerminationReason(position.getStatus());
        entity.setStatus(OnlineGameStatus.COMPLETED.name());
        entity.setEndedAt(now);
        entity.setOutcome(position.getResult().name());
        entity.setTerminationReason(reason.name());
        log.info(
                "[ONLINE_GAME] - ACTION: completeGame: gameId: {}, outcome: {}, reason: {}",
                entity.getId(),
                entity.getOutcome(),
                reason);
    }

    private TerminationReason toTerminationReason(GameStatus status) {
        return switch (status) {
            case CHECKMATE -> TerminationReason.CHECKMATE;
            case STALEMATE -> TerminationReason.STALEMATE;
            case INSUFFICIENT_MATERIAL -> TerminationReason.INSUFFICIENT_MATERIAL;
            case THREEFOLD_REPETITION -> TerminationReason.REPETITION;
            case FIFTY_MOVE_RULE -> TerminationReason.MOVE_COUNT;
            case ACTIVE -> throw new IllegalStateException("An active position has no termination reason.");
        };
    }

    // BR-INV-013/017: the Ready deadline is reached at the deadline itself; a participant silent for the
    // configured heartbeat timeout counts as a detected connection loss.
    private boolean cancelIfDue(OnlineGameEntity entity, Instant now) {
        if (!now.isBefore(entity.getReadyDeadlineAt())) {
            cancel(entity, now, CANCEL_READY_DEADLINE);
            return true;
        }
        Instant silentSince = silentSince(now);
        boolean heartbeatLost = !lastSeen(entity.getWhiteLastSeenAt(), entity).isAfter(silentSince)
                || !lastSeen(entity.getBlackLastSeenAt(), entity).isAfter(silentSince);
        if (heartbeatLost) {
            cancel(entity, now, CANCEL_HEARTBEAT_LOST);
            return true;
        }

        return false;
    }

    private void cancel(OnlineGameEntity entity, Instant now, String cause) {
        entity.setStatus(OnlineGameStatus.CANCELLED.name());
        entity.setEndedAt(now);
        entity.setTerminationReason(TerminationReason.PRE_START_CANCEL.name());
        log.info("[ONLINE_GAME] - ACTION: cancelPendingGame: gameId: {}, cause: {}", entity.getId(), cause);
    }

    private void recordReady(OnlineGameEntity entity, Long accountId, Instant now) {
        if (isWhite(entity, accountId) && entity.getWhiteReadyAt() == null) {
            entity.setWhiteReadyAt(now);
        }
        if (!isWhite(entity, accountId) && entity.getBlackReadyAt() == null) {
            entity.setBlackReadyAt(now);
        }
    }

    private void startIfBothReady(OnlineGameEntity entity, Instant now) {
        boolean bothReady = entity.getWhiteReadyAt() != null && entity.getBlackReadyAt() != null;
        if (!bothReady) {
            return;
        }
        entity.setStatus(OnlineGameStatus.ACTIVE.name());
        entity.setStartedAt(now);
        entity.setTurnStartedAt(now);
        log.info("[ONLINE_GAME] - ACTION: startGame: gameId: {}", entity.getId());
    }

    private void recordSeen(OnlineGameEntity entity, Long accountId, Instant now) {
        if (isWhite(entity, accountId)) {
            entity.setWhiteLastSeenAt(now);
        } else {
            entity.setBlackLastSeenAt(now);
        }
    }

    private OnlineGame toViewerGame(OnlineGameEntity entity, Long accountId) {
        OnlineGame game = onlineGameEntityMapper.toModel(entity);
        Map<Long, String> displayNames =
                accountService.getDisplayNames(List.of(game.getWhiteAccountId(), game.getBlackAccountId()));
        game.setWhiteDisplayName(displayNames.get(game.getWhiteAccountId()));
        game.setBlackDisplayName(displayNames.get(game.getBlackAccountId()));
        game.setViewerColor(isWhite(entity, accountId) ? ChessColor.WHITE : ChessColor.BLACK);

        return game;
    }

    // Games claimed before heartbeats were recorded have no last-seen value; the claim time stands in.
    private Instant lastSeen(Instant lastSeenAt, OnlineGameEntity entity) {
        return lastSeenAt == null ? entity.getCreatedAt() : lastSeenAt;
    }

    private Instant silentSince(Instant now) {
        return now.minus(onlineGameProperties.pendingHeartbeatTimeout());
    }

    private boolean isPending(OnlineGameEntity entity) {
        return PENDING.equals(entity.getStatus());
    }

    private boolean isWhiteToMove(OnlineGameEntity entity) {
        return entity.getLastPly() % 2 == 0;
    }

    private boolean isWhite(OnlineGameEntity entity, Long accountId) {
        return accountId.equals(entity.getWhiteAccountId());
    }

    private boolean isParticipant(OnlineGameEntity entity, Long accountId) {
        return accountId.equals(entity.getWhiteAccountId()) || accountId.equals(entity.getBlackAccountId());
    }

    // Columns are datetime2(3); truncating keeps in-memory and stored instants identical.
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
