import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ThemeService } from './theme.service';

const STORAGE_KEY = 'pu.theme';

interface MockMediaQueryList {
  matches: boolean;
  addEventListener: (type: 'change', listener: (e: { matches: boolean }) => void) => void;
  removeEventListener: (type: 'change', listener: (e: { matches: boolean }) => void) => void;
  fireChange(matches: boolean): void;
}

function mockMatchMedia(initialMatches: boolean): MockMediaQueryList {
  const listeners: Array<(e: { matches: boolean }) => void> = [];
  const mql: MockMediaQueryList = {
    matches: initialMatches,
    addEventListener: (_type, listener) => listeners.push(listener),
    removeEventListener: (_type, listener) => {
      const i = listeners.indexOf(listener);
      if (i >= 0) listeners.splice(i, 1);
    },
    fireChange(matches: boolean) {
      mql.matches = matches;
      listeners.forEach((l) => l({ matches }));
    },
  };
  vi.stubGlobal(
    'matchMedia',
    vi.fn().mockImplementation(() => mql),
  );
  return mql;
}

describe('ThemeService', () => {
  beforeEach(() => {
    localStorage.clear();
    mockMatchMedia(false);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    localStorage.clear();
  });

  it('brak wpisu w localStorage daje stan system, a resolved podąża za prefers-color-scheme', () => {
    mockMatchMedia(false);
    const service = new ThemeService();
    expect(service.choice()).toBe('system');
    expect(service.resolved()).toBe('light');
  });

  it('set("dark") zapisuje wybór w localStorage pod kluczem pu.theme', () => {
    const service = new ThemeService();
    service.set('dark');
    expect(localStorage.getItem(STORAGE_KEY)).toBe('dark');
    expect(service.choice()).toBe('dark');
    expect(service.resolved()).toBe('dark');
  });

  it('odczytuje zapisany wybór przy starcie', () => {
    localStorage.setItem(STORAGE_KEY, 'light');
    const service = new ThemeService();
    expect(service.choice()).toBe('light');
    expect(service.resolved()).toBe('light');
  });

  it('nieznana wartość w localStorage jest traktowana jak brak wpisu (FR-014)', () => {
    localStorage.setItem(STORAGE_KEY, 'sepia');
    const service = new ThemeService();
    expect(service.choice()).toBe('system');
  });

  it('wyjątek przy odczycie localStorage nie propaguje — stan system, aplikacja działa dalej (FR-014)', () => {
    const getItemSpy = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('tryb prywatny: dostęp zablokowany');
    });

    expect(() => new ThemeService()).not.toThrow();
    const service = new ThemeService();
    expect(service.choice()).toBe('system');

    getItemSpy.mockRestore();
  });

  it('wyjątek przy zapisie do localStorage nie propaguje — przełączenie działa w obrębie sesji (FR-014, US2 scenariusz 6)', () => {
    const service = new ThemeService();
    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('tryb prywatny: zapis zablokowany');
    });

    expect(() => service.set('dark')).not.toThrow();
    expect(service.choice()).toBe('dark');
    expect(service.resolved()).toBe('dark');

    setItemSpy.mockRestore();
  });

  it('resolved śledzi prefers-color-scheme TYLKO w stanie system (FR-012)', () => {
    const mql = mockMatchMedia(false);
    const service = new ThemeService();
    expect(service.resolved()).toBe('light');

    mql.fireChange(true);
    expect(service.resolved()).toBe('dark');
  });

  it('jawny wybór ignoruje kolejne zmiany preferencji systemowej (FR-013, US2 scenariusz 5)', () => {
    const mql = mockMatchMedia(false);
    const service = new ThemeService();
    service.set('light');

    mql.fireChange(true);

    expect(service.choice()).toBe('light');
    expect(service.resolved()).toBe('light');
  });

  it('toggle() przełącza jasny ↔ ciemny na podstawie aktualnie renderowanego motywu', () => {
    mockMatchMedia(false);
    const service = new ThemeService();
    expect(service.resolved()).toBe('light');

    service.toggle();
    expect(service.choice()).toBe('dark');
    expect(service.resolved()).toBe('dark');

    service.toggle();
    expect(service.choice()).toBe('light');
    expect(service.resolved()).toBe('light');
  });

  it('set() ustawia color-scheme na document.documentElement synchronicznie (contracts/theme-preference.md §1)', () => {
    const service = new ThemeService();
    service.set('dark');
    expect(document.documentElement.style.colorScheme).toBe('dark');

    service.set('light');
    expect(document.documentElement.style.colorScheme).toBe('light');

    service.set('system');
    expect(document.documentElement.style.colorScheme).toBe('light dark');
  });

  it('zdarzenie storage z innej karty stosuje zmianę w tej karcie', () => {
    const service = new ThemeService();
    expect(service.choice()).toBe('system');

    localStorage.setItem(STORAGE_KEY, 'dark');
    window.dispatchEvent(
      new StorageEvent('storage', { key: STORAGE_KEY, newValue: 'dark', storageArea: localStorage }),
    );

    expect(service.choice()).toBe('dark');
    expect(service.resolved()).toBe('dark');
  });

  it('ignoruje zdarzenia storage dla innych kluczy', () => {
    const service = new ThemeService();
    service.set('light');

    window.dispatchEvent(
      new StorageEvent('storage', { key: 'unrelated.key', newValue: 'dark', storageArea: localStorage }),
    );

    expect(service.choice()).toBe('light');
  });

  it('klucz pu.theme pozostaje nietknięty po symulacji wylogowania — czyszczenie sesji nie usuwa go (FR-011, US2 scenariusz 7)', () => {
    const service = new ThemeService();
    service.set('dark');

    // symulacja wylogowania: czyszczone są tylko klucze sesji, nigdy magazyn motywu
    localStorage.removeItem('session.token');
    localStorage.removeItem('auth.state');

    expect(localStorage.getItem(STORAGE_KEY)).toBe('dark');
    expect(service.choice()).toBe('dark');
  });
});
