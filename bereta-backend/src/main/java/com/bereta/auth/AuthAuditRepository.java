package com.bereta.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuthAuditRepository extends JpaRepository<AuthAuditEntity, UUID> {
    List<AuthAuditEntity> findAllByUserAccountIdOrderByCreatedAtAsc(UUID userAccountId);

    /** Dernière connexion réussie (fiche administrateur). */
    java.util.Optional<AuthAuditEntity> findFirstByUserAccountIdAndEventOrderByCreatedAtDesc(UUID userAccountId, String event);
}
