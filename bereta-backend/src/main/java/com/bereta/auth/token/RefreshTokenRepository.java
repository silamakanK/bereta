package com.bereta.auth.token;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshTokenEntity t join fetch t.userAccount where t.tokenHash = :hash")
    Optional<RefreshTokenEntity> findByHashForUpdate(@Param("hash") String hash);

    List<RefreshTokenEntity> findAllByFamilyIdAndRevokedAtIsNull(UUID familyId);

    List<RefreshTokenEntity> findAllByUserAccountIdAndRevokedAtIsNull(UUID userId);
}
