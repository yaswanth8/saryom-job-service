package com.saryom.jobservice.auth;

import com.saryom.jobservice.service.ImportTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authenticates {@code Authorization: Bearer sjt_…}: an import token the user
 * created on the site and gave to the job-search skill.
 *
 * <p>The gateway cannot verify these (they are not Firebase tokens), so it lets
 * the one upload route through unauthenticated and this filter decides. An
 * unknown or revoked token is a 401 here rather than an anonymous request, so
 * an automation holding a stale token is told so instead of silently uploading
 * nothing.
 */
@Component
public class ImportTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String TOKEN_PREFIX = "sjt_";
    private static final String BEARER = "Bearer ";

    private final ImportTokenService tokens;

    public ImportTokenAuthenticationFilter(ImportTokenService tokens) {
        this.tokens = tokens;
    }

    /** True when the header carries an import token rather than a Firebase ID token. */
    public static boolean isImportToken(String authorizationHeader) {
        return authorizationHeader != null
                && authorizationHeader.startsWith(BEARER)
                && authorizationHeader.substring(BEARER.length()).trim().startsWith(TOKEN_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!isImportToken(header)) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = header.substring(BEARER.length()).trim();
        var resolved = tokens.resolve(token);
        if (resolved.isEmpty()) {
            SecurityContextHolder.clearContext();
            ProblemJson.write(response, HttpStatus.UNAUTHORIZED, "Unauthorized",
                    "This import token is not valid. It may have been revoked; create a new one on saryom.com.");
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(resolved.get());
        filterChain.doFilter(request, response);
    }
}
