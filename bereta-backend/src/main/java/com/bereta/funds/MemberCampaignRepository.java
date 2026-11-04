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
    @Query("select e from MemberCampaignEntity e join fetch e.localFund join fetch e.campaign c " +
            "join fetch c.primaryFund where e.id = :id")
    Optional<MemberCampaignEntity> findByIdForUpdate(@Param("id") UUID id);

    List<MemberCampaignEntity> findAllByMemberIdOrderByCreatedAtDesc(UUID memberId);
    boolean existsByMemberIdAndCampaignId(UUID memberId, UUID campaignId);
    List<MemberCampaignEntity> findDistinctByLocalFundIdIn(Collection<UUID> localFundIds);
}
