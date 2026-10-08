package com.swd392.backend.chess.service;

import com.swd392.backend.chess.model.EngineSettings;
import java.util.List;

public interface EngineService {

    String findBestMove(List<String> uciMoves, EngineSettings settings);
}
