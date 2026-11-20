package com.bereta.associations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssociationRepository extends JpaRepository<AssociationEntity, UUID> {
    long countByStatus(AssociationStatus status);

    /** Verrou pour les actions de l'administrateur (archivage, restauration, transfert). */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from AssociationEntity a where a.id = :id")
    Optional<AssociationEntity> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
}
