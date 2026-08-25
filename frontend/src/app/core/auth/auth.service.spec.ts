import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { AuthState } from './auth-state';
import { AuthService } from './auth.service';

describe('AuthService.rehydrateSession', () => {
  let service: AuthService;
  let authState: AuthState;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    authState = TestBed.inject(AuthState);
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('sets AuthState.currentRole from a still-valid session (GET /auth/session)', () => {
    let completed = false;
    service.rehydrateSession().subscribe(() => (completed = true));

    httpMock.expectOne('/auth/session').flush({ role: 'DOCTOR' });

    expect(authState.currentRole()).toBe('DOCTOR');
    expect(completed).toBe(true);
  });

  it('leaves AuthState.currentRole null (never throws) when there is no valid session', () => {
    let completed = false;
    service.rehydrateSession().subscribe(() => (completed = true));

    httpMock
      .expectOne('/auth/session')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(authState.currentRole()).toBeNull();
    expect(completed).toBe(true);
  });
});
