package com.swd392.backend.matchmaking.dto;

import java.time.Instant;

public record QueueRequestDTO(
        Long id,
        String status,
        String mode,
        String variant,
        int ratingAtEntry,
        Instant joinedAt,
        Instant heartbeatAt,
        Instant leftAt,
        Long gameId,
        Integer heartbeatIntervalSeconds) {}
