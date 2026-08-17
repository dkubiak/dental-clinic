import { readFileSync } from 'node:fs';
import path from 'node:path';
import { TOTP, Secret } from 'otpauth';

export interface SeedAccount {
  email: string;
  password: string;
  totpSecret: string;
  role: 'RECEPTION' | 'DOCTOR' | 'ADMINISTRATOR';
}

const SEED_FILE_PATH = path.resolve(__dirname, '../.generated/seed-accounts.json');

/**
 * Reads the accounts E2eSeedRunner (backend, `-Dspring.profiles.active=e2e-seed`) wrote before
 * this suite ran — see quickstart.md Prerequisites and backend/.../e2eseed/E2eSeedRunner.java.
 * Run the backend with that profile active before `npm run e2e`.
 */
export function loadSeedAccounts(): Record<
  'reception' | 'doctor' | 'admin' | 'passwordResetTest',
  SeedAccount
> {
  let raw: string;
  try {
    raw = readFileSync(SEED_FILE_PATH, 'utf-8');
  } catch {
    throw new Error(
      `Seed accounts file not found at ${SEED_FILE_PATH}. Run the backend once with ` +
        `-Dspring.profiles.active=e2e-seed before running this suite (see quickstart.md).`,
    );
  }
  const accounts = JSON.parse(raw) as SeedAccount[];
  return {
    reception: accounts.find((a) => a.email === 'reception@clinic.test')!,
    doctor: accounts.find((a) => a.email === 'doctor@clinic.test')!,
    admin: accounts.find((a) => a.email === 'admin@clinic.test')!,
    // A dedicated RECEPTION-role account, separate from `reception` above, reserved for the
    // password-reset scenario, which mutates its password — see E2eSeedRunner.
    passwordResetTest: accounts.find((a) => a.email === 'password-reset-test@clinic.test')!,
  };
}

/** Computes the current 6-digit TOTP code for a seeded account's secret (RFC 6238). */
export function currentTotpCode(secret: string): string {
  const totp = new TOTP({
    secret: Secret.fromBase32(secret),
    algorithm: 'SHA1',
    digits: 6,
    period: 30,
  });
  return totp.generate();
}
