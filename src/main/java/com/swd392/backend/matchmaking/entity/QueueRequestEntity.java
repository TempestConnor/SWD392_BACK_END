package com.swd392.backend.matchmaking.entity;

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
@Table(name = "QueueRequest")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
public class QueueRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private Long accountId;

    @Column(name = "variant_code", nullable = false, length = 24, updatable = false)
    private String variantCode;

    @Column(name = "mode_code", nullable = false, length = 20, updatable = false)
    private String modeCode;

    @Column(name = "rating_at_entry", nullable = false, updatable = false)
    private int ratingAtEntry;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "heartbeat_at", nullable = false)
    private Instant heartbeatAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "exit_reason", length = 12)
    private String exitReason;

    @Column(name = "game_id")
    private Long gameId;
}
