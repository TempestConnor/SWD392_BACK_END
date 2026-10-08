package com.swd392.backend.localgame.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

// Omitted fields fall back to the default enabled 30+10 clock.
public record StartLocalGameDTO(
        Boolean clockEnabled,
        @Min(value = 5, message = "must be between 5 and 120") @Max(value = 120, message = "must be between 5 and 120")
                Integer initialMinutes,
        @Min(value = 0, message = "must be between 0 and 60") @Max(value = 60, message = "must be between 0 and 60")
                Integer incrementSeconds) {}
