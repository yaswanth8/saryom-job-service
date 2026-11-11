package com.saryom.jobservice.web.dto;

import com.saryom.jobservice.domain.ApplicationStatus;

import java.util.Map;

/** The signed-in user's pipeline at a glance, for the home page tile. */
public record ApplicationSummaryResponse(
        long total,
        /** Still in play: saved through offer. */
        long open,
        long followUpsDue,
        Map<ApplicationStatus, Long> byStatus) {
}
