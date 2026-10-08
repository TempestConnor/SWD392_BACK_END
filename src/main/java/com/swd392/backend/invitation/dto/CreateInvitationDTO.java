package com.swd392.backend.invitation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// Omitted clock fields fall back to the default enabled 30+10 clock, which is the only rated invite setting.
public record CreateInvitationDTO(
        @NotNull(message = "is required")
                @Pattern(regexp = "WHITE|BLACK|RANDOM", message = "must be one of WHITE, BLACK, RANDOM")
                String hostColor,
        Boolean clockEnabled,
        @Min(value = 5, message = "must be between 5 and 120") @Max(value = 120, message = "must be between 5 and 120")
                Integer initialMinutes,
        @Min(value = 0, message = "must be between 0 and 60") @Max(value = 60, message = "must be between 0 and 60")
                Integer incrementSeconds) {}
