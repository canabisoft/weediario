import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, input, signal } from '@angular/core';
import { DiariesApi, PhotoSize } from '../core/api/diaries.api';

/**
 * Foto de um diário.
 * <p>
 * Diários públicos usam a URL direta (cacheável). Diários privados exigem o token de acesso, que uma tag
 * {@code <img>} não envia: nesse caso a foto é baixada pela API autenticada e exibida como blob local.
 */
@Component({
  selector: 'app-diary-photo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block overflow-hidden bg-surface-raised' },
  template: `
    @if (src(); as url) {
      <img [src]="url" [alt]="alt()" class="size-full object-cover" loading="lazy" decoding="async" />
    }
  `,
})
export class DiaryPhoto {
  private readonly api = inject(DiariesApi);

  readonly diaryId = input.required<string>();
  readonly photoId = input.required<string>();
  readonly size = input<PhotoSize>('THUMBNAIL');
  readonly isPublic = input(true);
  readonly alt = input('');

  protected readonly src = signal<string | null>(null);
  private objectUrl: string | null = null;

  constructor() {
    effect((onCleanup) => {
      const diaryId = this.diaryId();
      const photoId = this.photoId();
      const size = this.size();
      if (this.isPublic()) {
        this.src.set(this.api.photoUrl(diaryId, photoId, size));
        return;
      }
      const subscription = this.api.photoBlob(diaryId, photoId, size).subscribe({
        next: (blob) => {
          this.revoke();
          this.objectUrl = URL.createObjectURL(blob);
          this.src.set(this.objectUrl);
        },
        error: () => this.src.set(null),
      });
      onCleanup(() => subscription.unsubscribe());
    });
    inject(DestroyRef).onDestroy(() => this.revoke());
  }

  private revoke(): void {
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
      this.objectUrl = null;
    }
  }
}
