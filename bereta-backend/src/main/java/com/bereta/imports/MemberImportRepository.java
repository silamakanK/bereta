package com.bereta.imports;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberImportRepository extends JpaRepository<MemberImportEntity, UUID> {

    /** Verrou de ligne (SELECT … FOR UPDATE) : deux validations simultanées passent l'une après l'autre. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from MemberImportEntity i where i.id = :id")
    Optional<MemberImportEntity> findByIdForUpdate(@Param("id") UUID id);

    List<MemberImportEntity> findAllByAssociationIdOrderByCreatedAtDesc(UUID associationId);

    /** Même document déjà importé (validé) dans l'association. */
    Optional<MemberImportEntity> findFirstByAssociationIdAndFileFingerprintAndStatusOrderByValidatedAtDesc(
            UUID associationId, String fileFingerprint, ImportStatus status);

    List<MemberImportEntity> findAllByStatusAndUpdatedAtBefore(ImportStatus status, Instant before);
}
