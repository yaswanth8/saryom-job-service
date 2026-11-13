package com.saryom.jobservice.service;

import com.saryom.jobservice.domain.ApplicationStatus;
import com.saryom.jobservice.domain.Fit;
import com.saryom.jobservice.domain.WorkType;
import com.saryom.jobservice.web.dto.TrackerRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TrackerRowMapperTest {

    static TrackerRow row(String status, String stage, String category) {
        return new TrackerRow("li-1", "Acme", "Not listed", "Java Developer", "Austin, TX · On-site",
                "Not stated", "LinkedIn", "https://example.com/job/1", "notes", "On-site", "W2", "High",
                status, stage, "Priya Shah", "Recruiter", "priya@acme.com", "555-0100",
                "https://linkedin.com/in/priya", "2026-11-01", "2026-11-02", "", "claude", "", category);
    }

    static TrackerRow row(String status, String stage) {
        return row(status, stage, null);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "New      | ''          | SAVED",
            "Review   | ''          | SAVED",
            "Queued   | ''          | SAVED",
            "Applied  | ''          | APPLIED",
            "Applied  | Screening   | SCREENING",
            "Applied  | Interview   | INTERVIEW",
            "Applied  | Offer       | OFFER",
            "Applied  | Rejected    | REJECTED",
            "Applied  | Withdrawn   | WITHDRAWN",
            "Applied  | No response | NO_RESPONSE",
            "Declined | ''          | DECLINED",
            // A stage left on a row that never got applied to means nothing.
            "Review   | Interview   | SAVED",
    })
    void theTrackersStatusAndStageBecomeOnePosition(String status, String stage, ApplicationStatus expected) {
        assertThat(TrackerRowMapper.status(row(status, stage))).isEqualTo(expected);
    }

    @Test
    void theTrackersWordsForUnknownBecomeNothing() {
        var posting = TrackerRowMapper.posting(row("Applied", ""));

        // "Not listed" and "Not stated" are how the tracker writes "we don't
        // know"; shown here they would read as a vendor called "Not listed".
        assertThat(posting.vendor()).isNull();
        assertThat(posting.pay()).isNull();
        assertThat(posting.company()).isEqualTo("Acme");
        assertThat(posting.workType()).isEqualTo(WorkType.ONSITE);
        assertThat(posting.fit()).isEqualTo(Fit.HIGH);
        assertThat(posting.employmentType()).isEqualTo("W2");
    }

    @Test
    void datesAreReadLenientlyAndABadOneIsSimplyAbsent() {
        assertThat(TrackerRowMapper.date("2026-11-02")).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(TrackerRowMapper.date("2026-11-02T14:03:00Z")).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(TrackerRowMapper.date("")).isNull();
        assertThat(TrackerRowMapper.date("tomorrow")).isNull();
    }

    @Test
    void anEmailThatIsNotOneIsDroppedRatherThanPublished() {
        var broken = new TrackerRow("li-2", "Acme", null, "Dev", null, null, null, null, null, null, null,
                null, "Applied", null, "Sam", null, "see profile", null, null, null, null, null, null, null, null);

        assertThat(TrackerRowMapper.contact(broken).email()).isNull();
        assertThat(TrackerRowMapper.contact(broken).name()).isEqualTo("Sam");
    }

    @Test
    void aRowNeedsAnIdACompanyAndARoleToBeTracked() {
        assertThat(TrackerRowMapper.isTrackable(row("Applied", ""))).isTrue();
        assertThat(TrackerRowMapper.isTrackable(new TrackerRow(null, "Acme", null, "Dev", null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null))).isFalse();
        assertThat(TrackerRowMapper.isTrackable(new TrackerRow("li-3", "Not listed", null, "Dev", null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null))).isFalse();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "Location, true", "Seniority, true", "Pay below floor, true", "Stale / duplicate, true",
            "Spam / duplicate, false", "spam, false"})
    void aSkippedJobsRecruiterIsWorthSharingUnlessItWasSpam(String category, boolean shareable) {
        assertThat(TrackerRowMapper.hasShareableRecruiter(row("Skipped", "", category))).isEqualTo(shareable);
    }

    @Test
    void aSkipWithNoReasonGivenIsNotTreatedAsSpam() {
        assertThat(TrackerRowMapper.hasShareableRecruiter(row("Skipped", ""))).isTrue();
    }

    /** A row as the skill's 2.x tracker writes it. */
    static TrackerRow row2x(String status, String reviewReason, String postedAt, String applyUrl) {
        return new TrackerRow("li-7", "Acme", null, "Java Developer", "Remote", "$120K", "LinkedIn",
                "https://example.com/job/7", "notes", "Remote", "Full-time", "Stretch", status, null,
                null, null, null, null, null, "2026-11-12", null, null, null, null, null,
                "We build payments software. Java 21, Spring Boot.", postedAt, "apify", true, applyUrl,
                "Workday", "Verified", reviewReason, "NA: no hiring team shown on the posting");
    }

    @Test
    void aTwoPointXRowCarriesThePostingsDetails() {
        var d = TrackerRowMapper.details(row2x("Review", "ready", "2026-11-12T08:30:00Z", "https://acme.example/apply/7"));

        assertThat(d.description()).startsWith("We build payments software");
        assertThat(d.postedAt()).isEqualTo(java.time.Instant.parse("2026-11-12T08:30:00Z"));
        assertThat(d.easyApply()).isTrue();
        assertThat(d.applyUrl()).isEqualTo("https://acme.example/apply/7");
        assertThat(d.ats()).isEqualTo("Workday");
        assertThat(d.sponsorship()).isEqualTo(com.saryom.jobservice.domain.Sponsorship.VERIFIED);
        assertThat(d.foundVia()).isEqualTo(com.saryom.jobservice.domain.FoundVia.APIFY);
        assertThat(d.contactNote()).startsWith("NA:");
        assertThat(d.nextStep()).isEqualTo(com.saryom.jobservice.domain.NextStep.READY);
        assertThat(TrackerRowMapper.posting(row2x("Review", "ready", null, null)).fit()).isEqualTo(Fit.STRETCH);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "null", value = {
            "Review  | ready        | READY",
            "Review  | contact      | REACH_OUT",
            "Review  | company-site | COMPANY_SITE",
            "Review  | needs-call   | YOUR_CALL",
            "Queued  | null         | QUEUED",
            "New     | null         | null",
            "Applied | ready        | null",
    })
    void whatASavedJobIsWaitingForComesFromItsStatusAndReviewReason(String status, String reason,
                                                                      com.saryom.jobservice.domain.NextStep expected) {
        assertThat(TrackerRowMapper.nextStep(row2x(status, reason, null, null))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "2026-11-12T08:30:00Z      | 2026-11-12T08:30:00Z",
            "2026-11-12T08:30:00+05:00 | 2026-11-12T03:30:00Z",
            "2026-11-12T08:30:00       | 2026-11-12T08:30:00Z",
            "2026-11-12                | 2026-11-12T00:00:00Z",
    })
    void aPostingTimeIsReadInEveryShapeTheTrackerWritesIt(String raw, String expected) {
        assertThat(TrackerRowMapper.instant(raw)).isEqualTo(java.time.Instant.parse(expected));
    }

    @Test
    void anUnreadablePostingTimeOrUnsafeLinkIsDroppedNotStored() {
        var d = TrackerRowMapper.details(row2x("Review", "ready", "yesterday", "javascript:alert(1)"));

        assertThat(d.postedAt()).isNull();
        assertThat(d.applyUrl()).isNull();
    }

    @Test
    void anOlderRowHasNoDetailsAtAll() {
        assertThat(TrackerRowMapper.details(row("Applied", ""))).isEqualTo(
                com.saryom.jobservice.domain.Application.Details.NONE);
    }
}
