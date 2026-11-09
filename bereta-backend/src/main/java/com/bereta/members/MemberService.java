package com.bereta.members;

import com.bereta.auth.PhoneNumberService;
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
    private final com.bereta.funds.PrimaryFundRepository primaryFundRepository;
    private final PhoneNumberService phoneNumberService;

    public MemberService(MemberRepository memberRepository, MemberCampaignRepository memberCampaignRepository,
                         UserAccountRepository userAccountRepository, FundAccessService fundAccessService,
                         LocalFundRepository localFundRepository, AssociationRequestContext associationContext,
                         com.bereta.funds.PrimaryFundRepository primaryFundRepository,
                         PhoneNumberService phoneNumberService) {
        this.primaryFundRepository = primaryFundRepository;
        this.phoneNumberService = phoneNumberService;
        this.memberRepository = memberRepository;
        this.memberCampaignRepository = memberCampaignRepository;
        this.userAccountRepository = userAccountRepository;
        this.fundAccessService = fundAccessService;
        this.localFundRepository = localFundRepository;
        this.associationContext = associationContext;
    }

    public List<MemberResponse> findAll(UUID userId) {
        return visibleMembers(userId).stream().map(MemberResponse::from).toList();
    }

    /** Membres visibles dans l'association active : tous pour le responsable, ceux de ses caisses pour le trésorier. */
    public List<MemberEntity> visibleMembers(UUID userId) {
        UUID associationId = associationContext.requireId();
        if (fundAccessService.isAssociationManager(userId, associationId)) {
            return memberRepository.findAllByAssociationIdOrderByLastNameAscFirstNameAsc(associationId);
        }
        Set<UUID> visibleIds = new HashSet<>();
        List<UUID> localFundIds = fundAccessService.accessibleLocalFundIds(userId);
        if (!localFundIds.isEmpty()) {
            memberRepository.findAllByCreatedInLocalFundIdIn(localFundIds)
                    .forEach(member -> visibleIds.add(member.getId()));
            memberCampaignRepository.findDistinctByLocalFundIdIn(localFundIds)
                    .forEach(enrollment -> visibleIds.add(enrollment.getMember().getId()));
        }
        return memberRepository.findAllById(visibleIds).stream()
                .filter(member -> member.getAssociation().getId().equals(associationId))
                .sorted(Comparator.comparing(MemberEntity::getLastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(MemberEntity::getFirstName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Archiver : le membre reste visible (filtre), mais n'est plus inscrit aux nouvelles campagnes. */
    @Transactional
    public MemberResponse setActive(UUID userId, UUID id, boolean active) {
        MemberEntity member = getMember(userId, id);
        if (active) member.reactivate(); else member.archive();
        return MemberResponse.from(memberRepository.save(member));
    }

    public MemberResponse findById(UUID userId, UUID id) {
        return MemberResponse.from(getMember(userId, id));
    }

    @Transactional
    public MemberResponse create(UUID userId, MemberRequest request) {
        UserAccountEntity creator = userAccountRepository.findById(userId).filter(UserAccountEntity::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session invalide"));
        com.bereta.funds.PrimaryFundEntity primaryFund;
        LocalFundEntity localFund = null;
        if (request.localFundId() == null) {
            // Sans caisse locale : membre suivi directement par la caisse principale (responsable uniquement).
            UUID associationId = associationContext.requireId();
            if (!fundAccessService.isAssociationManager(userId, associationId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choisissez une caisse locale.");
            }
            primaryFund = primaryFundRepository.findActiveByAssociationId(associationId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "L’association n’a pas encore de caisse principale."));
        } else {
            LocalFundEntity candidate = localFundRepository.findById(request.localFundId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Caisse locale introuvable"));
            localFund = fundAccessService.requireLocalAccess(
                    userId, candidate.getPrimaryFund().getId(), candidate.getId());
            primaryFund = localFund.getPrimaryFund();
        }
        MemberEntity member = new MemberEntity(
                primaryFund.getAssociation(),
                request.firstName().trim(),
                request.lastName().trim(),
                cleanPhone(request.phone()),
                creator,
                primaryFund,
                localFund
        );
        member.setPhoneE164(phoneNumberService.tryNormalize(member.getPhone()).orElse(null));
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
        member.setPhoneE164(phoneNumberService.tryNormalize(member.getPhone()).orElse(null));
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
                .stream().filter(enrollment -> enrollment.getLocalFund() != null)
                .anyMatch(enrollment -> fundAccessService.hasLocalAccess(
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
