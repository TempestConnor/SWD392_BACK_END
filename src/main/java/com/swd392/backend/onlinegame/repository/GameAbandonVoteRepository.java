package com.swd392.backend.onlinegame.repository;

import com.swd392.backend.onlinegame.entity.GameAbandonVoteEntity;
import com.swd392.backend.onlinegame.entity.GameAbandonVoteId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameAbandonVoteRepository extends JpaRepository<GameAbandonVoteEntity, GameAbandonVoteId> {

    List<GameAbandonVoteEntity> findByIdGameId(Long gameId);
}
