package com.dentalclinic.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

/**
 * Spring Session JDBC — shared session store across replicas (T027; research.md #5), backed by the
 * schema in V3__session.sql. Gives the 15-minute sliding idle timeout (FR-012) natively via {@code
 * maxInactiveIntervalInSeconds}; the additional 8-hour hard cap independent of activity (spec.md
 * Assumptions) is NOT something Spring Session supports natively (its own expiry is idle-based
 * only) — that is enforced by {@link SessionHardCapFilter}, wired into the security filter chain in
 * {@link SecurityConfig}.
 */
@Configuration
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 900) // 15 minutes, FR-012
public class SessionConfig {}
