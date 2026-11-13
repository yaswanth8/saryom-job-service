package com.saryom.jobservice.domain;

/**
 * How well the job-search skill judged the posting to match the candidate.
 * {@code STRETCH} asks for more years than the candidate has, within the
 * ceiling they set: worth a look, but a reach.
 */
public enum Fit {
    HIGH, MEDIUM, STRETCH, LOW
}
