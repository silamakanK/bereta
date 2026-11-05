package com.bereta.funds;

import com.bereta.members.MemberEntity;
import com.bereta.members.MemberService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Inscriptions aux campagnes. Règles :
 * <ul>
 *   <li>la campagne, le membre et la caisse appartiennent à l'association active ;</li>
 *   <li>la campagne est OUVERTE ;</li>
 *   <li>un trésorier n'inscrit que les membres de sa caisse, dans sa caisse ;</li>
 *   <li>seul le responsable inscrit sur la caisse principale (caisse locale vide) ;</li>
 *   <li>pas de double inscription (409) ; le montant dû est copié depuis la campagne.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class MemberCampaignService {

    private final CampaignRepository campaignRepository;
    private final LocalFundRepository localFundRepository;
    private final MemberCampaignRepository memberCampaignRepository;
    private final MemberService memberService;
    private final FundAccessService fundAccessService;

    public MemberCampaignService(CampaignRepository campaignRepository,
                                 LocalFundRepository localFundRepository,
                                 MemberCampaignRepository memberCampaignRepository,
                                 MemberService memberService,
                                 FundAccessService fundAccessService) {
        this.campaignRepository = campaignRepository;
        this.localFundRepository = localFundRepository;
        this.memberCampaignRepository = memberCampaignRepository;
        this.memberService = memberService;
        this.fundAccessService = fundAccessService;
    }

    public List<MemberCampaignResponse> findAllForMember(UUID userId, UUID memberId) {
        memberService.requireAccessibleMember(userId, memberId);
        FundScope scope = fundAccessService.scope(userId);
        return memberCampaignRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .filter(enrollment -> scope.covers(enrollment.getLocalFund()))
                .map(MemberCampaignResponse::from).toList();
    }

    @Transactional
    public MemberCampaignResponse enroll(UUID userId, UUID memberId, MemberCampaignRequest request) {
        FundScope scope = fundAccessService.scope(userId);
        MemberEntity member = memberService.requireAccessibleMember(userId, memberId);
        CampaignEntity campaign = campaignRepository.findById(request.campaignId())
                .filter(found -> found.getAssociation().getId().equals(scope.associationId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campagne introuvable"));
        if (!campaign.isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette campagne est clôturée : plus d’inscription possible.");
        }
        LocalFundEntity localFund = request.localFundId() == null ? member.getCreatedInLocalFund()
                : localFundRepository.findById(request.localFundId())
                    .filter(found -> found.getAssociation().getId().equals(scope.associationId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caisse locale introuvable"));
        if (localFund != null && !localFund.getPrimaryFund().getId().equals(campaign.getPrimaryFund().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La caisse locale doit appartenir à la caisse principale de la campagne.");
        }
        if (!scope.manager()) {
            // Trésorier : uniquement les membres de sa caisse, et dans sa caisse.
            boolean ownMember = member.getCreatedInLocalFund() != null && scope.covers(member.getCreatedInLocalFund());
            if (!ownMember || !scope.covers(localFund)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Un trésorier ne peut inscrire que les membres de sa caisse.");
            }
        }
        if (memberCampaignRepository.existsByMemberIdAndCampaignId(memberId, campaign.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce membre est déjà inscrit à cette campagne.");
        }
        return MemberCampaignResponse.from(memberCampaignRepository.save(
                new MemberCampaignEntity(member, campaign, localFund, campaign.getExpectedAmount())));
    }
}
