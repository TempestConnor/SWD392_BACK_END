package com.swd392.backend.chess.service;

import com.swd392.backend.chess.model.ChessColor;
import com.swd392.backend.chess.model.ChessPosition;
import com.swd392.backend.chess.model.GameResult;
import com.swd392.backend.chess.model.GameStatus;
import com.swd392.backend.common.exception.AppErrorMessage;
import com.swd392.backend.common.exception.AppException;
import io.github.wolfraam.chessgame.ChessGame;
import io.github.wolfraam.chessgame.board.Side;
import io.github.wolfraam.chessgame.move.IllegalMoveException;
import io.github.wolfraam.chessgame.move.Move;
import io.github.wolfraam.chessgame.notation.NotationType;
import io.github.wolfraam.chessgame.result.ChessGameResult;
import io.github.wolfraam.chessgame.result.ChessGameResultType;
import io.github.wolfraam.chessgame.result.DrawType;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ChessRulesServiceImpl implements ChessRulesService {

    @Override
    public ChessPosition replay(List<String> uciMoves) {
        ChessGame game = replayGame(uciMoves);

        return toPosition(game);
    }

    @Override
    public ChessPosition play(List<String> playedMoves, String uciMove) {
        ChessGame game = replayGame(playedMoves);
        playMove(game, uciMove, AppErrorMessage.ILLEGAL_MOVE);

        return toPosition(game);
    }

    private ChessGame replayGame(List<String> uciMoves) {
        ChessGame game = new ChessGame();
        for (String move : uciMoves) {
            playMove(game, move, AppErrorMessage.ILLEGAL_MOVE_SEQUENCE);
        }

        return game;
    }

    private void playMove(ChessGame game, String uciMove, AppErrorMessage illegalMoveError) {
        boolean gameOver = game.getGameResult() != null;
        if (gameOver) {
            throw new AppException(illegalMoveError);
        }
        Move move;
        try {
            move = game.getMove(NotationType.UCI, uciMove);
        } catch (IllegalMoveException | IllegalArgumentException exception) {
            throw new AppException(illegalMoveError, exception);
        }
        boolean illegal = move == null || !game.isLegalMove(move);
        if (illegal) {
            throw new AppException(illegalMoveError);
        }
        game.playMove(move);
    }

    private ChessPosition toPosition(ChessGame game) {
        List<String> sanMoves = game.getNotationList(NotationType.SAN);
        String lastMoveSan = sanMoves.isEmpty() ? null : sanMoves.getLast();
        ChessGameResult gameResult = game.getGameResult();
        ChessPosition position = ChessPosition.builder()
                .withFen(game.getFen())
                .withSideToMove(toColor(game.getSideToMove()))
                .withPly(sanMoves.size())
                .withLastMoveSan(lastMoveSan)
                .withStatus(toStatus(gameResult))
                .withResult(toResult(gameResult))
                .withLegalMoves(legalMoves(game))
                .build();

        return position;
    }

    private List<String> legalMoves(ChessGame game) {
        // The library still generates moves after automatic draws, so a finished game reports none.
        if (game.getGameResult() != null) {
            return List.of();
        }
        List<String> uciMoves = game.getLegalMoves().stream()
                .map(move -> game.getNotation(NotationType.UCI, move))
                .sorted(Comparator.naturalOrder())
                .toList();

        return uciMoves;
    }

    private ChessColor toColor(Side side) {
        return side == Side.WHITE ? ChessColor.WHITE : ChessColor.BLACK;
    }

    private GameStatus toStatus(ChessGameResult gameResult) {
        if (gameResult == null) {
            return GameStatus.ACTIVE;
        }
        if (gameResult.chessGameResultType != ChessGameResultType.DRAW) {
            return GameStatus.CHECKMATE;
        }

        return toDrawStatus(gameResult.drawType);
    }

    private GameStatus toDrawStatus(DrawType drawType) {
        return switch (drawType) {
            case STALE_MATE -> GameStatus.STALEMATE;
            case INSUFFICIENT_MATERIAL -> GameStatus.INSUFFICIENT_MATERIAL;
            case THREEFOLD_REPETITION -> GameStatus.THREEFOLD_REPETITION;
            case FIFTY_MOVE_RULE -> GameStatus.FIFTY_MOVE_RULE;
        };
    }

    private GameResult toResult(ChessGameResult gameResult) {
        if (gameResult == null) {
            return null;
        }

        return switch (gameResult.chessGameResultType) {
            case WHITE_WINS -> GameResult.WHITE_WIN;
            case BLACK_WINS -> GameResult.BLACK_WIN;
            case DRAW -> GameResult.DRAW;
        };
    }
}
