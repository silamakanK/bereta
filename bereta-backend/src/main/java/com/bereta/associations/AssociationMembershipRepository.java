package com.bereta.associations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssociationMembershipRepository extends JpaRepository<AssociationMembershipEntity, UUID> {
    List<AssociationMembershipEntity> findAllByUserAccountIdAndActiveTrueOrderByCreatedAtAsc(UUID userId);
    Optional<AssociationMembershipEntity> findByAssociationIdAndUserAccountIdAndActiveTrue(UUID associationId,
                                                                                           UUID userId);
    Optional<AssociationMembershipEntity> findByAssociationIdAndUserAccountId(UUID associationId, UUID userId);
    List<AssociationMembershipEntity> findAllByAssociationIdAndActiveTrueOrderByCreatedAtAsc(UUID associationId);
    Optional<AssociationMembershipEntity> findByIdAndAssociationIdAndActiveTrue(UUID id, UUID associationId);
    long countByActiveTrue();
}
