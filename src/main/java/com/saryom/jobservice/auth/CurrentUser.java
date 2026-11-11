package com.saryom.jobservice.auth;

import com.saryom.jobservice.error.UnauthorizedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Convenience accessor for the authenticated Firebase identity of the current request. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static String uid() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        // Anonymous requests carry an AnonymousAuthenticationToken with principal
        // "anonymousUser" — treat that as no authenticated user.
        // An import token identifies a user but is not a sign-in: it is good
        // for uploading only, so it does not count here. See uploaderUid().
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication instanceof ImportTokenAuthentication) {
            return null;
        }
        return authentication.getPrincipal() instanceof String uid ? uid : null;
    }

    /**
     * Whose data this request acts on: the signed-in user, or the owner of the
     * import token presented. Null for anonymous requests. Only the upload
     * endpoint and the row level security context should ask this; everything
     * else wants {@link #uid()}.
     */
    public static String uploaderUid() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof ImportTokenAuthentication token) {
            return (String) token.getPrincipal();
        }
        return uid();
    }

    /** The uploader's uid, or a 401 when neither a sign-in nor a valid import token is present. */
    public static String requireUploaderUid() {
        String uid = uploaderUid();
        if (uid == null) {
            throw new UnauthorizedException("Sign in, or send an import token, to upload jobs");
        }
        return uid;
    }

    /** The import token used for this request, or null when the caller signed in normally. */
    public static java.util.UUID importTokenId() {
        return SecurityContextHolder.getContext().getAuthentication() instanceof ImportTokenAuthentication token
                ? token.tokenId() : null;
    }

    /** The uid of the authenticated caller, or a 401 if the request is anonymous. */
    public static String requireUid() {
        String uid = uid();
        if (uid == null) {
            throw new UnauthorizedException("Authentication is required for this action");
        }
        return uid;
    }

    /** The full verified identity (uid, email, displayName) captured at authentication time. */
    public static VerifiedUser details() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof VerifiedUser user) {
            return user;
        }
        return null;
    }

    /** True when the authenticated caller carries the admin role claim. */
    public static boolean isAdmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getDetails() instanceof VerifiedUser user
                && user.isAdmin();
    }

    /** The admin caller's uid; non-admins get a 403 via AccessDeniedException. */
    public static String requireAdmin() {
        if (!isAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Administrator role required");
        }
        return requireUid();
    }
}
