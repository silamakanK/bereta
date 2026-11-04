package com.bereta.auth;

import com.bereta.funds.FundAccessService;
import com.bereta.funds.FundInvitationEntity;
import com.bereta.funds.FundInvitationRepository;
import com.bereta.funds.FundMembershipEntity;
import com.bereta.funds.FundMembershipRepository;
import com.bereta.funds.FundRole;
import com.bereta.funds.LocalFundEntity;
import com.bereta.funds.LocalFundRepository;
import com.bereta.funds.TreasurerInvitationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TreasurerInvitationService {

    private final FundAccessService fundAccessService;
    private final FundInvitationRepository invitationRepository;
    private final LocalFundRepository localFundRepository;
    private final UserAccountRepository userAccountRepository;
    private final FundMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public TreasurerInvitationService(FundAccessService fundAccessService,
                                      FundInvitationRepository invitationRepository,
                                      LocalFundRepository localFundRepository,
                                      UserAccountRepository userAccountRepository,
                                      FundMembershipRepository membershipRepository,
                                      PasswordEncoder passwordEncoder) {
        this.fundAccessService = fundAccessService;
        this.invitationRepository = invitationRepository;
        this.localFundRepository = localFundRepository;
        this.userAccountRepository = userAccountRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public TreasurerInvitationResponse create(UUID managerId, UUID primaryFundId,
                                               String email, UUID localFundId) {
        fundAccessService.requireManager(managerId, primaryFundId);
        LocalFundEntity localFund = localFundRepository.findById(localFundId)
                .filter(fund -> fund.getPrimaryFund().getId().equals(primaryFundId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "La caisse locale ne dépend pas de cette caisse principale."));
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userAccountRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un compte existe déjà pour cette adresse. Ajoutez ce compte aux accès de la caisse.");
        }

        byte[] randomToken = new byte[32];
        secureRandom.nextBytes(randomToken);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomToken);
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
        invitationRepository.save(new FundInvitationEntity(
                normalizedEmail, hashToken(token), localFund,
                userAccountRepository.findById(managerId).orElseThrow(), expiresAt));
        return new TreasurerInvitationResponse(normalizedEmail, localFund.getName(), token, expiresAt);
    }

    @Transactional
    public AuthUserResponse accept(AcceptInvitationRequest request) {
        String tokenHash = hashToken(request.token());
        FundInvitationEntity invitation = invitationRepository.findByTokenHashAndAcceptedAtIsNull(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Cette invitation est invalide ou a déjà été utilisée."));
        if (!invitation.getExpiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Cette invitation a expiré.");
        }

        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (!invitation.getEmail().equals(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Utilisez l’adresse e-mail à laquelle cette invitation a été envoyée.");
        }
        if (userAccountRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un compte existe déjà pour cette adresse.");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Le mot de passe ne peut pas dépasser 72 octets UTF-8.");
        }

        UserAccountEntity account = userAccountRepository.save(new UserAccountEntity(
                normalizedEmail, request.displayName().trim(), passwordEncoder.encode(request.password()), "TRESORIER"));
        membershipRepository.save(new FundMembershipEntity(
                account, invitation.getLocalFund().getPrimaryFund(), invitation.getLocalFund(), FundRole.TRESORIER));
        invitation.accept();
        return AuthUserResponse.from(new BeretaUserDetails(account));
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 indisponible", impossible);
        }
    }
}
