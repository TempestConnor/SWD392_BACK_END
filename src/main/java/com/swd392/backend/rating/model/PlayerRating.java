package com.swd392.backend.rating.model;

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
public class PlayerRating {
    private Long accountId;
    private String modeCode;
    private int rating;
    private int startingRating;
    private int gamesPlayed;
}
