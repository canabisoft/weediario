import { HttpClient, HttpEvent, HttpEventType } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, filter, map } from 'rxjs';
import { GrowEnvironment, GrowSetup, MyProfile, SetupSpec, UploadedImage } from './api.models';
import { compact } from './diaries.api';

export interface SetupInput extends SetupSpec {
  readonly name: string;
  readonly environment: GrowEnvironment;
  readonly productIds?: string[];
}

/** Configurações de segurança da conta. O e-mail volta mascarado (`br***@gm***.com`), nunca inteiro. */
export interface AccountSecurity {
  readonly hasPassword: boolean;
  readonly hasRecoveryPhrase: boolean;
  readonly email: string | null;
  readonly emailVerified: boolean;
  readonly events: readonly SecurityEvent[];
}

/** Mudança sensível na conta (sem IP, aparelho ou local). */
export interface SecurityEvent {
  readonly kind: string;
  readonly description: string;
  readonly occurredAt: string;
}

/** Progresso de upload (0–100) e, ao final, a imagem já sanitizada pelo servidor. */
export type UploadProgress = { readonly progress: number } | { readonly progress: 100; readonly image: UploadedImage };

/** Recursos da conta do usuário logado: perfil, segurança, setups e upload de fotos. */
@Injectable({ providedIn: 'root' })
export class AccountApi {
  private readonly http = inject(HttpClient);

  updateBio(bio: string): Observable<MyProfile> {
    return this.http.put<MyProfile>('/api/v1/me/profile', { bio: bio.trim() || null });
  }

  setAvatar(imageId: string): Observable<MyProfile> {
    return this.http.put<MyProfile>('/api/v1/me/avatar', { imageId });
  }

  removeAvatar(): Observable<MyProfile> {
    return this.http.delete<MyProfile>('/api/v1/me/avatar');
  }

  security(): Observable<AccountSecurity> {
    return this.http.get<AccountSecurity>('/api/v1/me/security');
  }

  /** Cadastra, troca ou (com `null`) remove o e-mail. Exige a senha atual. */
  changeEmail(currentPassword: string, email: string | null): Observable<AccountSecurity> {
    return this.http.put<AccountSecurity>('/api/v1/me/email', { currentPassword, email });
  }

  resendEmailVerification(): Observable<void> {
    return this.http.post<void>('/api/v1/me/email/resend', {});
  }

  /** Pacote .zip com todos os dados da conta (dados.json + fotos). Exige a senha atual. */
  exportData(currentPassword: string): Observable<Blob> {
    return this.http.post('/api/v1/me/export', { currentPassword }, { responseType: 'blob' });
  }

  /** Apaga a conta e tudo o que é dela. Exige a senha atual e o apelido digitado. */
  deleteAccount(currentPassword: string, username: string): Observable<void> {
    return this.http.post<void>('/api/v1/me/delete', { currentPassword, username });
  }

  setups(): Observable<GrowSetup[]> {
    return this.http.get<GrowSetup[]>('/api/v1/me/setups');
  }

  createSetup(setup: SetupInput): Observable<GrowSetup> {
    return this.http.post<GrowSetup>('/api/v1/me/setups', compact(setup));
  }

  updateSetup(id: string, setup: SetupInput): Observable<GrowSetup> {
    return this.http.put<GrowSetup>(`/api/v1/me/setups/${id}`, compact(setup));
  }

  deleteSetup(id: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/me/setups/${id}`);
  }

  /**
   * Envia uma foto. O servidor remove todos os metadados (GPS, aparelho, data) e devolve o id da versão limpa.
   */
  uploadImage(file: File): Observable<UploadProgress> {
    const form = new FormData();
    form.append('file', file);
    return this.http
      .post<UploadedImage>('/api/v1/media/images', form, { reportProgress: true, observe: 'events' })
      .pipe(
        filter((event: HttpEvent<UploadedImage>) =>
          event.type === HttpEventType.UploadProgress || event.type === HttpEventType.Response,
        ),
        map((event) => {
          if (event.type === HttpEventType.Response) {
            return { progress: 100 as const, image: event.body! };
          }
          const total = event.type === HttpEventType.UploadProgress ? (event.total ?? 0) : 0;
          const loaded = event.type === HttpEventType.UploadProgress ? event.loaded : 0;
          // Mantém em 95% até o servidor terminar de processar a imagem.
          return { progress: total ? Math.min(95, Math.round((loaded / total) * 100)) : 0 };
        }),
      );
  }
}
