package com.swd392.backend.onlinegame.dto;

import java.time.Instant;

public record OnlineGameDTO(
        Long id,
        String source,
        String status,
        boolean rated,
        String mode,
        String variant,
        TimeControlDTO clock,
        GamePlayerDTO white,
        GamePlayerDTO black,
        String viewerColor,
        Instant createdAt,
        Instant readyDeadlineAt,
        Instant startedAt,
        String initialFen,
        String currentFen,
        int lastPly,
        Long whiteRemainingMs,
        Long blackRemainingMs) {}
