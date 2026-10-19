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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swd392.backend.chess.model.ChessColor;
import com.swd392.backend.chess.model.GameResult;
import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.config.SecurityConfig;
import com.swd392.backend.onlinegame.mapper.OnlineGameMapperImpl;
import com.swd392.backend.onlinegame.model.GameMove;
import com.swd392.backend.onlinegame.model.GameSource;
import com.swd392.backend.onlinegame.model.MoveSubmission;
import com.swd392.backend.onlinegame.model.OnlineGame;
import com.swd392.backend.onlinegame.model.OnlineGameStatus;
import com.swd392.backend.onlinegame.model.TerminationReason;
import com.swd392.backend.onlinegame.service.OnlineGameService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OnlineGameController.class)
@Import({SecurityConfig.class, OnlineGameMapperImpl.class})
class OnlineGameControllerTest {

    private static final String GAME_URL = "/api/online-games/100";
    private static final Instant CLAIMED_AT = Instant.parse("2026-10-08T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-08T10:00:20Z");
    private static final String AFTER_E4_FEN = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OnlineGameService onlineGameService;

    @Test
    void get_game_by_non_participant_returns_404_ko() throws Exception {
        // given
        when(onlineGameService.getGame(100L, 3L)).thenThrow(new AppException(AppErrorMessage.ONLINE_GAME_NOT_FOUND));

        // when / then
        mockMvc.perform(get(GAME_URL).with(jwt().jwt(token -> token.subject("3"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ONLINE_GAME_NOT_FOUND"));
    }

    @Test
    void get_game_without_token_returns_401_ko() throws Exception {
        // when / then
        mockMvc.perform(get(GAME_URL)).andExpect(status().isUnauthorized());
        verify(onlineGameService, never()).getGame(anyLong(), anyLong());
    }

    @Test
    void ready_by_second_player_returns_active_game_with_both_ready_ok() throws Exception {
        // given
        var game = game(OnlineGameStatus.ACTIVE);
        game.setWhiteReadyAt(CLAIMED_AT.plusSeconds(5));
        game.setBlackReadyAt(NOW);
        game.setStartedAt(NOW);
        when(onlineGameService.markReady(100L, 2L)).thenReturn(game);

        // when / then
        mockMvc.perform(post(GAME_URL + "/ready").with(jwt().jwt(token -> token.subject("2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.white.ready").value(true))
                .andExpect(jsonPath("$.black.ready").value(true))
                .andExpect(jsonPath("$.startedAt").value("2026-10-08T10:00:20Z"))
                .andExpect(jsonPath("$.viewerColor").value("BLACK"));
    }

    @Test
    void ready_without_token_returns_401_ko() throws Exception {
        // when / then
        mockMvc.perform(post(GAME_URL + "/ready")).andExpect(status().isUnauthorized());
        verify(onlineGameService, never()).markReady(anyLong(), anyLong());
    }

    @Test
    void cancel_pending_game_returns_cancelled_game_with_reason_ok() throws Exception {
        // given
        var game = game(OnlineGameStatus.CANCELLED);
        game.setEndedAt(NOW);
        game.setTerminationReason(TerminationReason.PRE_START_CANCEL);
        when(onlineGameService.cancelPendingGame(100L, 2L)).thenReturn(game);

        // when / then
        mockMvc.perform(post(GAME_URL + "/cancel").with(jwt().jwt(token -> token.subject("2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.terminationReason").value("PRE_START_CANCEL"))
                .andExpect(jsonPath("$.endedAt").value("2026-10-08T10:00:20Z"));
    }

    @Test
    void cancel_started_game_returns_409_ko() throws Exception {
        // given
        when(onlineGameService.cancelPendingGame(100L, 1L))
                .thenThrow(new AppException(AppErrorMessage.ONLINE_GAME_ALREADY_STARTED));

        // when / then
        mockMvc.perform(post(GAME_URL + "/cancel").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ONLINE_GAME_ALREADY_STARTED"));
    }

    @Test
    void heartbeat_returns_current_pending_game_ok() throws Exception {
        // given
        when(onlineGameService.recordHeartbeat(100L, 1L)).thenReturn(game(OnlineGameStatus.PENDING));

        // when / then
        mockMvc.perform(post(GAME_URL + "/heartbeat").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.readyDeadlineAt").value("2026-10-08T10:03:00Z"));
    }

    @Test
    void make_move_returns_updated_game_ok() throws Exception {
        // given
        var game = game(OnlineGameStatus.ACTIVE);
        game.setLastPly(1);
        game.setCurrentFen(AFTER_E4_FEN);
        game.setWhiteRemainingMs(1_805_000L);
        game.setBlackRemainingMs(1_800_000L);
        game.setTurnStartedAt(NOW);
        when(onlineGameService.makeMove(eq(100L), eq(1L), any(MoveSubmission.class)))
                .thenReturn(game);
        var captor = ArgumentCaptor.forClass(MoveSubmission.class);

        // when / then
        mockMvc.perform(post(GAME_URL + "/moves")
                        .with(jwt().jwt(token -> token.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ply":1,"move":"e2e4"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastPly").value(1))
                .andExpect(jsonPath("$.currentFen").value(AFTER_E4_FEN))
                .andExpect(jsonPath("$.whiteRemainingMs").value(1_805_000))
                .andExpect(jsonPath("$.turnStartedAt").value("2026-10-08T10:00:20Z"));
        verify(onlineGameService).makeMove(eq(100L), eq(1L), captor.capture());
        assertAll(
                () -> assertThat(captor.getValue().getPly()).isEqualTo(1),
                () -> assertThat(captor.getValue().getMove()).isEqualTo("e2e4"));
    }

    @Test
    void make_move_finishing_game_returns_outcome_and_reason_ok() throws Exception {
        // given
        var game = game(OnlineGameStatus.COMPLETED);
        game.setEndedAt(NOW);
        game.setOutcome(GameResult.BLACK_WIN);
        game.setTerminationReason(TerminationReason.CHECKMATE);
        when(onlineGameService.makeMove(eq(100L), eq(2L), any(MoveSubmission.class)))
                .thenReturn(game);

        // when / then
        mockMvc.perform(post(GAME_URL + "/moves")
                        .with(jwt().jwt(token -> token.subject("2")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ply":4,"move":"d8h4"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.outcome").value("BLACK_WIN"))
                .andExpect(jsonPath("$.terminationReason").value("CHECKMATE"));
    }

    @Test
    void make_move_with_malformed_move_and_missing_ply_returns_400_ko() throws Exception {
        // when / then
        mockMvc.perform(post(GAME_URL + "/moves")
                        .with(jwt().jwt(token -> token.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"move":"e2-e4"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations.length()").value(2));
        verify(onlineGameService, never()).makeMove(anyLong(), anyLong(), any());
    }

    @Test
    void make_move_out_of_turn_returns_409_ko() throws Exception {
        // given
        when(onlineGameService.makeMove(eq(100L), eq(2L), any(MoveSubmission.class)))
                .thenThrow(new AppException(AppErrorMessage.NOT_YOUR_TURN));

        // when / then
        mockMvc.perform(post(GAME_URL + "/moves")
                        .with(jwt().jwt(token -> token.subject("2")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ply":1,"move":"e7e5"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NOT_YOUR_TURN"));
    }

    @Test
    void make_move_after_disconnect_forfeit_due_returns_409_ko() throws Exception {
        // given
        when(onlineGameService.makeMove(eq(100L), eq(2L), any(MoveSubmission.class)))
                .thenThrow(new AppException(AppErrorMessage.DISCONNECT_FORFEIT_DUE));

        // when / then
        mockMvc.perform(post(GAME_URL + "/moves")
                        .with(jwt().jwt(token -> token.subject("2")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ply":1,"move":"e7e5"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DISCONNECT_FORFEIT_DUE"));
    }

    @Test
    void heartbeat_on_active_game_shows_disconnected_opponent_ok() throws Exception {
        // given
        var game = game(OnlineGameStatus.ACTIVE);
        game.setStartedAt(CLAIMED_AT.plusSeconds(6));
        game.setBlackDisconnectedAt(CLAIMED_AT.plusSeconds(30));
        when(onlineGameService.recordHeartbeat(100L, 1L)).thenReturn(game);

        // when / then
        mockMvc.perform(post(GAME_URL + "/heartbeat").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.white.disconnectedAt").doesNotExist())
                .andExpect(jsonPath("$.black.disconnectedAt").value("2026-10-08T10:00:30Z"));
    }

    @Test
    void make_illegal_move_returns_400_ko() throws Exception {
        // given
        when(onlineGameService.makeMove(eq(100L), eq(1L), any(MoveSubmission.class)))
                .thenThrow(new AppException(AppErrorMessage.ILLEGAL_MOVE));

        // when / then
        mockMvc.perform(post(GAME_URL + "/moves")
                        .with(jwt().jwt(token -> token.subject("1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ply":1,"move":"e2e5"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ILLEGAL_MOVE"));
    }

    @Test
    void make_move_without_token_returns_401_ko() throws Exception {
        // when / then
        mockMvc.perform(post(GAME_URL + "/moves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"ply":1,"move":"e2e4"}"""))
                .andExpect(status().isUnauthorized());
        verify(onlineGameService, never()).makeMove(anyLong(), anyLong(), any());
    }

    @Test
    void resign_returns_completed_game_with_opponent_win_ok() throws Exception {
        // given
        var game = game(OnlineGameStatus.COMPLETED);
        game.setStartedAt(CLAIMED_AT.plusSeconds(6));
        game.setEndedAt(NOW);
        game.setOutcome(GameResult.BLACK_WIN);
        game.setTerminationReason(TerminationReason.RESIGNATION);
        when(onlineGameService.resign(100L, 1L)).thenReturn(game);

        // when / then
        mockMvc.perform(post(GAME_URL + "/resign").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.outcome").value("BLACK_WIN"))
                .andExpect(jsonPath("$.terminationReason").value("RESIGNATION"))
                .andExpect(jsonPath("$.endedAt").value("2026-10-08T10:00:20Z"));
    }

    @Test
    void resign_finished_game_returns_409_ko() throws Exception {
        // given
        when(onlineGameService.resign(100L, 1L)).thenThrow(new AppException(AppErrorMessage.ONLINE_GAME_NOT_ACTIVE));

        // when / then
        mockMvc.perform(post(GAME_URL + "/resign").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ONLINE_GAME_NOT_ACTIVE"));
    }

    @Test
    void resign_without_token_returns_401_ko() throws Exception {
        // when / then
        mockMvc.perform(post(GAME_URL + "/resign")).andExpect(status().isUnauthorized());
        verify(onlineGameService, never()).resign(anyLong(), anyLong());
    }

    @Test
    void abandon_first_vote_returns_active_game_with_vote_flag_ok() throws Exception {
        // given
        var game = game(OnlineGameStatus.ACTIVE);
        game.setStartedAt(CLAIMED_AT.plusSeconds(6));
        game.setBlackAbandonVoted(true);
        when(onlineGameService.voteToAbandon(100L, 2L)).thenReturn(game);

        // when / then
        mockMvc.perform(post(GAME_URL + "/abandon").with(jwt().jwt(token -> token.subject("2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.white.abandonVoted").value(false))
                .andExpect(jsonPath("$.black.abandonVoted").value(true))
                .andExpect(jsonPath("$.outcome").doesNotExist());
    }

    @Test
    void abandon_second_vote_returns_draw_by_mutual_abandonment_ok() throws Exception {
        // given
        var game = game(OnlineGameStatus.COMPLETED);
        game.setStartedAt(CLAIMED_AT.plusSeconds(6));
        game.setEndedAt(NOW);
        game.setOutcome(GameResult.DRAW);
        game.setTerminationReason(TerminationReason.MUTUAL_ABANDONMENT);
        game.setWhiteAbandonVoted(true);
        game.setBlackAbandonVoted(true);
        when(onlineGameService.voteToAbandon(100L, 1L)).thenReturn(game);

        // when / then
        mockMvc.perform(post(GAME_URL + "/abandon").with(jwt().jwt(token -> token.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.outcome").value("DRAW"))
                .andExpect(jsonPath("$.terminationReason").value("MUTUAL_ABANDONMENT"));
    }

    @Test
    void abandon_by_non_participant_returns_404_ko() throws Exception {
        // given
        when(onlineGameService.voteToAbandon(100L, 3L))
                .thenThrow(new AppException(AppErrorMessage.ONLINE_GAME_NOT_FOUND));

        // when / then
        mockMvc.perform(post(GAME_URL + "/abandon").with(jwt().jwt(token -> token.subject("3"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ONLINE_GAME_NOT_FOUND"));
    }

    @Test
    void abandon_without_token_returns_401_ko() throws Exception {
        // when / then
        mockMvc.perform(post(GAME_URL + "/abandon")).andExpect(status().isUnauthorized());
        verify(onlineGameService, never()).voteToAbandon(anyLong(), anyLong());
    }

    @Test
    void get_moves_returns_moves_in_order_ok() throws Exception {
        // given
        var move = GameMove.builder()
                .withGameId(100L)
                .withPly(1)
                .withPlayedByAccountId(1L)
                .withMoveUci("e2e4")
                .withSan("e4")
                .withResultingFen(AFTER_E4_FEN)
                .withWhiteRemainingMs(1_805_000L)
                .withBlackRemainingMs(1_800_000L)
                .withAcceptedAt(NOW)
                .build();
        when(onlineGameService.getMoves(100L, 2L)).thenReturn(List.of(move));

        // when / then
        mockMvc.perform(get(GAME_URL + "/moves").with(jwt().jwt(token -> token.subject("2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ply").value(1))
                .andExpect(jsonPath("$[0].move").value("e2e4"))
                .andExpect(jsonPath("$[0].san").value("e4"))
                .andExpect(jsonPath("$[0].fen").value(AFTER_E4_FEN))
                .andExpect(jsonPath("$[0].acceptedAt").value("2026-10-08T10:00:20Z"));
    }

    private OnlineGame game(OnlineGameStatus status) {
        return OnlineGame.builder()
                .withId(100L)
                .withSource(GameSource.INVITE)
                .withWhiteAccountId(1L)
                .withWhiteDisplayName("Alex")
                .withBlackAccountId(2L)
                .withBlackDisplayName("alex")
                .withViewerColor(status == OnlineGameStatus.ACTIVE ? ChessColor.BLACK : ChessColor.WHITE)
                .withVariantCode("STANDARD")
                .withModeCode("CLASSICAL")
                .withRated(true)
                .withClockEnabled(true)
                .withInitialSeconds(1800)
                .withIncrementSeconds(10)
                .withCreatedAt(CLAIMED_AT)
                .withReadyDeadlineAt(CLAIMED_AT.plusSeconds(180))
                .withStatus(status)
                .build();
    }
}
