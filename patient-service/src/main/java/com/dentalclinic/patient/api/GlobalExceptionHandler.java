package com.dentalclinic.patient.api;

import com.dentalclinic.patient.record.DuplicatePeselException;
import com.dentalclinic.patient.record.InvalidPeselException;
import com.dentalclinic.patient.record.PatientNotFoundException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Mirrors auth-service's own {@code GlobalExceptionHandler} (T028, rbac-policy.md rule 2): an
 * {@code @PreAuthorize} denial and a genuinely nonexistent resource return the exact same 404 body,
 * so a caller can never distinguish "not found" from "not allowed to know this exists".
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler({AccessDeniedException.class, PatientNotFoundException.class})
  public ResponseEntity<ErrorBody> handleNotFound() {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorBody("NOT_FOUND", "The requested resource was not found.", Instant.now()));
  }

  @ExceptionHandler({InvalidPeselException.class, MethodArgumentNotValidException.class})
  public ResponseEntity<ErrorBody> handleBadRequest(RuntimeException e) {
    String message =
        e instanceof InvalidPeselException
            ? "PESEL format or checksum is invalid."
            : "Request body failed validation.";
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorBody("VALIDATION_FAILED", message, Instant.now()));
  }

  @ExceptionHandler(DuplicatePeselException.class)
  public ResponseEntity<ErrorBody> handleDuplicatePesel() {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            new ErrorBody(
                "PESEL_ALREADY_EXISTS", "A record with this PESEL already exists.", Instant.now()));
  }

  public record ErrorBody(String code, String message, Instant timestamp) {}
}
