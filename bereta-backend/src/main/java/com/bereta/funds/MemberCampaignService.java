package com.bereta.funds;

import com.bereta.members.MemberEntity;
import com.bereta.members.MemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MemberCampaignService {

    private final MemberRepository memberRepository;
    private final CampaignRepository campaignRepository;
    private final LocalFundRepository localFundRepository;
    private final MemberCampaignRepository memberCampaignRepository;

    public MemberCampaignService(MemberRepository memberRepository,
                                 CampaignRepository campaignRepository,
                                 LocalFundRepository localFundRepository,
                                 MemberCampaignRepository memberCampaignRepository) {
        this.memberRepository = memberRepository;
        this.campaignRepository = campaignRepository;
        this.localFundRepository = localFundRepository;
        this.memberCampaignRepository = memberCampaignRepository;
    }

    public List<MemberCampaignResponse> findAllForMember(UUID memberId) {
        requireMember(memberId);
        return memberCampaignRepository.findAllByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .map(MemberCampaignResponse::from).toList();
    }

    @Transactional
    public MemberCampaignResponse enroll(UUID memberId, MemberCampaignRequest request) {
        MemberEntity member = requireMember(memberId);
        CampaignEntity campaign = campaignRepository.findById(request.campaignId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campagne introuvable"));
        LocalFundEntity localFund = localFundRepository.findById(request.localFundId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caisse locale introuvable"));

        if (!campaign.getPrimaryFund().getId().equals(localFund.getPrimaryFund().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La caisse locale doit appartenir à la même caisse principale que la campagne.");
        }
        if (memberCampaignRepository.existsByMemberIdAndCampaignId(memberId, campaign.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce membre est déjà inscrit à cette campagne.");
        }

        MemberCampaignEntity enrollment = new MemberCampaignEntity(
                member, campaign, localFund, campaign.getExpectedAmount());
        return MemberCampaignResponse.from(memberCampaignRepository.save(enrollment));
    }

    private MemberEntity requireMember(UUID id) {
        return memberRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));
    }
}
