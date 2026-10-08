package com.swd392.backend.rating.service;

import com.swd392.backend.rating.entity.PlayerRatingEntity;
import com.swd392.backend.rating.entity.RatingModeEntity;
import com.swd392.backend.rating.mapper.PlayerRatingEntityMapper;
import com.swd392.backend.rating.model.PlayerRating;
import com.swd392.backend.rating.repository.PlayerRatingRepository;
import com.swd392.backend.rating.repository.RatingModeRepository;
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

    private final RatingModeRepository ratingModeRepository;
    private final PlayerRatingRepository playerRatingRepository;
    private final PlayerRatingEntityMapper playerRatingEntityMapper;

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
