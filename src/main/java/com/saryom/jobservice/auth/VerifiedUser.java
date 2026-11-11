package com.saryom.jobservice.auth;

/**
 * Identity extracted from a verified Firebase ID token. {@code role} comes from
 * the Firebase custom claim {@code role} ("admin" or null for regular users).
 */
public record VerifiedUser(String uid, String email, String displayName, String role) {

    public static final String ADMIN_ROLE = "admin";

    /** Regular user (no elevated role). */
    public VerifiedUser(String uid, String email, String displayName) {
        this(uid, email, displayName, null);
    }

    public boolean isAdmin() {
        return ADMIN_ROLE.equals(role);
    }
}
