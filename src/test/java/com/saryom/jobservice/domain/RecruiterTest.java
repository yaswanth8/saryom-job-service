package com.saryom.jobservice.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecruiterTest {

    @Test
    void theSameEmailIsTheSamePersonHoweverItIsTyped() {
        assertThat(Recruiter.dedupeKey("Priya Shah", "Acme", " Priya@Acme.com ", null))
                .isEqualTo(Recruiter.dedupeKey("P. Shah", "Acme Staffing", "priya@acme.com", "https://x"));
    }

    @Test
    void aProfileLinkMatchesAcrossSchemeTrackingAndTrailingSlash() {
        assertThat(Recruiter.dedupeKey("Sam", null, null, "https://www.linkedin.com/in/sam-lee/?trk=feed"))
                .isEqualTo(Recruiter.dedupeKey("Sam Lee", "Globex", null, "http://linkedin.com/in/sam-lee"));
    }

    @Test
    void aBareNameOnlyMatchesAtTheSameCompany() {
        // "Priya S." at two firms is two people.
        assertThat(Recruiter.dedupeKey("Priya S.", "Acme", null, null))
                .isNotEqualTo(Recruiter.dedupeKey("Priya S.", "Globex", null, null));
        assertThat(Recruiter.dedupeKey("Priya  S.", "ACME", null, null))
                .isEqualTo(Recruiter.dedupeKey("priya s.", "Acme", null, null));
    }

    @Test
    void fillingGapsNeverOverwritesWhatIsAlreadyKnown() {
        Instant now = Instant.parse("2026-11-10T00:00:00Z");
        Recruiter r = new Recruiter(UUID.randomUUID(), "Priya Shah", "Recruiter", null,
                "priya@acme.com", null, null, "alice", now);

        boolean changed = r.fillGaps("Senior Recruiter", "Acme", "555-0100", null, now);

        assertThat(changed).isTrue();
        assertThat(r.getTitle()).isEqualTo("Recruiter");
        assertThat(r.getCompany()).isEqualTo("Acme");
        assertThat(r.getPhone()).isEqualTo("555-0100");
        assertThat(r.fillGaps("x", "y", "z", null, now)).isFalse();
    }
}
