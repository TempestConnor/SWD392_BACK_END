package com.swd392.backend.localgame.model;

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
public class ClockSettings {
    private Boolean enabled;
    private Integer initialMinutes;
    private Integer incrementSeconds;
}
