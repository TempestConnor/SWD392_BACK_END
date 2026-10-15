package com.swd392.backend.onlinegame.repository;

import com.swd392.backend.onlinegame.entity.GameMoveEntity;
import com.swd392.backend.onlinegame.entity.GameMoveId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameMoveRepository extends JpaRepository<GameMoveEntity, GameMoveId> {

    List<GameMoveEntity> findByIdGameIdOrderByIdPlyAsc(Long gameId);
}
