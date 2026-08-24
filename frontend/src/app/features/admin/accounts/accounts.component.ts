import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatToolbarModule } from '@angular/material/toolbar';
import type { StaffRole } from '../../../core/auth/auth-state';
import { AccountAdminService } from './account-admin.service';
import { StaffAccountSummary } from './accounts.models';

// 002-patient-records FR-006a — ASSISTANT included so administrators can create/reassign it via
// this existing account-management UI, same as the three original roles.
const ROLES: StaffRole[] = ['RECEPTION', 'DOCTOR', 'ADMINISTRATOR', 'ASSISTANT'];

/**
 * T078/T079a — admin-only account management screen (US3): list, create, role-change,
 * deactivate/reactivate, and admin-assisted MFA reset (FR-015b). Mobile-first per Principle IV.
 */
@Component({
  selector: 'app-accounts',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    MatToolbarModule,
  ],
  template: `
    <mat-toolbar color="primary">Zarządzanie kontami</mat-toolbar>
    <div class="content">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Nowe konto</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="createForm" (ngSubmit)="createAccount()" class="create-form">
            <mat-form-field appearance="outline">
              <mat-label>Adres e-mail</mat-label>
              <input matInput type="email" formControlName="email" />
            </mat-form-field>
            <mat-form-field appearance="outline">
              <mat-label>Rola</mat-label>
              <mat-select formControlName="role">
                @for (role of roles; track role) {
                  <mat-option [value]="role">{{ role }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <button mat-flat-button color="primary" type="submit" [disabled]="createForm.invalid">
              Utwórz konto
            </button>
          </form>
          @if (createError()) {
            <p class="error" role="alert">{{ createError() }}</p>
          }
        </mat-card-content>
      </mat-card>

      <div class="table-scroll">
        <table mat-table [dataSource]="accounts()" class="accounts-table">
          <ng-container matColumnDef="email">
            <th mat-header-cell *matHeaderCellDef>E-mail</th>
            <td mat-cell *matCellDef="let account">{{ account.email }}</td>
          </ng-container>
          <ng-container matColumnDef="role">
            <th mat-header-cell *matHeaderCellDef>Rola</th>
            <td mat-cell *matCellDef="let account">
              <mat-form-field appearance="outline" class="role-select">
                <!-- MatSelect computes its accessible name from a mat-label inside the
                     form-field via aria-labelledby, overriding any aria-label set directly on
                     it — an [attr.aria-label] here (as tried previously) is silently dropped. -->
                <mat-label>Rola</mat-label>
                <mat-select
                  [value]="account.role"
                  (selectionChange)="changeRole(account, $event.value)"
                >
                  @for (role of roles; track role) {
                    <mat-option [value]="role">{{ role }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
            </td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>Status</th>
            <td mat-cell *matCellDef="let account">{{ account.status }}</td>
          </ng-container>
          <ng-container matColumnDef="actions">
            <th mat-header-cell *matHeaderCellDef>Akcje</th>
            <td mat-cell *matCellDef="let account">
              @if (account.status === 'ACTIVE') {
                <button
                  mat-stroked-button
                  type="button"
                  [attr.aria-label]="'Dezaktywuj ' + account.email"
                  (click)="deactivate(account)"
                >
                  Dezaktywuj
                </button>
              } @else {
                <button
                  mat-stroked-button
                  type="button"
                  [attr.aria-label]="'Reaktywuj ' + account.email"
                  (click)="reactivate(account)"
                >
                  Reaktywuj
                </button>
              }
              @if (confirmingResetId() === account.id) {
                <button
                  mat-flat-button
                  color="warn"
                  type="button"
                  [attr.aria-label]="'Potwierdź reset MFA dla ' + account.email"
                  (click)="confirmResetMfa(account)"
                >
                  Potwierdź reset MFA
                </button>
                <button mat-button type="button" (click)="cancelResetMfa()">Anuluj</button>
              } @else {
                <button
                  mat-stroked-button
                  type="button"
                  [attr.aria-label]="'Resetuj MFA dla ' + account.email"
                  (click)="requestResetMfa(account)"
                >
                  Resetuj MFA
                </button>
              }
            </td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
      </div>
      @if (actionError()) {
        <p class="error" role="alert">{{ actionError() }}</p>
      }
    </div>
  `,
  styles: `
    .content {
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .create-form {
      display: flex;
      gap: 16px;
      flex-wrap: wrap;
      align-items: flex-start;
    }
    .table-scroll {
      overflow-x: auto;
    }
    .accounts-table {
      width: 100%;
    }
    .role-select {
      width: 160px;
    }
    .error {
      color: var(--mat-sys-error, #b3261e);
    }
  `,
})
export class AccountsComponent {
  private readonly accountAdminService = inject(AccountAdminService);
  private readonly formBuilder = inject(FormBuilder);

  readonly columns = ['email', 'role', 'status', 'actions'];
  readonly roles = ROLES;

  readonly accounts = signal<StaffAccountSummary[]>([]);
  readonly createError = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly confirmingResetId = signal<string | null>(null);

  readonly createForm = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    role: this.formBuilder.nonNullable.control<StaffRole>('RECEPTION'),
  });

  constructor() {
    this.reload();
  }

  reload(): void {
    this.accountAdminService.list().subscribe((accounts) => this.accounts.set(accounts));
  }

  createAccount(): void {
    if (this.createForm.invalid) {
      return;
    }
    this.createError.set(null);
    const { email, role } = this.createForm.getRawValue();
    this.accountAdminService.create(email, role).subscribe({
      next: () => {
        this.createForm.reset({ email: '', role: 'RECEPTION' });
        this.reload();
      },
      error: () => this.createError.set('Nie udało się utworzyć konta.'),
    });
  }

  changeRole(account: StaffAccountSummary, role: StaffRole): void {
    this.actionError.set(null);
    this.accountAdminService.changeRole(account.id, role).subscribe({
      next: () => this.reload(),
      error: () => this.actionError.set('Nie udało się zmienić roli.'),
    });
  }

  deactivate(account: StaffAccountSummary): void {
    this.actionError.set(null);
    this.accountAdminService.deactivate(account.id).subscribe({
      next: () => this.reload(),
      error: () => this.actionError.set('Nie udało się dezaktywować konta.'),
    });
  }

  reactivate(account: StaffAccountSummary): void {
    this.actionError.set(null);
    this.accountAdminService.reactivate(account.id).subscribe({
      next: () => this.reload(),
      error: () => this.actionError.set('Nie udało się reaktywować konta.'),
    });
  }

  requestResetMfa(account: StaffAccountSummary): void {
    this.confirmingResetId.set(account.id);
  }

  cancelResetMfa(): void {
    this.confirmingResetId.set(null);
  }

  confirmResetMfa(account: StaffAccountSummary): void {
    this.actionError.set(null);
    this.accountAdminService.resetMfa(account.id).subscribe({
      next: () => {
        this.confirmingResetId.set(null);
        this.reload();
      },
      error: () => this.actionError.set('Nie udało się zresetować MFA.'),
    });
  }
}
