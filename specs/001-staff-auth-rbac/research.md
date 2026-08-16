# Phase 0 Research: Staff Auth & RBAC

All items below were "NEEDS CLARIFICATION" or open technical decisions from Technical Context.
Each is resolved with a decision, rationale, and alternatives considered.

## 1. Backend language/runtime version

- **Decision**: Java 25 (LTS, GA September 2025; next LTS is Java 27, September 2027).
- **Rationale**: Constitution mandates Java as the backend language and requires the current LTS
  discipline implicitly (mirrors the explicit "current LTS" rule stated for Angular); Java 25 is
  the current LTS as of this plan's date (2026-08-16) and has runway to its 2028 EOL.
- **Alternatives considered**: Java 21 (previous LTS) — rejected, no reason to start a brand-new
  codebase one LTS behind. Java 26 (latest release, March 2026) — rejected, it is a non-LTS
  interim release under the 6-month cadence and would force an LTS migration sooner than needed.

## 2. Backend framework

- **Decision**: Spring Boot 4.1.x (latest stable, released June 2026) with Spring Security 7 for
  authentication and method-level (`@PreAuthorize`) authorization.
- **Rationale**: De facto standard for Java web services; Spring Security provides a mature,
  audited foundation for password hashing, session management, and RBAC rather than hand-rolling
  security-critical code — important given Principle II/III compliance stakes. Large ecosystem for
  the specific pieces this feature needs (Spring Data JPA, Spring Session JDBC, Flyway
  integration). Spring Boot 4 (GA November 2025) is the first line with first-class Java 25
  support while requiring Java 17 as its floor, and Spring Boot 4.1.x is the current stable patch
  line as of this plan's date — pairs directly with the Java 25 LTS decision in item 1.
- **Alternatives considered**: Micronaut/Quarkus — rejected, no compelling advantage for this
  feature's scope and smaller ecosystem for the Spring Session/Security integrations needed;
  hand-rolled auth on plain servlets — rejected outright, reinventing security-critical primitives
  is exactly the kind of avoidable risk Principle II warns against. Pinning to the older Spring
  Boot 3.x line — rejected, no reason to start a new codebase on the previous major version, and
  Spring Boot 4 is required for JUnit 5-only test tooling anyway (see item 9).

## 3. Frontend framework version

- **Decision**: Angular 21 (entered its LTS phase ~May 2026, running through May 2027).
- **Rationale**: Constitution mandates "current LTS" explicitly. Angular 22 (June 2026) is the
  newest stable release but is still in its 6-month *active support* window, not yet LTS; Angular
  20 is an older LTS nearing its Nov 2026 end. Angular 21 is the LTS release currently in its LTS
  window as of this plan's date.
- **Alternatives considered**: Angular 22 (latest) — rejected for a compliance-sensitive clinical
  system where "current LTS" is the constitution's explicit bar, not "latest"; Angular 20 —
  rejected, its LTS window ends sooner (Nov 2026), which would force a framework upgrade earlier
  in this project's life than necessary.

## 4. MFA mechanism (FR-015)

- **Decision**: TOTP (RFC 6238), authenticator-app based (compatible with Google Authenticator,
  Authy, 1Password, etc.), implemented via the `java-totp` library on the backend.
- **Rationale**: FR-015 requires MFA for all roles but leaves the mechanism to planning
  (Assumptions). TOTP avoids recurring per-message cost and external-provider dependency of SMS
  OTP, and avoids the weaker security posture of email-based OTP (an attacker who has already
  compromised the same mailbox used for login could also intercept an email OTP, whereas an
  authenticator app is a separate factor on a separate device). Spring Security 7 does not ship a
  built-in TOTP factor, so a small, focused library (`java-totp`, also available as
  `totp-spring-boot-starter`) is used instead of a hand-rolled HMAC implementation.
- **Alternatives considered**: SMS OTP — rejected, recurring cost, external SMS-gateway dependency
  and attack surface (SIM-swap risk), unnecessary for a staff-only (not public-facing) system.
  Email OTP — rejected, weaker factor separation as above. WebAuthn/passkeys — noted as a strong
  future upgrade path but rejected for v1 to keep enrollment simple (no hardware key or platform
  authenticator provisioning process defined yet); can be added later as an additional factor
  option without breaking this design.

## 5. Session vs. token strategy (FR-012 idle timeout, SC-001)

- **Decision**: Server-side session (Spring Session JDBC, backed by the same PostgreSQL
  RDS/Aurora instance), with sliding idle-timeout expiration (15 minutes per spec Assumptions).
- **Rationale**: FR-012 requires the system to be able to *end* a session after idle time and
  force re-authentication. A server-side session can be invalidated immediately and
  authoritatively at any time (e.g. when an admin deactivates an account mid-session — see Edge
  Cases in spec.md); a self-contained JWT would remain valid until expiry unless a separate
  revocation/deny-list store is added, which just re-introduces server-side state anyway. Spring
  Session JDBC reuses the already-required Postgres database instead of introducing a new stateful
  dependency (e.g. Redis/ElastiCache), keeping the Technology Stack Constraints footprint minimal
  for this first feature.
- **Alternatives considered**: Stateless JWT with short access-token expiry + refresh token —
  rejected for v1, adds a revocation-list requirement to satisfy "deactivate mid-session" and
  "idle timeout" semantics anyway, at higher complexity than a server-side session, for a
  staff-scale (tens of users) system with no cross-domain/mobile-native SSO requirement driving a
  need for tokens. JWT stored client-side without revocation — rejected outright, cannot satisfy
  "deactivated account loses access immediately" (Edge Cases).

## 6. Password hashing (FR-013)

- **Decision**: Argon2id, via Spring Security's `Argon2PasswordEncoder`.
- **Rationale**: OWASP's current recommended default for password storage; memory-hard, resists
  GPU/ASIC cracking better than bcrypt for a system protecting access to special-category (RODO
  Art. 9) patient data behind it.
- **Alternatives considered**: bcrypt — acceptable industry baseline but not memory-hard;
  rejected in favor of the stronger current OWASP recommendation given the sensitivity of what
  this login gates.

## 7. Audit log tamper-evidence (FR-008, Principle III)

- **Decision**: Two layers — (a) application layer: the audit log table is only ever `INSERT`ed
  by the backend, never `UPDATE`/`DELETE`d, and no API endpoint exposes edit/delete for it; (b)
  database layer: the application's DB role is granted `INSERT`/`SELECT` only on the audit log
  table (`UPDATE`/`DELETE` `REVOKE`d at the Postgres grant level), and each row stores a SHA-256
  hash chaining it to the previous row (`entry_hash = SHA256(previous_entry_hash || row fields)`),
  so any out-of-band tampering (e.g. direct DB access) is independently detectable by recomputing
  the chain, rather than relying solely on "the app doesn't expose a delete button."
- **Rationale**: FR-008/Principle III say the log must be tamper-evident "even by administrators
  ... through normal application flows" — application-layer restriction alone only prevents
  tampering *through the app*; the hash chain gives a verifiable guarantee independent of trusting
  the app layer, satisfying the spirit of "tamper-evident" rather than just "no delete button in
  the UI."
- **Alternatives considered**: App-layer-only restriction (no hash chain) — rejected as
  insufficient evidence of tamper-*evidence* (only tamper-*prevention through the app*, not
  detection of out-of-band changes). External/managed audit log service (e.g. a separate
  write-once store) — rejected as unnecessary infrastructure for this feature's scale; can be
  revisited later without changing the row schema (the hash chain travels with the data).

## 8. RBAC-denied response semantics (FR-005 — must not reveal resource existence)

- **Decision**: When a request is denied because the caller's role is out of scope for the target
  resource, the API returns `404 Not Found` (not `403 Forbidden`), identical to the response for a
  genuinely nonexistent resource. `401 Unauthorized` is still used (distinctly) for "not
  authenticated at all," since that case does not leak anything about a specific resource.
- **Rationale**: FR-005 explicitly requires not revealing "istnienia ani treści zasobu" (the
  resource's existence or content). A `403` response confirms the resource exists but is
  forbidden, which is itself the kind of information disclosure FR-005 prohibits; `404` for both
  "doesn't exist" and "exists but out of role scope" makes the two indistinguishable to the
  caller.
- **Alternatives considered**: `403 Forbidden` (typical REST convention) — rejected, directly
  conflicts with FR-005's explicit non-disclosure requirement.

## 9. Testing stack

- **Decision**: Backend — JUnit 5 (Jupiter) + Mockito + Spring Boot Test, with Testcontainers
  spinning up a real PostgreSQL instance for integration tests (never an in-memory DB substitute,
  so Flyway migrations and Postgres-specific behavior — e.g. the audit log grant restrictions —
  are actually exercised). JUnit 5 is not just a preference here: Spring Boot 4.x (item 2) removed
  JUnit 4 support entirely, so JUnit 5 is the only supported option for a project on this Spring
  Boot line. Frontend — Vitest for unit tests (Angular 21's current default test runner,
  replacing the now-deprecated Karma/Jasmine default). End-to-end — Playwright, driving the built
  Angular app against a real backend, to cover the spec's Acceptance Scenarios directly (e.g.
  "recepcja logs in and cannot reach lekarz-only resource").
- **Rationale**: Principle I requires failing tests before implementation for both stacks;
  Testcontainers avoids false confidence from an in-memory DB that wouldn't catch a Postgres
  grant/constraint bug (directly relevant to research item 7). Playwright gives the actual
  cross-role acceptance-scenario coverage the spec's "Independent Test" sections describe, which
  unit tests alone cannot; independent 2026 benchmarks also show it materially faster (~290ms vs
  ~420ms per test action) and lighter (~2.1GB vs ~3.2GB RAM for 10 parallel tests) than Cypress,
  with parallelization free and built-in — Cypress's equivalent parallel run/recording/analytics
  capability requires the paid Cypress Cloud SaaS, an ongoing cost and external dependency this
  project doesn't need to take on.
- **Alternatives considered**: Karma/Jasmine for frontend — rejected, deprecated as of Angular's
  2025 tooling shift and no longer the CLI default. JUnit 4 for backend — not viable, unsupported
  by Spring Boot 4.x. Cypress for e2e — evaluated directly against Playwright; Cypress's live
  in-browser debugging and more mature Angular component-testing support are genuine advantages,
  but rejected in favor of Playwright for this project given the performance/cost gap above and
  Playwright's broader browser coverage (including WebKit), which aligns with Principle IV's
  mobile-first testing needs across more mobile-relevant rendering engines.

## 10. Email delivery for password reset (FR-016)

- **Decision**: AWS SES (Simple Email Service) for sending the reset link, invoked from the
  backend.
- **Rationale**: Stays within the AWS-only hosting constraint; no new cloud provider or
  third-party SaaS dependency introduced; standard, low-operational-overhead choice for
  transactional email at this scale.
- **Alternatives considered**: Third-party transactional email provider (e.g. SendGrid, Postmark)
  — rejected, unnecessary external dependency outside AWS when SES covers the need.

## 11. MFA secret encryption at rest (FR-013)

- **Decision**: AWS KMS. A dedicated customer-managed KMS key encrypts/decrypts the
  `MfaEnrollment.totp_secret_encrypted` column value; the backend pod's IAM role (IRSA) is
  granted `kms:Encrypt`/`kms:Decrypt` on that key only, and a JPA `AttributeConverter` performs
  the encrypt-on-write/decrypt-on-read transparently (see tasks.md T009a, T022a).
- **Rationale**: FR-013 requires authentication data to be encrypted at rest; a TOTP shared
  secret is as sensitive as a password (possession of it defeats MFA entirely), so it warrants
  the same encryption bar. KMS keeps key management inside AWS-managed infrastructure (per the
  Technology Stack Constraints' AWS-only hosting rule) and grants/revokes access via IAM rather
  than an application-managed key, which is easier to audit and rotate.
- **Alternatives considered**: Postgres `pgcrypto` with an application-held symmetric key —
  rejected because the key would need to live somewhere (env var, app config) that is itself
  outside AWS's managed key-rotation/audit story; KMS is the AWS-native equivalent already used
  implicitly for RDS/EKS secrets, so no new operational pattern is introduced. Storing the secret
  unencrypted — rejected outright, direct FR-013 violation.

## 12. Backend build tool

- **Decision**: Gradle (Kotlin DSL, `build.gradle.kts`/`settings.gradle.kts`), with the Gradle
  Wrapper (`./gradlew`, `./gradlew.bat`) committed to the repository, plus the
  `org.gradle.toolchains.foojay-resolver-convention` plugin so Gradle auto-downloads the JDK 25
  toolchain (build.gradle.kts) if a contributor doesn't already have one locally.
- **Rationale**: the wrapper is a small (~48 KB) jar plus two launcher scripts checked into
  version control — running `./gradlew build` reproduces the exact same Gradle version for every
  contributor and in CI with zero local installation beyond a JVM to launch the wrapper itself,
  and the Foojay toolchain resolver removes even that requirement for the *target* JDK (25) used
  to compile/run the project. This was verified end-to-end while scaffolding this feature: the
  wrapper was generated once against the official Gradle 9.7.0 distribution
  (services.gradle.org, checksum-verified), and `./gradlew compileJava` / `spotlessCheck` /
  `checkstyleMain` all passed using only a temporarily-downloaded JDK, auto-provisioning JDK 25
  via Foojay for the actual compilation.
- **Alternatives considered**: Maven (`pom.xml`) — the project's original choice; functionally
  equivalent for this feature's needs, but Maven has no first-party "wrapper that also
  provisions the JDK" story as turnkey as Gradle's Wrapper + Foojay combination (Maven's own
  `mvnw` wrapper still requires a pre-existing JDK matching the project's target version), which
  was the deciding factor given the goal of a zero-local-install developer setup.

## Outcome

All Technical Context items are resolved; no remaining "NEEDS CLARIFICATION" markers.
