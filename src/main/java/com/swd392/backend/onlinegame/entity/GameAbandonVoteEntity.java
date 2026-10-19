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
@Table(name = "GameAbandonVote")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
public class GameAbandonVoteEntity {

    @EmbeddedId
    private GameAbandonVoteId id;

    @Column(name = "voted_at", nullable = false, updatable = false)
    private Instant votedAt;
}
