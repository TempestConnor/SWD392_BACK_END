package com.swd392.backend.matchmaking.repository;

import com.swd392.backend.matchmaking.entity.QueueRequestEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueueRequestRepository extends JpaRepository<QueueRequestEntity, Long> {

    // UX_Queue_one_active allows at most one open request (left_at IS NULL) per account.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<QueueRequestEntity> findForUpdateByAccountIdAndLeftAtIsNull(Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<QueueRequestEntity> findForUpdateById(Long id);

    Optional<QueueRequestEntity> findFirstByAccountIdOrderByIdDesc(Long accountId);

    // Served by the filtered IX_Queue_search index (open requests by variant and mode).
    List<QueueRequestEntity> findByModeCodeAndVariantCodeAndLeftAtIsNullOrderByJoinedAtAscIdAsc(
            String modeCode, String variantCode);

    @Query(
            """
            select r.id from QueueRequestEntity r
            where r.modeCode = :modeCode and r.variantCode = :variantCode
              and r.leftAt is null and r.heartbeatAt <= :silentSince
            """)
    List<Long> findOpenIdsSilentSince(
            @Param("modeCode") String modeCode,
            @Param("variantCode") String variantCode,
            @Param("silentSince") Instant silentSince);
}
