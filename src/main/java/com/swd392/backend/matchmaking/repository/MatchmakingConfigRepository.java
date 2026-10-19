package com.swd392.backend.matchmaking.repository;

import com.swd392.backend.matchmaking.entity.MatchmakingConfigEntity;
import com.swd392.backend.matchmaking.entity.MatchmakingConfigId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchmakingConfigRepository extends JpaRepository<MatchmakingConfigEntity, MatchmakingConfigId> {}
