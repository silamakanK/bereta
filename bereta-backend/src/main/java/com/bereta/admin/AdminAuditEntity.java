package com.bereta.admin;

import com.bereta.associations.AssociationEntity;
import com.bereta.auth.UserAccountEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_audit_log")
public class AdminAuditEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private UserAccountEntity actor;
    @Column(nullable = false, length = 80)
    private String action;
    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;
    @Column(name = "target_id")
    private UUID targetId;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "association_id")
    private AssociationEntity association;
    @Column(length = 1000)
    private String details;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AdminAuditEntity() { }
    public AdminAuditEntity(UserAccountEntity actor, String action, String targetType, UUID targetId,
                            AssociationEntity association, String details) {
        this.actor = actor;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.association = association;
        this.details = details;
    }
    @PrePersist void onCreate() { createdAt = Instant.now(); }
    public UUID getId() { return id; }
    public String getActorEmail() { return actor == null ? "Commande locale" : actor.getEmail(); }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public UUID getAssociationId() { return association == null ? null : association.getId(); }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
