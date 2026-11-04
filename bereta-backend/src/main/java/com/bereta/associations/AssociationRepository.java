package com.bereta.associations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AssociationRepository extends JpaRepository<AssociationEntity, UUID> {
    Optional<AssociationEntity> findByIdAndStatus(UUID id, AssociationStatus status);
    Page<AssociationEntity> findAllByNameContainingIgnoreCase(String name, Pageable pageable);
    java.util.List<AssociationEntity> findAllByStatusOrderByNameAsc(AssociationStatus status);
    long countByStatus(AssociationStatus status);
}
