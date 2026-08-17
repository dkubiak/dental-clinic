package com.dentalclinic.auth.api;

import com.dentalclinic.auth.account.AccountAdminService;
import com.dentalclinic.auth.account.StaffAccount;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * T077/T077a — {@code GET/POST /accounts}, {@code PATCH /accounts/{id}}, {@code .../deactivate},
 * {@code .../reactivate}, {@code .../mfa-reset} per contracts/auth-api.yaml, ADMINISTRATOR-only
 * (FR-009). {@code Principal#getName()} is the caller's account id — see SessionEstablisher, which
 * sets it as the authentication's principal (a bare string, not a UserDetails) at MFA verification
 * time.
 */
@RestController
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class AccountController {

  private final AccountAdminService accountAdminService;

  public AccountController(AccountAdminService accountAdminService) {
    this.accountAdminService = accountAdminService;
  }

  @GetMapping("/accounts")
  public ResponseEntity<List<AccountResponse>> list() {
    return ResponseEntity.ok(
        accountAdminService.listAll().stream().map(AccountResponse::from).toList());
  }

  @PostMapping("/accounts")
  public ResponseEntity<AccountResponse> create(
      @Valid @RequestBody CreateAccountRequest request, Principal principal) {
    StaffAccount account =
        accountAdminService.create(request.email(), request.role(), actorId(principal));
    return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(account));
  }

  @PatchMapping("/accounts/{id}")
  public ResponseEntity<AccountResponse> changeRole(
      @PathVariable UUID id, @Valid @RequestBody ChangeRoleRequest request, Principal principal) {
    StaffAccount account = accountAdminService.changeRole(id, request.role(), actorId(principal));
    return ResponseEntity.ok(AccountResponse.from(account));
  }

  @PostMapping("/accounts/{id}/deactivate")
  public ResponseEntity<Void> deactivate(@PathVariable UUID id, Principal principal) {
    accountAdminService.deactivate(id, actorId(principal));
    return ResponseEntity.ok().build();
  }

  @PostMapping("/accounts/{id}/reactivate")
  public ResponseEntity<Void> reactivate(@PathVariable UUID id, Principal principal) {
    accountAdminService.reactivate(id, actorId(principal));
    return ResponseEntity.ok().build();
  }

  @PostMapping("/accounts/{id}/mfa-reset")
  public ResponseEntity<Void> resetMfa(@PathVariable UUID id, Principal principal) {
    accountAdminService.resetMfa(id, actorId(principal));
    return ResponseEntity.ok().build();
  }

  private static UUID actorId(Principal principal) {
    return UUID.fromString(principal.getName());
  }
}
