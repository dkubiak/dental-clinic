import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

/**
 * Attaches the session cookie automatically (withCredentials) and redirects to /login on a 401
 * (T030). This is UX convenience only — the backend is the sole authorization enforcement point
 * (SecurityConfig, T026); this interceptor never makes an authorization decision itself.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);
  const authReq = req.clone({ withCredentials: true });

  return next(authReq).pipe(
    catchError((error) => {
      if (error?.status === 401) {
        router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};
