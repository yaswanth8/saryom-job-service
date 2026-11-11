package com.saryom.jobservice.rls;

import com.saryom.jobservice.auth.CurrentUser;

/**
 * Who a transaction runs as, in the terms the database's row level security
 * policies understand.
 *
 * @param uid       the user whose rows may be touched; null for anonymous
 * @param admin     moderators may edit or remove any directory entry
 * @param tokenHash set only while an import token is being looked up
 */
public record RequestIdentity(String uid, boolean admin, String tokenHash) {

    private static final ThreadLocal<String> PRESENTED_TOKEN_HASH = new ThreadLocal<>();

    /** The identity of the request on this thread, read from the security context. */
    public static RequestIdentity current() {
        return new RequestIdentity(CurrentUser.uploaderUid(), CurrentUser.isAdmin(),
                PRESENTED_TOKEN_HASH.get());
    }

    /**
     * Runs {@code lookup} with the given token hash presented to the database,
     * which is what lets it read the one matching token row before anyone is
     * signed in. Cleared afterwards, so nothing later on the thread inherits it.
     */
    public static <T> T presentingToken(String tokenHash, java.util.function.Supplier<T> lookup) {
        PRESENTED_TOKEN_HASH.set(tokenHash);
        try {
            return lookup.get();
        } finally {
            PRESENTED_TOKEN_HASH.remove();
        }
    }
}
