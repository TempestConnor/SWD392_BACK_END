package com.swd392.backend.onlinegame.dto;

import java.time.Instant;

// disconnectedAt is the player's last contact once they have been silent for the active heartbeat timeout;
// they forfeit three minutes after it unless they reconnect first (F11).
public record GamePlayerDTO(
        Long accountId, String displayName, boolean ready, boolean abandonVoted, Instant disconnectedAt) {}
