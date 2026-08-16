import '@analogjs/vite-plugin-angular/setup-vitest';

import { getTestBed } from '@angular/core/testing';
import {
  BrowserTestingModule,
  platformBrowserTesting,
} from '@angular/platform-browser/testing';

// @analogjs/vite-plugin-angular's setup-vitest only patches zone.js integration — it does not
// initialize Angular's TestBed environment, which every TestBed.configureTestingModule() call
// needs. Without this, component specs fail with "Need to call TestBed.initTestEnvironment()
// first" (T038).
getTestBed().initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
