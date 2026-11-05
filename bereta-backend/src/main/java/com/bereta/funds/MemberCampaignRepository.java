package com.bereta.funds;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.UUID;
import java.util.Collection;
import java.util.Optional;

public interface MemberCampaignRepository extends JpaRepository<MemberCampaignEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // Pas de jointure : FOR UPDATE est interdit sur le côté facultatif d'une jointure externe (caisse locale).
    @Query("select e from MemberCampaignEntity e where e.id = :id")
    Optional<MemberCampaignEntity> findByIdForUpdate(@Param("id") UUID id);

    /** Toutes les inscriptions de l'association, caisse principale comprise. */
    @Query("select e from MemberCampaignEntity e join fetch e.member join fetch e.campaign " +
            "left join fetch e.localFund where e.association.id = :associationId")
    List<MemberCampaignEntity> findAllForAssociation(@Param("associationId") UUID associationId);

    long countByCampaignId(UUID campaignId);

    List<MemberCampaignEntity> findAllByMemberIdOrderByCreatedAtDesc(UUID memberId);
    boolean existsByMemberIdAndCampaignId(UUID memberId, UUID campaignId);
    List<MemberCampaignEntity> findDistinctByLocalFundIdIn(Collection<UUID> localFundIds);
}
