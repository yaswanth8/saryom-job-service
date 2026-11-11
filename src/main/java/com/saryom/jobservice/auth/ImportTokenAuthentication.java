package com.saryom.jobservice.auth;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.UUID;

/**
 * A caller identified by an import token rather than a Firebase sign-in.
 *
 * <p>Deliberately its own type. A token is handed to an automation and is good
 * for one thing, uploading that user's job search, so {@link CurrentUser#uid()}
 * does not recognise it: every endpoint written against the signed-in user
 * rejects a token without having to remember to.
 */
public class ImportTokenAuthentication extends AbstractAuthenticationToken {

    private final String uid;
    private final UUID tokenId;

    public ImportTokenAuthentication(String uid, UUID tokenId) {
        super(List.of(new SimpleGrantedAuthority("SCOPE_IMPORT")));
        this.uid = uid;
        this.tokenId = tokenId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return uid;
    }

    public UUID tokenId() {
        return tokenId;
    }
}
