package com.swd392.backend.localgame.model;

import com.swd392.backend.chess.model.ChessPosition;
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
public class LocalGameStart {
    private ClockSettings clock;
    private ChessPosition position;
}
