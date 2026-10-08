package com.swd392.backend.invitation.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
public class Invitation {
    private Long id;
    private Long hostAccountId;
    private Long joinerAccountId;
    private Long gameId;
    private HostColor hostColor;
    private String variantCode;
    private String modeCode;
    private boolean clockEnabled;
    private Integer initialSeconds;
    private Integer incrementSeconds;
    private boolean rated;
    private InvitationStatus status;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant claimedAt;
    private Instant cancelledAt;
}
