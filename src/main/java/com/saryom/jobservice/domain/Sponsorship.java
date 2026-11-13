package com.saryom.jobservice.domain;

/** What a posting says about visa sponsorship, as the job-search skill read it. */
public enum Sponsorship {
    /** The posting says it can sponsor. */
    VERIFIED,
    /** The posting says nothing either way. */
    NEEDS_CONFIRMATION,
    /** The posting rules sponsorship out. */
    EXCLUDED
}
