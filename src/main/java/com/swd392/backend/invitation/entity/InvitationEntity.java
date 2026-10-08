package com.swd392.backend.invitation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Invitation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
public class InvitationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invitation_id")
    private Long id;

    @Column(name = "token_hash", nullable = false, length = 32, updatable = false)
    private byte[] tokenHash;

    @Column(name = "host_account_id", nullable = false, updatable = false)
    private Long hostAccountId;

    @Column(name = "joiner_account_id")
    private Long joinerAccountId;

    @Column(name = "game_id")
    private Long gameId;

    @Column(name = "host_color", nullable = false, length = 6, updatable = false)
    private String hostColor;

    @Column(name = "variant_code", nullable = false, length = 24, updatable = false)
    private String variantCode;

    @Column(name = "mode_code", nullable = false, length = 20, updatable = false)
    private String modeCode;

    @Column(name = "clock_enabled", nullable = false, updatable = false)
    private boolean clockEnabled;

    @Column(name = "initial_seconds", updatable = false)
    private Integer initialSeconds;

    @Column(name = "increment_seconds", updatable = false)
    private Integer incrementSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;
}
