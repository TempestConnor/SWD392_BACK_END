package com.swd392.backend.onlinegame.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "GameMove")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
public class GameMoveEntity {

    @EmbeddedId
    private GameMoveId id;

    @Column(name = "played_by_account_id", nullable = false, updatable = false)
    private Long playedByAccountId;

    @Column(name = "move_uci", nullable = false, length = 8, updatable = false)
    private String moveUci;

    @Column(name = "san", length = 24, updatable = false)
    private String san;

    @Column(name = "resulting_fen", nullable = false, length = 120, updatable = false)
    private String resultingFen;

    @Column(name = "white_remaining_ms", updatable = false)
    private Long whiteRemainingMs;

    @Column(name = "black_remaining_ms", updatable = false)
    private Long blackRemainingMs;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;
}
