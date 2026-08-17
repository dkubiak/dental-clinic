import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuditLogComponent } from './audit-log.component';
import { AuditLogService } from './audit-log.service';

describe('AuditLogComponent', () => {
  let fixture: ComponentFixture<AuditLogComponent>;
  let component: AuditLogComponent;
  let auditLogService: { list: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    auditLogService = {
      list: vi.fn().mockReturnValue(
        of({
          entries: [
            {
              id: 1,
              eventType: 'LOGIN_SUCCESS',
              actorAccountId: 'acc-1',
              targetAccountId: null,
              occurredAt: '2026-08-17T10:00:00Z',
              beforeState: null,
              afterState: null,
              metadata: null,
            },
          ],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        }),
      ),
    };

    await TestBed.configureTestingModule({
      imports: [AuditLogComponent],
      providers: [
        provideAnimationsAsync(),
        { provide: AuditLogService, useValue: auditLogService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AuditLogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads entries on init', () => {
    expect(auditLogService.list).toHaveBeenCalledWith({ eventType: undefined, page: 0 });
    expect(component.entries()).toHaveLength(1);
    expect(component.entries()[0].eventType).toBe('LOGIN_SUCCESS');
  });

  it('applying a filter resets to page 0 and re-queries with the selected event type', () => {
    component.filterForm.controls.eventType.setValue('MFA_RESET');
    component.applyFilter();

    expect(auditLogService.list).toHaveBeenCalledWith({ eventType: 'MFA_RESET', page: 0 });
  });

  it('nextPage advances the page when more pages exist', () => {
    auditLogService.list.mockReturnValue(
      of({ entries: [], page: 1, size: 20, totalElements: 25, totalPages: 2 }),
    );
    component.totalPages.set(2);

    component.nextPage();

    expect(component.page()).toBe(1);
    expect(auditLogService.list).toHaveBeenCalledWith({ eventType: undefined, page: 1 });
  });

  it('previousPage does nothing on the first page', () => {
    component.previousPage();

    expect(component.page()).toBe(0);
  });
});
