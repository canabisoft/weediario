import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { DiariesApi } from '../core/api/diaries.api';

/**
 * Transforma a URL de uma foto de diário em algo que um {@code <img>} consegue exibir.
 * <p>
 * Diários públicos usam a URL direta (cacheável pelo navegador). Diários privados exigem o token de acesso, que
 * uma tag {@code <img>} não envia: a foto é baixada pela API autenticada e exibida como blob local, revogado
 * quando quem assinou deixa de precisar dele.
 */
@Injectable({ providedIn: 'root' })
export class PhotoSource {
  private readonly api = inject(DiariesApi);

  resolve(url: string, isPublic: boolean): Observable<string> {
    if (isPublic) {
      return of(url);
    }
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
