package com.bereta.admin;

import com.bereta.admin.AdminDtos.Balance;
import com.bereta.admin.AdminDtos.Person;
import com.bereta.auth.PhoneNumberService;
import com.bereta.auth.UserAccountEntity;
import com.bereta.funds.CashRemittanceRepository;
import com.bereta.funds.FundMembershipEntity;
import com.bereta.funds.FundMembershipRepository;
import com.bereta.funds.FundRole;
import com.bereta.funds.PaymentRepository;
import com.bereta.funds.RemittanceStatus;
import com.bereta.members.MemberRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Chiffres des caisses locales pour la supervision, calculés en lot (une requête par nature, pas par caisse) :
 * argent encaissé, encore sur place, en transit ; trésoriers actifs ; membres actifs.
 */
@Component
@Transactional(readOnly = true)
class AdminFundFigures {

    private static final List<RemittanceStatus> COMMITTED = List.of(RemittanceStatus.EN_ATTENTE, RemittanceStatus.CONFIRMEE);

    private final PaymentRepository payments;
    private final CashRemittanceRepository remittances;
    private final FundMembershipRepository memberships;
    private final MemberRepository members;
    private final PhoneNumberService phones;

    AdminFundFigures(PaymentRepository payments, CashRemittanceRepository remittances,
                     FundMembershipRepository memberships, MemberRepository members, PhoneNumberService phones) {
        this.payments = payments;
        this.remittances = remittances;
        this.memberships = memberships;
        this.members = members;
        this.phones = phones;
    }

    record Figures(Map<UUID, Balance> balances, Map<UUID, List<UserAccountEntity>> treasurers,
                   Map<UUID, Long> memberCounts, BigDecimal confirmed) {

        Balance balance(UUID localFundId) {
            return balances.getOrDefault(localFundId, Balance.EMPTY);
        }

        List<UserAccountEntity> treasurersOf(UUID localFundId) {
            return treasurers.getOrDefault(localFundId, List.of());
        }

        long membersOf(UUID localFundId) {
            return memberCounts.getOrDefault(localFundId, 0L);
        }
    }

    Figures of(Collection<UUID> localFundIds) {
        if (localFundIds.isEmpty()) return new Figures(Map.of(), Map.of(), Map.of(), BigDecimal.ZERO);
        Map<UUID, BigDecimal> collected = new HashMap<>();
        payments.sumByLocalFund(localFundIds).forEach(row -> collected.put((UUID) row[0], (BigDecimal) row[1]));
        Map<UUID, BigDecimal> pending = new HashMap<>();
        Map<UUID, BigDecimal> confirmed = new HashMap<>();
        remittances.sumByLocalFundAndStatus(localFundIds, COMMITTED).forEach(row ->
                (row[1] == RemittanceStatus.EN_ATTENTE ? pending : confirmed).put((UUID) row[0], (BigDecimal) row[2]));

        Map<UUID, Balance> balances = new HashMap<>();
        for (UUID id : localFundIds) {
            BigDecimal in = collected.getOrDefault(id, BigDecimal.ZERO);
            BigDecimal transit = pending.getOrDefault(id, BigDecimal.ZERO);
            BigDecimal remitted = confirmed.getOrDefault(id, BigDecimal.ZERO);
            balances.put(id, new Balance(in, in.subtract(transit).subtract(remitted).max(BigDecimal.ZERO), transit));
        }
        Map<UUID, List<UserAccountEntity>> treasurers = memberships
                .findAllByLocalFundIdInAndRoleAndActiveTrueOrderByCreatedAtAsc(localFundIds, FundRole.TRESORIER).stream()
                .collect(Collectors.groupingBy(m -> m.getLocalFund().getId(),
                        Collectors.mapping(FundMembershipEntity::getUserAccount, Collectors.toList())));
        Map<UUID, Long> memberCounts = new HashMap<>();
        members.countActiveByLocalFund(localFundIds).forEach(row -> memberCounts.put((UUID) row[0], (Long) row[1]));
        BigDecimal confirmedTotal = confirmed.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Figures(balances, treasurers, memberCounts, confirmedTotal);
    }

    Person person(UserAccountEntity account) {
        return new Person(account.getId(), account.getDisplayName(), phones.display(account.getPhoneE164()),
                account.getEmail(), account.getRole(), account.isEnabled());
    }

    List<Person> people(List<UserAccountEntity> accounts) {
        return accounts.stream().map(this::person).toList();
    }

    static String label(UserAccountEntity account) {
        return account.getDisplayName() == null || account.getDisplayName().isBlank() ? "Sans nom" : account.getDisplayName();
    }
}
