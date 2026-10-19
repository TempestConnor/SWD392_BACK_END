package com.swd392.backend.rating.model;

import com.swd392.backend.chess.model.GameResult;
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
public class RatedGameResult {
    private Long gameId;
    private String modeCode;
    private Long whiteAccountId;
    private Long blackAccountId;
    private GameResult outcome;
}
