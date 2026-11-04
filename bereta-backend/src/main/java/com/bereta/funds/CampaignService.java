package com.bereta.funds;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final PrimaryFundRepository primaryFundRepository;
    private final FundAccessService fundAccessService;

    public CampaignService(CampaignRepository campaignRepository, PrimaryFundRepository primaryFundRepository,
                           FundAccessService fundAccessService) {
        this.campaignRepository = campaignRepository;
        this.primaryFundRepository = primaryFundRepository;
        this.fundAccessService = fundAccessService;
    }

    public List<CampaignResponse> findAll(UUID userId, UUID primaryFundId) {
        List<UUID> accessibleIds;
        if (primaryFundId == null) {
            accessibleIds = fundAccessService.managerPrimaryFundIds(userId);
            if (accessibleIds.isEmpty()) accessibleIds = fundAccessService.accessiblePrimaryFundIds(userId);
        } else {
            fundAccessService.requireAccess(userId, primaryFundId);
            accessibleIds = List.of(primaryFundId);
        }
        if (accessibleIds.isEmpty()) return List.of();
        List<CampaignEntity> campaigns = campaignRepository.findAllByPrimaryFundIdInOrderByCreatedAtDesc(accessibleIds);
        return campaigns.stream().map(CampaignResponse::from).toList();
    }

    public CampaignResponse findById(UUID userId, UUID id) {
        CampaignEntity campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campagne introuvable"));
        fundAccessService.requireAccess(userId, campaign.getPrimaryFund().getId());
        return CampaignResponse.from(campaign);
    }

    @Transactional
    public CampaignResponse create(UUID userId, CampaignRequest request) {
        if (request.campaignType() == CampaignType.ANNUAL
                && (request.campaignYear() == null || request.expectedAmount() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Une campagne annuelle doit avoir une année et un montant attendu.");
        }

        PrimaryFundEntity primaryFund = primaryFundRepository.findById(request.primaryFundId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Caisse principale introuvable"));
        fundAccessService.requireManager(userId, primaryFund.getId());
        CampaignEntity campaign = new CampaignEntity(primaryFund, request.name().trim(),
                request.campaignType(), request.campaignYear(), request.expectedAmount(), request.dueDate());
        return CampaignResponse.from(campaignRepository.save(campaign));
    }
}
