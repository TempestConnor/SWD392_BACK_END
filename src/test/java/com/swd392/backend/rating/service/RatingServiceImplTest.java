package com.swd392.backend.rating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swd392.backend.chess.model.GameResult;
import com.swd392.backend.rating.entity.PlayerRatingEntity;
import com.swd392.backend.rating.entity.PlayerRatingId;
import com.swd392.backend.rating.entity.RatingEventEntity;
import com.swd392.backend.rating.entity.RatingEventId;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
    private static final Long GAME_ID = 100L;
    private static final Long WHITE_ID = 1L;
    private static final Long BLACK_ID = 2L;
    private static final String CLASSICAL = "CLASSICAL";
    private static final int K_FACTOR = 20;
    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00.123Z");

    @Mock
    private RatingModeRepository ratingModeRepository;

    @Mock
    private PlayerRatingRepository playerRatingRepository;

    @Spy
    private PlayerRatingEntityMapper playerRatingEntityMapper = Mappers.getMapper(PlayerRatingEntityMapper.class);

    @Mock
    private RatingEventRepository ratingEventRepository;

    @Spy
    private RatingEventEntityMapper ratingEventEntityMapper = Mappers.getMapper(RatingEventEntityMapper.class);

    @Mock
    private Clock clock;

    @InjectMocks
    private RatingServiceImpl ratingService;

    @BeforeEach
    void fixClockAndMode() {
        lenient().when(clock.instant()).thenReturn(NOW);
        lenient().when(ratingModeRepository.findById(CLASSICAL)).thenReturn(Optional.of(mode(CLASSICAL)));
        lenient().when(ratingEventRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void initialize_ratings_creates_starting_rating_for_every_enabled_mode_ok() {
        // given
        when(ratingModeRepository.findByEnabledTrue()).thenReturn(List.of(mode(CLASSICAL), mode("RAPID")));
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
                        tuple(ACCOUNT_ID, CLASSICAL, 1600, 1600, 0), tuple(ACCOUNT_ID, "RAPID", 1600, 1600, 0));
    }

    @Test
    void apply_white_win_between_equal_ratings_moves_ten_points_ok() {
        // given
        var white = rating(WHITE_ID, 800);
        var black = rating(BLACK_ID, 800);
        lockable(white, black);

        // when
        var events = ratingService.applyGameResult(result(GameResult.WHITE_WIN));

        // then
        assertAll(
                () -> assertThat(events)
                        .extracting(
                                RatingEvent::getAccountId,
                                RatingEvent::getRatingBefore,
                                RatingEvent::getRatingDelta,
                                RatingEvent::getRatingAfter)
                        .containsExactly(tuple(WHITE_ID, 800, 10, 810), tuple(BLACK_ID, 800, -10, 790)),
                () -> assertThat(events)
                        .allSatisfy(event -> assertAll(
                                () -> assertThat(event.getGameId()).isEqualTo(GAME_ID),
                                () -> assertThat(event.getModeCode()).isEqualTo(CLASSICAL),
                                () -> assertThat(event.getKFactorUsed()).isEqualTo(K_FACTOR),
                                () -> assertThat(event.getAppliedAt()).isEqualTo(NOW))),
                () -> assertThat(white.getRating()).isEqualTo(810),
                () -> assertThat(black.getRating()).isEqualTo(790),
                () -> assertThat(white.getGamesPlayed()).isEqualTo(1),
                () -> assertThat(black.getGamesPlayed()).isEqualTo(1));
    }

    @Test
    void apply_upset_win_by_lower_rated_black_gains_more_points_ok() {
        // given
        var white = rating(WHITE_ID, 1200);
        var black = rating(BLACK_ID, 800);
        lockable(white, black);

        // when
        var events = ratingService.applyGameResult(result(GameResult.BLACK_WIN));

        // then
        assertThat(events)
                .extracting(RatingEvent::getRatingBefore, RatingEvent::getRatingDelta, RatingEvent::getRatingAfter)
                .containsExactly(tuple(1200, -18, 1182), tuple(800, 18, 818));
    }

    @Test
    void apply_favourite_win_rounds_fractional_change_half_up_ok() {
        // given
        lockable(rating(WHITE_ID, 1000), rating(BLACK_ID, 800));

        // when
        var events = ratingService.applyGameResult(result(GameResult.WHITE_WIN));

        // then
        assertThat(events).extracting(RatingEvent::getRatingDelta).containsExactly(5, -5);
    }

    @Test
    void apply_draw_records_zero_change_for_both_players_ok() {
        // given
        var white = rating(WHITE_ID, 1600);
        var black = rating(BLACK_ID, 400);
        lockable(white, black);

        // when
        var events = ratingService.applyGameResult(result(GameResult.DRAW));

        // then
        assertAll(
                () -> assertThat(events)
                        .extracting(
                                RatingEvent::getRatingBefore, RatingEvent::getRatingDelta, RatingEvent::getRatingAfter)
                        .containsExactly(tuple(1600, 0, 1600), tuple(400, 0, 400)),
                () -> assertThat(white.getGamesPlayed()).isEqualTo(1),
                () -> assertThat(black.getGamesPlayed()).isEqualTo(1));
    }

    @Test
    void apply_loss_near_zero_clamps_loser_at_zero_and_winner_keeps_full_change_ok() {
        // given
        var white = rating(WHITE_ID, 5);
        var black = rating(BLACK_ID, 5);
        lockable(white, black);

        // when
        var events = ratingService.applyGameResult(result(GameResult.WHITE_WIN));

        // then
        assertAll(
                () -> assertThat(events)
                        .extracting(
                                RatingEvent::getRatingBefore, RatingEvent::getRatingDelta, RatingEvent::getRatingAfter)
                        .containsExactly(tuple(5, 10, 15), tuple(5, -5, 0)),
                () -> assertThat(black.getRating()).isZero());
    }

    @Test
    void apply_uses_configured_k_factor_and_stores_it_ok() {
        // given
        when(ratingModeRepository.findById(CLASSICAL))
                .thenReturn(Optional.of(new RatingModeEntity(CLASSICAL, true, 32)));
        lockable(rating(WHITE_ID, 800), rating(BLACK_ID, 800));

        // when
        var events = ratingService.applyGameResult(result(GameResult.BLACK_WIN));

        // then
        assertAll(
                () -> assertThat(events).extracting(RatingEvent::getRatingDelta).containsExactly(-16, 16),
                () -> assertThat(events).extracting(RatingEvent::getKFactorUsed).containsOnly(32));
    }

    @Test
    void apply_locks_ratings_in_ascending_account_order_ok() {
        // given
        var white = rating(9L, 800);
        var black = rating(3L, 800);
        lockable(white, black);
        var result = result(GameResult.WHITE_WIN);
        result.setWhiteAccountId(9L);
        result.setBlackAccountId(3L);

        // when
        ratingService.applyGameResult(result);

        // then
        var order = inOrder(playerRatingRepository);
        order.verify(playerRatingRepository).findForUpdateById(new PlayerRatingId(3L, CLASSICAL));
        order.verify(playerRatingRepository).findForUpdateById(new PlayerRatingId(9L, CLASSICAL));
    }

    @Test
    void apply_for_already_rated_game_returns_stored_events_without_changes_ok() {
        // given
        when(ratingEventRepository.findByIdGameIdOrderByIdAccountId(GAME_ID))
                .thenReturn(List.of(storedEvent(WHITE_ID, 10), storedEvent(BLACK_ID, -10)));

        // when
        var events = ratingService.applyGameResult(result(GameResult.WHITE_WIN));

        // then
        assertAll(
                () -> assertThat(events).extracting(RatingEvent::getRatingDelta).containsExactly(10, -10),
                () -> verify(playerRatingRepository, never()).findForUpdateById(any()),
                () -> verify(ratingEventRepository, never()).saveAll(anyList()));
    }

    @Test
    void apply_without_player_rating_fails_and_saves_nothing_ko() {
        // given
        when(playerRatingRepository.findForUpdateById(new PlayerRatingId(WHITE_ID, CLASSICAL)))
                .thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> ratingService.applyGameResult(result(GameResult.WHITE_WIN)))
                .isInstanceOf(IllegalStateException.class);
        verify(ratingEventRepository, never()).saveAll(anyList());
    }

    private void lockable(PlayerRatingEntity white, PlayerRatingEntity black) {
        when(playerRatingRepository.findForUpdateById(white.getId())).thenReturn(Optional.of(white));
        when(playerRatingRepository.findForUpdateById(black.getId())).thenReturn(Optional.of(black));
    }

    private RatedGameResult result(GameResult outcome) {
        return RatedGameResult.builder()
                .withGameId(GAME_ID)
                .withModeCode(CLASSICAL)
                .withWhiteAccountId(WHITE_ID)
                .withBlackAccountId(BLACK_ID)
                .withOutcome(outcome)
                .build();
    }

    private PlayerRatingEntity rating(Long accountId, int value) {
        return new PlayerRatingEntity(new PlayerRatingId(accountId, CLASSICAL), value, value, 0);
    }

    private RatingEventEntity storedEvent(Long accountId, int delta) {
        return new RatingEventEntity(
                new RatingEventId(GAME_ID, accountId), CLASSICAL, 800, delta, 800 + delta, K_FACTOR, NOW);
    }

    private RatingModeEntity mode(String code) {
        return new RatingModeEntity(code, true, K_FACTOR);
    }
}
