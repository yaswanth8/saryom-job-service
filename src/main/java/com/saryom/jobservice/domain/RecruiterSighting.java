package com.saryom.jobservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/**
 * A recruiter seen hiring for a role, by one user. Several users reporting the
 * same recruiter for the same role is several rows, which is what the
 * directory's "seen by N people" is counted from.
 */
@Entity
@Table(name = "recruiter_sighting", uniqueConstraints = @UniqueConstraint(
        name = "uq_sighting", columnNames = {"recruiter_id", "contributed_by", "role_key"}))
public class RecruiterSighting {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "recruiter_id", nullable = false, updatable = false)
    private UUID recruiterId;

    @Column(name = "role_title", nullable = false)
    private String roleTitle;

    @Column(name = "role_key", nullable = false, updatable = false)
    private String roleKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_category", nullable = false)
    private RoleCategory roleCategory;

    private String company;

    private String location;

    @Column(name = "contributed_by", nullable = false, updatable = false)
    private String contributedBy;

    @Column(name = "seen_on", nullable = false)
    private LocalDate seenOn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RecruiterSighting() {
    }

    public RecruiterSighting(UUID id, UUID recruiterId, String roleTitle, String company,
                             String location, String contributedBy, LocalDate seenOn, Instant now) {
        this.id = id;
        this.recruiterId = recruiterId;
        this.roleTitle = roleTitle.trim();
        this.roleKey = roleKey(roleTitle);
        this.roleCategory = RoleCategory.of(roleTitle);
        this.company = company;
        this.location = location;
        this.contributedBy = contributedBy;
        this.seenOn = seenOn;
        this.createdAt = now;
    }

    public static String roleKey(String roleTitle) {
        return roleTitle.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** The same user saw the same recruiter for the same role again, more recently. */
    public void seenAgain(LocalDate when) {
        if (when.isAfter(seenOn)) {
            this.seenOn = when;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecruiterId() {
        return recruiterId;
    }

    public String getRoleTitle() {
        return roleTitle;
    }

    public String getRoleKey() {
        return roleKey;
    }

    public RoleCategory getRoleCategory() {
        return roleCategory;
    }

    public String getCompany() {
        return company;
    }

    public String getLocation() {
        return location;
    }

    public String getContributedBy() {
        return contributedBy;
    }

    public LocalDate getSeenOn() {
        return seenOn;
    }
}
