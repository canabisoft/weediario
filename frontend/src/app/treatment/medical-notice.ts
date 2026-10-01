import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Icon } from '../shared/icon';
import { MEDICAL_NOTICE } from './treatment-text';

/** Aviso fixo em todas as telas do acompanhamento: o app registra, não orienta. */
@Component({
  selector: 'app-medical-notice',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  template: `
    <p class="flex items-start gap-2 rounded-xl border border-line bg-surface-raised p-3 text-sm text-fg-muted" role="note">
      <app-icon name="shield" class="mt-0.5 size-4 shrink-0 text-accent" />
      <span>{{ text }}</span>
    </p>
  `,
})
export class MedicalNotice {
  protected readonly text = MEDICAL_NOTICE;
}
