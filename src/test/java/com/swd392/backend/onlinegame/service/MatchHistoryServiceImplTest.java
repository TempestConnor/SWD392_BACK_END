package com.swd392.backend.onlinegame.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swd392.backend.account.service.AccountService;
import com.swd392.backend.chess.model.ChessColor;
import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.onlinegame.entity.GameMoveEntity;
import com.swd392.backend.onlinegame.entity.GameMoveId;
import com.swd392.backend.onlinegame.entity.OnlineGameEntity;
import com.swd392.backend.onlinegame.mapper.GameMoveEntityMapper;
import com.swd392.backend.onlinegame.mapper.OnlineGameEntityMapper;
import com.swd392.backend.onlinegame.model.PlayerResult;
import com.swd392.backend.onlinegame.model.TerminationReason;
import com.swd392.backend.onlinegame.repository.GameMoveRepository;
import com.swd392.backend.onlinegame.repository.OnlineGameRepository;
import com.swd392.backend.rating.model.RatingEvent;
import com.swd392.backend.rating.service.RatingService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class MatchHistoryServiceImplTest {

    private static final Long GAME_ID = 100L;
    private static final Long WHITE_ID = 1L;
    private static final Long BLACK_ID = 2L;
    private static final Long OUTSIDER_ID = 3L;
    private static final Instant STARTED_AT = Instant.parse("2026-10-19T10:00:00Z");
    private static final Instant ENDED_AT = Instant.parse("2026-10-19T10:20:00Z");
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String AFTER_E4_FEN = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";

    @Mock
    private OnlineGameRepository onlineGameRepository;

    @Spy
    private OnlineGameEntityMapper onlineGameEntityMapper = Mappers.getMapper(OnlineGameEntityMapper.class);

    @Mock
    private GameMoveRepository gameMoveRepository;

    @Spy
    private GameMoveEntityMapper gameMoveEntityMapper = Mappers.getMapper(GameMoveEntityMapper.class);

    @Mock
    private AccountService accountService;

    @Mock
    private RatingService ratingService;

    @InjectMocks
    private MatchHistoryServiceImpl matchHistoryService;

    @Test
    void search_history_queries_completed_games_newest_first_ignoring_client_sort_ok() {
        // given
        var requested = PageRequest.of(2, 5, Sort.by("createdAt"));
        when(onlineGameRepository.findParticipantGames(eq(WHITE_ID), eq("COMPLETED"), any()))
                .thenReturn(Page.empty());

        // when
        matchHistoryService.searchHistory(WHITE_ID, requested);

        // then
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(onlineGameRepository).findParticipantGames(eq(WHITE_ID), eq("COMPLETED"), pageable.capture());
        assertAll(
                () -> assertThat(pageable.getValue().getPageNumber()).isEqualTo(2),
                () -> assertThat(pageable.getValue().getPageSize()).isEqualTo(5),
                () -> assertThat(pageable.getValue().getSort())
                        .isEqualTo(Sort.by(Sort.Order.desc("endedAt"), Sort.Order.desc("id"))));
    }

    @Test
    void search_history_shows_results_from_the_viewer_perspective_ok() {
        // given
        var whiteWin = completedGame(101L, "WHITE_WIN", TerminationReason.DISCONNECT);
        var blackWin = completedGame(102L, "BLACK_WIN", TerminationReason.RESIGNATION);
        var draw = completedGame(103L, "DRAW", TerminationReason.MUTUAL_ABANDONMENT);
        stubPage(BLACK_ID, List.of(whiteWin, blackWin, draw));
        when(ratingService.getGameEvents(List.of(101L, 102L, 103L))).thenReturn(List.of());

        // when
        var history = matchHistoryService.searchHistory(BLACK_ID, PageRequest.of(0, 20));

        // then
        assertAll(
                () -> assertThat(history.getContent())
                        .extracting(entry -> entry.getViewerResult())
                        .containsExactly(PlayerResult.LOSS, PlayerResult.WIN, PlayerResult.DRAW),
                () -> assertThat(history.getContent())
                        .extracting(entry -> entry.getGame().getViewerColor())
                        .containsOnly(ChessColor.BLACK),
                () -> assertThat(history.getContent().getFirst().getGame().getTerminationReason())
                        .isEqualTo(TerminationReason.DISCONNECT),
                () -> assertThat(history.getContent().getFirst().getGame().getWhiteDisplayName())
                        .isEqualTo("Alex"),
                () -> assertThat(history.getContent().getFirst().getGame().getBlackDisplayName())
                        .isEqualTo("Bea"),
                () -> assertThat(history.getTotalElements()).isEqualTo(3));
    }

    @Test
    void search_history_attaches_each_players_rating_event_to_its_game_ok() {
        // given
        var rated = completedGame(101L, "WHITE_WIN", TerminationReason.CHECKMATE);
        rated.setRated(true);
        var unrated = completedGame(102L, "BLACK_WIN", TerminationReason.RESIGNATION);
        stubPage(WHITE_ID, List.of(rated, unrated));
        var whiteEvent = ratingEvent(101L, WHITE_ID, 10);
        var blackEvent = ratingEvent(101L, BLACK_ID, -10);
        when(ratingService.getGameEvents(List.of(101L, 102L))).thenReturn(List.of(blackEvent, whiteEvent));

        // when
        var history = matchHistoryService.searchHistory(WHITE_ID, PageRequest.of(0, 20));

        // then
        var ratedEntry = history.getContent().getFirst();
        var unratedEntry = history.getContent().get(1);
        assertAll(
                () -> assertThat(ratedEntry.getWhiteRatingEvent()).isSameAs(whiteEvent),
                () -> assertThat(ratedEntry.getBlackRatingEvent()).isSameAs(blackEvent),
                () -> assertThat(unratedEntry.getWhiteRatingEvent()).isNull(),
                () -> assertThat(unratedEntry.getBlackRatingEvent()).isNull());
    }

    @Test
    void get_history_game_returns_moves_names_and_both_rating_events_ok() {
        // given
        var game = completedGame(GAME_ID, "BLACK_WIN", TerminationReason.TIMEOUT);
        game.setRated(true);
        when(onlineGameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(accountService.getDisplayNames(List.of(WHITE_ID, BLACK_ID)))
                .thenReturn(Map.of(WHITE_ID, "Alex", BLACK_ID, "Bea"));
        when(ratingService.getGameEvents(List.of(GAME_ID)))
                .thenReturn(List.of(ratingEvent(GAME_ID, WHITE_ID, -10), ratingEvent(GAME_ID, BLACK_ID, 10)));
        when(gameMoveRepository.findByIdGameIdOrderByIdPlyAsc(GAME_ID)).thenReturn(List.of(move()));

        // when
        var history = matchHistoryService.getHistoryGame(GAME_ID, WHITE_ID);

        // then
        assertAll(
                () -> assertThat(history.getViewerResult()).isEqualTo(PlayerResult.LOSS),
                () -> assertThat(history.getGame().getViewerColor()).isEqualTo(ChessColor.WHITE),
                () -> assertThat(history.getWhiteRatingEvent().getRatingDelta()).isEqualTo(-10),
                () -> assertThat(history.getBlackRatingEvent().getRatingDelta()).isEqualTo(10),
                () -> assertThat(history.getMoves()).singleElement().satisfies(move -> assertThat(move.getMoveUci())
                        .isEqualTo("e2e4")));
    }

    @Test
    void get_history_game_of_missing_game_returns_not_found_ko() {
        // given
        when(onlineGameRepository.findById(GAME_ID)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> matchHistoryService.getHistoryGame(GAME_ID, WHITE_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ONLINE_GAME_NOT_FOUND);
    }

    @Test
    void get_history_game_by_non_participant_returns_not_found_ko() {
        // given
        var game = completedGame(GAME_ID, "WHITE_WIN", TerminationReason.CHECKMATE);
        when(onlineGameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));

        // when / then
        assertThatThrownBy(() -> matchHistoryService.getHistoryGame(GAME_ID, OUTSIDER_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ONLINE_GAME_NOT_FOUND);
        verify(gameMoveRepository, never()).findByIdGameIdOrderByIdPlyAsc(anyLong());
    }

    @Test
    void get_history_game_of_cancelled_game_is_not_in_history_ko() {
        // given
        var game = completedGame(GAME_ID, null, TerminationReason.PRE_START_CANCEL);
        game.setStatus("CANCELLED");
        game.setStartedAt(null);
        when(onlineGameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));

        // when / then
        assertThatThrownBy(() -> matchHistoryService.getHistoryGame(GAME_ID, WHITE_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.GAME_NOT_IN_HISTORY);
        verify(gameMoveRepository, never()).findByIdGameIdOrderByIdPlyAsc(anyLong());
    }

    @Test
    void get_history_game_of_active_game_is_not_in_history_ko() {
        // given
        var game = completedGame(GAME_ID, null, null);
        game.setStatus("ACTIVE");
        game.setEndedAt(null);
        when(onlineGameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));

        // when / then
        assertThatThrownBy(() -> matchHistoryService.getHistoryGame(GAME_ID, BLACK_ID))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.GAME_NOT_IN_HISTORY);
    }

    private void stubPage(Long accountId, List<OnlineGameEntity> games) {
        when(onlineGameRepository.findParticipantGames(eq(accountId), eq("COMPLETED"), any()))
                .thenReturn(new PageImpl<>(games, PageRequest.of(0, 20), games.size()));
        when(accountService.getDisplayNames(List.of(WHITE_ID, BLACK_ID)))
                .thenReturn(Map.of(WHITE_ID, "Alex", BLACK_ID, "Bea"));
    }

    private OnlineGameEntity completedGame(Long id, String outcome, TerminationReason reason) {
        return OnlineGameEntity.builder()
                .withId(id)
                .withSource("INVITE")
                .withWhiteAccountId(WHITE_ID)
                .withBlackAccountId(BLACK_ID)
                .withVariantCode("STANDARD")
                .withModeCode("CLASSICAL")
                .withClockEnabled(true)
                .withInitialSeconds(1800)
                .withIncrementSeconds(10)
                .withCreatedAt(STARTED_AT.minusSeconds(30))
                .withReadyDeadlineAt(STARTED_AT.plusSeconds(150))
                .withStartedAt(STARTED_AT)
                .withEndedAt(ENDED_AT)
                .withStatus("COMPLETED")
                .withOutcome(outcome)
                .withTerminationReason(reason == null ? null : reason.name())
                .withInitialFen(START_FEN)
                .withCurrentFen(AFTER_E4_FEN)
                .withLastPly(1)
                .build();
    }

    private RatingEvent ratingEvent(Long gameId, Long accountId, int delta) {
        return RatingEvent.builder()
                .withGameId(gameId)
                .withAccountId(accountId)
                .withModeCode("CLASSICAL")
                .withRatingBefore(800)
                .withRatingDelta(delta)
                .withRatingAfter(800 + delta)
                .withKFactorUsed(20)
                .withAppliedAt(ENDED_AT)
                .build();
    }

    private GameMoveEntity move() {
        return GameMoveEntity.builder()
                .withId(new GameMoveId(GAME_ID, 1))
                .withMoveUci("e2e4")
                .withSan("e4")
                .withResultingFen(AFTER_E4_FEN)
                .withAcceptedAt(STARTED_AT.plusSeconds(3))
                .build();
    }
}
