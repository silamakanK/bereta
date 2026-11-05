package com.bereta.funds;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PrimaryFundRepository extends JpaRepository<PrimaryFundEntity, UUID> {
    List<PrimaryFundEntity> findAllByOrderByNameAsc();
    List<PrimaryFundEntity> findAllByAssociationIdOrderByNameAsc(UUID associationId);
    long countByAssociationId(UUID associationId);
    /** L'unique caisse principale active de l'association. */
    java.util.Optional<PrimaryFundEntity> findFirstByAssociationIdAndStatusOrderByCreatedAtAsc(UUID associationId, String status);
    default java.util.Optional<PrimaryFundEntity> findActiveByAssociationId(UUID associationId) {
        return findFirstByAssociationIdAndStatusOrderByCreatedAtAsc(associationId, "ACTIVE");
    }
    boolean existsByAssociationIdAndStatus(UUID associationId, String status);
}
