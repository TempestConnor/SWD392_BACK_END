package com.swd392.backend.invitation.repository;

import com.swd392.backend.invitation.entity.InvitationEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface InvitationRepository extends JpaRepository<InvitationEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InvitationEntity> findForUpdateByTokenHash(byte[] tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<InvitationEntity> findForUpdateById(Long id);
}
