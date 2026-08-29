import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { authInterceptor } from './auth.interceptor';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let router: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    router = { navigate: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: router },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('redirects to /login on a 401 from a regular endpoint', () => {
    http.get('/patients').subscribe({ error: () => undefined });

    httpMock.expectOne('/patients').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(router.navigate).toHaveBeenCalledWith(['/login']);
  });

  it('does NOT redirect on a 401 from /auth/session — AuthService.rehydrateSession documents this as a normal "not logged in" outcome it swallows itself, on every route including pre-auth ones (password-reset/*) reached by a fresh page load', () => {
    http.get('/auth/session').subscribe({ error: () => undefined });

    httpMock.expectOne('/auth/session').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('still propagates the 401 error to the caller even when the redirect is suppressed', () => {
    let observedStatus: number | undefined;
    http.get('/auth/session').subscribe({
      error: (err) => {
        observedStatus = err.status;
      },
    });

    httpMock.expectOne('/auth/session').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(observedStatus).toBe(401);
  });
});
