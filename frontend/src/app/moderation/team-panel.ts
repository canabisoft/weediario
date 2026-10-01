import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, of, switchMap } from 'rxjs';
import { AdminApi, TeamMember } from '../core/api/admin.api';
import { problemMessage } from '../core/api/problem';

const ROLE_LABEL: Record<TeamMember['role'], string> = { MODERATOR: 'moderador', ADMIN: 'admin' };
const STATUS_LABEL: Record<TeamMember['status'], string> = { ACTIVE: 'ativa', SUSPENDED: 'suspensa', BANNED: 'banida' };

/**
 * Equipe (só admin): quem é moderador ou admin, promover um grower a moderador e tirar um moderador da equipe.
 * Toda mudança vai para o log de moderação. Admins não são criados nem removidos por aqui.
 */
@Component({
  selector: 'app-team-panel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <form class="wd-card grid gap-2" (submit)="$event.preventDefault(); promote()" aria-labelledby="promote-title">
      <h2 id="promote-title" class="font-semibold">Promover a moderador</h2>
      <p class="text-sm text-fg-muted">
        Moderadores cuidam da fila de denúncias e do catálogo, mas nunca agem sobre outra conta da equipe. A mudança
        vale na hora e fica no log.
      </p>
      <div class="grid gap-2 sm:grid-cols-[1fr_2fr_auto]">
        <input class="wd-input" maxlength="30" placeholder="Apelido" aria-label="Apelido de quem será promovido"
               [value]="username()" (input)="username.set($any($event.target).value)" />
        <input class="wd-input" maxlength="500" placeholder="Motivo (opcional, vai para o log)" aria-label="Motivo"
               [value]="note()" (input)="note.set($any($event.target).value)" />
        <button type="submit" class="wd-button" [disabled]="!username().trim() || busy()">Promover</button>
      </div>
    </form>

    @if (message()) {
      <p role="status" class="mt-3 text-sm">{{ message() }}</p>
    }

    <h2 class="mb-2 mt-5 font-semibold">Equipe atual</h2>
    @if (team(); as members) {
      <ul class="grid gap-2">
        @for (member of members; track member.username) {
          <li class="wd-card flex flex-wrap items-center gap-3">
            <a [routerLink]="['/growers', member.username]" class="min-w-0 flex-1 truncate font-semibold hover:text-accent">{{ member.username }}</a>
            <span class="rounded-full px-2 py-0.5 text-xs font-semibold"
                  [class]="member.role === 'ADMIN' ? 'bg-accent/15 text-accent' : 'bg-surface-raised text-fg-muted'">
              {{ roleLabel[member.role] }}
            </span>
            @if (member.status !== 'ACTIVE') {
              <span class="rounded-full bg-danger/10 px-2 py-0.5 text-xs font-semibold text-danger">{{ statusLabel[member.status] }}</span>
            }
            @if (member.role === 'MODERATOR') {
              @if (confirming() === member.username) {
                <span class="flex items-center gap-2 text-sm">
                  Tirar da equipe?
                  <button type="button" class="font-semibold text-danger underline" [disabled]="busy()" (click)="remove(member.username)">Sim</button>
                  <button type="button" class="underline" (click)="confirming.set(null)">Não</button>
                </span>
              } @else {
                <button type="button" class="text-sm text-danger underline" (click)="confirming.set(member.username)">Tirar da equipe</button>
              }
            }
          </li>
        } @empty {
          <li class="wd-card text-center text-fg-muted">Ninguém na equipe.</li>
        }
      </ul>
    } @else {
      <div class="h-24 animate-pulse rounded-2xl bg-surface" aria-busy="true"></div>
    }
  `,
})
export class TeamPanel {
  private readonly api = inject(AdminApi);
  private readonly reload = signal(0);

  protected readonly roleLabel = ROLE_LABEL;
  protected readonly statusLabel = STATUS_LABEL;
  protected readonly username = signal('');
  protected readonly note = signal('');
  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly confirming = signal<string | null>(null);

  protected readonly team = toSignal(
    toObservable(this.reload).pipe(switchMap(() => this.api.team().pipe(catchError(() => of([] as TeamMember[]))))),
  );

  protected promote(): void {
    const username = this.username().trim();
    this.busy.set(true);
    this.api.promote(username, this.note().trim() || undefined).subscribe({
      next: () => this.done(`${username} agora é moderador.`, true),
      error: (error) => this.done(problemMessage(error), false),
    });
  }

  protected remove(username: string): void {
    this.busy.set(true);
    this.api.removeFromTeam(username).subscribe({
      next: () => this.done(`${username} saiu da equipe.`, false),
      error: (error) => this.done(problemMessage(error), false),
    });
  }

  private done(message: string, clearForm: boolean): void {
    this.busy.set(false);
    this.confirming.set(null);
    this.message.set(message);
    if (clearForm) {
      this.username.set('');
      this.note.set('');
    }
    this.reload.update((n) => n + 1);
  }
}
