package com.saryom.jobservice.web.dto;

import com.saryom.jobservice.domain.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record MoveRequest(@NotNull ApplicationStatus status) {
}
