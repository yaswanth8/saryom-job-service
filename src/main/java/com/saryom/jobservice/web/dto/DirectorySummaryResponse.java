package com.saryom.jobservice.web.dto;

import com.saryom.jobservice.domain.RoleCategory;

import java.util.List;

/** The public, aggregated face of the directory: counts only, no contact details. */
public record DirectorySummaryResponse(
        long recruiters,
        long contributors,
        List<CategoryCount> categories,
        List<CompanyCount> topCompanies,
        /** How many can be reached each way: counts only, for the directory's filters. */
        long withEmail,
        long withPhone,
        long withProfile) {

    public record CategoryCount(RoleCategory category, String label, long recruiters) {
    }

    public record CompanyCount(String company, long recruiters) {
    }
}
