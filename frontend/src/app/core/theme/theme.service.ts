import { Injectable, signal } from '@angular/core';

export type ThemeChoice = 'light' | 'dark' | 'system';

const STORAGE_KEY = 'pu.theme';

function isThemeValue(value: string | null): value is 'light' | 'dark' {
  return value === 'light' || value === 'dark';
}

/**
 * Preferencja motywu jasny/ciemny (contracts/theme-preference.md §3). Jedyny nośnik stanu
 * w DOM jest `color-scheme` na `<html>` (research.md R1) — bez klasy CSS, bez atrybutu `data-*`
 * (FR-026). Usługa NIE zależy od AuthService, sesji ani modelu konta (granica bramki
 * bezpieczeństwa z plan.md) i nigdy nie rzuca wyjątkiem, także gdy `localStorage` jest
 * niedostępny (FR-014).
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly systemPrefersDark = signal(this.readSystemPrefersDark());

  readonly choice = signal<ThemeChoice>(this.readStoredChoice());

  readonly resolved = signal<'light' | 'dark'>(this.computeResolved());

  constructor() {
    this.applyToDocument();

    this.watchSystemPreference();

    window.addEventListener('storage', (event: StorageEvent) => {
      if (event.key !== STORAGE_KEY) {
        return;
      }
      const next = isThemeValue(event.newValue) ? event.newValue : 'system';
      this.choice.set(next);
      this.resolved.set(this.computeResolved());
      this.applyToDocument();
    });
  }

  /** Ustawia wybór, zapisuje go i stosuje do DOM synchronicznie. Nigdy nie rzuca (FR-014). */
  set(choice: ThemeChoice): void {
    this.choice.set(choice);
    this.persist(choice);
    this.resolved.set(this.computeResolved());
    this.applyToDocument();
  }

  /** Przełącza jasny ↔ ciemny na podstawie aktualnie renderowanego motywu, nie wyboru. */
  toggle(): void {
    this.set(this.resolved() === 'dark' ? 'light' : 'dark');
  }

  private readStoredChoice(): ThemeChoice {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      return isThemeValue(stored) ? stored : 'system';
    } catch {
      return 'system';
    }
  }

  private persist(choice: ThemeChoice): void {
    try {
      if (choice === 'system') {
        localStorage.removeItem(STORAGE_KEY);
      } else {
        localStorage.setItem(STORAGE_KEY, choice);
      }
    } catch {
      // Tryb prywatny / dane witryny zablokowane: przełączenie działa w obrębie sesji,
      // bez komunikatu błędu (FR-014, US2 scenariusz 6).
    }
  }

  private readSystemPrefersDark(): boolean {
    try {
      return typeof matchMedia === 'function' && matchMedia('(prefers-color-scheme: dark)').matches;
    } catch {
      return false;
    }
  }

  private watchSystemPreference(): void {
    try {
      if (typeof matchMedia !== 'function') {
        return;
      }
      const mql = matchMedia('(prefers-color-scheme: dark)');
      mql.addEventListener('change', (event) => {
        this.systemPrefersDark.set(event.matches);
        if (this.choice() === 'system') {
          this.resolved.set(this.computeResolved());
          this.applyToDocument();
        }
      });
    } catch {
      // Brak matchMedia (np. środowisko testowe bez polyfillu) — degradacja do jasnego domyślnie.
    }
  }

  private computeResolved(): 'light' | 'dark' {
    const choice = this.choice();
    if (choice === 'system') {
      return this.systemPrefersDark() ? 'dark' : 'light';
    }
    return choice;
  }

  private applyToDocument(): void {
    const choice = this.choice();
    document.documentElement.style.colorScheme = choice === 'system' ? 'light dark' : choice;
  }
}
