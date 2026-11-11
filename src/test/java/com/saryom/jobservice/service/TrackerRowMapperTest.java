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

    static TrackerRow row(String status, String stage) {
        return new TrackerRow("li-1", "Acme", "Not listed", "Java Developer", "Austin, TX · On-site",
                "Not stated", "LinkedIn", "https://example.com/job/1", "notes", "On-site", "W2", "High",
                status, stage, "Priya Shah", "Recruiter", "priya@acme.com", "555-0100",
                "https://linkedin.com/in/priya", "2026-11-01", "2026-11-02", "", "claude", "");
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
                null, "Applied", null, "Sam", null, "see profile", null, null, null, null, null, null, null);

        assertThat(TrackerRowMapper.contact(broken).email()).isNull();
        assertThat(TrackerRowMapper.contact(broken).name()).isEqualTo("Sam");
    }

    @Test
    void aRowNeedsAnIdACompanyAndARoleToBeTracked() {
        assertThat(TrackerRowMapper.isTrackable(row("Applied", ""))).isTrue();
        assertThat(TrackerRowMapper.isTrackable(new TrackerRow(null, "Acme", null, "Dev", null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null))).isFalse();
        assertThat(TrackerRowMapper.isTrackable(new TrackerRow("li-3", "Not listed", null, "Dev", null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null))).isFalse();
    }
}
