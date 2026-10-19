package com.swd392.backend.matchmaking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// BR-MOVE-014 and the standard-only launch rule: classical/standard is the only queue; its 30+10 clock is fixed.
public record JoinQueueDTO(
        @NotNull(message = "is required") @Pattern(regexp = "CLASSICAL", message = "must be CLASSICAL") String mode,
        @NotNull(message = "is required") @Pattern(regexp = "STANDARD", message = "must be STANDARD") String variant) {}
