package com.bereta.auth.otp;

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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "otp_codes")
public class OtpCodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "challenge_id", nullable = false)
    private UUID challengeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccountEntity userAccount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OtpType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OtpChannel channel;

    @Column(nullable = false, length = 320)
    private String destination;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OtpCodeEntity() {
    }

    OtpCodeEntity(UUID challengeId, UserAccountEntity userAccount, OtpType type, OtpChannel channel,
                  String destination, String codeHash, Instant expiresAt) {
        this.challengeId = challengeId;
        this.userAccount = userAccount;
        this.type = type;
        this.channel = channel;
        this.destination = destination;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() { createdAt = Instant.now(); }

    public UUID getChallengeId() { return challengeId; }
    public UserAccountEntity getUserAccount() { return userAccount; }
    public OtpType getType() { return type; }
    public OtpChannel getChannel() { return channel; }
    public String getDestination() { return destination; }
    String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    int getAttempts() { return attempts; }
    public Instant getCreatedAt() { return createdAt; }

    boolean isActive(Instant now) { return usedAt == null && invalidatedAt == null && expiresAt.isAfter(now); }
    void recordFailedAttempt(int maxAttempts) {
        attempts++;
        if (attempts >= maxAttempts) invalidatedAt = Instant.now();
    }
    void markUsed() { usedAt = Instant.now(); }
    void invalidate() { if (usedAt == null && invalidatedAt == null) invalidatedAt = Instant.now(); }
}
