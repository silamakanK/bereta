package com.bereta.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccountEntity u where u.id = :id")
    Optional<UserAccountEntity> findByIdForUpdate(@Param("id") UUID id);

    Optional<UserAccountEntity> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<UserAccountEntity> findByPhoneE164(String phoneE164);
    boolean existsByPhoneE164(String phoneE164);
    long countByRoleAndEnabledTrue(String role);
    long countByEnabledTrue();
    long countByRole(String role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccountEntity u where u.role = 'ADMIN' and u.enabled = true order by u.id")
    java.util.List<UserAccountEntity> lockActiveAdministrators();
}
