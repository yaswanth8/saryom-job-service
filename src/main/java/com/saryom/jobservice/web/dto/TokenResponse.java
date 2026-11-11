package com.saryom.jobservice.web.dto;

import com.saryom.jobservice.domain.ImportToken;

import java.time.Instant;
import java.util.UUID;

/**
 * @param token the token itself, present only in the response that creates it.
 *              It is never stored and cannot be shown again.
 */
public record TokenResponse(UUID id, String label, Instant createdAt, Instant lastUsedAt, String token) {

    public static TokenResponse listed(ImportToken t) {
        return new TokenResponse(t.getId(), t.getLabel(), t.getCreatedAt(), t.getLastUsedAt(), null);
    }

    public static TokenResponse created(ImportToken t, String plaintext) {
        return new TokenResponse(t.getId(), t.getLabel(), t.getCreatedAt(), null, plaintext);
    }
}
