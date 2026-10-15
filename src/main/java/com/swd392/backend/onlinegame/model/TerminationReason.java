package com.swd392.backend.onlinegame.model;

public enum TerminationReason {
    CHECKMATE,
    RESIGNATION,
    TIMEOUT,
    STALEMATE,
    REPETITION,
    MOVE_COUNT,
    INSUFFICIENT_MATERIAL,
    AGREEMENT,
    DISCONNECT,
    MUTUAL_ABANDONMENT,
    PRE_START_CANCEL
}
