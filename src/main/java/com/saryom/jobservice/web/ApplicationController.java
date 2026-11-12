package com.saryom.jobservice.web;

import com.saryom.jobservice.auth.CurrentUser;
import com.saryom.jobservice.service.ApplicationService;
import com.saryom.jobservice.web.dto.ApplicationRequest;
import com.saryom.jobservice.web.dto.ApplicationResponse;
import com.saryom.jobservice.web.dto.ApplicationSummaryResponse;
import com.saryom.jobservice.web.dto.FollowUpRequest;
import com.saryom.jobservice.web.dto.MoveRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs/applications")
@Tag(name = "Applications", description = "The signed-in user's own job applications. Private.")
public class ApplicationController {

    private final ApplicationService applications;

    public ApplicationController(ApplicationService applications) {
        this.applications = applications;
    }

    @GetMapping
    public List<ApplicationResponse> list() {
        return applications.list(CurrentUser.requireUid());
    }

    /** Literal path, so it wins over {@code /{id}} rather than being read as an id. */
    @GetMapping("/summary")
    public ApplicationSummaryResponse summary() {
        return applications.summary(CurrentUser.requireUid());
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable UUID id) {
        return applications.get(id, CurrentUser.requireUid());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse create(@Valid @RequestBody ApplicationRequest request) {
        return applications.create(CurrentUser.requireUid(), request);
    }

    @PutMapping("/{id}")
    public ApplicationResponse update(@PathVariable UUID id, @Valid @RequestBody ApplicationRequest request) {
        return applications.update(id, CurrentUser.requireUid(), request);
    }

    /** Moving a card along the pipeline, without resending the whole form. */
    @PatchMapping("/{id}/status")
    public ApplicationResponse move(@PathVariable UUID id, @Valid @RequestBody MoveRequest request) {
        return applications.move(id, CurrentUser.requireUid(), request.status());
    }

    /** Setting or clearing the next follow-up date, e.g. after chasing a recruiter. */
    @PatchMapping("/{id}/follow-up")
    public ApplicationResponse followUp(@PathVariable UUID id, @RequestBody FollowUpRequest request) {
        return applications.scheduleFollowUp(id, CurrentUser.requireUid(), request.followUpOn());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        applications.delete(id, CurrentUser.requireUid());
    }
}
