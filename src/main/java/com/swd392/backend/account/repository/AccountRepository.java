package com.swd392.backend.account.repository;

import com.swd392.backend.account.entity.AccountEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    boolean existsByEmail(String email);

    boolean existsByDisplayName(String displayName);

    Optional<AccountEntity> findByEmail(String email);
}
