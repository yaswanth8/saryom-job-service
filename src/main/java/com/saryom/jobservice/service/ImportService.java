package com.saryom.jobservice.service;

import com.saryom.jobservice.domain.Application;
import com.saryom.jobservice.domain.Application.Contact;
import com.saryom.jobservice.domain.ApplicationRepository;
import com.saryom.jobservice.domain.ApplicationStatus;
import com.saryom.jobservice.web.dto.ImportRequest;
import com.saryom.jobservice.web.dto.ImportResponse;
import com.saryom.jobservice.web.dto.TrackerRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Takes a whole tracker from the job-search skill and brings the user's
 * applications here in line with it.
 *
 * <p>Uploading the same tracker twice changes nothing the second time: rows are
 * matched on the tracker's own id, so a re-run updates what it created.
 */
@Service
public class ImportService {

    private final ApplicationRepository applications;
    private final RecruiterService recruiters;
    private final ImportTokenService tokens;
    private final Clock clock;
    private final int maxRows;

    public ImportService(ApplicationRepository applications, RecruiterService recruiters,
                         ImportTokenService tokens, Clock clock,
                         @Value("${saryom.jobs.import.max-rows:1000}") int maxRows) {
        this.applications = applications;
        this.recruiters = recruiters;
        this.tokens = tokens;
        this.clock = clock;
        this.maxRows = maxRows;
    }

    /** @param tokenId the import token used, or null when the user uploaded while signed in */
    @Transactional
    public ImportResponse upload(String uid, UUID tokenId, ImportRequest request) {
        if (request.jobs().size() > maxRows) {
            throw new IllegalArgumentException(
                    "An upload can hold at most " + maxRows + " jobs; send the rest in a second one");
        }
        var now = clock.instant();
        LocalDate today = LocalDate.now(clock);

        int ignored = 0;
        int invalid = 0;
        int shared = 0;
        // The last occurrence of an id wins, as it would if they arrived in turn.
        Map<String, TrackerRow> rows = new LinkedHashMap<>();
        for (TrackerRow row : request.jobs()) {
            if (row == null || !TrackerRowMapper.isTrackable(row)) {
                invalid++;
            } else if (TrackerRowMapper.isSkipped(row)) {
                ignored++;
                // The job stays off the board, but whoever posted it is still a
                // recruiter worth knowing about, unless the row was spam.
                if (request.sharesContacts() && TrackerRowMapper.hasShareableRecruiter(row)
                        && share(uid, row, today)) {
                    shared++;
                }
            } else {
                rows.put(row.id().trim(), row);
            }
        }

        Map<String, Application> existing = rows.isEmpty() ? Map.of()
                : applications.findByUserIdAndExternalIdIn(uid, rows.keySet()).stream()
                        .collect(Collectors.toMap(Application::getExternalId, Function.identity()));

        int created = 0;
        int updated = 0;
        for (var entry : rows.entrySet()) {
            TrackerRow row = entry.getValue();
            var posting = TrackerRowMapper.posting(row);
            Contact contact = TrackerRowMapper.contact(row);
            ApplicationStatus status = TrackerRowMapper.status(row);
            LocalDate foundOn = TrackerRowMapper.date(row.foundAt());
            LocalDate appliedOn = TrackerRowMapper.date(row.appliedAt());
            LocalDate followUpOn = TrackerRowMapper.date(row.followUpAt());

            Application application = existing.get(entry.getKey());
            if (application == null) {
                application = new Application(UUID.randomUUID(), uid, entry.getKey(),
                        posting.company(), posting.role(), ApplicationStatus.SAVED, now);
                application.setUserNote(TrackerRowMapper.value(row.userNote(), 4000), now);
                application.setDates(foundOn == null ? today : foundOn, appliedOn, followUpOn, now);
                application.setContact(contact, now);
                created++;
            } else {
                // What the user has since recorded here outranks a stale tracker:
                // dates they set stay unless the tracker has one, a contact is
                // only replaced by another contact, and their notes are theirs.
                application.setDates(
                        foundOn == null ? application.getFoundOn() : foundOn,
                        appliedOn == null ? application.getAppliedOn() : appliedOn,
                        followUpOn == null ? application.getFollowUpOn() : followUpOn, now);
                if (contact.name() != null) {
                    application.setContact(contact, now);
                }
                updated++;
            }
            application.describe(posting, now);
            application.setAppliedBy(TrackerRowMapper.value(row.appliedBy(), 40));
            application.advanceTo(status, today, now);
            applications.save(application);

            if (request.sharesContacts() && share(uid, row, today)) {
                shared++;
            }
        }

        if (tokenId != null) {
            tokens.markUsed(tokenId, uid);
        }
        return new ImportResponse(request.jobs().size(), created, updated, ignored, invalid, shared);
    }

    /** Adds the row's contact, if it has one, to the shared directory. */
    private boolean share(String uid, TrackerRow row, LocalDate today) {
        Contact contact = TrackerRowMapper.contact(row);
        if (contact.name() == null) {
            return false;
        }
        var posting = TrackerRowMapper.posting(row);
        LocalDate foundOn = TrackerRowMapper.date(row.foundAt());
        return recruiters.share(uid, contact, posting.role(),
                ApplicationService.firmOf(posting.vendor(), posting.company()),
                posting.location(), foundOn == null ? today : foundOn) != null;
    }
}
