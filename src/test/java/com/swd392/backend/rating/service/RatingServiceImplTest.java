package com.swd392.backend.rating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.swd392.backend.rating.entity.RatingModeEntity;
import com.swd392.backend.rating.mapper.PlayerRatingEntityMapper;
import com.swd392.backend.rating.model.PlayerRating;
import com.swd392.backend.rating.repository.PlayerRatingRepository;
import com.swd392.backend.rating.repository.RatingModeRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RatingServiceImplTest {

    private static final Long ACCOUNT_ID = 7L;

    @Mock
    private RatingModeRepository ratingModeRepository;

    @Mock
    private PlayerRatingRepository playerRatingRepository;

    @Spy
    private PlayerRatingEntityMapper playerRatingEntityMapper = Mappers.getMapper(PlayerRatingEntityMapper.class);

    @InjectMocks
    private RatingServiceImpl ratingService;

    @Test
    void initialize_ratings_creates_starting_rating_for_every_enabled_mode_ok() {
        // given
        when(ratingModeRepository.findByEnabledTrue()).thenReturn(List.of(mode("CLASSICAL"), mode("RAPID")));
        when(playerRatingRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var ratings = ratingService.initializeRatings(ACCOUNT_ID, 1600);

        // then
        assertThat(ratings)
                .extracting(
                        PlayerRating::getAccountId,
                        PlayerRating::getModeCode,
                        PlayerRating::getRating,
                        PlayerRating::getStartingRating,
                        PlayerRating::getGamesPlayed)
                .containsExactly(
                        tuple(ACCOUNT_ID, "CLASSICAL", 1600, 1600, 0), tuple(ACCOUNT_ID, "RAPID", 1600, 1600, 0));
    }

    private RatingModeEntity mode(String code) {
        return new RatingModeEntity(code, true, 20);
    }
}
