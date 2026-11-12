package com.saryom.jobservice.web.dto;

import java.time.LocalDate;

/** The next follow-up date, or null to clear it. */
public record FollowUpRequest(LocalDate followUpOn) {
}
