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
import com.swd392.backend.onlinegame.entity.GameAbandonVoteEntity;
import com.swd392.backend.onlinegame.entity.GameAbandonVoteId;
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
import com.swd392.backend.onlinegame.repository.GameAbandonVoteRepository;
import com.swd392.backend.onlinegame.repository.GameMoveRepository;
import com.swd392.backend.onlinegame.repository.OnlineGameRepository;
import com.swd392.backend.rating.model.RatedGameResult;
import com.swd392.backend.rating.service.RatingService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final Duration RECONNECT_GRACE = Duration.ofMinutes(3);
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
    private final GameAbandonVoteRepository gameAbandonVoteRepository;
    private final ChessRulesService chessRulesService;
    private final AccountService accountService;
    private final RatingService ratingService;
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
        if (!isPending(entity) && !isActive(entity)) {
            return toViewerGame(entity, accountId);
        }
        Instant now = now();
        if (isPending(entity)) {
            recordSeen(entity, accountId, now);
            cancelIfDue(entity, now);
        } else if (!completeIfEndingDue(entity, now)) {
            recordPresence(entity, accountId, now);
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
        rejectIfEndingDue(entity, now);
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
        recordPresence(entity, accountId, now);
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
    public OnlineGame resign(Long gameId, Long accountId) {
        OnlineGameEntity entity = lockParticipantGame(gameId, accountId);
        if (isResignedBy(entity, accountId)) {
            return toViewerGame(entity, accountId);
        }
        Instant now = now();
        if (!completeIfEndingDue(entity, now)) {
            requireActive(entity);
            GameResult outcome = isWhite(entity, accountId) ? GameResult.BLACK_WIN : GameResult.WHITE_WIN;
            complete(entity, now, outcome, TerminationReason.RESIGNATION);
        }
        OnlineGameEntity saved = onlineGameRepository.save(entity);

        return toViewerGame(saved, accountId);
    }

    // F10: a vote is permanent until the game ends (decision 2026-10-19); the second participant's vote ends
    // the game as a draw. Votes are serialized by the game row lock, so simultaneous votes complete it once.
    @Override
    public OnlineGame voteToAbandon(Long gameId, Long accountId) {
        OnlineGameEntity entity = lockParticipantGame(gameId, accountId);
        List<GameAbandonVoteEntity> votes = gameAbandonVoteRepository.findByIdGameId(gameId);
        boolean callerVoted =
                votes.stream().anyMatch(vote -> accountId.equals(vote.getId().getAccountId()));
        Instant now = now();
        boolean ended = completeIfEndingDue(entity, now);
        if (callerVoted && !ended) {
            return toViewerGame(entity, accountId);
        }
        if (!ended) {
            requireActive(entity);
            GameAbandonVoteEntity vote = GameAbandonVoteEntity.builder()
                    .withId(new GameAbandonVoteId(gameId, accountId))
                    .withVotedAt(now)
                    .build();
            gameAbandonVoteRepository.save(vote);
            log.info("[ONLINE_GAME] - ACTION: voteToAbandon: gameId: {}, accountId: {}", gameId, accountId);
            recordPresence(entity, accountId, now);
            boolean opponentVoted = !votes.isEmpty();
            if (opponentVoted) {
                complete(entity, now, GameResult.DRAW, TerminationReason.MUTUAL_ABANDONMENT);
            }
        }
        OnlineGameEntity saved = onlineGameRepository.save(entity);

        return toViewerGame(saved, accountId);
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
    public List<Long> findActiveGamesDue() {
        Instant now = now();

        return onlineGameRepository.findActiveSweepCandidates(ACTIVE, activeSilentSince(now)).stream()
                .filter(entity -> dueEnding(entity, now).isPresent() || hasUnmarkedDisconnect(entity, now))
                .map(OnlineGameEntity::getId)
                .toList();
    }

    @Override
    public void resolveActiveGame(Long gameId) {
        onlineGameRepository.findForUpdateById(gameId).filter(this::isActive).ifPresent(entity -> {
            Instant now = now();
            boolean changed = completeIfEndingDue(entity, now) || markDisconnected(entity, now);
            if (changed) {
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
        requireActive(entity);
        if (submission.getPly() != entity.getLastPly() + 1) {
            throw new AppException(AppErrorMessage.MOVE_OUT_OF_SEQUENCE);
        }
        if (isWhiteToMove(entity) != isWhite(entity, accountId)) {
            throw new AppException(AppErrorMessage.NOT_YOUR_TURN);
        }
    }

    private Long moverRemainingAfterMove(OnlineGameEntity entity, Instant now) {
        if (!entity.isClockEnabled()) {
            return null;
        }

        return sideToMoveRemainingMs(entity, now) + entity.getIncrementSeconds() * MILLIS_PER_SECOND;
    }

    // A move arriving once the game's ending is due is rejected and changes no rows (decision 2026-10-15);
    // the TIMEOUT or DISCONNECT result is recorded separately by completeIfEndingDue.
    private void rejectIfEndingDue(OnlineGameEntity entity, Instant now) {
        Optional<Ending> ending = dueEnding(entity, now);
        if (ending.isEmpty()) {
            return;
        }
        boolean timedOut = ending.get().reason() == TerminationReason.TIMEOUT;

        throw new AppException(timedOut ? AppErrorMessage.TIME_EXPIRED : AppErrorMessage.DISCONNECT_FORFEIT_DUE);
    }

    // Records the due ending, if any. ended_at is the instant it fell due, not the detection time, and the
    // running clock is stopped at that instant (a flagged player's remaining time is 0).
    private boolean completeIfEndingDue(OnlineGameEntity entity, Instant now) {
        Optional<Ending> ending = dueEnding(entity, now);
        if (ending.isEmpty()) {
            return false;
        }
        Ending due = ending.get();
        boolean whiteLoses = due.loser() == ChessColor.WHITE;
        if (entity.isClockEnabled()) {
            long remainingMs = Math.max(0L, sideToMoveRemainingMs(entity, due.at()));
            if (isWhiteToMove(entity)) {
                entity.setWhiteRemainingMs(remainingMs);
            } else {
                entity.setBlackRemainingMs(remainingMs);
            }
        }
        if (due.reason() == TerminationReason.DISCONNECT) {
            markForfeitedDisconnect(entity, whiteLoses);
        }
        GameResult outcome = whiteLoses ? GameResult.BLACK_WIN : GameResult.WHITE_WIN;
        complete(entity, due.at(), outcome, due.reason());

        return true;
    }

    // The ending that fell due first decides an active game:
    // - BR-GAME-014/F09: the side to move loses on time once its clock reaches zero, whatever material the
    //   opponent has left (decision 2026-10-15). A game without a clock never times out.
    // - BR-GAME-015/016/F11: a participant forfeits three minutes after their last contact; reaching the
    //   deadline forfeits, and when both are away the earlier deadline loses (decisions 2026-10-19).
    // Candidates are in tie-break order: on an exact tie the flag falls first, then the side to move forfeits.
    private Optional<Ending> dueEnding(OnlineGameEntity entity, Instant now) {
        if (!isActive(entity)) {
            return Optional.empty();
        }
        ChessColor sideToMove = isWhiteToMove(entity) ? ChessColor.WHITE : ChessColor.BLACK;
        ChessColor opponent = sideToMove == ChessColor.WHITE ? ChessColor.BLACK : ChessColor.WHITE;
        List<Ending> candidates = entity.isClockEnabled()
                ? List.of(flagFall(entity, sideToMove), forfeit(entity, sideToMove), forfeit(entity, opponent))
                : List.of(forfeit(entity, sideToMove), forfeit(entity, opponent));
        Ending first = null;
        for (Ending candidate : candidates) {
            boolean due = !candidate.at().isAfter(now);
            if (due && (first == null || candidate.at().isBefore(first.at()))) {
                first = candidate;
            }
        }

        return Optional.ofNullable(first);
    }

    private Ending flagFall(OnlineGameEntity entity, ChessColor sideToMove) {
        long remainingMs = sideToMove == ChessColor.WHITE ? entity.getWhiteRemainingMs() : entity.getBlackRemainingMs();

        return new Ending(entity.getTurnStartedAt().plusMillis(remainingMs), sideToMove, TerminationReason.TIMEOUT);
    }

    private Ending forfeit(OnlineGameEntity entity, ChessColor player) {
        Instant lastSeenAt = player == ChessColor.WHITE ? entity.getWhiteLastSeenAt() : entity.getBlackLastSeenAt();

        return new Ending(lastSeen(lastSeenAt, entity).plus(RECONNECT_GRACE), player, TerminationReason.DISCONNECT);
    }

    // The forfeiting player stays recorded as disconnected since their last contact.
    private void markForfeitedDisconnect(OnlineGameEntity entity, boolean white) {
        if (white && entity.getWhiteDisconnectedAt() == null) {
            entity.setWhiteDisconnectedAt(lastSeen(entity.getWhiteLastSeenAt(), entity));
        }
        if (!white && entity.getBlackDisconnectedAt() == null) {
            entity.setBlackDisconnectedAt(lastSeen(entity.getBlackLastSeenAt(), entity));
        }
    }

    // Any action by a participant in an active game reconnects them; the opponent is marked disconnected,
    // from their last contact, once silent for the active heartbeat timeout.
    private void recordPresence(OnlineGameEntity entity, Long accountId, Instant now) {
        recordSeen(entity, accountId, now);
        markDisconnected(entity, now);
    }

    private boolean markDisconnected(OnlineGameEntity entity, Instant now) {
        Instant silentSince = activeSilentSince(now);
        Instant whiteLastSeen = lastSeen(entity.getWhiteLastSeenAt(), entity);
        Instant blackLastSeen = lastSeen(entity.getBlackLastSeenAt(), entity);
        boolean whiteSilent = isUnmarkedSilence(entity.getWhiteDisconnectedAt(), whiteLastSeen, silentSince);
        boolean blackSilent = isUnmarkedSilence(entity.getBlackDisconnectedAt(), blackLastSeen, silentSince);
        if (whiteSilent) {
            entity.setWhiteDisconnectedAt(whiteLastSeen);
        }
        if (blackSilent) {
            entity.setBlackDisconnectedAt(blackLastSeen);
        }
        if (whiteSilent || blackSilent) {
            log.info(
                    "[ONLINE_GAME] - ACTION: markDisconnected: gameId: {}, white: {}, black: {}",
                    entity.getId(),
                    whiteSilent,
                    blackSilent);
        }

        return whiteSilent || blackSilent;
    }

    private boolean hasUnmarkedDisconnect(OnlineGameEntity entity, Instant now) {
        Instant silentSince = activeSilentSince(now);
        Instant whiteLastSeen = lastSeen(entity.getWhiteLastSeenAt(), entity);
        Instant blackLastSeen = lastSeen(entity.getBlackLastSeenAt(), entity);

        return isUnmarkedSilence(entity.getWhiteDisconnectedAt(), whiteLastSeen, silentSince)
                || isUnmarkedSilence(entity.getBlackDisconnectedAt(), blackLastSeen, silentSince);
    }

    // Exactly the timeout counts as silent, as for pending games.
    private boolean isUnmarkedSilence(Instant disconnectedAt, Instant lastSeenAt, Instant silentSince) {
        return disconnectedAt == null && !lastSeenAt.isAfter(silentSince);
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
        complete(entity, now, position.getResult(), toTerminationReason(position.getStatus()));
    }

    private void complete(OnlineGameEntity entity, Instant endedAt, GameResult outcome, TerminationReason reason) {
        entity.setStatus(OnlineGameStatus.COMPLETED.name());
        entity.setEndedAt(endedAt);
        entity.setOutcome(outcome.name());
        entity.setTerminationReason(reason.name());
        log.info(
                "[ONLINE_GAME] - ACTION: completeGame: gameId: {}, outcome: {}, reason: {}",
                entity.getId(),
                outcome,
                reason);
        applyRatingIfRated(entity, outcome);
    }

    // F12/BR-RATE-006-008: every completion path ends here under the game row lock, so a rated game's result
    // is rated once and in the same transaction; unrated games and pre-start cancellations are never rated.
    private void applyRatingIfRated(OnlineGameEntity entity, GameResult outcome) {
        if (!entity.isRated()) {
            return;
        }
        RatedGameResult result = RatedGameResult.builder()
                .withGameId(entity.getId())
                .withModeCode(entity.getModeCode())
                .withWhiteAccountId(entity.getWhiteAccountId())
                .withBlackAccountId(entity.getBlackAccountId())
                .withOutcome(outcome)
                .build();
        ratingService.applyGameResult(result);
    }

    private void requireActive(OnlineGameEntity entity) {
        if (!isActive(entity)) {
            throw new AppException(AppErrorMessage.ONLINE_GAME_NOT_ACTIVE);
        }
    }

    // A retried resignation (e.g. after a lost response) returns the game the caller already resigned.
    private boolean isResignedBy(OnlineGameEntity entity, Long accountId) {
        GameResult callerLoss = isWhite(entity, accountId) ? GameResult.BLACK_WIN : GameResult.WHITE_WIN;

        return TerminationReason.RESIGNATION.name().equals(entity.getTerminationReason())
                && callerLoss.name().equals(entity.getOutcome());
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
            entity.setWhiteDisconnectedAt(null);
        } else {
            entity.setBlackLastSeenAt(now);
            entity.setBlackDisconnectedAt(null);
        }
    }

    private OnlineGame toViewerGame(OnlineGameEntity entity, Long accountId) {
        OnlineGame game = onlineGameEntityMapper.toModel(entity);
        Map<Long, String> displayNames =
                accountService.getDisplayNames(List.of(game.getWhiteAccountId(), game.getBlackAccountId()));
        game.setWhiteDisplayName(displayNames.get(game.getWhiteAccountId()));
        game.setBlackDisplayName(displayNames.get(game.getBlackAccountId()));
        game.setViewerColor(isWhite(entity, accountId) ? ChessColor.WHITE : ChessColor.BLACK);
        boolean started = entity.getStartedAt() != null;
        if (started) {
            List<Long> voterIds = gameAbandonVoteRepository.findByIdGameId(entity.getId()).stream()
                    .map(vote -> vote.getId().getAccountId())
                    .toList();
            game.setWhiteAbandonVoted(voterIds.contains(game.getWhiteAccountId()));
            game.setBlackAbandonVoted(voterIds.contains(game.getBlackAccountId()));
        }

        return game;
    }

    // Games claimed before heartbeats were recorded have no last-seen value; the claim time stands in.
    private Instant lastSeen(Instant lastSeenAt, OnlineGameEntity entity) {
        return lastSeenAt == null ? entity.getCreatedAt() : lastSeenAt;
    }

    private Instant silentSince(Instant now) {
        return now.minus(onlineGameProperties.pendingHeartbeatTimeout());
    }

    private Instant activeSilentSince(Instant now) {
        return now.minus(onlineGameProperties.activeHeartbeatTimeout());
    }

    private boolean isPending(OnlineGameEntity entity) {
        return PENDING.equals(entity.getStatus());
    }

    private boolean isActive(OnlineGameEntity entity) {
        return ACTIVE.equals(entity.getStatus());
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

    private record Ending(Instant at, ChessColor loser, TerminationReason reason) {}
}
