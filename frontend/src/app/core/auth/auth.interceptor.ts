import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

/**
 * Attaches the session cookie automatically (withCredentials) and redirects to /login on a 401
 * (T030). This is UX convenience only — the backend is the sole authorization enforcement point
 * (SecurityConfig, T026); this interceptor never makes an authorization decision itself.
 *
 * <p>{@code /auth/session} is exempt: {@link AuthService.rehydrateSession} calls it once at every
 * app bootstrap (app.config.ts, `provideAppInitializer`), on every route, and its own javadoc
 * documents a 401 there as "an entirely normal 'not logged in' outcome, not an error" that it
 * swallows itself. Without this exemption, that swallowing never gets a chance to run: this
 * interceptor's redirect fires first, on every single fresh page load of any pre-auth route other
 * than /login itself (password-reset/request, password-reset/confirm — reached, in real usage, by
 * clicking a link in an email with no prior app state) — bouncing the user straight back to
 * /login before the page they actually navigated to ever renders.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const authReq = req.clone({ withCredentials: true });

  return next(authReq).pipe(
    catchError((error) => {
      if (error?.status === 401 && !req.url.endsWith('/auth/session')) {
        router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};
