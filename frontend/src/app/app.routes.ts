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
import { PatientSearchComponent } from './features/patients/patient-search/patient-search.component';
import { PatientCreateComponent } from './features/patients/patient-create/patient-create.component';
import { PatientDetailComponent } from './features/patients/patient-detail/patient-detail.component';

export const routes: Routes = [
  { path: '', redirectTo: '/login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'login/mfa', component: MfaChallengeComponent },
  { path: 'password-reset/request', component: PasswordResetRequestComponent },
  { path: 'password-reset/confirm', component: PasswordResetConfirmComponent },
  // RECEPTION/DOCTOR/ASSISTANT all share the persistent, mobile-first AppShellComponent
  // (core/shell, T015). The patient-search screen is the shared default landing view (T040);
  // ADMINISTRATOR is a separate tier (no clinical-data access, data-model.md) and deliberately
  // stays outside this shell.
  {
    path: '',
    component: AppShellComponent,
    children: [
      {
        path: 'patients',
        component: PatientSearchComponent,
        canMatch: [roleGuard(['RECEPTION', 'DOCTOR', 'ASSISTANT'])],
      },
      // FR-001 — creation is RECEPTION/DOCTOR only; ASSISTANT has read-only basic-data access
      // (FR-006a). Must precede 'patients/:id' so the literal segment matches first.
      {
        path: 'patients/new',
        component: PatientCreateComponent,
        canMatch: [roleGuard(['RECEPTION', 'DOCTOR'])],
      },
      {
        path: 'patients/:id',
        component: PatientDetailComponent,
        canMatch: [roleGuard(['RECEPTION', 'DOCTOR', 'ASSISTANT'])],
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
