import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, filter, map, of, switchMap } from 'rxjs';
import { DiaryCard, FollowedGrower } from '../core/api/api.models';
import { AccountApi } from '../core/api/account.api';
import { DiariesApi } from '../core/api/diaries.api';
import { problemMessage } from '../core/api/problem';
import { SocialApi } from '../core/api/social.api';
import { AuthService } from '../core/auth/auth.service';
import { DiaryCardComponent } from '../diaries/diary-card';
import { Avatar } from '../shared/avatar';
import { Icon } from '../shared/icon';
import { AccountSecurityPanel } from './account-security';
import { AccountDataPanel } from './account-data';

/**
 * Área do grower logado: perfil (bio e foto), segurança da conta, diários (inclusive privados), quem ele segue e sair.
 */
@Component({
  selector: 'app-my-profile-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, Avatar, Icon, DiaryCardComponent, AccountSecurityPanel, AccountDataPanel],
  template: `
    @if (auth.me(); as me) {
      <section class="wd-card grid gap-4">
        <div class="flex flex-wrap items-center gap-4">
          <app-avatar class="size-20 text-2xl" [username]="me.username" [hasAvatar]="me.hasAvatar" [version]="avatarVersion()" />
          <div class="min-w-0 flex-1">
            <h1 class="text-2xl font-bold">{{ me.username }}</h1>
            <a [routerLink]="['/growers', me.username]" class="text-sm text-accent">Ver meu perfil público</a>
          </div>
        </div>

        <div class="flex flex-wrap gap-2">
          <label class="wd-button cursor-pointer bg-surface-raised text-fg">
            <app-icon name="camera" class="size-5" /> {{ me.hasAvatar ? 'Trocar foto' : 'Adicionar foto' }}
            <input type="file" accept="image/jpeg,image/png,image/webp" class="sr-only" (change)="uploadAvatar($event)" />
          </label>
          @if (me.hasAvatar) {
            <button type="button" class="wd-button bg-surface-raised text-fg" (click)="removeAvatar()">Remover foto</button>
          }
        </div>
        <p class="text-xs text-fg-muted">Sua foto passa pela mesma faxina das fotos dos diários: sem GPS, sem modelo do celular, sem data. Dica: evite mostrar o rosto.</p>

        <form class="grid gap-2" (submit)="$event.preventDefault(); saveBio()">
          <label class="wd-label" for="bio">Bio</label>
          <textarea id="bio" rows="3" maxlength="280" class="wd-input py-2 text-base" [formControl]="bio" placeholder="Há quanto tempo cultiva, estilo preferido…"></textarea>
          <div class="flex items-center gap-3">
            <button type="submit" class="wd-button" [disabled]="bio.invalid || busy()">Salvar bio</button>
            <span class="text-xs text-fg-muted">{{ bio.value.length }}/280</span>
          </div>
        </form>

        @if (message()) {
          <p role="status" class="text-sm text-fg-muted">{{ message() }}</p>
        }
      </section>

      <app-account-security [justRegisteredWithEmail]="email() === 'confirmar'" />

      <div class="mt-6 flex items-center justify-between">
        <h2 class="text-lg font-semibold">Meus diários</h2>
        <a routerLink="/diarios/novo" class="text-sm font-semibold text-accent">+ Novo diário</a>
      </div>
      <ul class="mt-3 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        @for (diary of myDiaries(); track diary.id) {
          <li><app-diary-card [diary]="diary" /></li>
        } @empty {
          <li class="wd-card text-center text-fg-muted sm:col-span-2 lg:col-span-3">Você ainda não tem diários.</li>
        }
      </ul>

      <h2 class="mt-8 text-lg font-semibold">Quem eu sigo</h2>
      <p class="text-xs text-fg-muted">Só você vê esta lista.</p>
      <ul class="mt-3 flex flex-wrap gap-2">
        @for (grower of following(); track grower.username) {
          <li>
            <a [routerLink]="['/growers', grower.username]" class="flex items-center gap-2 rounded-full border border-line py-1 pl-1 pr-3 text-sm">
              <app-avatar class="size-8 text-xs" [username]="grower.username" [hasAvatar]="grower.hasAvatar" />
              {{ grower.username }}
            </a>
          </li>
        } @empty {
          <li class="text-sm text-fg-muted">Você ainda não segue ninguém. Descubra growers no <a routerLink="/growers" class="text-accent">ranking</a>.</li>
        }
      </ul>

      <app-account-data [username]="me.username" />

      <button type="button" class="wd-button mt-10 bg-surface-raised text-danger" (click)="logout()">
        <app-icon name="logout" class="size-5" /> Sair
      </button>
    }
  `,
})
export class MyProfilePage {
  /** {@code ?email=confirmar}: acabou de se cadastrar com e-mail (lembrete de confirmar). */
  readonly email = input<string | undefined>();
  private readonly account = inject(AccountApi);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  protected readonly bio = new FormControl(this.auth.me()?.bio ?? '', { nonNullable: true, validators: [Validators.maxLength(280)] });
  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly avatarVersion = signal(0);

  protected readonly myDiaries = toSignal(
    inject(DiariesApi).mine().pipe(map((page) => page.items), catchError(() => of<DiaryCard[]>([]))),
    { initialValue: [] },
  );
  protected readonly following = toSignal(inject(SocialApi).following().pipe(catchError(() => of<FollowedGrower[]>([]))), {
    initialValue: [],
  });

  protected saveBio(): void {
    this.busy.set(true);
    this.account.updateBio(this.bio.value).subscribe({
      next: (profile) => {
        this.auth.updateMe(profile);
        this.message.set('Bio atualizada.');
        this.busy.set(false);
      },
      error: (error) => {
        this.message.set(problemMessage(error));
        this.busy.set(false);
      },
    });
  }

  protected uploadAvatar(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    this.message.set('Enviando foto…');
    this.account
      .uploadImage(file)
      .pipe(
        filter((progress) => 'image' in progress),
        switchMap((progress) => this.account.setAvatar(('image' in progress ? progress.image : null)!.id)),
      )
      .subscribe({
        next: (profile) => {
          this.auth.updateMe(profile);
          this.avatarVersion.update((v) => v + 1);
          this.message.set('Foto atualizada.');
        },
        error: (error) => this.message.set(problemMessage(error)),
      });
  }

  protected removeAvatar(): void {
    this.account.removeAvatar().subscribe({
      next: (profile) => {
        this.auth.updateMe(profile);
        this.message.set('Foto removida.');
      },
      error: (error) => this.message.set(problemMessage(error)),
    });
  }

  protected async logout(): Promise<void> {
    await this.auth.logout();
    await this.router.navigateByUrl('/');
  }
}
