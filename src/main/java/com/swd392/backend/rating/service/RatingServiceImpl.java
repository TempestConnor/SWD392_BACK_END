package com.swd392.backend.rating.service;

import com.swd392.backend.chess.model.GameResult;
import com.swd392.backend.rating.entity.PlayerRatingEntity;
import com.swd392.backend.rating.entity.PlayerRatingId;
import com.swd392.backend.rating.entity.RatingEventEntity;
import com.swd392.backend.rating.entity.RatingModeEntity;
import com.swd392.backend.rating.mapper.PlayerRatingEntityMapper;
import com.swd392.backend.rating.mapper.RatingEventEntityMapper;
import com.swd392.backend.rating.model.PlayerRating;
import com.swd392.backend.rating.model.RatedGameResult;
import com.swd392.backend.rating.model.RatingEvent;
import com.swd392.backend.rating.repository.PlayerRatingRepository;
import com.swd392.backend.rating.repository.RatingEventRepository;
import com.swd392.backend.rating.repository.RatingModeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class RatingServiceImpl implements RatingService {

    private static final int NO_GAMES_PLAYED = 0;
    private static final int MIN_RATING = 0;
    private static final int NO_CHANGE = 0;
    private static final double ELO_SCALE = 400.0;
    private static final double WIN_SCORE = 1.0;

    private final RatingModeRepository ratingModeRepository;
    private final PlayerRatingRepository playerRatingRepository;
    private final PlayerRatingEntityMapper playerRatingEntityMapper;
    private final RatingEventRepository ratingEventRepository;
    private final RatingEventEntityMapper ratingEventEntityMapper;
    private final Clock clock;

    @Override
    public List<PlayerRating> initializeRatings(Long accountId, int startingRating) {
        List<PlayerRatingEntity> entities = ratingModeRepository.findByEnabledTrue().stream()
                .map(mode -> newRating(accountId, mode, startingRating))
                .map(playerRatingEntityMapper::toEntity)
                .toList();

        List<PlayerRatingEntity> saved = playerRatingRepository.saveAll(entities);
        log.info("[RATING] - ACTION: initializeRatings: accountId: {}, modes: {}", accountId, saved.size());

        return saved.stream().map(playerRatingEntityMapper::toModel).toList();
    }

    @Override
    public List<PlayerRating> getRatings(Long accountId) {
        return playerRatingRepository.findByIdAccountIdOrderByIdModeCode(accountId).stream()
                .map(playerRatingEntityMapper::toModel)
                .toList();
    }

    @Override
    public boolean isModeEnabled(String modeCode) {
        return ratingModeRepository
                .findById(modeCode)
                .map(RatingModeEntity::isEnabled)
                .orElse(false);
    }

    // Joins the caller's transaction and holds the rating row lock until it commits, so callers that read the
    // rating as an input (such as a queue entry) serialize per player and mode.
    @Override
    public PlayerRating getRatingForUpdate(Long accountId, String modeCode) {
        return playerRatingEntityMapper.toModel(lockRating(accountId, modeCode));
    }

    @Override
    public List<RatingEvent> getGameEvents(Collection<Long> gameIds) {
        if (gameIds.isEmpty()) {
            return List.of();
        }

        return ratingEventEntityMapper.toModels(ratingEventRepository.findByIdGameIdIn(gameIds));
    }

    // BR-RATE-006/022: joins the caller's game-completion transaction, so the result and both rating
    // applications commit together. The RatingEvent key (game_id, account_id) backs exactly-once; a repeated
    // call for the same game returns the events already stored and changes nothing.
    @Override
    public List<RatingEvent> applyGameResult(RatedGameResult result) {
        List<RatingEventEntity> existing = ratingEventRepository.findByIdGameIdOrderByIdAccountId(result.getGameId());
        if (!existing.isEmpty()) {
            return ratingEventEntityMapper.toModels(existing);
        }
        String modeCode = result.getModeCode();
        int kFactor = ratingModeRepository
                .findById(modeCode)
                .map(RatingModeEntity::getKFactor)
                .orElseThrow(() -> new IllegalStateException("Rating mode " + modeCode + " does not exist."));
        boolean whiteLocksFirst = result.getWhiteAccountId() < result.getBlackAccountId();
        PlayerRatingEntity first =
                lockRating(whiteLocksFirst ? result.getWhiteAccountId() : result.getBlackAccountId(), modeCode);
        PlayerRatingEntity second =
                lockRating(whiteLocksFirst ? result.getBlackAccountId() : result.getWhiteAccountId(), modeCode);
        PlayerRatingEntity white = whiteLocksFirst ? first : second;
        PlayerRatingEntity black = whiteLocksFirst ? second : first;

        int whiteDelta = whiteDelta(white.getRating(), black.getRating(), kFactor, result.getOutcome());
        Instant appliedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        List<RatingEventEntity> events = List.of(
                applyDelta(white, whiteDelta, kFactor, result.getGameId(), appliedAt),
                applyDelta(black, -whiteDelta, kFactor, result.getGameId(), appliedAt));
        List<RatingEventEntity> saved = ratingEventRepository.saveAll(events);
        log.info(
                "[RATING] - ACTION: applyGameResult: gameId: {}, outcome: {}, whiteDelta: {}, blackDelta: {}",
                result.getGameId(),
                result.getOutcome(),
                saved.get(0).getRatingDelta(),
                saved.get(1).getRatingDelta());

        return ratingEventEntityMapper.toModels(saved);
    }

    // Ratings are locked in ascending account order, so concurrent games sharing a player cannot deadlock.
    private PlayerRatingEntity lockRating(Long accountId, String modeCode) {
        return playerRatingRepository
                .findForUpdateById(new PlayerRatingId(accountId, modeCode))
                .orElseThrow(
                        () -> new IllegalStateException("Account " + accountId + " has no " + modeCode + " rating."));
    }

    // BR-ACC-012 simple Elo; BR-RATE-009 every draw is +0 for both. Decision 2026-10-19: the winner's change
    // K * (1 - expected score) is rounded half up and the loser receives its negative.
    private int whiteDelta(int whiteRating, int blackRating, int kFactor, GameResult outcome) {
        return switch (outcome) {
            case DRAW -> NO_CHANGE;
            case WHITE_WIN -> winnerDelta(whiteRating, blackRating, kFactor);
            case BLACK_WIN -> -winnerDelta(blackRating, whiteRating, kFactor);
        };
    }

    private int winnerDelta(int winnerRating, int loserRating, int kFactor) {
        double expected = 1.0 / (1.0 + Math.pow(10.0, (loserRating - winnerRating) / ELO_SCALE));

        return (int) Math.round(kFactor * (WIN_SCORE - expected));
    }

    // Decision 2026-10-19: a rating never falls below 0, so the loser's stored delta shrinks to reach exactly 0
    // while the winner keeps the full change; every rated game counts towards games_played, draws included.
    private RatingEventEntity applyDelta(
            PlayerRatingEntity rating, int delta, int kFactor, Long gameId, Instant appliedAt) {
        int before = rating.getRating();
        int after = Math.max(MIN_RATING, before + delta);
        rating.setRating(after);
        rating.setGamesPlayed(rating.getGamesPlayed() + 1);
        RatingEvent event = RatingEvent.builder()
                .withGameId(gameId)
                .withAccountId(rating.getId().getAccountId())
                .withModeCode(rating.getId().getModeCode())
                .withRatingBefore(before)
                .withRatingDelta(after - before)
                .withRatingAfter(after)
                .withKFactorUsed(kFactor)
                .withAppliedAt(appliedAt)
                .build();

        return ratingEventEntityMapper.toEntity(event);
    }

    private PlayerRating newRating(Long accountId, RatingModeEntity mode, int startingRating) {
        PlayerRating rating = PlayerRating.builder()
                .withAccountId(accountId)
                .withModeCode(mode.getModeCode())
                .withRating(startingRating)
                .withStartingRating(startingRating)
                .withGamesPlayed(NO_GAMES_PLAYED)
                .build();

        return rating;
    }
}
