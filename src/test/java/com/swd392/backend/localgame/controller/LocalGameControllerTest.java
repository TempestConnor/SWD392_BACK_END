package com.swd392.backend.localgame.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
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
import com.swd392.backend.localgame.mapper.LocalGameMapperImpl;
import com.swd392.backend.localgame.model.ClockSettings;
import com.swd392.backend.localgame.model.LocalGameStart;
import com.swd392.backend.localgame.service.LocalGameService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LocalGameController.class)
@Import({SecurityConfig.class, LocalGameMapperImpl.class})
class LocalGameControllerTest {

    private static final String START_URL = "/api/local-games";
    private static final String STATE_URL = "/api/local-games/state";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LocalGameService localGameService;

    @Test
    void start_game_as_guest_returns_clock_and_position_ok() throws Exception {
        // given
        var body =
                """
                {"clockEnabled":true,"initialMinutes":15,"incrementSeconds":5}
                """;
        var start = new LocalGameStart(new ClockSettings(true, 15, 5), position(0, List.of("e2e4", "d2d4")));
        when(localGameService.start(any())).thenReturn(start);

        // when
        mockMvc.perform(post(START_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clock.enabled").value(true))
                .andExpect(jsonPath("$.clock.initialMinutes").value(15))
                .andExpect(jsonPath("$.clock.incrementSeconds").value(5))
                .andExpect(jsonPath("$.position.ply").value(0))
                .andExpect(jsonPath("$.position.sideToMove").value("WHITE"))
                .andExpect(jsonPath("$.position.legalMoves").value(containsInAnyOrder("e2e4", "d2d4")));

        // then
        verify(localGameService)
                .start(argThat(clock -> Boolean.TRUE.equals(clock.getEnabled())
                        && clock.getInitialMinutes() == 15
                        && clock.getIncrementSeconds() == 5));
    }

    @Test
    void start_game_with_empty_body_passes_unset_clock_ok() throws Exception {
        // given
        var start = new LocalGameStart(new ClockSettings(true, 30, 10), position(0, List.of()));
        when(localGameService.start(any())).thenReturn(start);

        // when
        mockMvc.perform(post(START_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clock.initialMinutes").value(30));

        // then
        verify(localGameService)
                .start(argThat(clock -> clock.getEnabled() == null
                        && clock.getInitialMinutes() == null
                        && clock.getIncrementSeconds() == null));
    }

    @Test
    void start_game_clock_out_of_range_returns_400_problem_ko() throws Exception {
        // given
        var body =
                """
                {"clockEnabled":true,"initialMinutes":4,"incrementSeconds":61}
                """;

        // when / then
        mockMvc.perform(post(START_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[*].field")
                        .value(containsInAnyOrder("initialMinutes", "incrementSeconds")));
        verify(localGameService, never()).start(any());
    }

    @Test
    void start_game_disabled_clock_with_timing_returns_400_problem_ko() throws Exception {
        // given
        var body = """
                {"clockEnabled":false,"incrementSeconds":10}
                """;
        when(localGameService.start(any())).thenThrow(new AppException(AppErrorMessage.DISABLED_CLOCK_HAS_TIMING));

        // when / then
        mockMvc.perform(post(START_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("DISABLED_CLOCK_HAS_TIMING"));
    }

    @Test
    void state_as_guest_returns_replayed_position_ok() throws Exception {
        // given
        var body = """
                {"moves":["e2e4"]}
                """;
        when(localGameService.replay(eq(List.of("e2e4")))).thenReturn(position(1, List.of("e7e5")));

        // when / then
        mockMvc.perform(post(STATE_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ply").value(1))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.legalMoves[0]").value("e7e5"));
    }

    @Test
    void state_invalid_move_format_returns_400_problem_ko() throws Exception {
        // given
        var body = """
                {"moves":["e2e4","e7e5;quit"]}
                """;

        // when / then
        mockMvc.perform(post(STATE_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("moves[1]"));
        verify(localGameService, never()).replay(anyList());
    }

    @Test
    void state_missing_moves_returns_400_ko() throws Exception {
        // when / then
        mockMvc.perform(post(STATE_URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("moves"));
    }

    @Test
    void state_illegal_sequence_returns_400_problem_ko() throws Exception {
        // given
        var body = """
                {"moves":["e2e5"]}
                """;
        when(localGameService.replay(anyList())).thenThrow(new AppException(AppErrorMessage.ILLEGAL_MOVE_SEQUENCE));

        // when / then
        mockMvc.perform(post(STATE_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ILLEGAL_MOVE_SEQUENCE"));
    }

    private ChessPosition position(int ply, List<String> legalMoves) {
        return ChessPosition.builder()
                .withFen("fen")
                .withSideToMove(ChessColor.WHITE)
                .withPly(ply)
                .withStatus(GameStatus.ACTIVE)
                .withLegalMoves(legalMoves)
                .build();
    }
}
