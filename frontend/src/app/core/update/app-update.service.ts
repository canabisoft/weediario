import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { filter, interval } from 'rxjs';

const CHECK_EVERY_MS = 30 * 60_000;

/**
 * O app é um PWA: o service worker guarda a versão antiga em cache. Quando uma versão nova termina de baixar,
 * avisamos a pessoa em vez de recarregar sozinho (não perde o que estiver sendo digitado).
 */
@Injectable({ providedIn: 'root' })
export class AppUpdateService {
  private readonly updates = inject(SwUpdate, { optional: true });

  /** Versão nova baixada, esperando recarregar. */
  readonly available = signal(false);

  constructor() {
    const updates = this.updates;
    if (!updates?.isEnabled) {
      return;
    }
    const destroyRef = inject(DestroyRef);
    const sub = updates.versionUpdates
      .pipe(filter((event): event is VersionReadyEvent => event.type === 'VERSION_READY'))
      .subscribe(() => this.available.set(true));
    // Quem deixa o app aberto por horas também fica sabendo.
    const timer = interval(CHECK_EVERY_MS).subscribe(() => updates.checkForUpdate().catch(() => false));
    destroyRef.onDestroy(() => {
      sub.unsubscribe();
      timer.unsubscribe();
    });
  }

  async apply(): Promise<void> {
    if (this.updates?.isEnabled) {
      await this.updates.activateUpdate().catch(() => false);
    }
    document.location.reload();
  }
}
