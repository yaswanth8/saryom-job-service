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
        String userNote) {
}
