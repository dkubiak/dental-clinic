# dental-clinic-app

Developed via Spec-Driven Development (Spec Kit) — see [CLAUDE.md](CLAUDE.md) for the workflow and
`.specify/memory/constitution.md` for binding project rules.

## Local development stack

```bash
cp .env.example .env   # first time only; safe defaults, edit if you want different local passwords
docker compose up --build
```

This starts:

| Service        | URL                     | What it is                                      |
|----------------|--------------------------|--------------------------------------------------|
| `frontend`     | http://localhost:4200    | Angular app, served by nginx (proxies API calls) |
| `auth-service` | http://localhost:8080    | Spring Boot backend                              |
| `postgres`     | localhost:5432            | `dental_clinic_auth` database                    |

Stop with `docker compose down` (add `-v` to also wipe the Postgres volume and reset all seed
data).

No AWS account/LocalStack needed: `auth-service` runs with `SPRING_PROFILES_ACTIVE=e2e-seed`,
which stubs KMS/SES in-process and auto-seeds test accounts on startup (see below). Captured
password-reset emails land in `.local/e2e-seed/sent-emails.jsonl`.

**Convention:** every new backend service or frontend module gets its own block in
`docker-compose.yml`, named to match its Helm chart under `helm/<name>` — see the comment at the
top of that file.

### Logging in locally

Three staff accounts (one per role) plus a dedicated password-reset test account are seeded
automatically, MFA already enrolled. Credentials and TOTP secrets are written to
`.local/e2e-seed/seed-accounts.json` on every startup (gitignored):

| Email                              | Role           |
|-------------------------------------|----------------|
| `reception@clinic.test`             | RECEPTION      |
| `doctor@clinic.test`                | DOCTOR         |
| `admin@clinic.test`                 | ADMINISTRATOR  |
| `password-reset-test@clinic.test`   | RECEPTION      |

Password for all of them: `correct-horse-battery-staple`.

Login requires a TOTP code (MFA is mandatory — FR-015, no bypass). To get one:
- Import the account's `totpSecret` (from `seed-accounts.json`) into any authenticator app
  (Google Authenticator, Authy, 1Password, ...), or
- Compute one manually (standard TOTP: SHA1, 6 digits, 30s step), e.g.:
  ```bash
  python3 -c "
  import base64, hmac, hashlib, struct, time
  key = base64.b32decode('<totpSecret>')
  h = hmac.new(key, struct.pack('>Q', int(time.time())//30), hashlib.sha1).digest()
  o = h[-1] & 0x0F
  print(str((struct.unpack('>I', h[o:o+4])[0] & 0x7fffffff) % 1000000).zfill(6))
  "
  ```

These accounts only exist locally/in CI (`e2e-seed` profile) — never in a deployed environment.
