import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatToolbarModule } from '@angular/material/toolbar';
import { AuditLogService } from './audit-log.service';
import { AuditLogEntry } from './audit-log.models';

const EVENT_TYPES = [
  'LOGIN_SUCCESS',
  'LOGIN_FAILURE',
  'LOGIN_DENIED_LOCKED',
  'LOGIN_DENIED_DEACTIVATED',
  'LOGIN_DENIED_RATE_LIMITED',
  'MFA_FAILURE',
  'MFA_RESET',
  'ROLE_CHANGED',
  'ACCOUNT_CREATED',
  'ACCOUNT_DEACTIVATED',
  'ACCOUNT_DEACTIVATION_DENIED_LAST_ADMIN',
  'ACCOUNT_REACTIVATED',
  'PASSWORD_RESET_REQUESTED',
  'PASSWORD_RESET_SUCCEEDED',
  'PASSWORD_RESET_FAILED',
  'PASSWORD_RESET_EXPIRED',
  'ACCESS_DENIED_OUT_OF_ROLE',
] as const;

/**
 * T064 — read-only, admin-only audit log review screen (US2, FR-008a): filterable by event type,
 * paginated per {@code GET /audit-log} (T063). Mobile-first per Principle IV — the table scrolls
 * horizontally on narrow viewports rather than the page itself.
 */
@Component({
  selector: 'app-audit-log',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatTableModule,
    MatToolbarModule,
  ],
  template: `
    <mat-toolbar color="primary">Log audytowy</mat-toolbar>
    <div class="content">
      <form [formGroup]="filterForm" class="filters">
        <mat-form-field appearance="outline">
          <mat-label>Typ zdarzenia</mat-label>
          <mat-select formControlName="eventType" (selectionChange)="applyFilter()">
            <mat-option [value]="null">Wszystkie</mat-option>
            @for (type of eventTypes; track type) {
              <mat-option [value]="type">{{ type }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </form>

      <div class="table-scroll">
        <table mat-table [dataSource]="entries()" class="audit-table">
          <ng-container matColumnDef="occurredAt">
            <th mat-header-cell *matHeaderCellDef>Czas</th>
            <td mat-cell *matCellDef="let entry">{{ entry.occurredAt }}</td>
          </ng-container>
          <ng-container matColumnDef="eventType">
            <th mat-header-cell *matHeaderCellDef>Zdarzenie</th>
            <td mat-cell *matCellDef="let entry">{{ entry.eventType }}</td>
          </ng-container>
          <ng-container matColumnDef="actorAccountId">
            <th mat-header-cell *matHeaderCellDef>Wykonawca</th>
            <td mat-cell *matCellDef="let entry">{{ entry.actorAccountId ?? '—' }}</td>
          </ng-container>
          <ng-container matColumnDef="targetAccountId">
            <th mat-header-cell *matHeaderCellDef>Dotyczy konta</th>
            <td mat-cell *matCellDef="let entry">{{ entry.targetAccountId ?? '—' }}</td>
          </ng-container>
          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns"></tr>
        </table>
      </div>

      @if (entries().length === 0) {
        <p>Brak wpisów spełniających kryteria.</p>
      }

      <div class="pagination">
        <button mat-stroked-button type="button" [disabled]="page() === 0" (click)="previousPage()">
          Poprzednia
        </button>
        <span>Strona {{ page() + 1 }} z {{ totalPages() || 1 }}</span>
        <button
          mat-stroked-button
          type="button"
          [disabled]="page() + 1 >= totalPages()"
          (click)="nextPage()"
        >
          Następna
        </button>
      </div>
    </div>
  `,
  styles: `
    .content {
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .filters {
      display: flex;
      gap: 16px;
      flex-wrap: wrap;
    }
    .table-scroll {
      overflow-x: auto;
    }
    .audit-table {
      width: 100%;
    }
    .pagination {
      display: flex;
      align-items: center;
      gap: 12px;
    }
  `,
})
export class AuditLogComponent {
  private readonly auditLogService = inject(AuditLogService);
  private readonly formBuilder = inject(FormBuilder);

  readonly columns = ['occurredAt', 'eventType', 'actorAccountId', 'targetAccountId'];
  readonly eventTypes = EVENT_TYPES;

  readonly entries = signal<AuditLogEntry[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);

  readonly filterForm = this.formBuilder.group({
    eventType: this.formBuilder.control<string | null>(null),
  });

  constructor() {
    this.load();
  }

  applyFilter(): void {
    this.page.set(0);
    this.load();
  }

  previousPage(): void {
    if (this.page() > 0) {
      this.page.set(this.page() - 1);
      this.load();
    }
  }

  nextPage(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.page.set(this.page() + 1);
      this.load();
    }
  }

  private load(): void {
    const eventType = this.filterForm.controls.eventType.value ?? undefined;
    this.auditLogService.list({ eventType, page: this.page() }).subscribe((result) => {
      this.entries.set(result.entries);
      this.totalPages.set(result.totalPages);
    });
  }
}
