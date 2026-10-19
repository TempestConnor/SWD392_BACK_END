package com.swd392.backend.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.online-game")
public record OnlineGameProperties(
        @NotNull @DurationMin(seconds = 1) Duration pendingHeartbeatTimeout,
        @NotNull @DurationMin(seconds = 1) Duration activeHeartbeatTimeout,
        @NotNull @DurationMin(seconds = 1) Duration sweepInterval) {}
