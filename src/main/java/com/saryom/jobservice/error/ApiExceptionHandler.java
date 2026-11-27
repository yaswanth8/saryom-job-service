package com.saryom.jobservice.error;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/** Translates domain and validation errors into RFC 7807 {@link ProblemDetail} responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not Found", e.getMessage());
    }

    @ExceptionHandler(UnauthorizedException.class)
    ProblemDetail handleUnauthorized(UnauthorizedException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException e) {
        return problem(HttpStatus.CONFLICT, "Conflict", e.getMessage());
    }

    /**
     * Two requests wrote the same thing at once: the same recruiter added from
     * two uploads, or one application edited in two tabs. Either way the caller
     * can simply try again, so it is a conflict rather than a server error.
     */
    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    ProblemDetail handleLostRace(Exception e) {
        return problem(HttpStatus.CONFLICT, "Conflict",
                "This was changed by another request at the same moment. Please try again.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleForbidden(AccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "Forbidden", "You do not have access to this resource");
    }

    /**
     * A page number so large that page times size passes Integer.MAX_VALUE, as in
     * {@code ?page=99999999}. Spring Data refuses to build that offset, and it used
     * to surface as a 500. It is the caller's mistake, so it is a 400. Any other
     * misuse of the data layer is still our bug and stays a 500.
     */
    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    ProblemDetail handlePageTooFar(InvalidDataAccessApiUsageException e) {
        String message = e.getMessage();
        if (message != null && message.startsWith("Page offset exceeds")) {
            return problem(HttpStatus.BAD_REQUEST, "Bad Request", "page is too large");
        }
        throw e;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation Failed",
                "One or more fields are invalid");
        Map<String, String> errors = new LinkedHashMap<>();
        for (var fieldError : e.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        problem.setProperty("errors", errors);
        return problem;
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
