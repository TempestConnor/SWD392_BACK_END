package com.swd392.backend.localgame.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record LocalMovesDTO(
        // 6000 plies exceeds the longest game possible when the 50-move rule ends games automatically.
        @NotNull(message = "is required") @Size(max = 6000, message = "must contain at most 6000 moves")
                List<
                                @NotNull(message = "must not be null")
                                // UCI coordinate move: from-square, to-square, optional promotion piece.
                                @Pattern(
                                        regexp = "[a-h][1-8][a-h][1-8][qrbn]?",
                                        message = "must be a UCI move like e2e4")
                                String>
                        moves) {}
