import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { catchError, map, of, switchMap } from 'rxjs';
import { AdminApi, AdminDashboard } from '../core/api/admin.api';
import { problemMessage } from '../core/api/problem';

interface Stat {
  readonly label: string;
  readonly value: number;
  readonly hint?: string;
}

/**
 * Métricas da plataforma para o admin: contas, conteúdo publicado, atividade recente e fila da moderação.
 * Tudo é contagem no banco; nada identifica uma pessoa e nada vem de ferramenta externa.
 */
@Component({
  selector: 'app-admin-metrics',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p class="mb-3 text-sm text-fg-muted">
      Contagens feitas no nosso banco na hora em que você abre esta aba. Sem rastreamento, sem cookies e sem dados de
      tratamento.
    </p>
    @if (state(); as s) {
      @if (s.error) {
        <p role="alert" class="text-sm text-danger">{{ s.error }}</p>
      } @else if (s.data; as d) {
        @for (group of groups(d); track group.title) {
          <section class="mb-5">
            <h2 class="mb-2 font-semibold">{{ group.title }}</h2>
            <dl class="grid grid-cols-2 gap-2 sm:grid-cols-4">
              @for (stat of group.stats; track stat.label) {
                <div class="wd-card">
                  <dt class="text-xs text-fg-muted">{{ stat.label }}</dt>
                  <dd class="text-2xl font-bold tabular-nums">{{ format(stat.value) }}</dd>
                  @if (stat.hint) {
                    <dd class="text-xs text-fg-muted">{{ stat.hint }}</dd>
                  }
                </div>
              }
            </dl>
          </section>
        }
        <div class="flex flex-wrap items-center gap-3 text-xs text-fg-muted">
          <span>Atualizado {{ time(d.generatedAt) }}</span>
          <button type="button" class="font-semibold text-accent underline" (click)="reload.update((n) => n + 1)">Atualizar</button>
        </div>
      }
    } @else {
      <div class="h-40 animate-pulse rounded-2xl bg-surface" aria-busy="true"></div>
    }
  `,
})
export class AdminMetrics {
  private readonly api = inject(AdminApi);
  protected readonly reload = signal(0);

  protected readonly state = toSignal(
    toObservable(this.reload).pipe(
      switchMap(() =>
        this.api.metrics().pipe(
          map((data) => ({ data, error: null as string | null })),
          catchError((error) => of({ data: null, error: problemMessage(error) })),
        ),
      ),
    ),
  );

  protected groups(d: AdminDashboard): { title: string; stats: Stat[] }[] {
    return [
      {
        title: 'Contas',
        stats: [
          { label: 'Cadastradas', value: d.accounts.total },
          { label: 'Ativas', value: d.accounts.active, hint: 'nem suspensas nem banidas' },
          { label: 'Suspensas', value: d.accounts.suspended },
          { label: 'Banidas', value: d.accounts.banned },
        ],
      },
      {
        title: 'Conteúdo publicado',
        stats: [
          { label: 'Total visível', value: d.published },
          { label: 'Diários públicos', value: d.content.publicDiaries, hint: `${this.format(d.content.privateDiaries)} privados` },
          { label: 'Updates', value: d.content.diaryUpdates },
          { label: 'Comentários', value: d.content.comments + d.content.recipeComments },
          { label: 'Receitas públicas', value: d.content.publicRecipes },
          { label: 'Avaliações', value: d.content.reviews },
        ],
      },
      {
        title: 'Atividade recente',
        stats: [
          { label: 'Growers ativos (7 dias)', value: d.last7Days.activeGrowers, hint: `${this.format(d.last30Days.activeGrowers)} em 30 dias` },
          { label: 'Contas novas (7 dias)', value: d.last7Days.newAccounts, hint: `${this.format(d.last30Days.newAccounts)} em 30 dias` },
          { label: 'Updates (7 dias)', value: d.last7Days.diaryUpdates, hint: `${this.format(d.last30Days.diaryUpdates)} em 30 dias` },
          { label: 'Diários novos (7 dias)', value: d.last7Days.newDiaries, hint: `${this.format(d.last30Days.newDiaries)} em 30 dias` },
        ],
      },
      {
        title: 'Moderação e equipe',
        stats: [
          { label: 'Na fila', value: d.moderation.pending, hint: `${this.format(d.moderation.held)} retidos pelo filtro` },
          { label: 'Decisões (30 dias)', value: d.moderation.decisionsLast30Days },
          { label: 'Moderadores', value: d.accounts.moderators },
          { label: 'Admins', value: d.accounts.admins },
        ],
      },
    ];
  }

  protected format(value: number): string {
    return value.toLocaleString('pt-BR');
  }

  protected time(iso: string): string {
    return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
  }
}
