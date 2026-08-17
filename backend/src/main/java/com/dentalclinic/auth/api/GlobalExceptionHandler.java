package com.dentalclinic.auth.api;

import com.dentalclinic.auth.account.AccountDeactivatedException;
import com.dentalclinic.auth.account.AccountLockedException;
import com.dentalclinic.auth.account.InvalidCredentialsException;
import com.dentalclinic.auth.account.InvalidMfaCodeException;
import com.dentalclinic.auth.account.InvalidPreAuthTokenException;
import com.dentalclinic.auth.account.PasswordPolicyException;
import com.dentalclinic.auth.account.RateLimitExceededException;
import com.dentalclinic.auth.passwordreset.PasswordResetTokenInvalidException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

  @ExceptionHandler({
    InvalidCredentialsException.class,
    InvalidPreAuthTokenException.class,
    InvalidMfaCodeException.class
  })
  public ResponseEntity<ErrorBody> handleUnauthorized(RuntimeException e) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(new ErrorBody("UNAUTHORIZED", e.getMessage(), Instant.now()));
  }

  @ExceptionHandler(AccountLockedException.class)
  public ResponseEntity<ErrorBody> handleLocked(AccountLockedException e) {
    return ResponseEntity.status(HttpStatus.LOCKED)
        .body(new ErrorBody("ACCOUNT_LOCKED", e.getMessage(), Instant.now()));
  }

  @ExceptionHandler(AccountDeactivatedException.class)
  public ResponseEntity<ErrorBody> handleDeactivated(AccountDeactivatedException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ErrorBody("ACCOUNT_DEACTIVATED", e.getMessage(), Instant.now()));
  }

  @ExceptionHandler(RateLimitExceededException.class)
  public ResponseEntity<ErrorBody> handleRateLimited(RateLimitExceededException e) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .body(new ErrorBody("RATE_LIMITED", e.getMessage(), Instant.now()));
  }

  @ExceptionHandler(PasswordResetTokenInvalidException.class)
  public ResponseEntity<ErrorBody> handleResetTokenInvalid(PasswordResetTokenInvalidException e) {
    return ResponseEntity.status(HttpStatus.GONE)
        .body(new ErrorBody("RESET_TOKEN_INVALID", e.getMessage(), Instant.now()));
  }

  @ExceptionHandler(PasswordPolicyException.class)
  public ResponseEntity<ErrorBody> handlePasswordPolicy(PasswordPolicyException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorBody("PASSWORD_POLICY_VIOLATION", e.getMessage(), Instant.now()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorBody> handleValidation() {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorBody("VALIDATION_FAILED", "Request body failed validation.", Instant.now()));
  }

  public record ErrorBody(String code, String message, Instant timestamp) {}
}
