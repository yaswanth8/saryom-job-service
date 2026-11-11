package com.saryom.jobservice.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ImportRequest(
        @NotNull List<TrackerRow> jobs,
        /** Null means yes; see {@link ApplicationRequest#shareContact()}. */
        Boolean shareContacts) {

    public boolean sharesContacts() {
        return shareContacts == null || shareContacts;
    }
}
