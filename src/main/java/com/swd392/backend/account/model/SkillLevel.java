package com.swd392.backend.account.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SkillLevel {
    BEGINNER(400),
    INTERMEDIATE(800),
    ADVANCED(1200),
    EXPERT(1600);

    private final int startingRating;
}
