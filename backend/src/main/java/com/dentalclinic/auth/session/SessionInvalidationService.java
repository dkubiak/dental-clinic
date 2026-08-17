package com.dentalclinic.auth.session;

import java.util.UUID;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * T075/T075a — invalidates every active session belonging to a {@code StaffAccount}, used when an
 * account is deactivated (FR-010, Edge Cases in spec.md) or its role is changed (FR-007a), so the
 * change takes effect immediately rather than only after the old session naturally expires.
 *
 * <p>{@code PRINCIPAL_NAME} is the account id as a string (see SessionEstablisher/SecurityConfig),
 * which is what {@link FindByIndexNameSessionRepository#findByPrincipalName} indexes against
 * (Spring Session JDBC's {@code spring_session.principal_name} column, V3__session.sql).
 */
@Service
public class SessionInvalidationService {

  private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

  public SessionInvalidationService(
      FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
    this.sessionRepository = sessionRepository;
  }

  public void invalidateSessionsFor(UUID accountId) {
    sessionRepository
        .findByPrincipalName(accountId.toString())
        .keySet()
        .forEach(sessionRepository::deleteById);
  }
}
