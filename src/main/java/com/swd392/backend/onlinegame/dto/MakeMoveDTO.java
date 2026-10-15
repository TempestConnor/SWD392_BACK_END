package com.swd392.backend.onlinegame.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record MakeMoveDTO(
        // The ply this move would occupy: the game's lastPly + 1. A stale client value is rejected.
        @NotNull(message = "is required") @Positive(message = "must be positive") Integer ply,
        // UCI coordinate move: from-square, to-square, optional promotion piece.
        @NotNull(message = "is required")
                @Pattern(regexp = "[a-h][1-8][a-h][1-8][qrbn]?", message = "must be a UCI move like e2e4")
                String move) {}
