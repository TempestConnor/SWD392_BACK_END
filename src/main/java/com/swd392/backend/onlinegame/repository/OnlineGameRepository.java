package com.swd392.backend.onlinegame.repository;

import com.swd392.backend.onlinegame.entity.OnlineGameEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // Active games that may need a TIMEOUT or DISCONNECT result or a disconnect mark: every clocked game (which
    // side's clock runs depends on last_ply, so flag fall is checked in the service) and every game with a
    // participant silent since the active heartbeat cutoff.
    @Query(
            """
            select g from OnlineGameEntity g
            where g.status = :status
              and (g.clockEnabled = true
                or coalesce(g.whiteLastSeenAt, g.createdAt) <= :silentSince
                or coalesce(g.blackLastSeenAt, g.createdAt) <= :silentSince)
            """)
    List<OnlineGameEntity> findActiveSweepCandidates(
            @Param("status") String status, @Param("silentSince") Instant silentSince);

    // Backed by IX_Game_white_history / IX_Game_black_history (participant, ended_at desc).
    @Query(
            """
            select g from OnlineGameEntity g
            where g.status = :status
              and (g.whiteAccountId = :accountId or g.blackAccountId = :accountId)
            """)
    Page<OnlineGameEntity> findParticipantGames(
            @Param("accountId") Long accountId, @Param("status") String status, Pageable pageable);
}
