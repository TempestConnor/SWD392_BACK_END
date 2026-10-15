package com.swd392.backend.onlinegame.model;

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
public class GameMove {
    private Long gameId;
    private int ply;
    private Long playedByAccountId;
    private String moveUci;
    private String san;
    private String resultingFen;
    private Long whiteRemainingMs;
    private Long blackRemainingMs;
    private Instant acceptedAt;
}
