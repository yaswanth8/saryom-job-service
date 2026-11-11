package com.saryom.jobservice.web.dto;

import jakarta.validation.constraints.Size;

public record CreateTokenRequest(@Size(max = 80) String label) {
}
