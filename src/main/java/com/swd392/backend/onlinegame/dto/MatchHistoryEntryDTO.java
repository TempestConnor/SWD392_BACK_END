package com.swd392.backend.onlinegame.dto;

import java.time.Instant;

// result is WIN, LOSS or DRAW from the viewer's side; ratingChange is the viewer's own event, null for unrated games.
public record MatchHistoryEntryDTO(
        Long gameId,
        String source,
        boolean rated,
        String mode,
        String variant,
        TimeControlDTO clock,
        String viewerColor,
        HistoryPlayerDTO opponent,
        String result,
        String outcome,
        String terminationReason,
        Instant startedAt,
        Instant endedAt,
        int plyCount,
        RatingChangeDTO ratingChange) {}
