import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { DiariesApi } from '../core/api/diaries.api';

/**
 * Transforma a URL de uma foto de diário em algo que um {@code <img>} consegue exibir.
 * <p>
 * Comunidade fechada: toda foto exige o token de acesso, que uma tag {@code <img>} não envia. A foto é baixada pela
 * API autenticada (o navegador a guarda no cache privado dele) e exibida como blob local, revogado quando quem
 * assinou deixa de precisar dele.
 */
@Injectable({ providedIn: 'root' })
export class PhotoSource {
  private readonly api = inject(DiariesApi);

  resolve(url: string): Observable<string> {
    // Não completa de propósito: o teardown (que revoga o blob) só deve rodar quando a foto sair da tela.
    return new Observable<string>((subscriber) => {
      let objectUrl: string | null = null;
      const download = this.api.photoBlob(url).subscribe({
        next: (blob) => {
          objectUrl = URL.createObjectURL(blob);
          subscriber.next(objectUrl);
        },
        error: (error) => subscriber.error(error),
      });
      return () => {
        download.unsubscribe();
        if (objectUrl) {
          URL.revokeObjectURL(objectUrl);
        }
      };
    });
  }
}
