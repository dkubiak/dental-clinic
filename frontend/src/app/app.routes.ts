import { Routes } from '@angular/router';
import { roleGuard } from './core/auth/role.guard';
import { LoginComponent } from './features/auth/login/login.component';
import { MfaChallengeComponent } from './features/auth/login/mfa-challenge.component';
import { PasswordResetConfirmComponent } from './features/auth/password-reset/password-reset-confirm.component';
import { PasswordResetRequestComponent } from './features/auth/password-reset/password-reset-request.component';
import { RoleHomeComponent } from './features/home/role-home.component';

// Populated per feature phase (US1 here; US2 audit-log / US3 admin/accounts in Phase 4-5).
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
    data: { roleLabel: 'Administrator' },
    canMatch: [roleGuard(['ADMINISTRATOR'])],
  },
];
