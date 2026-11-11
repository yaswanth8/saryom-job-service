package com.saryom.jobservice.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local/test token verifier active under the {@code dev} profile. Accepts tokens
 * shaped {@code dev:<uid>} (regular user) or {@code dev:<uid>:admin} (admin
 * role) and derives a deterministic identity from the uid.
 */
@Component
@Profile("dev")
public class StubTokenVerifier implements TokenVerifier {

    @Override
    public VerifiedUser verify(String idToken) throws InvalidTokenException {
        if (idToken == null || !idToken.startsWith("dev:")) {
            throw new InvalidTokenException("Dev token must be shaped 'dev:<uid>'");
        }
        String uid = idToken.substring("dev:".length()).trim();
        String role = null;
        if (uid.endsWith(":admin")) {
            uid = uid.substring(0, uid.length() - ":admin".length()).trim();
            role = VerifiedUser.ADMIN_ROLE;
        }
        if (uid.isEmpty()) {
            throw new InvalidTokenException("Dev token has empty uid");
        }
        return new VerifiedUser(uid, uid + "@dev.local", uid, role);
    }
}
