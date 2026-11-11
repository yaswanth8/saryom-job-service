package com.saryom.jobservice.web.dto;

import com.saryom.jobservice.domain.RoleCategory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A directory entry.
 *
 * <p>Who a recruiter is and what they hire for is shown to anyone. How to reach
 * them is shown to signed-in users only: email, phone and profile link are null
 * and {@code contactLocked} is true for anonymous requests. Open contact details
 * on a public page are harvested by scrapers within days, and these are other
 * people's details.
 */
public record RecruiterResponse(
        UUID id,
        String fullName,
        String title,
        String company,
        String email,
        String phone,
        String profileUrl,
        boolean contactLocked,
        List<Role> roles,
        LocalDate lastSeenOn,
        /** How many different users reported this recruiter. */
        int seenBy,
        /** True when the viewer added this entry and may remove it. */
        boolean mine) {

    public record Role(String title, RoleCategory category, String categoryLabel) {
    }
}
