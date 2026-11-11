package com.saryom.jobservice.web;

import com.saryom.jobservice.auth.CurrentUser;
import com.saryom.jobservice.service.ImportTokenService;
import com.saryom.jobservice.web.dto.CreateTokenRequest;
import com.saryom.jobservice.web.dto.TokenResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Import tokens are managed by the signed-in user only: a token cannot be used
 * to mint or list tokens, because {@code CurrentUser.requireUid()} does not
 * accept one.
 */
@RestController
@RequestMapping("/api/jobs/tokens")
@Tag(name = "Import tokens", description = "Tokens the job-search skill uses to upload on your behalf.")
public class TokenController {

    private final ImportTokenService tokens;

    public TokenController(ImportTokenService tokens) {
        this.tokens = tokens;
    }

    @GetMapping
    public List<TokenResponse> list() {
        return tokens.list(CurrentUser.requireUid());
    }

    /** The response carries the token itself, once. It cannot be retrieved afterwards. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse create(@Valid @RequestBody(required = false) CreateTokenRequest request) {
        return tokens.create(CurrentUser.requireUid(), request == null ? null : request.label());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID id) {
        tokens.revoke(id, CurrentUser.requireUid());
    }
}
