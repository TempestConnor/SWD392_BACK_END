package com.swd392.backend.cpugame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.swd392.backend.chess.model.EngineSettings;
import com.swd392.backend.chess.service.EngineService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "STOCKFISH_PATH", matches = ".+")
class CpuGameStockfishIntegrationTest {

    private static final int PLAYER_TURNS = 4;
    private static final EngineSettings PLAYER_SETTINGS = new EngineSettings(20, Duration.ofMillis(50));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EngineService engineService;

    @Test
    void guest_starts_as_black_and_cpu_opens_with_legal_move_ok() throws Exception {
        // given
        var body = """
                {"difficulty":"EASY","playerColor":"BLACK"}
                """;

        // when / then
        mockMvc.perform(post("/api/cpu-games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerColor").value("BLACK"))
                .andExpect(jsonPath("$.cpuMove").isString())
                .andExpect(jsonPath("$.ply").value(1))
                .andExpect(jsonPath("$.sideToMove").value("BLACK"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void guest_plays_several_turns_against_hard_cpu_ok() throws Exception {
        // given
        var moves = new ArrayList<String>();
        var cpuReplies = new ArrayList<String>();

        // when
        for (var turn = 0; turn < PLAYER_TURNS; turn++) {
            moves.add(engineService.findBestMove(moves, PLAYER_SETTINGS));
            cpuReplies.add(requestCpuMove(moves));
            moves.add(cpuReplies.getLast());
        }

        // then
        assertThat(cpuReplies).hasSize(PLAYER_TURNS).allMatch(move -> move.matches("[a-h][1-8][a-h][1-8][qrbn]?"));
    }

    private String requestCpuMove(List<String> moves) throws Exception {
        var body = """
                {"difficulty":"HARD","playerColor":"WHITE","moves":%s}
                """
                .formatted(toJsonArray(moves));
        var result = mockMvc.perform(post("/api/cpu-games/cpu-move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ply").value(moves.size() + 1))
                .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.cpuMove");
    }

    private String toJsonArray(List<String> moves) {
        return moves.stream().map(move -> "\"" + move + "\"").toList().toString();
    }
}
