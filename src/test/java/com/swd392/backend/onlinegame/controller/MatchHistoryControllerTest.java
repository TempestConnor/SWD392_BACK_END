package com.swd392.backend.onlinegame.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swd392.backend.chess.model.ChessColor;
import com.swd392.backend.chess.model.GameResult;
import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.config.SecurityConfig;
import com.swd392.backend.onlinegame.mapper.MatchHistoryMapperImpl;
import com.swd392.backend.onlinegame.mapper.OnlineGameMapperImpl;
import com.swd392.backend.onlinegame.model.GameHistory;
import com.swd392.backend.onlinegame.model.GameMove;
import com.swd392.backend.onlinegame.model.GameSource;
import com.swd392.backend.onlinegame.model.OnlineGame;
import com.swd392.backend.onlinegame.model.OnlineGameStatus;
import com.swd392.backend.onlinegame.model.PlayerResult;
import com.swd392.backend.onlinegame.model.TerminationReason;
import com.swd392.backend.onlinegame.service.MatchHistoryService;
import com.swd392.backend.rating.model.RatingEvent;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MatchHistoryController.class)
@Import({SecurityConfig.class, OnlineGameMapperImpl.class, MatchHistoryMapperImpl.class})
class MatchHistoryControllerTest {

    private static final String HISTORY_URL = "/api/match-history";
    private static final Instant STARTED_AT = Instant.parse("2026-10-19T10:00:00Z");
    private static final Instant ENDED_AT = Instant.parse("2026-10-19T10:20:00Z");
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String AFTER_E4_FEN = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MatchHistoryService matchHistoryService;

    @Test
    void search_history_returns_page_with_viewer_perspective_and_own_rating_change_ok() throws Exception {
        // given
        var entry = history(ChessColor.BLACK, PlayerResult.WIN);
        entry.setWhiteRatingEvent(ratingEvent(1L, -10));
        entry.setBlackRatingEvent(ratingEvent(2L, 10));
        when(matchHistoryService.searchHistory(eq(2L), any()))
                .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 21));

        // when / then
        mockMvc.perform(get(HISTORY_URL).with(jwt().jwt(token -> token.subject("2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].gameId").value(100))
                .andExpect(jsonPath("$.content[0].viewerColor").value("BLACK"))
                .andExpect(jsonPath("$.content[0].opponent.accountId").value(1))
                .andExpect(jsonPath("$.content[0].opponent.displayName").value("Alex"))
                .andExpect(jsonPath("$.content[0].result").value("WIN"))
                .andExpect(jsonPath("$.content[0].outcome").value("BLACK_WIN"))
                .andExpect(jsonPath("$.content[0].terminationReason").value("DISCONNECT"))
                .andExpect(jsonPath("$.content[0].clock.initialMinutes").value(30))
                .andExpect(jsonPath("$.content[0].clock.incrementSeconds").value(10))
                .andExpect(jsonPath("$.content[0].endedAt").value("2026-10-19T10:20:00Z"))
                .andExpect(jsonPath("$.content[0].plyCount").value(1))
                .andExpect(jsonPath("$.content[0].ratingChange.ratingBefore").value(800))
                .andExpect(jsonPath("$.content[0].ratingChange.ratingDelta").value(10))
                .andExpect(jsonPath("$.content[0].ratingChange.ratingAfter").value(810))
                .andExpect(jsonPath("$.content[0].ratingChange.kFactor").value(20))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(21))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void search_history_of_unrated_game_has_no_rating_change_ok() throws Exception {
        // given
        var entry = history(ChessColor.WHITE, PlayerResult.LOSS);
        entry.getGame().setRated(false);
        when(matchHistoryService.searchHistory(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 1));

        // when / then
        mockMvc.perform(get(HISTORY_URL).with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].rated").value(false))
                .andExpect(jsonPath("$.content[0].result").value("LOSS"))
                .andExpect(jsonPath("$.content[0].opponent.displayName").value("Bea"))
                .andExpect(jsonPath("$.content[0].ratingChange").isEmpty());
    }

    @Test
    void search_history_passes_requested_page_and_clamps_size_ok() throws Exception {
        // given
        when(matchHistoryService.searchHistory(eq(1L), any())).thenReturn(new PageImpl<>(List.of()));

        // when
        mockMvc.perform(get(HISTORY_URL + "?page=3&size=500").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isOk());

        // then
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(matchHistoryService).searchHistory(eq(1L), pageable.capture());
        assertAll(
                () -> assertThat(pageable.getValue().getPageNumber()).isEqualTo(3),
                () -> assertThat(pageable.getValue().getPageSize()).isEqualTo(100));
    }

    @Test
    void search_history_without_token_returns_401_ko() throws Exception {
        // when / then
        mockMvc.perform(get(HISTORY_URL)).andExpect(status().isUnauthorized());
        verify(matchHistoryService, never()).searchHistory(anyLong(), any());
    }

    @Test
    void get_history_game_returns_both_players_ratings_and_moves_ok() throws Exception {
        // given
        var detail = history(ChessColor.WHITE, PlayerResult.LOSS);
        detail.setWhiteRatingEvent(ratingEvent(1L, -10));
        detail.setBlackRatingEvent(ratingEvent(2L, 10));
        detail.setMoves(List.of(move()));
        when(matchHistoryService.getHistoryGame(100L, 1L)).thenReturn(detail);

        // when / then
        mockMvc.perform(get(HISTORY_URL + "/100").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value(100))
                .andExpect(jsonPath("$.viewerColor").value("WHITE"))
                .andExpect(jsonPath("$.result").value("LOSS"))
                .andExpect(jsonPath("$.white.displayName").value("Alex"))
                .andExpect(jsonPath("$.black.displayName").value("Bea"))
                .andExpect(jsonPath("$.whiteRatingChange.ratingDelta").value(-10))
                .andExpect(jsonPath("$.blackRatingChange.ratingDelta").value(10))
                .andExpect(jsonPath("$.initialFen").value(START_FEN))
                .andExpect(jsonPath("$.finalFen").value(AFTER_E4_FEN))
                .andExpect(jsonPath("$.moves[0].ply").value(1))
                .andExpect(jsonPath("$.moves[0].move").value("e2e4"))
                .andExpect(jsonPath("$.moves[0].san").value("e4"));
    }

    @Test
    void get_history_game_not_in_history_returns_404_ko() throws Exception {
        // given
        when(matchHistoryService.getHistoryGame(100L, 1L))
                .thenThrow(new AppException(AppErrorMessage.GAME_NOT_IN_HISTORY));

        // when / then
        mockMvc.perform(get(HISTORY_URL + "/100").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("GAME_NOT_IN_HISTORY"));
    }

    @Test
    void get_history_game_by_non_participant_returns_404_ko() throws Exception {
        // given
        when(matchHistoryService.getHistoryGame(100L, 3L))
                .thenThrow(new AppException(AppErrorMessage.ONLINE_GAME_NOT_FOUND));

        // when / then
        mockMvc.perform(get(HISTORY_URL + "/100").with(jwt().jwt(token -> token.subject("3"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ONLINE_GAME_NOT_FOUND"));
    }

    private GameHistory history(ChessColor viewerColor, PlayerResult result) {
        var game = OnlineGame.builder()
                .withId(100L)
                .withSource(GameSource.INVITE)
                .withWhiteAccountId(1L)
                .withWhiteDisplayName("Alex")
                .withBlackAccountId(2L)
                .withBlackDisplayName("Bea")
                .withViewerColor(viewerColor)
                .withVariantCode("STANDARD")
                .withModeCode("CLASSICAL")
                .withRated(true)
                .withClockEnabled(true)
                .withInitialSeconds(1800)
                .withIncrementSeconds(10)
                .withStartedAt(STARTED_AT)
                .withEndedAt(ENDED_AT)
                .withStatus(OnlineGameStatus.COMPLETED)
                .withOutcome(GameResult.BLACK_WIN)
                .withTerminationReason(TerminationReason.DISCONNECT)
                .withInitialFen(START_FEN)
                .withCurrentFen(AFTER_E4_FEN)
                .withLastPly(1)
                .build();

        return GameHistory.builder().withGame(game).withViewerResult(result).build();
    }

    private RatingEvent ratingEvent(Long accountId, int delta) {
        return RatingEvent.builder()
                .withGameId(100L)
                .withAccountId(accountId)
                .withModeCode("CLASSICAL")
                .withRatingBefore(800)
                .withRatingDelta(delta)
                .withRatingAfter(800 + delta)
                .withKFactorUsed(20)
                .withAppliedAt(ENDED_AT)
                .build();
    }

    private GameMove move() {
        return GameMove.builder()
                .withGameId(100L)
                .withPly(1)
                .withMoveUci("e2e4")
                .withSan("e4")
                .withResultingFen(AFTER_E4_FEN)
                .withAcceptedAt(STARTED_AT.plusSeconds(3))
                .build();
    }
}
