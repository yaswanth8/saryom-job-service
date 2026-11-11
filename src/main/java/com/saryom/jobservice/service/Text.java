package com.saryom.jobservice.service;

/** Tidying of free text arriving from forms and uploads. */
final class Text {

    private Text() {
    }

    /** Trimmed, or null when there is nothing there. */
    static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** As {@link #clean}, cut to {@code max} characters so one field cannot carry a novel. */
    static String clean(String value, int max) {
        String cleaned = clean(value);
        return cleaned != null && cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }
}
