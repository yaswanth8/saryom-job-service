package com.saryom.jobservice.domain;

import java.util.Locale;

/** The orders the recruiter directory can be listed in. */
public enum RecruiterSort {
    /** Most recently seen hiring. */
    RECENT,
    /** Reported by the most job seekers. */
    REPORTED,
    /** Alphabetical by name. */
    NAME,
    /** By firm, then name. */
    COMPANY,
    /** Newest entries in the directory. */
    NEWEST;

    /** Lenient: an unknown or missing value is the default rather than an error. */
    public static RecruiterSort of(String value) {
        if (value == null || value.isBlank()) {
            return RECENT;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return RECENT;
        }
    }
}
