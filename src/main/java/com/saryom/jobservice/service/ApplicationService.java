package com.saryom.jobservice.service;

import com.saryom.jobservice.domain.Application;
import com.saryom.jobservice.domain.Application.Contact;
import com.saryom.jobservice.domain.Application.Posting;
import com.saryom.jobservice.domain.ApplicationRepository;
import com.saryom.jobservice.domain.ApplicationStatus;
import com.saryom.jobservice.error.NotFoundException;
import com.saryom.jobservice.web.dto.ApplicationRequest;
import com.saryom.jobservice.web.dto.ApplicationResponse;
import com.saryom.jobservice.web.dto.ApplicationSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A user's own job applications. Nothing here is visible to anyone else. */
@Service
public class ApplicationService {

    private final ApplicationRepository applications;
    private final RecruiterService recruiters;
    private final Clock clock;

    public ApplicationService(ApplicationRepository applications, RecruiterService recruiters,
                              Clock clock) {
        this.applications = applications;
        this.recruiters = recruiters;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> list(String uid) {
        LocalDate today = LocalDate.now(clock);
        return applications.findByUserIdOrderByUpdatedAtDesc(uid).stream()
                .map(a -> ApplicationResponse.from(a, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public ApplicationSummaryResponse summary(String uid) {
        LocalDate today = LocalDate.now(clock);
        Map<ApplicationStatus, Long> byStatus = new EnumMap<>(ApplicationStatus.class);
        long total = 0;
        long open = 0;
        long due = 0;
        for (Application a : applications.findByUserIdOrderByUpdatedAtDesc(uid)) {
            byStatus.merge(a.getStatus(), 1L, Long::sum);
            total++;
            if (a.getStatus().isOpen()) {
                open++;
                if (a.getFollowUpOn() != null && !a.getFollowUpOn().isAfter(today)) {
                    due++;
                }
            }
        }
        return new ApplicationSummaryResponse(total, open, due, byStatus);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(UUID id, String uid) {
        return ApplicationResponse.from(owned(id, uid), LocalDate.now(clock));
    }

    @Transactional
    public ApplicationResponse create(String uid, ApplicationRequest request) {
        var now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        Application application = new Application(UUID.randomUUID(), uid, null,
                request.company().trim(), request.role().trim(), ApplicationStatus.SAVED, now);
        apply(application, request, uid, today);
        application.setDates(today, request.appliedOn(), request.followUpOn(), now);
        application.moveTo(request.status(), today, now);
        return ApplicationResponse.from(applications.save(application), today);
    }

    @Transactional
    public ApplicationResponse update(UUID id, String uid, ApplicationRequest request) {
        var now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        Application application = owned(id, uid);
        apply(application, request, uid, today);
        application.setDates(application.getFoundOn(), request.appliedOn(), request.followUpOn(), now);
        application.moveTo(request.status(), today, now);
        return ApplicationResponse.from(applications.save(application), today);
    }

    @Transactional
    public ApplicationResponse move(UUID id, String uid, ApplicationStatus status) {
        LocalDate today = LocalDate.now(clock);
        Application application = owned(id, uid);
        application.moveTo(status, today, clock.instant());
        return ApplicationResponse.from(applications.save(application), today);
    }

    @Transactional
    public void delete(UUID id, String uid) {
        applications.delete(owned(id, uid));
    }

    private void apply(Application application, ApplicationRequest request, String uid, LocalDate today) {
        var now = clock.instant();
        // A hand-edited row keeps whatever fit and notes an upload gave it: the
        // form has no fields for them, so it must not blank them.
        Posting before = application.posting();
        application.describe(new Posting(request.company().trim(), Text.clean(request.vendor()),
                request.role().trim(), Text.clean(request.location()), request.workType(),
                Text.clean(request.employmentType()), Text.clean(request.pay()),
                Text.clean(request.source()), Text.clean(request.link()), before.fit(),
                before.notes()), now);
        application.setUserNote(Text.clean(request.userNote()), now);
        Contact contact = new Contact(Text.clean(request.contactName()), Text.clean(request.contactTitle()),
                Text.clean(request.contactEmail()), Text.clean(request.contactPhone()),
                Text.clean(request.contactUrl()));
        application.setContact(contact, now);
        if (request.sharesContact() && application.hasContact()) {
            recruiters.share(uid, contact, application.getRole(),
                    firmOf(application.getVendor(), application.getCompany()),
                    application.getLocation(), today);
        }
    }

    /** A recruiter works for the staffing firm when there is one, not for its client. */
    static String firmOf(String vendor, String company) {
        return vendor != null && !vendor.isBlank() ? vendor : company;
    }

    private Application owned(UUID id, String uid) {
        // Someone else's id reads as "not found", not "forbidden": whether a
        // given application exists is itself private.
        return applications.findByIdAndUserId(id, uid)
                .orElseThrow(() -> new NotFoundException("No application " + id));
    }
}
