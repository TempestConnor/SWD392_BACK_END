package com.swd392.backend.cpugame.model;

import com.swd392.backend.chess.model.ChessColor;
import com.swd392.backend.chess.model.ChessPosition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
public class CpuGameState {
    private CpuDifficulty difficulty;
    private ChessColor playerColor;
    private String cpuMove;
    private String cpuMoveSan;
    private ChessPosition position;
}
