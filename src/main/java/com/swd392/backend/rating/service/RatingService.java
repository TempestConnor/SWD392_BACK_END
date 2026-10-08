package com.swd392.backend.rating.service;

import com.swd392.backend.rating.model.PlayerRating;
import java.util.List;

public interface RatingService {

    List<PlayerRating> initializeRatings(Long accountId, int startingRating);

    List<PlayerRating> getRatings(Long accountId);
}
