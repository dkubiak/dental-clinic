import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Client-side mirror of the backend's PeselValidator (research.md #1) — UX feedback only, the
 * server is authoritative. Poland's standard 11-digit weighted checksum. An empty/absent value is
 * valid (PESEL is optional, FR-002).
 */
export function peselChecksumValidator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = (control.value ?? '').toString().trim();
    if (value === '') {
      return null;
    }
    if (!/^\d{11}$/.test(value)) {
      return { peselChecksum: true };
    }
    const weights = [1, 3, 7, 9, 1, 3, 7, 9, 1, 3];
    const sum = weights.reduce((acc, w, i) => acc + w * Number(value[i]), 0);
    const expected = (10 - (sum % 10)) % 10;
    return expected === Number(value[10]) ? null : { peselChecksum: true };
  };
}
