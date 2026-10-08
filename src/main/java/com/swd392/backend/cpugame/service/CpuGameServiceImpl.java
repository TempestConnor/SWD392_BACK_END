package com.swd392.backend.cpugame.service;

import com.swd392.backend.chess.model.ChessColor;
import com.swd392.backend.chess.model.ChessPosition;
import com.swd392.backend.chess.model.EngineSettings;
import com.swd392.backend.chess.model.GameStatus;
import com.swd392.backend.chess.service.ChessRulesService;
import com.swd392.backend.chess.service.EngineService;
import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import com.swd392.backend.cpugame.model.CpuDifficulty;
import com.swd392.backend.cpugame.model.CpuGameStart;
import com.swd392.backend.cpugame.model.CpuGameState;
import com.swd392.backend.cpugame.model.CpuTurn;
import com.swd392.backend.cpugame.model.PlayerColorChoice;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CpuGameServiceImpl implements CpuGameService {

    private final ChessRulesService chessRulesService;
    private final EngineService engineService;

    @Override
    public CpuGameState start(CpuGameStart start) {
        ChessColor playerColor = resolveColor(start.getPlayerColor());
        CpuTurn openingTurn = CpuTurn.builder()
                .withDifficulty(start.getDifficulty())
                .withPlayerColor(playerColor)
                .withMoves(List.of())
                .build();
        if (playerColor == ChessColor.BLACK) {
            return playCpuTurn(openingTurn);
        }
        ChessPosition initialPosition = chessRulesService.replay(List.of());

        return toState(openingTurn, null, initialPosition);
    }

    @Override
    public CpuGameState playCpuTurn(CpuTurn turn) {
        ChessPosition position = chessRulesService.replay(turn.getMoves());
        if (position.getStatus() != GameStatus.ACTIVE) {
            return toState(turn, null, position);
        }
        if (position.getSideToMove() == turn.getPlayerColor()) {
            throw new AppException(AppErrorMessage.NOT_CPU_TURN);
        }

        String cpuMove = engineService.findBestMove(turn.getMoves(), toEngineSettings(turn.getDifficulty()));
        List<String> movesWithCpuMove = new ArrayList<>(turn.getMoves());
        movesWithCpuMove.add(cpuMove);
        ChessPosition afterCpuMove = replayEngineResult(movesWithCpuMove);

        return toState(turn, cpuMove, afterCpuMove);
    }

    private ChessPosition replayEngineResult(List<String> moves) {
        try {
            return chessRulesService.replay(moves);
        } catch (AppException exception) {
            throw new AppException(AppErrorMessage.ENGINE_UNAVAILABLE, exception);
        }
    }

    private ChessColor resolveColor(PlayerColorChoice choice) {
        return switch (choice) {
            case WHITE -> ChessColor.WHITE;
            case BLACK -> ChessColor.BLACK;
            case RANDOM -> ThreadLocalRandom.current().nextBoolean() ? ChessColor.WHITE : ChessColor.BLACK;
        };
    }

    private EngineSettings toEngineSettings(CpuDifficulty difficulty) {
        EngineSettings settings = EngineSettings.builder()
                .withSkillLevel(difficulty.getSkillLevel())
                .withMoveTime(difficulty.getMoveTime())
                .build();

        return settings;
    }

    private CpuGameState toState(CpuTurn turn, String cpuMove, ChessPosition position) {
        CpuGameState state = CpuGameState.builder()
                .withDifficulty(turn.getDifficulty())
                .withPlayerColor(turn.getPlayerColor())
                .withCpuMove(cpuMove)
                .withCpuMoveSan(cpuMove == null ? null : position.getLastMoveSan())
                .withPosition(position)
                .build();

        return state;
    }
}
