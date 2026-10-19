package com.swd392.backend.matchmaking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "MatchmakingConfig")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
public class MatchmakingConfigEntity {

    @EmbeddedId
    private MatchmakingConfigId id;

    @Column(name = "initial_gap", nullable = false)
    private int initialGap;

    @Column(name = "gap_step", nullable = false)
    private int gapStep;

    @Column(name = "gap_step_seconds", nullable = false)
    private int gapStepSeconds;

    @Column(name = "max_gap", nullable = false)
    private int maxGap;

    @Column(name = "heartbeat_seconds", nullable = false)
    private int heartbeatSeconds;

    @Column(name = "queue_grace_seconds", nullable = false)
    private int queueGraceSeconds;

    @Column(name = "ready_timeout_seconds", nullable = false)
    private int readyTimeoutSeconds;
}
