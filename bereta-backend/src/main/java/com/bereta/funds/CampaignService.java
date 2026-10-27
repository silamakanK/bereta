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

    public CampaignService(CampaignRepository campaignRepository, PrimaryFundRepository primaryFundRepository) {
        this.campaignRepository = campaignRepository;
        this.primaryFundRepository = primaryFundRepository;
    }

    public List<CampaignResponse> findAll(UUID primaryFundId) {
        List<CampaignEntity> campaigns = primaryFundId == null
                ? campaignRepository.findAllByOrderByCreatedAtDesc()
                : campaignRepository.findAllByPrimaryFundIdOrderByCreatedAtDesc(primaryFundId);
        return campaigns.stream().map(CampaignResponse::from).toList();
    }

    public CampaignResponse findById(UUID id) {
        return campaignRepository.findById(id)
                .map(CampaignResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campagne introuvable"));
    }

    @Transactional
    public CampaignResponse create(CampaignRequest request) {
        if (request.campaignType() == CampaignType.ANNUAL
                && (request.campaignYear() == null || request.expectedAmount() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Une campagne annuelle doit avoir une année et un montant attendu.");
        }

        PrimaryFundEntity primaryFund = primaryFundRepository.findById(request.primaryFundId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Caisse principale introuvable"));
        CampaignEntity campaign = new CampaignEntity(primaryFund, request.name().trim(),
                request.campaignType(), request.campaignYear(), request.expectedAmount(), request.dueDate());
        return CampaignResponse.from(campaignRepository.save(campaign));
    }
}
