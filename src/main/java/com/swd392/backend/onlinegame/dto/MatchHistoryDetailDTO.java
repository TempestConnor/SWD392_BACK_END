package com.swd392.backend.onlinegame.dto;

import java.time.Instant;
import java.util.List;

// Rating changes are the stored historical events of each player, null for unrated games.
public record MatchHistoryDetailDTO(
        Long gameId,
        String source,
        boolean rated,
        String mode,
        String variant,
        TimeControlDTO clock,
        String viewerColor,
        String result,
        HistoryPlayerDTO white,
        HistoryPlayerDTO black,
        RatingChangeDTO whiteRatingChange,
        RatingChangeDTO blackRatingChange,
        String outcome,
        String terminationReason,
        Instant startedAt,
        Instant endedAt,
        String initialFen,
        String finalFen,
        int plyCount,
        List<GameMoveDTO> moves) {}
