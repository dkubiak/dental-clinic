import { ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { authInterceptor } from './core/auth/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    // withComponentInputBinding: route `data`/params bind directly to component inputs (used by
    // RoleHomeComponent's `roleLabel` input, app.routes.ts).
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
    provideAnimationsAsync(), // Angular Material
    // Rehydrates AuthState from a still-valid session cookie before the router makes its first
    // roleGuard decision — without this, a full page reload/deep link always loses the in-memory
    // role verifyMfa set and bounces an already-authenticated user back to /login (T063 finding).
    provideAppInitializer(() => firstValueFrom(inject(AuthService).rehydrateSession())),
  ],
};
