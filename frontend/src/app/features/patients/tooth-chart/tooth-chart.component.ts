import { Component, Input, OnInit, computed, inject, signal } from '@angular/core';
import { PatientsService } from '../patients.service';
import { ToothStateEntry } from '../patients.models';

/** Adult permanent dentition, FDI/ISO 3950 numbering: quadrants 1–4, positions 1–8 (11–18, 21–28,
 * 31–38, 41–48) — mirrors patient-service's ToothChartInitializer. */
export const FDI_TOOTH_NUMBERS: readonly number[] = [1, 2, 3, 4].flatMap((quadrant) =>
  [1, 2, 3, 4, 5, 6, 7, 8].map((position) => quadrant * 10 + position),
);

/** Upper-jaw quadrants (1, 2) render on the top row, lower-jaw quadrants (3, 4) on the bottom —
 * both mirrored outward from the midline, matching how an adult jaw diagram reads clinically. */
function toothPosition(toothNumber: number): { x: number; y: number } {
  const quadrant = Math.floor(toothNumber / 10);
  const position = toothNumber % 10; // 1 (central incisor) .. 8 (wisdom tooth)
  const isUpper = quadrant === 1 || quadrant === 2;
  const isRight = quadrant === 1 || quadrant === 3; // quadrants 1/4 = patient's right, drawn on the left
  const columnFromMidline = position - 1;
  const x = isRight ? 160 - (columnFromMidline + 1) * 20 : 160 + columnFromMidline * 20;
  const y = isUpper ? 20 : 90;
  return { x, y };
}

/** US2 — jaw SVG (32-tooth adult FDI layout), tap-to-select + healthy/sick toggle (FR-005/FR-006).
 * Used as a tab inside patient-detail (T050); driven entirely by the `patientId` input. */
@Component({
  selector: 'app-tooth-chart',
  standalone: true,
  imports: [],
  template: `
    <svg viewBox="0 0 320 160" class="jaw" role="img" aria-label="Schemat uzębienia">
      @for (tooth of teeth(); track tooth.toothNumber) {
        <rect
          [attr.data-testid]="'tooth-' + tooth.toothNumber"
          [attr.x]="position(tooth.toothNumber).x"
          [attr.y]="position(tooth.toothNumber).y"
          width="18"
          height="28"
          rx="3"
          class="tooth"
          [class.sick]="tooth.status === 'SICK'"
          [class.selected]="selectedTooth() === tooth.toothNumber"
          (click)="select(tooth.toothNumber)"
        />
      }
    </svg>

    @if (selected(); as t) {
      <div class="tooth-detail">
        <p>Ząb {{ t.toothNumber }}: {{ t.status === 'HEALTHY' ? 'zdrowy' : 'chory' }}</p>
        <button type="button" data-testid="toggle-status" (click)="toggle()">
          Oznacz jako {{ t.status === 'HEALTHY' ? 'chory' : 'zdrowy' }}
        </button>
      </div>
    }
  `,
  styles: `
    .jaw {
      width: 100%;
      max-width: 360px;
    }
    .tooth {
      fill: var(--pu-tooth-healthy-fill);
      stroke: var(--pu-tooth-healthy-stroke);
      stroke-width: 1;
      cursor: pointer;
    }
    .tooth.sick {
      fill: var(--pu-tooth-diseased-fill);
      stroke: var(--pu-tooth-diseased-stroke);
    }
    .tooth.selected {
      stroke: var(--pu-tooth-selected-stroke);
      stroke-width: 2;
    }
    .tooth-detail {
      margin-top: 12px;
    }
  `,
})
export class ToothChartComponent implements OnInit {
  @Input({ required: true }) patientId!: string;

  private readonly patientsService = inject(PatientsService);

  readonly teeth = signal<ToothStateEntry[]>([]);
  readonly selectedTooth = signal<number | null>(null);
  readonly selected = computed(
    () => this.teeth().find((t) => t.toothNumber === this.selectedTooth()) ?? null,
  );

  ngOnInit(): void {
    this.patientsService.getToothChart(this.patientId).subscribe((teeth) => this.teeth.set(teeth));
  }

  position(toothNumber: number): { x: number; y: number } {
    return toothPosition(toothNumber);
  }

  select(toothNumber: number): void {
    this.selectedTooth.set(toothNumber);
  }

  toggle(): void {
    const current = this.selected();
    if (!current) {
      return;
    }
    const newStatus = current.status === 'HEALTHY' ? 'SICK' : 'HEALTHY';
    this.patientsService
      .setToothStatus(this.patientId, current.toothNumber, newStatus)
      .subscribe((updated) => {
        this.teeth.update((all) =>
          all.map((t) => (t.toothNumber === updated.toothNumber ? updated : t)),
        );
      });
  }
}
