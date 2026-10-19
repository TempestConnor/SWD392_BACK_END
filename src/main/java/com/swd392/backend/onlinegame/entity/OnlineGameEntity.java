package com.swd392.backend.onlinegame.entity;

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
@Table(name = "OnlineGame")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
public class OnlineGameEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "game_id")
    private Long id;

    @Column(name = "source", nullable = false, length = 12, updatable = false)
    private String source;

    @Column(name = "white_account_id", nullable = false, updatable = false)
    private Long whiteAccountId;

    @Column(name = "black_account_id", nullable = false, updatable = false)
    private Long blackAccountId;

    @Column(name = "variant_code", nullable = false, length = 24, updatable = false)
    private String variantCode;

    @Column(name = "mode_code", nullable = false, length = 20, updatable = false)
    private String modeCode;

    @Column(name = "is_rated", nullable = false, updatable = false)
    private boolean rated;

    @Column(name = "clock_enabled", nullable = false, updatable = false)
    private boolean clockEnabled;

    @Column(name = "initial_seconds", updatable = false)
    private Integer initialSeconds;

    @Column(name = "increment_seconds", updatable = false)
    private Integer incrementSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "ready_deadline_at", nullable = false, updatable = false)
    private Instant readyDeadlineAt;

    @Column(name = "white_ready_at")
    private Instant whiteReadyAt;

    @Column(name = "black_ready_at")
    private Instant blackReadyAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "status", nullable = false, length = 12)
    private String status;

    @Column(name = "outcome", length = 12)
    private String outcome;

    @Column(name = "termination_reason", length = 24)
    private String terminationReason;

    @Column(name = "initial_fen", nullable = false, length = 120, updatable = false)
    private String initialFen;

    @Column(name = "current_fen", nullable = false, length = 120)
    private String currentFen;

    @Column(name = "last_ply", nullable = false)
    private int lastPly;

    @Column(name = "white_remaining_ms")
    private Long whiteRemainingMs;

    @Column(name = "black_remaining_ms")
    private Long blackRemainingMs;

    @Column(name = "turn_started_at")
    private Instant turnStartedAt;

    @Column(name = "white_disconnected_at")
    private Instant whiteDisconnectedAt;

    @Column(name = "black_disconnected_at")
    private Instant blackDisconnectedAt;

    @Column(name = "white_last_seen_at")
    private Instant whiteLastSeenAt;

    @Column(name = "black_last_seen_at")
    private Instant blackLastSeenAt;
}
