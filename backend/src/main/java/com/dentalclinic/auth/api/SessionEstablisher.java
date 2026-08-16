package com.dentalclinic.auth.api;

import com.dentalclinic.auth.config.SessionHardCapFilter;
import com.dentalclinic.auth.role.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Establishes the authenticated session after MFA verification succeeds (MfaController, T045) — the
 * principal name is the account's id (matching {@code Session.account_id} in data-model.md;
 * StaffAccountRepository is the join back to email/role), and the single granted authority is
 * {@code ROLE_<role>}, which is what every {@code @PreAuthorize("hasRole(...)")} check (T047)
 * evaluates against.
 */
@Component
public class SessionEstablisher {

  private final SecurityContextRepository securityContextRepository;

  public SessionEstablisher(SecurityContextRepository securityContextRepository) {
    this.securityContextRepository = securityContextRepository;
  }

  public void establish(
      HttpServletRequest request, HttpServletResponse response, UUID accountId, Role role) {
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            accountId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);

    // FR-012's 8-hour hard cap (SessionHardCapFilter) is measured from this moment, not session
    // creation, so a session that only later completes MFA doesn't start its clock early.
    HttpSession session = request.getSession(true);
    session.setAttribute(SessionHardCapFilter.AUTHENTICATED_AT_ATTRIBUTE, Instant.now());
  }
}
