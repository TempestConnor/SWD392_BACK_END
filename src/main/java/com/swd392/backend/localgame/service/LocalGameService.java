package com.swd392.backend.localgame.service;

import com.swd392.backend.chess.model.ChessPosition;
import com.swd392.backend.localgame.model.ClockSettings;
import com.swd392.backend.localgame.model.LocalGameStart;
import java.util.List;

public interface LocalGameService {

    LocalGameStart start(ClockSettings requestedClock);

    ChessPosition replay(List<String> moves);
}
