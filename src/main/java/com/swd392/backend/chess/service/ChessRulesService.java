package com.swd392.backend.chess.service;

import com.swd392.backend.chess.model.ChessPosition;
import java.util.List;

public interface ChessRulesService {

    ChessPosition replay(List<String> uciMoves);
}
