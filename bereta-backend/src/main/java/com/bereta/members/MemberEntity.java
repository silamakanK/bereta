package com.bereta.members;

import com.bereta.associations.AssociationEntity;
import com.bereta.auth.UserAccountEntity;
import com.bereta.funds.PrimaryFundEntity;
import com.bereta.funds.LocalFundEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "members")
public class MemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "association_id", nullable = false)
    private AssociationEntity association;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(length = 30)
    private String phone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private UserAccountEntity createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_in_primary_fund_id")
    private PrimaryFundEntity createdInPrimaryFund;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_in_local_fund_id")
    private LocalFundEntity createdInLocalFund;

    /** Membre archivé : conservé, mais plus inscrit automatiquement aux nouvelles campagnes. */
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MemberEntity() {
    }

    public MemberEntity(AssociationEntity association, String firstName, String lastName, String phone,
                        UserAccountEntity createdBy,
                        PrimaryFundEntity createdInPrimaryFund, LocalFundEntity createdInLocalFund) {
        this.association = association;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.createdBy = createdBy;
        this.createdInPrimaryFund = createdInPrimaryFund;
        this.createdInLocalFund = createdInLocalFund;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public AssociationEntity getAssociation() { return association; }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPhone() {
        return phone;
    }

    public UserAccountEntity getCreatedBy() { return createdBy; }
    public PrimaryFundEntity getCreatedInPrimaryFund() { return createdInPrimaryFund; }
    public LocalFundEntity getCreatedInLocalFund() { return createdInLocalFund; }

    public boolean isActive() { return active; }
    public void archive() { active = false; }
    public void reactivate() { active = true; }

    public void updateDetails(String firstName, String lastName, String phone) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
