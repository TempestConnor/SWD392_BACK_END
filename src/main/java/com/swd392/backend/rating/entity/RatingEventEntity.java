package com.swd392.backend.rating.entity;

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
@Table(name = "RatingEvent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
public class RatingEventEntity {

    @EmbeddedId
    private RatingEventId id;

    @Column(name = "mode_code", nullable = false, length = 20, updatable = false)
    private String modeCode;

    @Column(name = "rating_before", nullable = false, updatable = false)
    private int ratingBefore;

    @Column(name = "rating_delta", nullable = false, updatable = false)
    private int ratingDelta;

    @Column(name = "rating_after", nullable = false, updatable = false)
    private int ratingAfter;

    @Column(name = "k_factor_used", nullable = false, updatable = false)
    private int kFactorUsed;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;
}
