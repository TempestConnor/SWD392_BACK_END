package com.swd392.backend.cpugame.model;

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
public class CpuGameStart {
    private CpuDifficulty difficulty;
    private PlayerColorChoice playerColor;
}
