package com.bereta.funds;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MemberCampaignRepository extends JpaRepository<MemberCampaignEntity, UUID> {
    List<MemberCampaignEntity> findAllByMemberIdOrderByCreatedAtDesc(UUID memberId);
    boolean existsByMemberIdAndCampaignId(UUID memberId, UUID campaignId);
}
