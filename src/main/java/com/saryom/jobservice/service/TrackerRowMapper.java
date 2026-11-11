package com.saryom.jobservice.service;

import com.saryom.jobservice.domain.Application.Contact;
import com.saryom.jobservice.domain.Application.Posting;
import com.saryom.jobservice.domain.ApplicationStatus;
import com.saryom.jobservice.domain.Fit;
import com.saryom.jobservice.domain.WorkType;
import com.saryom.jobservice.web.dto.TrackerRow;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Set;

/**
 * Reads a row as the job-search skill's tracker writes it.
 *
 * <p>The tracker is a document store behind a dashboard: every field is a
 * string, an unknown value is the words "Not listed" or "Not stated" rather
 * than an absence, and status is split across {@code status} and {@code stage}.
 * This is the one place that knows those conventions.
 */
final class TrackerRowMapper {

    private static final Set<String> PLACEHOLDERS = Set.of("not listed", "not stated", "n/a", "-");

    private TrackerRowMapper() {
    }

    /** Null for the tracker's stand-ins for "unknown" as well as for blanks. */
    static String value(String raw, int max) {
        String cleaned = Text.clean(raw, max);
        return cleaned == null || PLACEHOLDERS.contains(cleaned.toLowerCase(Locale.ROOT)) ? null : cleaned;
    }

    /** Skipped rows are jobs the user never wanted: the skill's memory, not their pipeline. */
    static boolean isSkipped(TrackerRow row) {
        return "skipped".equalsIgnoreCase(Text.clean(row.status()));
    }

    /**
     * Whether a skipped row's recruiter still belongs in the shared directory.
     *
     * <p>A job is skipped because it did not suit this user: the wrong state, too
     * senior, a pay floor. None of that says anything about the recruiter, who
     * may be exactly who the next user is looking for. The one exception is a
     * row skipped as spam, where the poster is the problem.
     */
    static boolean hasShareableRecruiter(TrackerRow row) {
        String category = Text.clean(row.category());
        return category == null || !category.toLowerCase(java.util.Locale.ROOT).startsWith("spam");
    }

    static boolean isTrackable(TrackerRow row) {
        return Text.clean(row.id()) != null && value(row.company(), 200) != null
                && value(row.role(), 200) != null;
    }

    static Posting posting(TrackerRow row) {
        return new Posting(value(row.company(), 200), value(row.vendor(), 200), value(row.role(), 200),
                value(row.where(), 200), workType(row.workType()), value(row.type(), 60),
                value(row.pay(), 120), value(row.source(), 60), value(row.link(), 2000),
                fit(row.fit()), value(row.notes(), 4000));
    }

    static Contact contact(TrackerRow row) {
        String email = value(row.contactEmail(), 320);
        return new Contact(value(row.contactName(), 200), value(row.contactTitle(), 200),
                email != null && email.contains("@") ? email : null,
                value(row.contactPhone(), 60), value(row.contactUrl(), 2000));
    }

    /**
     * The tracker's status and stage as one position in the pipeline. New,
     * Review and Queued are all "not applied yet" from the user's side; once
     * applied, the stage says how far it has gone.
     */
    static ApplicationStatus status(TrackerRow row) {
        String status = lower(row.status());
        if ("declined".equals(status)) {
            return ApplicationStatus.DECLINED;
        }
        if (!"applied".equals(status)) {
            return ApplicationStatus.SAVED;
        }
        return switch (lower(row.stage())) {
            case "screening" -> ApplicationStatus.SCREENING;
            case "interview" -> ApplicationStatus.INTERVIEW;
            case "offer" -> ApplicationStatus.OFFER;
            case "rejected" -> ApplicationStatus.REJECTED;
            case "withdrawn" -> ApplicationStatus.WITHDRAWN;
            case "no response" -> ApplicationStatus.NO_RESPONSE;
            default -> ApplicationStatus.APPLIED;
        };
    }

    static LocalDate date(String raw) {
        String cleaned = Text.clean(raw);
        if (cleaned == null) {
            return null;
        }
        try {
            // Tolerates a full timestamp by reading only the date part.
            return LocalDate.parse(cleaned.length() > 10 ? cleaned.substring(0, 10) : cleaned);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static WorkType workType(String raw) {
        return switch (lower(raw)) {
            case "remote" -> WorkType.REMOTE;
            case "hybrid" -> WorkType.HYBRID;
            case "on-site", "onsite", "on site" -> WorkType.ONSITE;
            default -> null;
        };
    }

    private static Fit fit(String raw) {
        return switch (lower(raw)) {
            case "high" -> Fit.HIGH;
            case "medium" -> Fit.MEDIUM;
            case "low" -> Fit.LOW;
            default -> null;
        };
    }

    private static String lower(String raw) {
        String cleaned = Text.clean(raw);
        return cleaned == null ? "" : cleaned.toLowerCase(Locale.ROOT);
    }
}
