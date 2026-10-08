package com.swd392.backend.localgame.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.swd392.backend.chess.model.ChessColor;
import com.swd392.backend.chess.model.GameResult;
import com.swd392.backend.chess.model.GameStatus;
import com.swd392.backend.chess.service.ChessRulesServiceImpl;
import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.localgame.model.ClockSettings;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalGameServiceImplTest {

    @Spy
    private ChessRulesServiceImpl chessRulesService = new ChessRulesServiceImpl();

    @InjectMocks
    private LocalGameServiceImpl localGameService;

    @Test
    void start_without_clock_settings_uses_default_30_plus_10_ok() {
        // given
        var requested = new ClockSettings(null, null, null);

        // when
        var start = localGameService.start(requested);

        // then
        assertAll(
                () -> assertThat(start.getClock().getEnabled()).isTrue(),
                () -> assertThat(start.getClock().getInitialMinutes()).isEqualTo(30),
                () -> assertThat(start.getClock().getIncrementSeconds()).isEqualTo(10),
                () -> assertThat(start.getPosition().getPly()).isZero(),
                () -> assertThat(start.getPosition().getSideToMove()).isEqualTo(ChessColor.WHITE),
                () -> assertThat(start.getPosition().getLegalMoves()).hasSize(20));
    }

    @Test
    void start_with_custom_clock_keeps_requested_values_ok() {
        // given
        var requested = new ClockSettings(true, 5, 0);

        // when
        var start = localGameService.start(requested);

        // then
        assertAll(
                () -> assertThat(start.getClock().getEnabled()).isTrue(),
                () -> assertThat(start.getClock().getInitialMinutes()).isEqualTo(5),
                () -> assertThat(start.getClock().getIncrementSeconds()).isZero());
    }

    @Test
    void start_with_partial_clock_defaults_missing_field_ok() {
        // given
        var requested = new ClockSettings(null, 15, null);

        // when
        var start = localGameService.start(requested);

        // then
        assertAll(
                () -> assertThat(start.getClock().getInitialMinutes()).isEqualTo(15),
                () -> assertThat(start.getClock().getIncrementSeconds()).isEqualTo(10));
    }

    @Test
    void start_with_disabled_clock_has_no_timing_ok() {
        // given
        var requested = new ClockSettings(false, null, null);

        // when
        var start = localGameService.start(requested);

        // then
        assertAll(
                () -> assertThat(start.getClock().getEnabled()).isFalse(),
                () -> assertThat(start.getClock().getInitialMinutes()).isNull(),
                () -> assertThat(start.getClock().getIncrementSeconds()).isNull());
    }

    @Test
    void start_with_disabled_clock_and_increment_ko() {
        // given
        var requested = new ClockSettings(false, null, 5);

        // when / then
        assertThatThrownBy(() -> localGameService.start(requested))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.DISABLED_CLOCK_HAS_TIMING);
    }

    @Test
    void replay_alternates_sides_and_returns_position_ok() {
        // given
        var moves = List.of("e2e4", "e7e5", "g1f3");

        // when
        var position = localGameService.replay(moves);

        // then
        assertAll(
                () -> assertThat(position.getPly()).isEqualTo(3),
                () -> assertThat(position.getSideToMove()).isEqualTo(ChessColor.BLACK),
                () -> assertThat(position.getLastMoveSan()).isEqualTo("Nf3"),
                () -> assertThat(position.getStatus()).isEqualTo(GameStatus.ACTIVE));
    }

    @Test
    void replay_checkmate_returns_result_without_legal_moves_ok() {
        // given
        var moves = List.of("f2f3", "e7e5", "g2g4", "d8h4");

        // when
        var position = localGameService.replay(moves);

        // then
        assertAll(
                () -> assertThat(position.getStatus()).isEqualTo(GameStatus.CHECKMATE),
                () -> assertThat(position.getResult()).isEqualTo(GameResult.BLACK_WIN),
                () -> assertThat(position.getLegalMoves()).isEmpty());
    }

    @Test
    void replay_illegal_move_ko() {
        // given
        var moves = List.of("e2e4", "e7e5", "e4e5");

        // when / then
        assertThatThrownBy(() -> localGameService.replay(moves))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ILLEGAL_MOVE_SEQUENCE);
    }
}
