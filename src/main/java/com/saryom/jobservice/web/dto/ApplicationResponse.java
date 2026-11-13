package com.saryom.jobservice.web.dto;

import com.saryom.jobservice.domain.Application;
import com.saryom.jobservice.domain.ApplicationStatus;
import com.saryom.jobservice.domain.Fit;
import com.saryom.jobservice.domain.FoundVia;
import com.saryom.jobservice.domain.NextStep;
import com.saryom.jobservice.domain.Sponsorship;
import com.saryom.jobservice.domain.WorkType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        String externalId,
        String company,
        String vendor,
        String role,
        String location,
        WorkType workType,
        String employmentType,
        String pay,
        String source,
        String link,
        Fit fit,
        ApplicationStatus status,
        String notes,
        String userNote,
        String contactName,
        String contactTitle,
        String contactEmail,
        String contactPhone,
        String contactUrl,
        LocalDate foundOn,
        LocalDate appliedOn,
        LocalDate followUpOn,
        /** True when it is time to chase this application: see {@link Application#followUpDue}. */
        boolean followUpDue,
        String appliedBy,
        /** The posting's text, as the job-search skill saved it. */
        String description,
        Instant postedAt,
        /** True for Easy Apply, false for the company's own site, null when not known. */
        Boolean easyApply,
        String applyUrl,
        String ats,
        Sponsorship sponsorship,
        FoundVia foundVia,
        /** Why there is no contact, when there is none. */
        String contactNote,
        /** What a saved job is waiting for; null once applied. */
        NextStep nextStep,
        Instant createdAt,
        Instant updatedAt) {

    public static ApplicationResponse from(Application a, LocalDate today) {
        var posting = a.posting();
        var contact = a.contact();
        boolean due = a.followUpDue(today);
        var d = a.details();
        return new ApplicationResponse(a.getId(), a.getExternalId(), posting.company(), posting.vendor(),
                posting.role(), posting.location(), posting.workType(), posting.employmentType(),
                posting.pay(), posting.source(), posting.link(), posting.fit(), a.getStatus(),
                posting.notes(), a.getUserNote(), contact.name(), contact.title(), contact.email(),
                contact.phone(), contact.url(), a.getFoundOn(), a.getAppliedOn(), a.getFollowUpOn(),
                due, a.getAppliedBy(), d.description(), d.postedAt(), d.easyApply(), d.applyUrl(), d.ats(),
                d.sponsorship(), d.foundVia(), d.contactNote(),
                a.getStatus() == ApplicationStatus.SAVED ? d.nextStep() : null,
                a.getCreatedAt(), a.getUpdatedAt());
    }
}
