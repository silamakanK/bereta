package com.bereta.admin;

import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Journal en ajout seul : volontairement limité à l'écriture d'une entrée et à la lecture
 * (pas de JpaRepository, donc aucune méthode update ni delete). Les recherches passent par {@link AdminAuditQuery}.
 */
public interface AdminAuditRepository extends Repository<AdminAuditEntity, UUID> {

    AdminAuditEntity save(AdminAuditEntity entry);

    Optional<AdminAuditEntity> findById(UUID id);

    long count();
}
