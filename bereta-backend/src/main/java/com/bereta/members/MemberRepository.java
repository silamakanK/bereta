package com.bereta.members;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<MemberEntity, UUID> {

    List<MemberEntity> findAllByOrderByLastNameAscFirstNameAsc();
    List<MemberEntity> findAllByCreatedByIdOrderByLastNameAscFirstNameAsc(UUID userId);
    List<MemberEntity> findAllByCreatedInLocalFundIdIn(List<UUID> localFundIds);
    List<MemberEntity> findAllByCreatedInPrimaryFundIdIn(List<UUID> primaryFundIds);
    List<MemberEntity> findAllByAssociationIdOrderByLastNameAscFirstNameAsc(UUID associationId);
    long countByAssociationId(UUID associationId);

    @Modifying
    @Query(value = "UPDATE members SET created_by_user_id = :userId, created_in_primary_fund_id = "
            + "(SELECT primary_fund_id FROM fund_memberships WHERE user_account_id = :userId "
            + "AND role = 'RESPONSABLE' AND active = TRUE ORDER BY created_at LIMIT 1) "
            + "WHERE created_by_user_id IS NULL", nativeQuery = true)
    int assignUnownedMembersTo(@Param("userId") UUID userId);
    /** Membres actifs (non archivés) de l'association : inscrits automatiquement aux nouvelles campagnes. */
    List<MemberEntity> findAllByAssociationIdAndActiveTrue(UUID associationId);
}
