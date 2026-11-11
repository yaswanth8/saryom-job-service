package com.saryom.jobservice.web.dto;

/**
 * What an upload did, in numbers the uploader can report back to the user.
 *
 * @param ignored    rows the tracker marks Skipped: jobs the user never wanted
 * @param invalid    rows with no id, company or role, which cannot be tracked
 * @param recruitersShared contacts added to, or confirmed in, the shared directory
 */
public record ImportResponse(int received, int created, int updated, int ignored, int invalid,
                             int recruitersShared) {
}
