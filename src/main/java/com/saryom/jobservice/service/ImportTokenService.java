package com.saryom.jobservice.service;

import com.saryom.jobservice.auth.ImportTokenAuthentication;
import com.saryom.jobservice.auth.ImportTokenAuthenticationFilter;
import com.saryom.jobservice.domain.ImportToken;
import com.saryom.jobservice.domain.ImportTokenRepository;
import com.saryom.jobservice.error.ConflictException;
import com.saryom.jobservice.error.NotFoundException;
import com.saryom.jobservice.rls.RequestIdentity;
import com.saryom.jobservice.web.dto.TokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Import tokens: how the job-search skill, running in a user's own Claude,
 * uploads to that user's tracker here.
 *
 * <p>A token is 256 random bits, shown once and stored only as a SHA-256 hash.
 * A fast hash is the right one for this: unlike a password there is nothing to
 * guess, and the lookup has to be an indexed equality match.
 */
@Service
public class ImportTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DEFAULT_LABEL = "Job search skill";

    private final ImportTokenRepository tokens;
    private final Clock clock;
    private final int maxPerUser;

    public ImportTokenService(ImportTokenRepository tokens, Clock clock,
                              @Value("${saryom.jobs.tokens.max-per-user:5}") int maxPerUser) {
        this.tokens = tokens;
        this.clock = clock;
        this.maxPerUser = maxPerUser;
    }

    @Transactional
    public TokenResponse create(String uid, String label) {
        if (tokens.countByUserId(uid) >= maxPerUser) {
            throw new ConflictException("You already have " + maxPerUser
                    + " import tokens. Revoke one you no longer use first.");
        }
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String plaintext = ImportTokenAuthenticationFilter.TOKEN_PREFIX
                + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        String name = Text.clean(label, 80);
        ImportToken saved = tokens.save(new ImportToken(UUID.randomUUID(), uid,
                name == null ? DEFAULT_LABEL : name, hash(plaintext), clock.instant()));
        return TokenResponse.created(saved, plaintext);
    }

    @Transactional(readOnly = true)
    public List<TokenResponse> list(String uid) {
        return tokens.findByUserIdOrderByCreatedAtDesc(uid).stream().map(TokenResponse::listed).toList();
    }

    @Transactional
    public void revoke(UUID id, String uid) {
        tokens.delete(tokens.findByIdAndUserId(id, uid)
                .orElseThrow(() -> new NotFoundException("No import token " + id)));
    }

    /**
     * Who a presented token belongs to, if anyone.
     *
     * <p>Runs before the request has an identity, so the hash is presented to
     * the database for the duration of the lookup: the token table's policy
     * shows a row to whoever holds its hash, and to no one else.
     */
    public Optional<ImportTokenAuthentication> resolve(String plaintext) {
        String hash = hash(plaintext);
        return RequestIdentity.presentingToken(hash, () -> tokens.findByTokenHash(hash))
                .map(t -> new ImportTokenAuthentication(t.getUserId(), t.getId()));
    }

    @Transactional
    public void markUsed(UUID id, String uid) {
        tokens.findByIdAndUserId(id, uid).ifPresent(t -> {
            t.markUsed(clock.instant());
            tokens.save(t);
        });
    }

    static String hash(String plaintext) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every JVM", e);
        }
    }
}
