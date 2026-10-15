package com.swd392.backend.onlinegame.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(setterPrefix = "with")
@EqualsAndHashCode
public class GameMoveId implements Serializable {

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "ply", nullable = false)
    private Integer ply;
}
