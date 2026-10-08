package com.swd392.backend.invitation.dto;

import com.swd392.backend.onlinegame.dto.TimeControlDTO;
import java.time.Instant;

public record InvitationDTO(
        Long id,
        String status,
        String hostColor,
        boolean rated,
        String mode,
        String variant,
        TimeControlDTO clock,
        Instant createdAt,
        Instant expiresAt,
        Instant claimedAt,
        Instant cancelledAt,
        Long gameId) {}
