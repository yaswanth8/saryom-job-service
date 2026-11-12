package com.saryom.jobservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One job a user is tracking: saved, applied to, or somewhere past that.
 * Private to its owner, which the database enforces as well as this service.
 */
@Entity
@Table(name = "application", uniqueConstraints = @UniqueConstraint(
        name = "uq_application_user_external", columnNames = {"user_id", "external_id"}))
public class Application {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    /** The job-search skill's id for this posting; null for rows typed in by hand. */
    @Column(name = "external_id", updatable = false)
    private String externalId;

    @Column(nullable = false)
    private String company;

    /** The staffing firm, when the posting came through one. */
    private String vendor;

    @Column(nullable = false)
    private String role;

    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type")
    private WorkType workType;

    @Column(name = "employment_type")
    private String employmentType;

    private String pay;

    private String source;

    private String link;

    @Enumerated(EnumType.STRING)
    private Fit fit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    /** Notes that came with the posting. An upload may replace these. */
    private String notes;

    /** The user's own notes. An upload never touches these. */
    @Column(name = "user_note")
    private String userNote;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_title")
    private String contactTitle;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "contact_url")
    private String contactUrl;

    @Column(name = "found_on")
    private LocalDate foundOn;

    @Column(name = "applied_on")
    private LocalDate appliedOn;

    @Column(name = "follow_up_on")
    private LocalDate followUpOn;

    @Column(name = "applied_by")
    private String appliedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Application() {
    }

    public Application(UUID id, String userId, String externalId, String company, String role,
                       ApplicationStatus status, Instant now) {
        this.id = id;
        this.userId = userId;
        this.externalId = externalId;
        this.company = company;
        this.role = role;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** The posting itself: what an upload or an edit form may rewrite wholesale. */
    public void describe(Posting posting, Instant now) {
        this.company = posting.company();
        this.vendor = posting.vendor();
        this.role = posting.role();
        this.location = posting.location();
        this.workType = posting.workType();
        this.employmentType = posting.employmentType();
        this.pay = posting.pay();
        this.source = posting.source();
        this.link = posting.link();
        this.fit = posting.fit();
        this.notes = posting.notes();
        this.updatedAt = now;
    }

    public void setContact(Contact contact, Instant now) {
        this.contactName = contact.name();
        this.contactTitle = contact.title();
        this.contactEmail = contact.email();
        this.contactPhone = contact.phone();
        this.contactUrl = contact.url();
        this.updatedAt = now;
    }

    /**
     * Moves the application along. Applying stamps the date if none was given,
     * so "applied" always has a day attached for the follow-up reminder to
     * count from.
     */
    public void moveTo(ApplicationStatus next, LocalDate today, Instant now) {
        if (next == ApplicationStatus.APPLIED && appliedOn == null) {
            this.appliedOn = today;
        }
        this.status = next;
        this.updatedAt = now;
    }

    /** As {@link #moveTo}, but never backwards: for uploads, which may be stale. */
    public void advanceTo(ApplicationStatus next, LocalDate today, Instant now) {
        if (!status.isAheadOf(next)) {
            moveTo(next, today, now);
        }
    }

    public void setDates(LocalDate foundOn, LocalDate appliedOn, LocalDate followUpOn, Instant now) {
        this.foundOn = foundOn;
        this.appliedOn = appliedOn;
        this.followUpOn = followUpOn;
        this.updatedAt = now;
    }

    public void setAppliedBy(String appliedBy) {
        this.appliedBy = appliedBy;
    }

    public void setUserNote(String userNote, Instant now) {
        this.userNote = userNote;
        this.updatedAt = now;
    }

    /** Days after applying with no reply before a follow-up is due, unless a date is set. */
    public static final int DEFAULT_FOLLOW_UP_DAYS = 7;

    /**
     * Whether it is time to chase this application.
     *
     * <p>A follow-up date, when there is one, decides. Without one, an
     * application that has sat at "applied" for a week is due: that is the
     * job-search tracker's rule, and most applications never get a date typed
     * in, so a date-only rule left the reminder silent for nearly all of them.
     */
    public boolean followUpDue(LocalDate today) {
        if (!status.isOpen()) {
            return false;
        }
        if (followUpOn != null) {
            return !followUpOn.isAfter(today);
        }
        return status == ApplicationStatus.APPLIED && appliedOn != null
                && !appliedOn.plusDays(DEFAULT_FOLLOW_UP_DAYS).isAfter(today);
    }

    /** Sets, moves or (with null) clears the follow-up date. */
    public void scheduleFollowUp(LocalDate on, Instant now) {
        this.followUpOn = on;
        this.updatedAt = now;
    }

    public boolean hasContact() {
        return contactName != null && !contactName.isBlank();
    }

    public Contact contact() {
        return new Contact(contactName, contactTitle, contactEmail, contactPhone, contactUrl);
    }

    public Posting posting() {
        return new Posting(company, vendor, role, location, workType, employmentType, pay, source,
                link, fit, notes);
    }

    /** The descriptive fields of a job posting, grouped so they travel together. */
    public record Posting(String company, String vendor, String role, String location,
                          WorkType workType, String employmentType, String pay, String source,
                          String link, Fit fit, String notes) {
    }

    /** The recruiter or hiring contact attached to a posting. */
    public record Contact(String name, String title, String email, String phone, String url) {

        public static final Contact NONE = new Contact(null, null, null, null, null);
    }

    public UUID getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getCompany() {
        return company;
    }

    public String getVendor() {
        return vendor;
    }

    public String getRole() {
        return role;
    }

    public String getLocation() {
        return location;
    }

    public WorkType getWorkType() {
        return workType;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public String getPay() {
        return pay;
    }

    public String getSource() {
        return source;
    }

    public String getLink() {
        return link;
    }

    public Fit getFit() {
        return fit;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public String getUserNote() {
        return userNote;
    }

    public LocalDate getFoundOn() {
        return foundOn;
    }

    public LocalDate getAppliedOn() {
        return appliedOn;
    }

    public LocalDate getFollowUpOn() {
        return followUpOn;
    }

    public String getAppliedBy() {
        return appliedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
