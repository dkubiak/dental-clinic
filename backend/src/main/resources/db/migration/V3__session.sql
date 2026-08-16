-- Spring Session JDBC schema (research.md #5) — official schema shipped with
-- spring-session-jdbc, applied via Flyway instead of Spring Session's own schema
-- initializer (spring.session.jdbc.initialize-schema: never in application.yml) so it is
-- versioned alongside every other table.
--
-- PRINCIPAL_NAME is populated with the authenticated StaffAccount's id (see SecurityConfig,
-- T026) and is what T075/T075a query via FindByIndexNameSessionRepository to invalidate every
-- active session for an account on deactivation (FR-010) or role change (FR-007a).
--
-- MAX_INACTIVE_INTERVAL/EXPIRY_TIME give the 15-minute sliding idle timeout (FR-012) natively;
-- the additional 8-hour hard cap (spec Assumptions) independent of activity is enforced in
-- application code (SessionConfig / a request filter, T027), since Spring Session's own expiry
-- is idle-based only.
CREATE TABLE spring_session (
    primary_id            CHAR(36) NOT NULL,
    session_id            CHAR(36) NOT NULL,
    creation_time         BIGINT NOT NULL,
    last_access_time      BIGINT NOT NULL,
    max_inactive_interval INT NOT NULL,
    expiry_time           BIGINT NOT NULL,
    principal_name        VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36) NOT NULL,
    attribute_name      VARCHAR(200) NOT NULL,
    attribute_bytes      BYTEA NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session (primary_id) ON DELETE CASCADE
);
