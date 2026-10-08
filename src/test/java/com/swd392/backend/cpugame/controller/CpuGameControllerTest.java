package com.swd392.backend.cpugame.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.swd392.backend.chess.model.ChessColor;
import com.swd392.backend.chess.model.ChessPosition;
import com.swd392.backend.chess.model.GameStatus;
import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.config.SecurityConfig;
import com.swd392.backend.cpugame.mapper.CpuGameMapperImpl;
import com.swd392.backend.cpugame.model.CpuDifficulty;
import com.swd392.backend.cpugame.model.CpuGameState;
import com.swd392.backend.cpugame.model.CpuTurn;
import com.swd392.backend.cpugame.model.PlayerColorChoice;
import com.swd392.backend.cpugame.service.CpuGameService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CpuGameController.class)
@Import({SecurityConfig.class, CpuGameMapperImpl.class})
class CpuGameControllerTest {

    private static final String START_URL = "/api/cpu-games";
    private static final String CPU_MOVE_URL = "/api/cpu-games/cpu-move";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CpuGameService cpuGameService;

    @Test
    void cpu_move_as_guest_returns_engine_move_ok() throws Exception {
        // given
        var body =
                """
                {"difficulty":"MEDIUM","playerColor":"WHITE","moves":["e2e4"]}
                """;
        when(cpuGameService.playCpuTurn(any())).thenReturn(state("e7e5", "e5", 2));
        var captor = ArgumentCaptor.forClass(CpuTurn.class);

        // when
        mockMvc.perform(post(CPU_MOVE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpuMove").value("e7e5"))
                .andExpect(jsonPath("$.cpuMoveSan").value("e5"))
                .andExpect(jsonPath("$.ply").value(2))
                .andExpect(jsonPath("$.sideToMove").value("WHITE"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.difficulty").value("MEDIUM"));

        // then
        verify(cpuGameService).playCpuTurn(captor.capture());
        assertAll(
                () -> assertThat(captor.getValue().getDifficulty()).isEqualTo(CpuDifficulty.MEDIUM),
                () -> assertThat(captor.getValue().getPlayerColor()).isEqualTo(ChessColor.WHITE),
                () -> assertThat(captor.getValue().getMoves()).containsExactly("e2e4"));
    }

    @Test
    void cpu_move_invalid_fields_returns_400_problem_ko() throws Exception {
        // given
        var body =
                """
                {"difficulty":"IMPOSSIBLE","playerColor":"RANDOM","moves":["e2e4","e7e5;quit"]}
                """;

        // when / then
        mockMvc.perform(post(CPU_MOVE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[*].field")
                        .value(containsInAnyOrder("difficulty", "playerColor", "moves[1]")));
        verify(cpuGameService, never()).playCpuTurn(any());
    }

    @Test
    void cpu_move_missing_moves_returns_400_ko() throws Exception {
        // given
        var body = """
                {"difficulty":"EASY","playerColor":"WHITE"}
                """;

        // when / then
        mockMvc.perform(post(CPU_MOVE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("moves"));
    }

    @Test
    void cpu_move_illegal_sequence_returns_400_problem_ko() throws Exception {
        // given
        var body = """
                {"difficulty":"EASY","playerColor":"WHITE","moves":["e2e5"]}
                """;
        when(cpuGameService.playCpuTurn(any())).thenThrow(new AppException(AppErrorMessage.ILLEGAL_MOVE_SEQUENCE));

        // when / then
        mockMvc.perform(post(CPU_MOVE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ILLEGAL_MOVE_SEQUENCE"));
    }

    @Test
    void cpu_move_engine_unavailable_returns_503_problem_ko() throws Exception {
        // given
        var body = """
                {"difficulty":"EASY","playerColor":"WHITE","moves":["e2e4"]}
                """;
        when(cpuGameService.playCpuTurn(any())).thenThrow(new AppException(AppErrorMessage.ENGINE_UNAVAILABLE));

        // when / then
        mockMvc.perform(post(CPU_MOVE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("ENGINE_UNAVAILABLE"));
    }

    @Test
    void start_game_as_guest_maps_random_color_choice_ok() throws Exception {
        // given
        var body = """
                {"difficulty":"HARD","playerColor":"RANDOM"}
                """;
        when(cpuGameService.start(any())).thenReturn(state(null, null, 0));

        // when
        mockMvc.perform(post(START_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerColor").value("WHITE"))
                .andExpect(jsonPath("$.cpuMove").doesNotExist());

        // then
        verify(cpuGameService)
                .start(argThat(start -> start.getPlayerColor() == PlayerColorChoice.RANDOM
                        && start.getDifficulty() == CpuDifficulty.HARD));
    }

    private CpuGameState state(String cpuMove, String cpuMoveSan, int ply) {
        var position = ChessPosition.builder()
                .withFen("fen")
                .withSideToMove(ChessColor.WHITE)
                .withPly(ply)
                .withStatus(GameStatus.ACTIVE)
                .build();

        return CpuGameState.builder()
                .withDifficulty(CpuDifficulty.MEDIUM)
                .withPlayerColor(ChessColor.WHITE)
                .withCpuMove(cpuMove)
                .withCpuMoveSan(cpuMoveSan)
                .withPosition(position)
                .build();
    }
}
