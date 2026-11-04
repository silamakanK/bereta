package com.bereta.funds;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FundMembershipRepository extends JpaRepository<FundMembershipEntity, UUID> {
    List<FundMembershipEntity> findAllByUserAccountIdAndActiveTrueOrderByCreatedAtAsc(UUID userId);
    List<FundMembershipEntity> findAllByUserAccountIdAndAssociationIdAndActiveTrueOrderByCreatedAtAsc(UUID userId, UUID associationId);
    List<FundMembershipEntity> findAllByAssociationIdAndActiveTrueOrderByCreatedAtAsc(UUID associationId);
    List<FundMembershipEntity> findAllByPrimaryFundIdAndActiveTrueOrderByRoleAscCreatedAtAsc(UUID primaryFundId);
    Optional<FundMembershipEntity> findByUserAccountIdAndPrimaryFundIdAndActiveTrue(UUID userId, UUID primaryFundId);
    Optional<FundMembershipEntity> findByUserAccountIdAndPrimaryFundId(UUID userId, UUID primaryFundId);
    Optional<FundMembershipEntity> findByUserAccountIdAndLocalFundId(UUID userId, UUID localFundId);
    Optional<FundMembershipEntity> findByIdAndPrimaryFundIdAndActiveTrue(UUID id, UUID primaryFundId);
    boolean existsByUserAccountIdAndPrimaryFundIdAndRoleAndActiveTrue(
            UUID userId, UUID primaryFundId, FundRole role);
    boolean existsByUserAccountIdAndAssociationIdAndRoleAndActiveTrue(UUID userId, UUID associationId, FundRole role);
    boolean existsByUserAccountIdAndPrimaryFundIdAndLocalFundIdAndActiveTrue(
            UUID userId, UUID primaryFundId, UUID localFundId);
    boolean existsByPrimaryFundIdAndActiveTrue(UUID primaryFundId);
}
