package com.bereta.funds;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.UUID;

public interface LocalFundRepository extends JpaRepository<LocalFundEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from LocalFundEntity f join fetch f.primaryFund where f.id = :id")
    java.util.Optional<LocalFundEntity> findByIdForUpdate(@Param("id") UUID id);

    List<LocalFundEntity> findAllByPrimaryFundIdOrderByNameAsc(UUID primaryFundId);
    List<LocalFundEntity> findAllByAssociationIdOrderByNameAsc(UUID associationId);
    long countByAssociationId(UUID associationId);
    List<LocalFundEntity> findAllByPrimaryFundIdInOrderByNameAsc(List<UUID> primaryFundIds);
}
