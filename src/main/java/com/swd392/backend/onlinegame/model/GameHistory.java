package com.swd392.backend.onlinegame.model;

import com.swd392.backend.rating.model.RatingEvent;
import java.util.List;
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
public class GameHistory {
    private OnlineGame game;
    private PlayerResult viewerResult;
    private RatingEvent whiteRatingEvent;
    private RatingEvent blackRatingEvent;
    private List<GameMove> moves;
}
