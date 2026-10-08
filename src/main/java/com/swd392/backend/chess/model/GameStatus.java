package com.swd392.backend.chess.model;

public enum GameStatus {
    ACTIVE,
    CHECKMATE,
    STALEMATE,
    INSUFFICIENT_MATERIAL,
    THREEFOLD_REPETITION,
    FIFTY_MOVE_RULE
}
