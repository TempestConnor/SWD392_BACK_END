package com.swd392.backend.rating.repository;

import com.swd392.backend.rating.entity.PlayerRatingEntity;
import com.swd392.backend.rating.entity.PlayerRatingId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRatingRepository extends JpaRepository<PlayerRatingEntity, PlayerRatingId> {

    List<PlayerRatingEntity> findByIdAccountIdOrderByIdModeCode(Long accountId);
}
