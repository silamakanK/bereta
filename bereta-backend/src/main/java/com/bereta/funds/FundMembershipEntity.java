package com.bereta.funds;

import com.bereta.associations.AssociationEntity;
import com.bereta.auth.UserAccountEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fund_memberships", uniqueConstraints = @UniqueConstraint(
        name = "uq_fund_memberships_user_local", columnNames = {"user_account_id", "local_fund_id"}))
public class FundMembershipEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "association_id", nullable = false)
    private AssociationEntity association;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccountEntity userAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "primary_fund_id", nullable = false)
    private PrimaryFundEntity primaryFund;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "local_fund_id")
    private LocalFundEntity localFund;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FundRole role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FundMembershipEntity() {
    }

    public FundMembershipEntity(UserAccountEntity userAccount, PrimaryFundEntity primaryFund,
                                LocalFundEntity localFund, FundRole role) {
        this.userAccount = userAccount;
        this.association = primaryFund.getAssociation();
        this.primaryFund = primaryFund;
        this.localFund = localFund;
        this.role = role;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public AssociationEntity getAssociation() { return association; }
    public UserAccountEntity getUserAccount() { return userAccount; }
    public PrimaryFundEntity getPrimaryFund() { return primaryFund; }
    public LocalFundEntity getLocalFund() { return localFund; }
    public FundRole getRole() { return role; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }

    public void assignTo(LocalFundEntity localFund) {
        this.localFund = localFund;
        this.role = FundRole.TRESORIER;
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }
}
