package com.bereta.funds;

import com.bereta.members.MemberEntity;
import com.bereta.members.MemberService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

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
        return memberCampaignRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .filter(enrollment -> fundAccessService.hasLocalAccess(userId,
                        enrollment.getCampaign().getPrimaryFund().getId(), enrollment.getLocalFund().getId()))
                .map(MemberCampaignResponse::from).toList();
    }

    @Transactional
    public MemberCampaignResponse enroll(UUID userId, UUID memberId, MemberCampaignRequest request) {
        MemberEntity member = memberService.requireAccessibleMember(userId, memberId);
        CampaignEntity campaign = campaignRepository.findById(request.campaignId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campagne introuvable"));
        LocalFundEntity localFund = localFundRepository.findById(request.localFundId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caisse locale introuvable"));

        if (!campaign.getPrimaryFund().getId().equals(localFund.getPrimaryFund().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La caisse locale doit appartenir à la même caisse principale que la campagne.");
        }
        if (!member.getAssociation().getId().equals(campaign.getAssociation().getId())
                || !campaign.getAssociation().getId().equals(localFund.getAssociation().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le membre, la campagne et la caisse locale doivent appartenir à la même association.");
        }
        fundAccessService.requireLocalAccess(userId, campaign.getPrimaryFund().getId(), localFund.getId());
        if (memberCampaignRepository.existsByMemberIdAndCampaignId(memberId, campaign.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce membre est déjà inscrit à cette campagne.");
        }

        MemberCampaignEntity enrollment = new MemberCampaignEntity(
                member, campaign, localFund, campaign.getExpectedAmount());
        return MemberCampaignResponse.from(memberCampaignRepository.save(enrollment));
    }

}
