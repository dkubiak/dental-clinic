import { Routes } from '@angular/router';
import { roleGuard } from './core/auth/role.guard';
import { AppShellComponent } from './core/shell/app-shell.component';
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
  // RECEPTION/DOCTOR/ASSISTANT all share the persistent, mobile-first AppShellComponent
  // (core/shell, T015) — replacing RoleHomeComponent's placeholder body as the routed shell.
  // Each child route keeps its own precise per-role canMatch (unchanged from before T016), so
  // this wrapping doesn't loosen who can reach which path. The child bodies are still
  // RoleHomeComponent for now — features/patients (US1, T038-T040) replaces them with the real
  // patient-search default landing screen; ADMINISTRATOR is a separate tier (no clinical-data
  // access, data-model.md) and deliberately stays outside this shell.
  {
    path: '',
    component: AppShellComponent,
    children: [
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
      // 002-patient-records (FR-006a) — new ASSISTANT role, read-only basic data + tooth-chart.
      {
        path: 'assistant',
        component: RoleHomeComponent,
        data: { roleLabel: 'Asystent/asystentka' },
        canMatch: [roleGuard(['ASSISTANT'])],
      },
    ],
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
