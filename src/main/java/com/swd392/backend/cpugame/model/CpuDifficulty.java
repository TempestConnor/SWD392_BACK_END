package com.swd392.backend.cpugame.model;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CpuDifficulty {
    EASY(1, Duration.ofMillis(100)),
    MEDIUM(8, Duration.ofMillis(300)),
    HARD(15, Duration.ofSeconds(1));

    private final int skillLevel;
    private final Duration moveTime;
}
