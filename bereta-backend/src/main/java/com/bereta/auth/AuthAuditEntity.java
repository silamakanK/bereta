package com.bereta.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_audit_log")
public class AuthAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_account_id")
    private UUID userAccountId;

    @Column(nullable = false, length = 40)
    private String event;

    @Column(name = "identifier_hint", length = 64)
    private String identifierHint;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(length = 300)
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuthAuditEntity() {
    }

    AuthAuditEntity(UUID userAccountId, AuthEvent event, String identifierHint, ClientInfo client, String details) {
        this.userAccountId = userAccountId;
        this.event = event.name();
        this.identifierHint = identifierHint;
        this.ipAddress = client.ipAddress();
        this.userAgent = client.userAgent();
        this.details = details;
    }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public UUID getUserAccountId() { return userAccountId; }
    public String getEvent() { return event; }
    public String getIdentifierHint() { return identifierHint; }
    public Instant getCreatedAt() { return createdAt; }
}
