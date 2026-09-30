import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
import { PhotoSource } from './photo-source';

/**
 * Foto de um diário em cards e listas. Recebe a URL já pronta da API (normalmente a {@code thumbnailUrl}).
 */
@Component({
  selector: 'app-diary-photo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block overflow-hidden bg-surface-raised' },
  template: `
    @if (src(); as url) {
      <img [src]="url" [alt]="alt()" class="size-full object-cover" loading="lazy" decoding="async" draggable="false" />
    }
  `,
})
export class DiaryPhoto {
  private readonly source = inject(PhotoSource);

  readonly url = input.required<string>();
  readonly isPublic = input(true);
  readonly alt = input('');

  protected readonly src = signal<string | null>(null);

  constructor() {
    effect((onCleanup) => {
      const subscription = this.source.resolve(this.url(), this.isPublic()).subscribe({
        next: (src) => this.src.set(src),
        error: () => this.src.set(null),
      });
      onCleanup(() => {
        subscription.unsubscribe();
        this.src.set(null);
      });
    });
  }
}
