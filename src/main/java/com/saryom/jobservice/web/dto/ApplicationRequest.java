package com.saryom.jobservice.web.dto;

import com.saryom.jobservice.domain.ApplicationStatus;
import com.saryom.jobservice.domain.WorkType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** The add/edit form for an application the user enters or corrects by hand. */
public record ApplicationRequest(
        @NotBlank @Size(max = 200) String company,
        @Size(max = 200) String vendor,
        @NotBlank @Size(max = 200) String role,
        @Size(max = 200) String location,
        WorkType workType,
        @Size(max = 60) String employmentType,
        @Size(max = 120) String pay,
        @Size(max = 60) String source,
        @Size(max = 2000) String link,
        @NotNull ApplicationStatus status,
        @Size(max = 4000) String userNote,
        @Size(max = 200) String contactName,
        @Size(max = 200) String contactTitle,
        @Email @Size(max = 320) String contactEmail,
        @Size(max = 60) String contactPhone,
        @Size(max = 2000) String contactUrl,
        LocalDate appliedOn,
        LocalDate followUpOn,
        /**
         * Whether the contact also goes into the shared recruiter directory.
         * Null means yes: the directory is only as useful as what people add.
         */
        Boolean shareContact) {

    public boolean sharesContact() {
        return shareContact == null || shareContact;
    }
}
