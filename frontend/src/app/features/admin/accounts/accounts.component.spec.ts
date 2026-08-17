import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AccountsComponent } from './accounts.component';
import { AccountAdminService } from './account-admin.service';
import { StaffAccountSummary } from './accounts.models';

const sampleAccount: StaffAccountSummary = {
  id: 'acc-1',
  email: 'reception@dentalclinic.example',
  role: 'RECEPTION',
  status: 'ACTIVE',
  mfaEnrolled: true,
  createdAt: '2026-08-17T10:00:00Z',
};

describe('AccountsComponent', () => {
  let fixture: ComponentFixture<AccountsComponent>;
  let component: AccountsComponent;
  let accountAdminService: {
    list: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    changeRole: ReturnType<typeof vi.fn>;
    deactivate: ReturnType<typeof vi.fn>;
    reactivate: ReturnType<typeof vi.fn>;
    resetMfa: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    accountAdminService = {
      list: vi.fn().mockReturnValue(of([sampleAccount])),
      create: vi.fn().mockReturnValue(of(sampleAccount)),
      changeRole: vi.fn().mockReturnValue(of(sampleAccount)),
      deactivate: vi.fn().mockReturnValue(of(undefined)),
      reactivate: vi.fn().mockReturnValue(of(undefined)),
      resetMfa: vi.fn().mockReturnValue(of(undefined)),
    };

    await TestBed.configureTestingModule({
      imports: [AccountsComponent],
      providers: [
        provideAnimationsAsync(),
        { provide: AccountAdminService, useValue: accountAdminService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AccountsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads accounts on init', () => {
    expect(accountAdminService.list).toHaveBeenCalled();
    expect(component.accounts()).toEqual([sampleAccount]);
  });

  it('creates an account and reloads the list on success', () => {
    component.createForm.setValue({ email: 'new-staff@dentalclinic.example', role: 'DOCTOR' });

    component.createAccount();

    expect(accountAdminService.create).toHaveBeenCalledWith(
      'new-staff@dentalclinic.example',
      'DOCTOR',
    );
    expect(accountAdminService.list).toHaveBeenCalledTimes(2);
  });

  it('shows an error when account creation fails', () => {
    accountAdminService.create.mockReturnValue(throwError(() => new Error('boom')));
    component.createForm.setValue({ email: 'new-staff@dentalclinic.example', role: 'DOCTOR' });

    component.createAccount();

    expect(component.createError()).toContain('Nie udało się utworzyć konta');
  });

  it('changes an account role', () => {
    component.changeRole(sampleAccount, 'ADMINISTRATOR');

    expect(accountAdminService.changeRole).toHaveBeenCalledWith('acc-1', 'ADMINISTRATOR');
  });

  it('deactivates and reactivates an account', () => {
    component.deactivate(sampleAccount);
    expect(accountAdminService.deactivate).toHaveBeenCalledWith('acc-1');

    component.reactivate(sampleAccount);
    expect(accountAdminService.reactivate).toHaveBeenCalledWith('acc-1');
  });

  it('MFA reset requires a two-step confirmation before calling the backend', () => {
    component.requestResetMfa(sampleAccount);
    expect(component.confirmingResetId()).toBe('acc-1');
    expect(accountAdminService.resetMfa).not.toHaveBeenCalled();

    component.confirmResetMfa(sampleAccount);
    expect(accountAdminService.resetMfa).toHaveBeenCalledWith('acc-1');
    expect(component.confirmingResetId()).toBeNull();
  });

  it('MFA reset can be cancelled without calling the backend', () => {
    component.requestResetMfa(sampleAccount);
    component.cancelResetMfa();

    expect(component.confirmingResetId()).toBeNull();
    expect(accountAdminService.resetMfa).not.toHaveBeenCalled();
  });
});
