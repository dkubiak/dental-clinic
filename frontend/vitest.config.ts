import { defineConfig } from 'vitest/config';
import angular from '@analogjs/vite-plugin-angular';

// Vitest as the configured unit test runner (research.md #9) — replaces the deprecated
// Karma/Jasmine default.
export default defineConfig({
  plugins: [angular()],
  test: {
    globals: true,
    setupFiles: ['src/test-setup.ts'],
    include: ['src/**/*.spec.ts'],
    environment: 'jsdom',
    reporters: ['default'],
  },
});
