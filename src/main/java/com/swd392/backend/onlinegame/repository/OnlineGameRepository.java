package com.swd392.backend.onlinegame.repository;

import com.swd392.backend.onlinegame.entity.OnlineGameEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OnlineGameRepository extends JpaRepository<OnlineGameEntity, Long> {}
