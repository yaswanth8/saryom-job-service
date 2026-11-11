package com.saryom.jobservice.service;

import com.saryom.jobservice.domain.Application;
import com.saryom.jobservice.domain.ApplicationRepository;
import com.saryom.jobservice.domain.ApplicationStatus;
import com.saryom.jobservice.domain.Recruiter;
import com.saryom.jobservice.web.dto.ImportRequest;
import com.saryom.jobservice.web.dto.ImportResponse;
import com.saryom.jobservice.web.dto.TrackerRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-10T12:00:00Z"), ZoneOffset.UTC);
    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final RecruiterService recruiters = mock(RecruiterService.class);
    private final ImportTokenService tokens = mock(ImportTokenService.class);
    private final ImportService service = new ImportService(applications, recruiters, tokens, clock, 3);

    @BeforeEach
    void setUp() {
        when(applications.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));
        when(applications.findByUserIdAndExternalIdIn(eq("alice"), anyCollection())).thenReturn(List.of());
        when(recruiters.share(any(), any(), any(), any(), any(), any())).thenReturn(mock(Recruiter.class));
    }

    private Application existing(String externalId, ApplicationStatus status) {
        Application a = new Application(UUID.randomUUID(), "alice", externalId, "Acme", "Java Developer",
                ApplicationStatus.SAVED, clock.instant());
        a.moveTo(status, LocalDate.now(clock), clock.instant());
        when(applications.findByUserIdAndExternalIdIn(eq("alice"), anyCollection())).thenReturn(List.of(a));
        return a;
    }

    private Application saved() {
        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(applications).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void aNewRowBecomesAnApplicationCarryingItsContact() {
        ImportResponse result = service.upload("alice", null,
                new ImportRequest(List.of(TrackerRowMapperTest.row("Applied", "Screening")), null));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.recruitersShared()).isEqualTo(1);
        Application a = saved();
        assertThat(a.getExternalId()).isEqualTo("li-1");
        assertThat(a.getStatus()).isEqualTo(ApplicationStatus.SCREENING);
        assertThat(a.getAppliedOn()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(a.contact().email()).isEqualTo("priya@acme.com");
    }

    @Test
    void anUploadNeverSetsAnApplicationBack() {
        // The user recorded an interview here; the tracker still says applied.
        Application a = existing("li-1", ApplicationStatus.INTERVIEW);

        ImportResponse result = service.upload("alice", null,
                new ImportRequest(List.of(TrackerRowMapperTest.row("Applied", "")), null));

        assertThat(result.updated()).isEqualTo(1);
        assertThat(a.getStatus()).isEqualTo(ApplicationStatus.INTERVIEW);
    }

    @Test
    void anUploadDoesMoveAnApplicationForward() {
        Application a = existing("li-1", ApplicationStatus.APPLIED);

        service.upload("alice", null,
                new ImportRequest(List.of(TrackerRowMapperTest.row("Applied", "Offer")), null));

        assertThat(a.getStatus()).isEqualTo(ApplicationStatus.OFFER);
    }

    @Test
    void theUsersOwnNoteSurvivesAnUpload() {
        Application a = existing("li-1", ApplicationStatus.APPLIED);
        a.setUserNote("Call back Thursday", clock.instant());

        service.upload("alice", null,
                new ImportRequest(List.of(TrackerRowMapperTest.row("Applied", "")), null));

        assertThat(a.getUserNote()).isEqualTo("Call back Thursday");
    }

    @Test
    void skippedAndUntrackableRowsAreCountedNotStored() {
        var skipped = TrackerRowMapperTest.row("Skipped", "");
        var noCompany = new TrackerRow("li-9", null, null, "Dev", null, null, null, null, null, null, null,
                null, "Applied", null, null, null, null, null, null, null, null, null, null, null, null);

        ImportResponse result = service.upload("alice", null,
                new ImportRequest(List.of(skipped, noCompany), null));

        assertThat(result.ignored()).isEqualTo(1);
        assertThat(result.invalid()).isEqualTo(1);
        assertThat(result.created()).isZero();
        verify(applications, never()).save(any());
    }

    // A job skipped for its location says nothing against whoever posted it.
    @Test
    void aSkippedJobStaysOffTheBoardButItsRecruiterIsShared() {
        ImportResponse result = service.upload("alice", null,
                new ImportRequest(List.of(TrackerRowMapperTest.row("Skipped", "", "Location")), null));

        assertThat(result.ignored()).isEqualTo(1);
        assertThat(result.created()).isZero();
        assertThat(result.recruitersShared()).isEqualTo(1);
        verify(applications, never()).save(any());
        verify(recruiters).share(eq("alice"), any(), eq("Java Developer"), eq("Acme"), any(), any());
    }

    @Test
    void aJobSkippedAsSpamSharesNobody() {
        ImportResponse result = service.upload("alice", null,
                new ImportRequest(List.of(TrackerRowMapperTest.row("Skipped", "", "Spam / duplicate")), null));

        assertThat(result.recruitersShared()).isZero();
        verify(recruiters, never()).share(any(), any(), any(), any(), any(), any());
    }

    @Test
    void aSkippedJobsRecruiterStaysPrivateWhenTheUploaderSaysSo() {
        ImportResponse result = service.upload("alice", null,
                new ImportRequest(List.of(TrackerRowMapperTest.row("Skipped", "", "Location")), false));

        assertThat(result.recruitersShared()).isZero();
        verify(recruiters, never()).share(any(), any(), any(), any(), any(), any());
    }

    @Test
    void contactsStayPrivateWhenTheUploaderSaysSo() {
        ImportResponse result = service.upload("alice", null,
                new ImportRequest(List.of(TrackerRowMapperTest.row("Applied", "")), false));

        assertThat(result.recruitersShared()).isZero();
        verify(recruiters, never()).share(any(), any(), any(), any(), any(), any());
        // Still kept on the user's own application.
        assertThat(saved().contact().name()).isEqualTo("Priya Shah");
    }

    @Test
    void aStaffingFirmsRecruiterIsFiledUnderTheFirmNotItsClient() {
        var viaVendor = new TrackerRow("li-4", "Big Bank", "TekStaff", "Java Developer", null, null, null,
                null, null, null, null, null, "Applied", null, "Sam Lee", null, "sam@tekstaff.com", null,
                null, null, null, null, null, null, null);

        service.upload("alice", null, new ImportRequest(List.of(viaVendor), null));

        verify(recruiters).share(eq("alice"), any(), eq("Java Developer"), eq("TekStaff"), any(), any());
    }

    @Test
    void anOversizedUploadIsRefusedWhole() {
        List<TrackerRow> four = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            four.add(TrackerRowMapperTest.row("Applied", ""));
        }

        assertThatThrownBy(() -> service.upload("alice", null, new ImportRequest(four, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(applications, never()).save(any());
    }

    @Test
    void usingATokenRecordsThatItWasUsed() {
        UUID tokenId = UUID.randomUUID();

        service.upload("alice", tokenId, new ImportRequest(List.of(), null));

        verify(tokens).markUsed(tokenId, "alice");
    }
}
