package com.swd392.backend.chess.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.swd392.backend.chess.model.ChessColor;
import com.swd392.backend.chess.model.GameResult;
import com.swd392.backend.chess.model.GameStatus;
import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChessRulesServiceImplTest {

    private static final List<String> FOOLS_MATE = List.of("f2f3", "e7e5", "g2g4", "d8h4");
    private static final List<String> LOYD_STALEMATE = List.of(
            "e2e3", "a7a5", "d1h5", "a8a6", "h5a5", "h7h5", "h2h4", "a6h6", "a5c7", "f7f6", "c7d7", "e8f7", "d7b7",
            "d8d3", "b7b8", "d3h7", "b8c8", "f7g6", "c8e6");
    private static final List<String> KNIGHT_SHUFFLE_TWICE =
            List.of("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8");

    private final ChessRulesServiceImpl chessRulesService = new ChessRulesServiceImpl();

    @Test
    void replay_empty_game_is_active_initial_position_ok() {
        // when
        var position = chessRulesService.replay(List.of());

        // then
        assertAll(
                () -> assertThat(position.getFen()).startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq"),
                () -> assertThat(position.getSideToMove()).isEqualTo(ChessColor.WHITE),
                () -> assertThat(position.getPly()).isZero(),
                () -> assertThat(position.getLastMoveSan()).isNull(),
                () -> assertThat(position.getStatus()).isEqualTo(GameStatus.ACTIVE),
                () -> assertThat(position.getResult()).isNull());
    }

    @Test
    void replay_fools_mate_is_checkmate_black_win_ok() {
        // when
        var position = chessRulesService.replay(FOOLS_MATE);

        // then
        assertAll(
                () -> assertThat(position.getStatus()).isEqualTo(GameStatus.CHECKMATE),
                () -> assertThat(position.getResult()).isEqualTo(GameResult.BLACK_WIN),
                () -> assertThat(position.getLastMoveSan()).isEqualTo("Qh4#"),
                () -> assertThat(position.getPly()).isEqualTo(4));
    }

    @Test
    void replay_loyd_stalemate_is_draw_ok() {
        // when
        var position = chessRulesService.replay(LOYD_STALEMATE);

        // then
        assertAll(
                () -> assertThat(position.getStatus()).isEqualTo(GameStatus.STALEMATE),
                () -> assertThat(position.getResult()).isEqualTo(GameResult.DRAW));
    }

    @Test
    void replay_threefold_repetition_ends_game_as_draw_ok() {
        // when
        var position = chessRulesService.replay(KNIGHT_SHUFFLE_TWICE);

        // then
        assertAll(
                () -> assertThat(position.getStatus()).isEqualTo(GameStatus.THREEFOLD_REPETITION),
                () -> assertThat(position.getResult()).isEqualTo(GameResult.DRAW));
    }

    @Test
    void replay_promotion_move_ok() {
        // given
        var moves = List.of("a2a4", "b7b5", "a4b5", "a7a6", "b5a6", "c8b7", "a6b7", "g8f6", "b7a8q");

        // when
        var position = chessRulesService.replay(moves);

        // then
        assertAll(
                () -> assertThat(position.getLastMoveSan()).isEqualTo("bxa8=Q"),
                () -> assertThat(position.getStatus()).isEqualTo(GameStatus.ACTIVE));
    }

    @Test
    void replay_initial_position_lists_twenty_legal_uci_moves_ok() {
        // when
        var position = chessRulesService.replay(List.of());

        // then
        assertAll(
                () -> assertThat(position.getLegalMoves()).hasSize(20),
                () -> assertThat(position.getLegalMoves()).contains("e2e4", "g1f3", "b1a3"),
                () -> assertThat(position.getLegalMoves()).isSorted());
    }

    @Test
    void replay_legal_moves_include_every_promotion_piece_ok() {
        // given
        var moves = List.of("a2a4", "b7b5", "a4b5", "a7a6", "b5a6", "c8b7", "a6b7", "g8f6");

        // when
        var position = chessRulesService.replay(moves);

        // then
        assertThat(position.getLegalMoves()).contains("b7a8q", "b7a8r", "b7a8b", "b7a8n");
    }

    @Test
    void replay_automatic_draw_lists_no_legal_moves_ok() {
        // when
        var position = chessRulesService.replay(KNIGHT_SHUFFLE_TWICE);

        // then
        assertThat(position.getLegalMoves()).isEmpty();
    }

    @Test
    void replay_illegal_move_ko() {
        // given
        var moves = List.of("e2e4", "e7e5", "e4e5");

        // when / then
        assertThatThrownBy(() -> chessRulesService.replay(moves))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ILLEGAL_MOVE_SEQUENCE);
    }

    @Test
    void replay_move_out_of_turn_ko() {
        // given
        var moves = List.of("e2e4", "d2d4");

        // when / then
        assertThatThrownBy(() -> chessRulesService.replay(moves))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ILLEGAL_MOVE_SEQUENCE);
    }

    @Test
    void replay_move_ignoring_check_ko() {
        // given
        var moves = List.of("e2e4", "d7d5", "f1b5", "a7a6");

        // when / then
        assertThatThrownBy(() -> chessRulesService.replay(moves))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ILLEGAL_MOVE_SEQUENCE);
    }

    @Test
    void replay_move_after_checkmate_ko() {
        // given
        var moves = List.of("f2f3", "e7e5", "g2g4", "d8h4", "a2a3");

        // when / then
        assertThatThrownBy(() -> chessRulesService.replay(moves))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ILLEGAL_MOVE_SEQUENCE);
    }

    @Test
    void play_legal_move_returns_resulting_position_and_san_ok() {
        // when
        var position = chessRulesService.play(List.of("e2e4"), "e7e5");

        // then
        assertAll(
                () -> assertThat(position.getFen())
                        .isEqualTo("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2"),
                () -> assertThat(position.getPly()).isEqualTo(2),
                () -> assertThat(position.getLastMoveSan()).isEqualTo("e5"),
                () -> assertThat(position.getStatus()).isEqualTo(GameStatus.ACTIVE));
    }

    @Test
    void play_checkmating_move_is_checkmate_ok() {
        // when
        var position = chessRulesService.play(FOOLS_MATE.subList(0, 3), "d8h4");

        // then
        assertAll(
                () -> assertThat(position.getStatus()).isEqualTo(GameStatus.CHECKMATE),
                () -> assertThat(position.getResult()).isEqualTo(GameResult.BLACK_WIN));
    }

    @Test
    void play_illegal_move_ko() {
        // when / then
        assertThatThrownBy(() -> chessRulesService.play(List.of("e2e4"), "e5e4"))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ILLEGAL_MOVE);
    }

    @Test
    void play_move_after_checkmate_ko() {
        // when / then
        assertThatThrownBy(() -> chessRulesService.play(FOOLS_MATE, "a2a3"))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ILLEGAL_MOVE);
    }
}
