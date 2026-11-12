package com.saryom.jobservice.web.dto;

import com.saryom.jobservice.domain.ApplicationStatus;
import com.saryom.jobservice.domain.RoleCategory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One recruiter, in full: everything the directory card shows, plus each role
 * they were seen hiring for with where and when, others at the same firm, and
 * the viewer's own applications that name them.
 *
 * <p>The same rule as the card: email, phone and profile link are null and
 * {@code contactLocked} is true for anonymous requests, and {@code applications}
 * is always empty for them. A viewer only ever sees their own applications.
 */
public record RecruiterProfileResponse(
        UUID id,
        String fullName,
        String title,
        String company,
        String email,
        String phone,
        String profileUrl,
        boolean contactLocked,
        boolean mine,
        /** How many different users reported this recruiter. */
        int seenBy,
        LocalDate lastSeenOn,
        /** When the recruiter was added to the directory. */
        LocalDate addedOn,
        List<Role> roles,
        List<Colleague> colleagues,
        List<LinkedApplication> applications) {

    /** A role as reported, merged across the people who reported it. */
    public record Role(String title, RoleCategory category, String categoryLabel, List<String> companies,
                       List<String> locations, LocalDate lastSeenOn, int seenBy) {
    }

    public record Colleague(UUID id, String fullName, String title) {
    }

    public record LinkedApplication(UUID id, String company, String role, ApplicationStatus status,
                                    LocalDate appliedOn) {
    }
}
