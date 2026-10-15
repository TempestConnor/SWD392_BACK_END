package com.swd392.backend.onlinegame.repository;

import com.swd392.backend.onlinegame.entity.OnlineGameEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OnlineGameRepository extends JpaRepository<OnlineGameEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OnlineGameEntity> findForUpdateById(Long id);

    // Last-seen columns may be null on games claimed before heartbeats were recorded; the claim time stands in.
    @Query(
            """
            select g.id from OnlineGameEntity g
            where g.status = :status
              and (g.readyDeadlineAt <= :now
                or coalesce(g.whiteLastSeenAt, g.createdAt) <= :silentSince
                or coalesce(g.blackLastSeenAt, g.createdAt) <= :silentSince)
            """)
    List<Long> findIdsDueForPreStartCancel(
            @Param("status") String status, @Param("now") Instant now, @Param("silentSince") Instant silentSince);

    // Which side's clock is running depends on last_ply, so flag fall is checked in the service.
    List<OnlineGameEntity> findByStatusAndClockEnabledTrue(String status);
}
