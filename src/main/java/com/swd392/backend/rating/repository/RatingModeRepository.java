package com.swd392.backend.rating.repository;

import com.swd392.backend.rating.entity.RatingModeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingModeRepository extends JpaRepository<RatingModeEntity, String> {

    List<RatingModeEntity> findByEnabledTrue();
}
