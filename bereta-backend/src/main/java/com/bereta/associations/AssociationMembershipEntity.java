package com.bereta.associations;

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
@Table(name = "association_memberships", uniqueConstraints = @UniqueConstraint(
        name = "uq_association_membership", columnNames = {"association_id", "user_account_id"}))
public class AssociationMembershipEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "association_id", nullable = false)
    private AssociationEntity association;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccountEntity userAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AssociationRole role = AssociationRole.RESPONSABLE_ASSOCIATION;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssociationMembershipEntity() { }

    public AssociationMembershipEntity(AssociationEntity association, UserAccountEntity userAccount) {
        this.association = association;
        this.userAccount = userAccount;
    }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public void deactivate() { active = false; }
    public void activate() { active = true; }
    public UUID getId() { return id; }
    public AssociationEntity getAssociation() { return association; }
    public UserAccountEntity getUserAccount() { return userAccount; }
    public AssociationRole getRole() { return role; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
}
