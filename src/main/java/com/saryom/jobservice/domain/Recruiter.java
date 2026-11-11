package com.saryom.jobservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/**
 * A recruiter in the shared directory. Public to read: every signed-in user
 * sees the same entry, whoever first came across the person.
 */
@Entity
@Table(name = "recruiter")
public class Recruiter {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "dedupe_key", nullable = false, updatable = false)
    private String dedupeKey;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String title;

    private String company;

    private String email;

    private String phone;

    @Column(name = "profile_url")
    private String profileUrl;

    @Column(name = "contributed_by", nullable = false, updatable = false)
    private String contributedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Recruiter() {
    }

    public Recruiter(UUID id, String fullName, String title, String company, String email,
                     String phone, String profileUrl, String contributedBy, Instant now) {
        this.id = id;
        this.dedupeKey = dedupeKey(fullName, company, email, profileUrl);
        this.fullName = fullName;
        this.title = title;
        this.company = company;
        this.email = email;
        this.phone = phone;
        this.profileUrl = profileUrl;
        this.contributedBy = contributedBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * What makes two sightings the same person. Email is the strongest signal
     * and a profile link the next; a bare name is only trusted together with
     * the company, since "Priya S." at two firms is two people.
     */
    public static String dedupeKey(String fullName, String company, String email, String profileUrl) {
        if (email != null && !email.isBlank()) {
            return "email:" + email.trim().toLowerCase(Locale.ROOT);
        }
        if (profileUrl != null && !profileUrl.isBlank()) {
            String url = profileUrl.trim().toLowerCase(Locale.ROOT)
                    .replaceFirst("^https?://(www\\.)?", "")
                    .replaceFirst("[?#].*$", "")
                    .replaceFirst("/+$", "");
            return "url:" + url;
        }
        return "name:" + normalize(fullName) + "|" + normalize(company);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** Fills in what the directory did not know yet; never overwrites what it did. */
    public boolean fillGaps(String title, String company, String phone, String profileUrl, Instant now) {
        boolean changed = false;
        if (isBlank(this.title) && !isBlank(title)) {
            this.title = title;
            changed = true;
        }
        if (isBlank(this.company) && !isBlank(company)) {
            this.company = company;
            changed = true;
        }
        if (isBlank(this.phone) && !isBlank(phone)) {
            this.phone = phone;
            changed = true;
        }
        if (isBlank(this.profileUrl) && !isBlank(profileUrl)) {
            this.profileUrl = profileUrl;
            changed = true;
        }
        if (changed) {
            this.updatedAt = now;
        }
        return changed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public boolean wasContributedBy(String uid) {
        return contributedBy.equals(uid);
    }

    public UUID getId() {
        return id;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public String getFullName() {
        return fullName;
    }

    public String getTitle() {
        return title;
    }

    public String getCompany() {
        return company;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getProfileUrl() {
        return profileUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
