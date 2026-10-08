package com.swd392.backend.cpugame.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swd392.backend.chess.model.ChessColor;
import com.swd392.backend.chess.model.EngineSettings;
import com.swd392.backend.chess.model.GameResult;
import com.swd392.backend.chess.model.GameStatus;
import com.swd392.backend.chess.service.ChessRulesServiceImpl;
import com.swd392.backend.chess.service.EngineService;
import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.cpugame.model.CpuDifficulty;
import com.swd392.backend.cpugame.model.CpuGameStart;
import com.swd392.backend.cpugame.model.CpuTurn;
import com.swd392.backend.cpugame.model.PlayerColorChoice;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CpuGameServiceImplTest {

    @Spy
    private ChessRulesServiceImpl chessRulesService = new ChessRulesServiceImpl();

    @Mock
    private EngineService engineService;

    @InjectMocks
    private CpuGameServiceImpl cpuGameService;

    @Test
    void play_cpu_turn_returns_engine_move_and_resulting_position_ok() {
        // given
        var turn = turn(CpuDifficulty.MEDIUM, ChessColor.WHITE, List.of("e2e4"));
        when(engineService.findBestMove(eq(List.of("e2e4")), any())).thenReturn("e7e5");

        // when
        var state = cpuGameService.playCpuTurn(turn);

        // then
        assertAll(
                () -> assertThat(state.getCpuMove()).isEqualTo("e7e5"),
                () -> assertThat(state.getCpuMoveSan()).isEqualTo("e5"),
                () -> assertThat(state.getPosition().getPly()).isEqualTo(2),
                () -> assertThat(state.getPosition().getSideToMove()).isEqualTo(ChessColor.WHITE),
                () -> assertThat(state.getPosition().getStatus()).isEqualTo(GameStatus.ACTIVE));
    }

    @Test
    void play_cpu_turn_uses_difficulty_skill_level_and_move_time_ok() {
        // given
        var turn = turn(CpuDifficulty.HARD, ChessColor.WHITE, List.of("d2d4"));
        when(engineService.findBestMove(anyList(), any())).thenReturn("d7d5");
        var captor = ArgumentCaptor.forClass(EngineSettings.class);

        // when
        cpuGameService.playCpuTurn(turn);

        // then
        verify(engineService).findBestMove(eq(List.of("d2d4")), captor.capture());
        assertAll(
                () -> assertThat(captor.getValue().getSkillLevel()).isEqualTo(15),
                () -> assertThat(captor.getValue().getMoveTime()).isEqualTo(Duration.ofSeconds(1)));
    }

    @Test
    void play_cpu_turn_when_player_to_move_ko() {
        // given
        var turn = turn(CpuDifficulty.EASY, ChessColor.WHITE, List.of("e2e4", "e7e5"));

        // when / then
        assertThatThrownBy(() -> cpuGameService.playCpuTurn(turn))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.NOT_CPU_TURN);
        verify(engineService, never()).findBestMove(anyList(), any());
    }

    @Test
    void play_cpu_turn_after_player_checkmates_returns_finished_game_without_engine_ok() {
        // given
        var turn = turn(CpuDifficulty.EASY, ChessColor.BLACK, List.of("f2f3", "e7e5", "g2g4", "d8h4"));

        // when
        var state = cpuGameService.playCpuTurn(turn);

        // then
        assertAll(
                () -> assertThat(state.getCpuMove()).isNull(),
                () -> assertThat(state.getCpuMoveSan()).isNull(),
                () -> assertThat(state.getPosition().getStatus()).isEqualTo(GameStatus.CHECKMATE),
                () -> assertThat(state.getPosition().getResult()).isEqualTo(GameResult.BLACK_WIN));
        verify(engineService, never()).findBestMove(anyList(), any());
    }

    @Test
    void play_cpu_turn_with_illegal_player_move_ko() {
        // given
        var turn = turn(CpuDifficulty.EASY, ChessColor.WHITE, List.of("e2e5"));

        // when / then
        assertThatThrownBy(() -> cpuGameService.playCpuTurn(turn))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ILLEGAL_MOVE_SEQUENCE);
        verify(engineService, never()).findBestMove(anyList(), any());
    }

    @Test
    void play_cpu_turn_when_engine_returns_illegal_move_ko() {
        // given
        var turn = turn(CpuDifficulty.EASY, ChessColor.WHITE, List.of("e2e4"));
        when(engineService.findBestMove(anyList(), any())).thenReturn("e7e4");

        // when / then
        assertThatThrownBy(() -> cpuGameService.playCpuTurn(turn))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ENGINE_UNAVAILABLE);
    }

    @Test
    void start_as_white_returns_initial_position_without_engine_ok() {
        // given
        var start = new CpuGameStart(CpuDifficulty.EASY, PlayerColorChoice.WHITE);

        // when
        var state = cpuGameService.start(start);

        // then
        assertAll(
                () -> assertThat(state.getPlayerColor()).isEqualTo(ChessColor.WHITE),
                () -> assertThat(state.getCpuMove()).isNull(),
                () -> assertThat(state.getPosition().getPly()).isZero());
        verify(engineService, never()).findBestMove(anyList(), any());
    }

    @Test
    void start_as_black_cpu_plays_first_move_ok() {
        // given
        var start = new CpuGameStart(CpuDifficulty.EASY, PlayerColorChoice.BLACK);
        when(engineService.findBestMove(eq(List.of()), any())).thenReturn("e2e4");

        // when
        var state = cpuGameService.start(start);

        // then
        assertAll(
                () -> assertThat(state.getPlayerColor()).isEqualTo(ChessColor.BLACK),
                () -> assertThat(state.getCpuMove()).isEqualTo("e2e4"),
                () -> assertThat(state.getPosition().getSideToMove()).isEqualTo(ChessColor.BLACK));
    }

    @Test
    void start_random_resolves_to_concrete_color_ok() {
        // given
        var start = new CpuGameStart(CpuDifficulty.EASY, PlayerColorChoice.RANDOM);
        lenient().when(engineService.findBestMove(anyList(), any())).thenReturn("e2e4");

        // when
        var state = cpuGameService.start(start);

        // then
        assertThat(state.getPlayerColor()).isIn(ChessColor.WHITE, ChessColor.BLACK);
    }

    private CpuTurn turn(CpuDifficulty difficulty, ChessColor playerColor, List<String> moves) {
        return new CpuTurn(difficulty, playerColor, moves);
    }
}
