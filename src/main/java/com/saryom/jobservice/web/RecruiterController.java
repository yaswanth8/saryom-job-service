package com.saryom.jobservice.web;

import com.saryom.jobservice.auth.CurrentUser;
import com.saryom.jobservice.domain.RecruiterSort;
import com.saryom.jobservice.domain.RoleCategory;
import com.saryom.jobservice.service.RecruiterService;
import com.saryom.jobservice.web.dto.DirectorySummaryResponse;
import com.saryom.jobservice.web.dto.RecruiterProfileResponse;
import com.saryom.jobservice.web.dto.RecruiterRequest;
import com.saryom.jobservice.web.dto.RecruiterResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/jobs/recruiters")
@Tag(name = "Recruiters", description = "The shared recruiter directory. Public to browse; "
        + "contact details are returned to signed-in users only.")
public class RecruiterController {

    private final RecruiterService recruiters;

    public RecruiterController(RecruiterService recruiters) {
        this.recruiters = recruiters;
    }

    @GetMapping
    public Page<RecruiterResponse> browse(@RequestParam(required = false) RoleCategory category,
                                          @RequestParam(required = false) String q,
                                          @RequestParam(required = false) String sort,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return recruiters.browse(category, q, RecruiterSort.of(sort), page, size, CurrentUser.uid());
    }

    /** Counts by kind of role and by company. No contact details, so safe to show anyone. */
    @GetMapping("/summary")
    public DirectorySummaryResponse summary() {
        return recruiters.summary();
    }

    /** One recruiter in full. Public, with contact details and the viewer's applications for signed-in users. */
    @GetMapping("/{id}")
    public RecruiterProfileResponse profile(@PathVariable UUID id) {
        return recruiters.profile(id, CurrentUser.uid());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RecruiterResponse contribute(@Valid @RequestBody RecruiterRequest request) {
        return recruiters.contribute(CurrentUser.requireUid(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id) {
        recruiters.remove(id, CurrentUser.requireUid(), CurrentUser.isAdmin());
    }
}
