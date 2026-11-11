package com.saryom.jobservice.domain;

/**
 * Where an application stands. One field rather than the tracker's separate
 * status and stage: a person thinks "I'm interviewing there", not "applied,
 * stage interview".
 */
public enum ApplicationStatus {
    /** Found and worth applying to, not applied yet. */
    SAVED(0),
    APPLIED(1),
    SCREENING(2),
    INTERVIEW(3),
    OFFER(4),
    REJECTED(5),
    WITHDRAWN(5),
    NO_RESPONSE(5),
    /** The user decided against applying. */
    DECLINED(5);

    private final int progress;

    ApplicationStatus(int progress) {
        this.progress = progress;
    }

    /**
     * True when moving to {@code next} would lose ground.
     *
     * <p>An upload carries the tracker's view, which lags whatever the user has
     * since recorded here: someone who marked an interview on the site must not
     * be set back to "applied" by the next upload.
     */
    public boolean isAheadOf(ApplicationStatus next) {
        return progress > next.progress;
    }

    public boolean isOpen() {
        return progress < 5;
    }
}
