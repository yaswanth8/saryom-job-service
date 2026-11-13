package com.saryom.jobservice.domain;

/**
 * What a saved job is waiting for, from the job-search skill's "Needs you"
 * list. Only meaningful while the application is still saved.
 */
public enum NextStep {
    /** Passed every check in a search-and-save run: apply, or let Claude. */
    READY,
    /** A recruiter post: apply by email, phone or message. */
    REACH_OUT,
    /** Takes applications on the company's own site. */
    COMPANY_SITE,
    /** Something only the user can decide. */
    YOUR_CALL,
    /** Queued for Claude's next apply run. */
    QUEUED
}
