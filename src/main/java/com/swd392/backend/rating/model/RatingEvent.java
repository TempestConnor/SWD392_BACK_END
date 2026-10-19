package com.swd392.backend.rating.model;

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
public class RatingEvent {
    private Long gameId;
    private Long accountId;
    private String modeCode;
    private int ratingBefore;
    private int ratingDelta;
    private int ratingAfter;
    private int kFactorUsed;
    private Instant appliedAt;
}
