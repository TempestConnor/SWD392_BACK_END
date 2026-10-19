package com.swd392.backend.matchmaking.service;

import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.matchmaking.entity.MatchmakingConfigEntity;
import com.swd392.backend.matchmaking.entity.MatchmakingConfigId;
import com.swd392.backend.matchmaking.entity.QueueRequestEntity;
import com.swd392.backend.matchmaking.mapper.QueueRequestEntityMapper;
import com.swd392.backend.matchmaking.model.QueueJoinRequest;
import com.swd392.backend.matchmaking.model.QueuePairing;
import com.swd392.backend.matchmaking.model.QueueRequest;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// BR-RATE-015/018/019: one open queue request per account, explicit cancellation leaves immediately, and a
// request silent for the configured heartbeat interval plus grace (decision 2026-10-19: 10 + 20 seconds) closes
// as TIMED_OUT at that deadline. Pairing (F06) consumes two compatible requests and creates one pending rated
// game atomically. Every change happens under the request's row lock.
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class MatchmakingServiceImpl implements MatchmakingService {

    private static final int MATCHMAKING_INITIAL_SECONDS = 30 * 60;
    private static final int MATCHMAKING_INCREMENT_SECONDS = 10;

    private final QueueRequestRepository queueRequestRepository;
    private final QueueRequestEntityMapper queueRequestEntityMapper;
    private final MatchmakingConfigRepository matchmakingConfigRepository;
    private final RatingService ratingService;
    private final OnlineGameService onlineGameService;
    private final Clock clock;

    // The player's rating row is locked first, so concurrent joins of one account (multiple tabs) serialize and
    // the rating captured at entry is the committed current rating. UX_Queue_one_active backs the same rule.
    @Override
    public QueueRequest join(Long accountId, QueueJoinRequest request) {
        MatchmakingConfigEntity config = enabledConfig(request.getModeCode(), request.getVariantCode());
        PlayerRating rating = ratingService.getRatingForUpdate(accountId, request.getModeCode());
        Instant now = now();
        Optional<QueueRequestEntity> open = queueRequestRepository.findForUpdateByAccountIdAndLeftAtIsNull(accountId);
        if (open.isPresent()) {
            QueueRequestEntity entity = open.get();
            if (!timeOutIfDue(entity, now)) {
                entity.setHeartbeatAt(now);
                return toQueueRequest(queueRequestRepository.save(entity));
            }
            queueRequestRepository.saveAndFlush(entity);
        }

        QueueRequest queueRequest = QueueRequest.builder()
                .withAccountId(accountId)
                .withModeCode(request.getModeCode())
                .withVariantCode(request.getVariantCode())
                .withRatingAtEntry(rating.getRating())
                .withJoinedAt(now)
                .withHeartbeatAt(now)
                .build();
        QueueRequestEntity saved = queueRequestRepository.saveAndFlush(queueRequestEntityMapper.toEntity(queueRequest));
        log.info(
                "[MATCHMAKING] - ACTION: join: requestId: {}, accountId: {}, mode: {}",
                saved.getId(),
                accountId,
                saved.getModeCode());

        return toQueueRequest(saved, config);
    }

    @Override
    public QueueRequest heartbeat(Long accountId) {
        Optional<QueueRequestEntity> open = queueRequestRepository.findForUpdateByAccountIdAndLeftAtIsNull(accountId);
        if (open.isEmpty()) {
            return latestRequest(accountId);
        }

        QueueRequestEntity entity = open.get();
        Instant now = now();
        if (!timeOutIfDue(entity, now)) {
            entity.setHeartbeatAt(now);
        }

        return toQueueRequest(queueRequestRepository.save(entity));
    }

    // A request already closed (matched, cancelled or timed out) is returned unchanged, so a repeated cancel is
    // harmless and a cancel that loses the race against pairing reports the match.
    @Override
    public QueueRequest cancel(Long accountId) {
        Optional<QueueRequestEntity> open = queueRequestRepository.findForUpdateByAccountIdAndLeftAtIsNull(accountId);
        if (open.isEmpty()) {
            return latestRequest(accountId);
        }

        QueueRequestEntity entity = open.get();
        Instant now = now();
        if (!timeOutIfDue(entity, now)) {
            close(entity, now, QueueStatus.CANCELLED);
            log.info("[MATCHMAKING] - ACTION: cancel: requestId: {}", entity.getId());
        }

        return toQueueRequest(queueRequestRepository.save(entity));
    }

    @Override
    public List<Long> findRequestsDueForTimeout() {
        Instant now = now();

        return matchmakingConfigRepository.findAll().stream()
                .flatMap(config -> queueRequestRepository
                        .findOpenIdsSilentSince(
                                config.getId().getModeCode(),
                                config.getId().getVariantCode(),
                                now.minus(silenceLimit(config)))
                        .stream())
                .toList();
    }

    @Override
    public void timeOutIfDue(Long requestId) {
        queueRequestRepository
                .findForUpdateById(requestId)
                .filter(entity -> entity.getLeftAt() == null)
                .filter(entity -> timeOutIfDue(entity, now()))
                .ifPresent(queueRequestRepository::save);
    }

    // BR-RATE-013/014/016: open requests of the same enabled mode and variant are compatible when their rating
    // difference (at entry) fits within each request's own current gap, so the shorter waiter limits it (decision
    // 2026-10-19). Older requests choose first and take the compatible request with the closest rating, the
    // earlier joiner on a tie. This is a snapshot only; pair() re-checks every pairing under the row locks.
    @Override
    public List<QueuePairing> findPairingCandidates() {
        Instant now = now();

        return matchmakingConfigRepository.findAll().stream()
                .filter(config -> ratingService.isModeEnabled(config.getId().getModeCode()))
                .flatMap(config -> pairingCandidates(config, now).stream())
                .toList();
    }

    // BR-RATE-020/021: both requests are locked in ascending id order, so competing workers serialize without
    // deadlock, and are re-checked as open, not silent and compatible. Consuming both and creating the pending
    // rated game then happen in one transaction; a pairing that no longer qualifies changes nothing except
    // recording a timeout that has fallen due.
    @Override
    public Optional<Long> pair(Long firstRequestId, Long secondRequestId) {
        Optional<QueueRequestEntity> lower = lockOpenRequest(Math.min(firstRequestId, secondRequestId));
        if (lower.isEmpty()) {
            return Optional.empty();
        }
        Optional<QueueRequestEntity> higher = lockOpenRequest(Math.max(firstRequestId, secondRequestId));
        if (higher.isEmpty()) {
            return Optional.empty();
        }

        QueueRequestEntity first = lower.get();
        QueueRequestEntity second = higher.get();
        Instant now = now();
        boolean firstTimedOut = timeOutIfDue(first, now);
        boolean secondTimedOut = timeOutIfDue(second, now);
        if (firstTimedOut || secondTimedOut) {
            queueRequestRepository.save(first);
            queueRequestRepository.save(second);
            return Optional.empty();
        }
        Optional<MatchmakingConfigEntity> config = configOf(first);
        boolean compatible =
                isSameQueue(first, second) && config.isPresent() && isCompatible(first, second, config.get(), now);
        if (!compatible) {
            return Optional.empty();
        }

        OnlineGame game = onlineGameService.createPendingGame(newGame(first, second, now));
        match(first, game.getId(), now);
        match(second, game.getId(), now);
        queueRequestRepository.save(first);
        queueRequestRepository.save(second);
        log.info(
                "[MATCHMAKING] - ACTION: pair: requestIds: {}, {}, gameId: {}",
                first.getId(),
                second.getId(),
                game.getId());

        return Optional.of(game.getId());
    }

    private List<QueuePairing> pairingCandidates(MatchmakingConfigEntity config, Instant now) {
        List<QueueRequestEntity> waiting = queueRequestRepository
                .findByModeCodeAndVariantCodeAndLeftAtIsNullOrderByJoinedAtAscIdAsc(
                        config.getId().getModeCode(), config.getId().getVariantCode())
                .stream()
                .filter(entity -> now.isBefore(entity.getHeartbeatAt().plus(silenceLimit(config))))
                .collect(Collectors.toCollection(ArrayList::new));
        List<QueuePairing> pairings = new ArrayList<>();
        while (!waiting.isEmpty()) {
            QueueRequestEntity first = waiting.removeFirst();
            Optional<QueueRequestEntity> closest = waiting.stream()
                    .filter(candidate -> isCompatible(first, candidate, config, now))
                    .min(Comparator.comparingInt(candidate -> ratingDifference(first, candidate)));
            closest.ifPresent(second -> {
                waiting.remove(second);
                pairings.add(new QueuePairing(first.getId(), second.getId()));
            });
        }

        return pairings;
    }

    private boolean isSameQueue(QueueRequestEntity first, QueueRequestEntity second) {
        return first.getModeCode().equals(second.getModeCode())
                && first.getVariantCode().equals(second.getVariantCode());
    }

    private boolean isCompatible(
            QueueRequestEntity first, QueueRequestEntity second, MatchmakingConfigEntity config, Instant now) {
        int difference = ratingDifference(first, second);

        return !first.getAccountId().equals(second.getAccountId())
                && difference <= currentGap(first, config, now)
                && difference <= currentGap(second, config, now);
    }

    private int ratingDifference(QueueRequestEntity first, QueueRequestEntity second) {
        return Math.abs(first.getRatingAtEntry() - second.getRatingAtEntry());
    }

    // The gap widens by one step after each full step interval of waiting (exactly the interval counts) and never
    // exceeds the maximum (BR-RATE-016).
    private long currentGap(QueueRequestEntity entity, MatchmakingConfigEntity config, Instant now) {
        long waitedSeconds =
                Math.max(0, Duration.between(entity.getJoinedAt(), now).toSeconds());
        long steps = waitedSeconds / config.getGapStepSeconds();

        return Math.min(config.getMaxGap(), config.getInitialGap() + steps * config.getGapStep());
    }

    // BR-GAME-007/008, BR-MOVE-013/014: colors are assigned at random (decision 2026-10-19); matchmade games are
    // rated and use the queue's fixed 30+10 clock.
    private OnlineGame newGame(QueueRequestEntity first, QueueRequestEntity second, Instant now) {
        boolean firstIsWhite = ThreadLocalRandom.current().nextBoolean();
        OnlineGame game = OnlineGame.builder()
                .withSource(GameSource.MATCHMAKING)
                .withWhiteAccountId(firstIsWhite ? first.getAccountId() : second.getAccountId())
                .withBlackAccountId(firstIsWhite ? second.getAccountId() : first.getAccountId())
                .withVariantCode(first.getVariantCode())
                .withModeCode(first.getModeCode())
                .withRated(true)
                .withClockEnabled(true)
                .withInitialSeconds(MATCHMAKING_INITIAL_SECONDS)
                .withIncrementSeconds(MATCHMAKING_INCREMENT_SECONDS)
                .withCreatedAt(now)
                .build();

        return game;
    }

    private void match(QueueRequestEntity entity, Long gameId, Instant now) {
        close(entity, now, QueueStatus.MATCHED);
        entity.setGameId(gameId);
    }

    private Optional<QueueRequestEntity> lockOpenRequest(Long requestId) {
        return queueRequestRepository.findForUpdateById(requestId).filter(entity -> entity.getLeftAt() == null);
    }

    // The request leaves the queue at the instant its silence limit was reached, not when the expiry is detected.
    private boolean timeOutIfDue(QueueRequestEntity entity, Instant now) {
        Optional<Instant> deadline =
                configOf(entity).map(config -> entity.getHeartbeatAt().plus(silenceLimit(config)));
        boolean due = deadline.isPresent() && !now.isBefore(deadline.get());
        if (due) {
            close(entity, deadline.get(), QueueStatus.TIMED_OUT);
            log.info("[MATCHMAKING] - ACTION: timeOut: requestId: {}", entity.getId());
        }

        return due;
    }

    private void close(QueueRequestEntity entity, Instant leftAt, QueueStatus exitReason) {
        entity.setLeftAt(leftAt);
        entity.setExitReason(exitReason.name());
    }

    private MatchmakingConfigEntity enabledConfig(String modeCode, String variantCode) {
        return matchmakingConfigRepository
                .findById(new MatchmakingConfigId(modeCode, variantCode))
                .filter(config -> ratingService.isModeEnabled(modeCode))
                .orElseThrow(() -> new AppException(AppErrorMessage.MATCHMAKING_UNAVAILABLE));
    }

    private Optional<MatchmakingConfigEntity> configOf(QueueRequestEntity entity) {
        return matchmakingConfigRepository.findById(
                new MatchmakingConfigId(entity.getModeCode(), entity.getVariantCode()));
    }

    private Duration silenceLimit(MatchmakingConfigEntity config) {
        return Duration.ofSeconds(config.getHeartbeatSeconds() + config.getQueueGraceSeconds());
    }

    private QueueRequest latestRequest(Long accountId) {
        return queueRequestRepository
                .findFirstByAccountIdOrderByIdDesc(accountId)
                .map(this::toQueueRequest)
                .orElseThrow(() -> new AppException(AppErrorMessage.QUEUE_REQUEST_NOT_FOUND));
    }

    private QueueRequest toQueueRequest(QueueRequestEntity entity) {
        return toQueueRequest(entity, configOf(entity).orElse(null));
    }

    private QueueRequest toQueueRequest(QueueRequestEntity entity, MatchmakingConfigEntity config) {
        QueueRequest queueRequest = queueRequestEntityMapper.toModel(entity);
        queueRequest.setStatus(
                entity.getLeftAt() == null ? QueueStatus.QUEUED : QueueStatus.valueOf(entity.getExitReason()));
        queueRequest.setHeartbeatIntervalSeconds(config == null ? null : config.getHeartbeatSeconds());

        return queueRequest;
    }

    // Columns are datetime2(3); truncating keeps stored and returned instants identical.
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
