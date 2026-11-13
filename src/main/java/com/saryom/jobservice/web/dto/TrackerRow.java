package com.saryom.jobservice.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One row exactly as the job-search skill's tracker stores it
 * ({@code jobs/<id>} in its database), so the skill can upload its documents
 * without reshaping them. Every field is a string there, dates included, and
 * "Not listed" stands in for unknown; {@code TrackerRowMapper} makes sense of
 * that. Fields the tracker has and this service does not use are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrackerRow(
        String id,
        String company,
        String vendor,
        String role,
        String where,
        String pay,
        String source,
        String link,
        String notes,
        String workType,
        String type,
        String fit,
        String status,
        String stage,
        String contactName,
        String contactTitle,
        String contactEmail,
        String contactPhone,
        String contactUrl,
        String foundAt,
        String appliedAt,
        String followUpAt,
        String appliedBy,
        String userNote,
        /** Why a Skipped row was skipped, e.g. "Location" or "Spam / duplicate". */
        String category,
        // Added by the skill's 2.x trackers; absent from older ones.
        /** The posting's own text, already shortened by the skill. */
        String desc,
        /** When the posting went up: an ISO timestamp, or a date. */
        String postedAt,
        /** "apify" or "browser": how the skill found it. */
        String via,
        Boolean easyApply,
        String applyUrl,
        /** The company's application system, e.g. "Workday". */
        String ats,
        /** "Verified", "Needs Confirmation" or "Excluded". */
        String spons,
        /** Why a Review row needs the user: ready, contact, company-site, needs-call. */
        String reviewReason,
        /** Why there is no contact, e.g. "NA: Indeed hides the employer contact". */
        String contactNote) {

    /** A row as trackers before 2.0 wrote it, with none of the posting details. */
    public TrackerRow(String id, String company, String vendor, String role, String where, String pay, String source,
                      String link, String notes, String workType, String type, String fit, String status, String stage,
                      String contactName, String contactTitle, String contactEmail, String contactPhone,
                      String contactUrl, String foundAt, String appliedAt, String followUpAt, String appliedBy,
                      String userNote, String category) {
        this(id, company, vendor, role, where, pay, source, link, notes, workType, type, fit, status, stage,
                contactName, contactTitle, contactEmail, contactPhone, contactUrl, foundAt, appliedAt, followUpAt,
                appliedBy, userNote, category, null, null, null, null, null, null, null, null, null);
    }
}
