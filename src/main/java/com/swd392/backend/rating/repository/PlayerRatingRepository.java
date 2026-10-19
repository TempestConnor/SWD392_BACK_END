package com.swd392.backend.rating.repository;

import com.swd392.backend.rating.entity.PlayerRatingEntity;
import com.swd392.backend.rating.entity.PlayerRatingId;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PlayerRatingRepository extends JpaRepository<PlayerRatingEntity, PlayerRatingId> {

    List<PlayerRatingEntity> findByIdAccountIdOrderByIdModeCode(Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PlayerRatingEntity> findForUpdateById(PlayerRatingId id);
}
