import { Routes } from '@angular/router';
import { roleGuard } from './core/auth/role.guard';
import { LoginComponent } from './features/auth/login/login.component';
import { MfaChallengeComponent } from './features/auth/login/mfa-challenge.component';
import { PasswordResetConfirmComponent } from './features/auth/password-reset/password-reset-confirm.component';
import { PasswordResetRequestComponent } from './features/auth/password-reset/password-reset-request.component';
import { RoleHomeComponent } from './features/home/role-home.component';
import { AuditLogComponent } from './features/admin/audit-log/audit-log.component';
import { AccountsComponent } from './features/admin/accounts/accounts.component';

export const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'login/mfa', component: MfaChallengeComponent },
  { path: 'password-reset/request', component: PasswordResetRequestComponent },
  { path: 'password-reset/confirm', component: PasswordResetConfirmComponent },
  {
    path: 'reception',
    component: RoleHomeComponent,
    data: { roleLabel: 'Recepcja' },
    canMatch: [roleGuard(['RECEPTION'])],
  },
  {
    path: 'doctor',
    component: RoleHomeComponent,
    data: { roleLabel: 'Lekarz' },
    canMatch: [roleGuard(['DOCTOR'])],
  },
  {
    path: 'admin',
    component: RoleHomeComponent,
    data: { roleLabel: 'Administrator', adminLinks: true },
    canMatch: [roleGuard(['ADMINISTRATOR'])],
  },
  // US2 (audit log) / US3 (account management) — admin-only screens (roleGuard is UX only; the
  // backend's @PreAuthorize + 404-not-403 mapping, T047/T063/T077, is the real boundary).
  {
    path: 'admin/audit-log',
    component: AuditLogComponent,
    canMatch: [roleGuard(['ADMINISTRATOR'])],
  },
  {
    path: 'admin/accounts',
    component: AccountsComponent,
    canMatch: [roleGuard(['ADMINISTRATOR'])],
  },
];
