package com.swd392.backend.onlinegame.dto;

import java.time.Instant;

public record GameMoveDTO(
        int ply,
        String move,
        String san,
        String fen,
        Long whiteRemainingMs,
        Long blackRemainingMs,
        Instant acceptedAt) {}
