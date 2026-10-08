package com.swd392.backend.rating.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "PlayerRating")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
public class PlayerRatingEntity {

    @EmbeddedId
    private PlayerRatingId id;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Column(name = "starting_rating", nullable = false)
    private int startingRating;

    @Column(name = "games_played", nullable = false)
    private int gamesPlayed;
}
