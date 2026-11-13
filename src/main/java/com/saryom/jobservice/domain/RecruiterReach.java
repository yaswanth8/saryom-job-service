package com.saryom.jobservice.domain;

import java.util.Locale;

/** Narrows the directory to recruiters with one particular way to reach them. */
public enum RecruiterReach {
    ANY, EMAIL, PHONE, PROFILE;

    /** Lenient: an unknown or missing value means no narrowing. */
    public static RecruiterReach of(String value) {
        if (value == null || value.isBlank()) {
            return ANY;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ANY;
        }
    }

    /** As the directory query takes it. */
    public String param() {
        return name().toLowerCase(Locale.ROOT);
    }
}
