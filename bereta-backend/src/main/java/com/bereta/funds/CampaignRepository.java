package com.bereta.funds;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CampaignRepository extends JpaRepository<CampaignEntity, UUID> {
    List<CampaignEntity> findAllByOrderByCreatedAtDesc();
    long countByAssociationId(UUID associationId);
    long countByAssociationIdAndStatus(UUID associationId, String status);
    List<CampaignEntity> findAllByPrimaryFundIdOrderByCreatedAtDesc(UUID primaryFundId);
    List<CampaignEntity> findAllByPrimaryFundIdInOrderByCreatedAtDesc(List<UUID> primaryFundIds);
}
