package com.swd392.backend.onlinegame.dto;

public record RatingChangeDTO(int ratingBefore, int ratingDelta, int ratingAfter, int kFactor) {}
