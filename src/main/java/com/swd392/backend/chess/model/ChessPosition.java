package com.swd392.backend.chess.model;

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
public class ChessPosition {
    private String fen;
    private ChessColor sideToMove;
    private int ply;
    private String lastMoveSan;
    private GameStatus status;
    private GameResult result;

    @Builder.Default
    private List<String> legalMoves = new ArrayList<>();
}
