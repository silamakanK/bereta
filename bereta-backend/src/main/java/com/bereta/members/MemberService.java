package com.bereta.members;

import com.bereta.auth.UserAccountEntity;
import com.bereta.auth.UserAccountRepository;
import com.bereta.associations.AssociationRequestContext;
import com.bereta.funds.FundAccessService;
import com.bereta.funds.LocalFundEntity;
import com.bereta.funds.LocalFundRepository;
import com.bereta.funds.MemberCampaignEntity;
import com.bereta.funds.MemberCampaignRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final MemberCampaignRepository memberCampaignRepository;
    private final UserAccountRepository userAccountRepository;
    private final FundAccessService fundAccessService;
    private final LocalFundRepository localFundRepository;
    private final AssociationRequestContext associationContext;

    public MemberService(MemberRepository memberRepository, MemberCampaignRepository memberCampaignRepository,
                         UserAccountRepository userAccountRepository, FundAccessService fundAccessService,
                         LocalFundRepository localFundRepository, AssociationRequestContext associationContext) {
        this.memberRepository = memberRepository;
        this.memberCampaignRepository = memberCampaignRepository;
        this.userAccountRepository = userAccountRepository;
        this.fundAccessService = fundAccessService;
        this.localFundRepository = localFundRepository;
        this.associationContext = associationContext;
    }

    public List<MemberResponse> findAll(UUID userId) {
        UUID associationId = associationContext.requireId();
        if (fundAccessService.isAssociationManager(userId, associationId)) {
            return memberRepository.findAllByAssociationIdOrderByLastNameAscFirstNameAsc(associationId).stream()
                    .map(MemberResponse::from).toList();
        }
        Set<UUID> visibleIds = new HashSet<>();
        List<UUID> localFundIds = fundAccessService.accessibleLocalFundIds(userId);
        if (!localFundIds.isEmpty()) {
            memberRepository.findAllByCreatedInLocalFundIdIn(localFundIds)
                    .forEach(member -> visibleIds.add(member.getId()));
            memberCampaignRepository.findDistinctByLocalFundIdIn(localFundIds)
                    .forEach(enrollment -> visibleIds.add(enrollment.getMember().getId()));
        }
        List<UUID> managerFundIds = fundAccessService.managerPrimaryFundIds(userId);
        if (!managerFundIds.isEmpty()) {
            memberRepository.findAllByCreatedInPrimaryFundIdIn(managerFundIds)
                    .forEach(member -> visibleIds.add(member.getId()));
        }
        return memberRepository.findAllById(visibleIds).stream()
                .filter(member -> member.getAssociation().getId().equals(associationId))
                .sorted(Comparator.comparing(MemberEntity::getLastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(MemberEntity::getFirstName, String.CASE_INSENSITIVE_ORDER))
                .map(MemberResponse::from)
                .toList();
    }

    public MemberResponse findById(UUID userId, UUID id) {
        return MemberResponse.from(getMember(userId, id));
    }

    @Transactional
    public MemberResponse create(UUID userId, MemberRequest request) {
        if (request.localFundId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choisissez une caisse locale.");
        }
        LocalFundEntity candidate = localFundRepository.findById(request.localFundId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caisse locale introuvable"));
        LocalFundEntity localFund = fundAccessService.requireLocalAccess(
                userId, candidate.getPrimaryFund().getId(), candidate.getId());
        UserAccountEntity creator = userAccountRepository.findById(userId).filter(UserAccountEntity::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session invalide"));
        MemberEntity member = new MemberEntity(
                localFund.getAssociation(),
                request.firstName().trim(),
                request.lastName().trim(),
                cleanPhone(request.phone()),
                creator,
                localFund.getPrimaryFund(),
                localFund
        );
        return MemberResponse.from(memberRepository.save(member));
    }

    @Transactional
    public MemberResponse update(UUID userId, UUID id, MemberRequest request) {
        MemberEntity member = getMember(userId, id);
        member.updateDetails(
                request.firstName().trim(),
                request.lastName().trim(),
                cleanPhone(request.phone())
        );
        return MemberResponse.from(memberRepository.save(member));
    }

    public MemberEntity requireAccessibleMember(UUID userId, UUID id) {
        MemberEntity member = memberRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));
        if (!member.getAssociation().getId().equals(associationContext.requireId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable");
        }
        if (fundAccessService.isAssociationManager(userId, member.getAssociation().getId())) return member;
        if (member.getCreatedInLocalFund() != null && member.getCreatedInPrimaryFund() != null
                && fundAccessService.hasLocalAccess(userId,
                member.getCreatedInPrimaryFund().getId(), member.getCreatedInLocalFund().getId())) return member;
        if (member.getCreatedInLocalFund() == null && member.getCreatedInPrimaryFund() != null
                && fundAccessService.hasManagerAccess(userId, member.getCreatedInPrimaryFund().getId())) return member;

        boolean enrolledInAccessibleLocalFund = memberCampaignRepository.findAllByMemberIdOrderByCreatedAtDesc(id)
                .stream().anyMatch(enrollment -> fundAccessService.hasLocalAccess(
                        userId,
                        enrollment.getCampaign().getPrimaryFund().getId(),
                        enrollment.getLocalFund().getId()));
        if (!enrolledInAccessibleLocalFund) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable");
        }
        return member;
    }

    private MemberEntity getMember(UUID userId, UUID id) {
        return requireAccessibleMember(userId, id);
    }

    private String cleanPhone(String phone) {
        return phone == null || phone.isBlank() ? null : phone.trim();
    }
}
