package com.dentalclinic.auth.api;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * T028 (research.md #8; contracts/rbac-policy.md rule 2). {@code @PreAuthorize} denials (T047)
 * throw {@link AccessDeniedException} from inside the controller call — Spring MVC's {@code
 * HandlerExceptionResolver} chain resolves it here BEFORE it would otherwise reach {@code
 * ExceptionTranslationFilter}, which is what makes intercepting it at this layer (rather than a
 * security-filter {@code AccessDeniedHandler}) work.
 *
 * <p>FR-005 requires that an out-of-role-scope denial never reveal whether the target resource
 * exists — so the response body below is deliberately generic and identical to what a genuinely
 * nonexistent resource returns; no endpoint in this service ever returns a bespoke 404 body that
 * could be distinguished from this one.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorBody> handleAccessDenied() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorBody("NOT_FOUND", "The requested resource was not found.", Instant.now()));
  }

  public record ErrorBody(String code, String message, Instant timestamp) {}
}
