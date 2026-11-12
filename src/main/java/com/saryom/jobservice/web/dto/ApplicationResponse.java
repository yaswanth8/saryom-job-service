package com.saryom.jobservice.web.dto;

import com.saryom.jobservice.domain.Application;
import com.saryom.jobservice.domain.ApplicationStatus;
import com.saryom.jobservice.domain.Fit;
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
        Instant createdAt,
        Instant updatedAt) {

    public static ApplicationResponse from(Application a, LocalDate today) {
        var posting = a.posting();
        var contact = a.contact();
        boolean due = a.followUpDue(today);
        return new ApplicationResponse(a.getId(), a.getExternalId(), posting.company(), posting.vendor(),
                posting.role(), posting.location(), posting.workType(), posting.employmentType(),
                posting.pay(), posting.source(), posting.link(), posting.fit(), a.getStatus(),
                posting.notes(), a.getUserNote(), contact.name(), contact.title(), contact.email(),
                contact.phone(), contact.url(), a.getFoundOn(), a.getAppliedOn(), a.getFollowUpOn(),
                due, a.getAppliedBy(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
