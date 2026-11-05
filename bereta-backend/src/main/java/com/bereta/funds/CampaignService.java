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
    private final MemberCampaignRepository enrollmentRepository;
    private final com.bereta.members.MemberRepository memberRepository;

    public CampaignService(CampaignRepository campaignRepository, PrimaryFundRepository primaryFundRepository,
                           FundAccessService fundAccessService, MemberCampaignRepository enrollmentRepository,
                           com.bereta.members.MemberRepository memberRepository) {
        this.campaignRepository = campaignRepository;
        this.primaryFundRepository = primaryFundRepository;
        this.fundAccessService = fundAccessService;
        this.enrollmentRepository = enrollmentRepository;
        this.memberRepository = memberRepository;
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
        return campaigns.stream().map(this::withCount).toList();
    }

    public CampaignResponse findById(UUID userId, UUID id) {
        CampaignEntity campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campagne introuvable"));
        fundAccessService.requireAccess(userId, campaign.getPrimaryFund().getId());
        return withCount(campaign);
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
        CampaignEntity campaign = campaignRepository.save(new CampaignEntity(primaryFund, request.name().trim(),
                request.campaignType(), request.campaignYear(), request.expectedAmount(), request.dueDate()));
        // Même transaction : tous les membres actifs de l'association sont inscrits, chacun dans sa caisse
        // (caisse locale, ou caisse principale si le membre n'a pas de caisse locale). Montant dû copié.
        List<MemberCampaignEntity> enrollments = memberRepository
                .findAllByAssociationIdAndActiveTrue(primaryFund.getAssociation().getId()).stream()
                .map(member -> new MemberCampaignEntity(member, campaign, member.getCreatedInLocalFund(),
                        campaign.getExpectedAmount()))
                .toList();
        enrollmentRepository.saveAll(enrollments);
        return CampaignResponse.from(campaign, enrollments.size());
    }

    /** Clôture par le responsable : plus aucune inscription ; les paiements restent possibles pour les restes dus. */
    @Transactional
    public CampaignResponse close(UUID userId, UUID id) {
        CampaignEntity campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campagne introuvable"));
        fundAccessService.requireManager(userId, campaign.getPrimaryFund().getId());
        if (!campaign.isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette campagne est déjà clôturée.");
        }
        campaign.close();
        return withCount(campaign);
    }

    private CampaignResponse withCount(CampaignEntity campaign) {
        return CampaignResponse.from(campaign, enrollmentRepository.countByCampaignId(campaign.getId()));
    }
}
