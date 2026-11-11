package com.saryom.jobservice.web;

import com.saryom.jobservice.auth.CurrentUser;
import com.saryom.jobservice.service.ImportService;
import com.saryom.jobservice.web.dto.ImportRequest;
import com.saryom.jobservice.web.dto.ImportResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs/import")
@Tag(name = "Import", description = "Upload a job-search skill tracker. Accepts a sign-in or an import token.")
public class ImportController {

    private final ImportService imports;

    public ImportController(ImportService imports) {
        this.imports = imports;
    }

    /**
     * The one endpoint an import token works on. Send the tracker's rows as
     * they are; uploading again updates rather than duplicates.
     */
    @PostMapping
    public ImportResponse upload(@Valid @RequestBody ImportRequest request) {
        return imports.upload(CurrentUser.requireUploaderUid(), CurrentUser.importTokenId(), request);
    }
}
