package com.swd392.backend.rating.repository;

import com.swd392.backend.rating.entity.RatingEventEntity;
import com.swd392.backend.rating.entity.RatingEventId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingEventRepository extends JpaRepository<RatingEventEntity, RatingEventId> {

    List<RatingEventEntity> findByIdGameIdOrderByIdAccountId(Long gameId);

    List<RatingEventEntity> findByIdGameIdIn(Collection<Long> gameIds);
}
