package com.swd392.backend.cpugame.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record StartCpuGameDTO(
        @NotNull(message = "is required")
                @Pattern(regexp = "EASY|MEDIUM|HARD", message = "must be one of EASY, MEDIUM, HARD")
                String difficulty,
        @NotNull(message = "is required")
                @Pattern(regexp = "WHITE|BLACK|RANDOM", message = "must be one of WHITE, BLACK, RANDOM")
                String playerColor) {}
