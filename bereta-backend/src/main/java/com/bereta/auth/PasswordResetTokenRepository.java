package com.bereta.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetTokenEntity t join fetch t.userAccount " +
            "where t.tokenHash = :tokenHash and t.usedAt is null")
    Optional<PasswordResetTokenEntity> findUnusedForUpdate(@Param("tokenHash") String tokenHash);

    List<PasswordResetTokenEntity> findAllByUserAccountIdAndUsedAtIsNull(UUID userId);
}
