package com.swd392.backend.cpugame.model;

import com.swd392.backend.chess.model.ChessColor;
import java.util.ArrayList;
import java.util.List;
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
public class CpuTurn {
    private CpuDifficulty difficulty;
    private ChessColor playerColor;

    @Builder.Default
    private List<String> moves = new ArrayList<>();
}
