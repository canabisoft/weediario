import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DiaryCard } from '../core/api/api.models';
import { DiaryPhoto } from '../shared/diary-photo';
import { Icon } from '../shared/icon';
import { MEDIUM_LABEL, STAGE_LABEL, STATUS_LABEL } from '../shared/labels';

@Component({
  selector: 'app-diary-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DiaryPhoto, Icon],
  template: `
    @let d = diary();
    <!-- O título é o link do diário e se estica sobre o card inteiro; o apelido do autor é outro link, por cima. -->
    <article
      class="group relative overflow-hidden rounded-2xl border border-line bg-surface transition-colors focus-within:border-accent hover:border-accent/50"
    >
      <div class="relative aspect-[4/3]">
        @if (d.coverThumbnailUrl) {
          <app-diary-photo class="size-full" [url]="d.coverThumbnailUrl" [isPublic]="d.visibility === 'PUBLIC'" />
        } @else {
          <div class="grid size-full place-items-center bg-surface-raised text-accent/40">
            <app-icon name="leaf" class="size-14" [strokeWidth]="1.2" />
          </div>
        }
        <span class="absolute left-2 top-2 rounded-full bg-canvas/85 px-2 py-0.5 text-xs font-semibold text-fg">
          {{ stage[d.currentStage] }} · semana {{ d.currentWeek }}
        </span>
        @if (d.moderationHold) {
          <span class="absolute right-2 top-2 inline-flex items-center gap-1 rounded-full bg-warn/90 px-2 py-0.5 text-xs font-semibold text-black">
            <app-icon name="eyeOff" class="size-3" /> Em análise
          </span>
        } @else if (d.visibility === 'PRIVATE') {
          <span class="absolute right-2 top-2 inline-flex items-center gap-1 rounded-full bg-canvas/85 px-2 py-0.5 text-xs">
            <app-icon name="lock" class="size-3" /> Privado
          </span>
        } @else if (d.visibility === 'COMMUNITY') {
          <span class="absolute right-2 top-2 inline-flex items-center gap-1 rounded-full bg-canvas/85 px-2 py-0.5 text-xs">
            <app-icon name="user" class="size-3" /> Comunidade
          </span>
        }
      </div>
      <div class="p-3">
        <a [routerLink]="['/diarios', d.id]" class="block truncate font-semibold outline-none after:absolute after:inset-0 group-hover:text-accent">
          {{ d.title }}
        </a>
        <p class="truncate text-sm text-fg-muted">{{ d.strainName }}{{ d.breeder ? ' · ' + d.breeder : '' }}</p>
        <p class="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-fg-muted">
          <a [routerLink]="['/growers', d.author]" class="relative z-10 font-medium text-fg hover:text-accent">{{ d.author }}</a>
          <span>{{ medium[d.medium] }}</span>
          @if (d.status !== 'IN_PROGRESS') {
            <span class="font-medium text-accent">{{ status[d.status] }}</span>
          }
          <span class="ml-auto inline-flex items-center gap-2">
            <span class="inline-flex items-center gap-0.5"><app-icon name="flame" class="size-3.5" />{{ d.reactionsCount }}</span>
            <span class="inline-flex items-center gap-0.5"><app-icon name="chat" class="size-3.5" />{{ d.commentsCount }}</span>
          </span>
        </p>
      </div>
    </article>
  `,
})
export class DiaryCardComponent {
  readonly diary = input.required<DiaryCard>();

  protected readonly stage = STAGE_LABEL;
  protected readonly medium = MEDIUM_LABEL;
  protected readonly status = STATUS_LABEL;
}
