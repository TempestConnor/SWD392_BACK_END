package com.swd392.backend.rating.service;

import com.swd392.backend.rating.model.PlayerRating;
import com.swd392.backend.rating.model.RatedGameResult;
import com.swd392.backend.rating.model.RatingEvent;
import java.util.Collection;
import java.util.List;

public interface RatingService {

    List<PlayerRating> initializeRatings(Long accountId, int startingRating);

    List<PlayerRating> getRatings(Long accountId);

    boolean isModeEnabled(String modeCode);

    PlayerRating getRatingForUpdate(Long accountId, String modeCode);

    List<RatingEvent> applyGameResult(RatedGameResult result);

    List<RatingEvent> getGameEvents(Collection<Long> gameIds);
}
