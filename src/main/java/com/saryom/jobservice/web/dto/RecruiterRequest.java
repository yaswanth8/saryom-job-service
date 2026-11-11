package com.saryom.jobservice.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Adding a recruiter to the shared directory by hand. */
public record RecruiterRequest(
        @NotBlank @Size(max = 200) String fullName,
        @Size(max = 200) String title,
        @Size(max = 200) String company,
        @Email @Size(max = 320) String email,
        @Size(max = 60) String phone,
        @Size(max = 2000) String profileUrl,
        /** What they were hiring for; this is what the directory is filtered by. */
        @NotBlank @Size(max = 200) String roleTitle,
        @Size(max = 200) String location) {
}
