package com.saryom.jobservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Lets the job-search skill upload to one user's tracker without their Google
 * sign-in. Only a hash is kept, so a copy of this table is not a set of keys.
 */
@Entity
@Table(name = "import_token")
public class ImportToken {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    @Column(nullable = false)
    private String label;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected ImportToken() {
    }

    public ImportToken(UUID id, String userId, String label, String tokenHash, Instant now) {
        this.id = id;
        this.userId = userId;
        this.label = label;
        this.tokenHash = tokenHash;
        this.createdAt = now;
    }

    public void markUsed(Instant when) {
        this.lastUsedAt = when;
    }

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getLabel() {
        return label;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
