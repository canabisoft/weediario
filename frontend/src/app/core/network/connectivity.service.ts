import { DOCUMENT, Injectable, inject, signal } from '@angular/core';

/**
 * Estado da conexão. Dentro da tenda o sinal costuma ser ruim; as ferramentas continuam funcionando
 * offline graças ao service worker, e a interface avisa quando algo depende da rede.
 */
@Injectable({ providedIn: 'root' })
export class ConnectivityService {
  private readonly window = inject(DOCUMENT).defaultView;

  readonly online = signal(this.window?.navigator.onLine ?? true);

  constructor() {
    this.window?.addEventListener('online', () => this.online.set(true));
    this.window?.addEventListener('offline', () => this.online.set(false));
  }
}
