package com.swd392.backend.chess.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swd392.backend.chess.model.EngineSettings;
import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.config.EngineProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EngineServiceImplTest {

    private static final Duration ACQUIRE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SHORT_MOVE_TIME = Duration.ofMillis(100);
    private static final List<String> BLACK_MATES_IN_ONE = List.of("f2f3", "e7e5", "g2g4");

    private final ChessRulesServiceImpl chessRulesService = new ChessRulesServiceImpl();
    private EngineServiceImpl engineService;

    @AfterEach
    void shutdownEngines() {
        if (engineService != null) {
            engineService.shutdown();
        }
    }

    @Test
    void find_best_move_without_configured_path_ko() {
        // given
        engineService = new EngineServiceImpl(new EngineProperties("", 1, ACQUIRE_TIMEOUT));

        // when / then
        assertThatThrownBy(() -> engineService.findBestMove(List.of(), settings(1, SHORT_MOVE_TIME)))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ENGINE_UNAVAILABLE);
    }

    @Test
    void find_best_move_with_missing_executable_ko() {
        // given
        engineService = new EngineServiceImpl(new EngineProperties("does-not-exist/stockfish.exe", 1, ACQUIRE_TIMEOUT));

        // when / then
        assertThatThrownBy(() -> engineService.findBestMove(List.of(), settings(1, SHORT_MOVE_TIME)))
                .isInstanceOf(AppException.class)
                .extracting("errorMessage")
                .isEqualTo(AppErrorMessage.ENGINE_UNAVAILABLE);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 8, 15})
    @EnabledIfEnvironmentVariable(named = "STOCKFISH_PATH", matches = ".+")
    void stockfish_returns_legal_move_at_each_difficulty_skill_level_ok(int skillLevel) {
        // given
        engineService = stockfishService(1);
        var moves = List.of("e2e4", "c7c5", "g1f3");

        // when
        var bestMove = engineService.findBestMove(moves, settings(skillLevel, SHORT_MOVE_TIME));

        // then
        assertThat(isLegalContinuation(moves, bestMove)).isTrue();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "STOCKFISH_PATH", matches = ".+")
    void stockfish_full_strength_finds_mate_in_one_ok() {
        // given
        engineService = stockfishService(1);

        // when
        var bestMove = engineService.findBestMove(BLACK_MATES_IN_ONE, settings(20, Duration.ofMillis(300)));

        // then
        assertThat(bestMove).isEqualTo("d8h4");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "STOCKFISH_PATH", matches = ".+")
    void stockfish_reuses_engine_across_different_games_ok() {
        // given
        engineService = stockfishService(1);
        var firstGame = List.of("d2d4");
        var secondGame = List.of("e2e4", "e7e5", "g1f3");

        // when
        var firstMove = engineService.findBestMove(firstGame, settings(15, SHORT_MOVE_TIME));
        var secondMove = engineService.findBestMove(secondGame, settings(15, SHORT_MOVE_TIME));

        // then
        assertThat(isLegalContinuation(firstGame, firstMove)).isTrue();
        assertThat(isLegalContinuation(secondGame, secondMove)).isTrue();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "STOCKFISH_PATH", matches = ".+")
    void stockfish_pool_serves_more_concurrent_requests_than_instances_ok() throws Exception {
        // given
        engineService = stockfishService(2);
        var requests = new ArrayList<Callable<String>>();
        for (var i = 0; i < 6; i++) {
            requests.add(() -> engineService.findBestMove(List.of("e2e4"), settings(8, SHORT_MOVE_TIME)));
        }

        // when
        List<Future<String>> results;
        try (var executor = Executors.newFixedThreadPool(requests.size())) {
            results = executor.invokeAll(requests);
        }

        // then
        for (var result : results) {
            assertThat(isLegalContinuation(List.of("e2e4"), result.get())).isTrue();
        }
    }

    private EngineServiceImpl stockfishService(int poolSize) {
        return new EngineServiceImpl(new EngineProperties(System.getenv("STOCKFISH_PATH"), poolSize, ACQUIRE_TIMEOUT));
    }

    private boolean isLegalContinuation(List<String> moves, String candidate) {
        var withCandidate = new ArrayList<>(moves);
        withCandidate.add(candidate);
        try {
            chessRulesService.replay(withCandidate);
            return true;
        } catch (AppException exception) {
            return false;
        }
    }

    private EngineSettings settings(int skillLevel, Duration moveTime) {
        return new EngineSettings(skillLevel, moveTime);
    }
}
