package com.bereta.admin;

import com.bereta.associations.AssociationEntity;
import com.bereta.auth.UserAccountEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Entrée du journal d'audit administrateur, en ajout seul : entité immuable (aucun setter, {@link Immutable}),
 * repository sans modification ni suppression, et trigger PostgreSQL qui refuse UPDATE et DELETE.
 */
@Entity
@Immutable
@Table(name = "admin_audit_log")
public class AdminAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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

    /** Texte libre des entrées antérieures au motif obligatoire. */
    @Column(length = 1000)
    private String details;

    @Column(length = 500)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_values")
    private Map<String, Object> beforeValues;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_values")
    private Map<String, Object> afterValues;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AdminAuditEntity() {
    }

    AdminAuditEntity(UserAccountEntity actor, AdminAuditService.Entry entry, String ipAddress) {
        this.actor = actor;
        this.action = entry.action().name();
        this.targetType = entry.targetType();
        this.targetId = entry.targetId();
        this.association = entry.association();
        this.reason = entry.reason();
        this.beforeValues = entry.before();
        this.afterValues = entry.after();
        this.ipAddress = ipAddress;
    }

    AdminAuditEntity(UserAccountEntity actor, String action, String targetType, UUID targetId,
                     AssociationEntity association, String details, String ipAddress) {
        this.actor = actor;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.association = association;
        this.details = details;
        this.ipAddress = ipAddress;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UserAccountEntity getActor() { return actor; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public AssociationEntity getAssociation() { return association; }
    public String getDetails() { return details; }
    public String getReason() { return reason; }
    public Map<String, Object> getBeforeValues() { return beforeValues; }
    public Map<String, Object> getAfterValues() { return afterValues; }
    public String getIpAddress() { return ipAddress; }
    public Instant getCreatedAt() { return createdAt; }
}
