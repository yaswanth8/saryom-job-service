package com.saryom.jobservice.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationTest {

    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    private static Application applied(LocalDate on) {
        Application a = new Application(UUID.randomUUID(), "alice", null, "Acme", "Java Developer",
                ApplicationStatus.SAVED, NOW);
        a.setDates(on, on, null, NOW);
        a.moveTo(ApplicationStatus.APPLIED, on, NOW);
        return a;
    }

    // Regression: only a typed-in date made a follow-up due, and almost no
    // application has one, so the reminder stayed silent.
    @Test
    void aWeekAtAppliedWithNoReplyIsDueWithoutADate() {
        assertThat(applied(TODAY.minusDays(7)).followUpDue(TODAY)).isTrue();
        assertThat(applied(TODAY.minusDays(6)).followUpDue(TODAY)).isFalse();
    }

    @Test
    void aDateWhenSetDecidesEitherWay() {
        Application old = applied(TODAY.minusDays(20));
        old.scheduleFollowUp(TODAY.plusDays(7), NOW);
        assertThat(old.followUpDue(TODAY)).isFalse();

        Application fresh = applied(TODAY.minusDays(1));
        fresh.scheduleFollowUp(TODAY, NOW);
        assertThat(fresh.followUpDue(TODAY)).isTrue();
    }

    @Test
    void nothingIsDueOnceAReplyHasComeOrItIsClosed() {
        Application interviewing = applied(TODAY.minusDays(30));
        interviewing.moveTo(ApplicationStatus.INTERVIEW, TODAY, NOW);
        assertThat(interviewing.followUpDue(TODAY)).isFalse();

        Application rejected = applied(TODAY.minusDays(30));
        rejected.scheduleFollowUp(TODAY.minusDays(1), NOW);
        rejected.moveTo(ApplicationStatus.REJECTED, TODAY, NOW);
        assertThat(rejected.followUpDue(TODAY)).isFalse();
    }

    @Test
    void clearingTheDateFallsBackToTheWeekRule() {
        Application a = applied(TODAY.minusDays(10));
        a.scheduleFollowUp(TODAY.plusDays(3), NOW);
        a.scheduleFollowUp(null, NOW);
        assertThat(a.followUpDue(TODAY)).isTrue();
    }
}
