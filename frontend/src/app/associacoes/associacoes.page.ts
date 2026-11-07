import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import {
  Associacao,
  CATALOGO_EM,
  CATEGORIAS,
  CATEGORIAS_CHAVES,
  Categoria,
  LEVANTADO_EM,
  ORIGEM,
  UFS,
  filtrar,
  linkEmail,
  linkTelefone,
  linkWhatsapp,
  listar,
} from './associacoes.data';

/**
 * Diretório gratuito de associações de pacientes de cannabis medicinal, por estado. Página pública e pré-renderizada:
 * a lista inteira está no HTML (o filtro por estado só esconde itens no navegador).
 * <p>
 * Só nome, cidade, estado, logotipo e os canais da própria associação (WhatsApp, e-mail, telefone, site), mais a
 * categoria genérica de produto que ela informa ter (óleo, pomada...), atribuída a ela: sem nome de produto, imagem,
 * concentração, preço, sem redirecionador e sem contagem de cliques. O contato sai com "vim do weediario.com". O weediário não usa analytics nem recebe comissão por esta lista.
 */
@Component({
  selector: 'app-associacoes-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header>
      <h1 class="text-2xl font-bold sm:text-3xl">Associações de cannabis medicinal</h1>
      <p class="mt-2 max-w-2xl text-fg-muted">
        Encontre uma associação de pacientes no seu estado e fale direto com ela. A lista é grátis,
        sem cadastro, e o weediário não recebe nada por ela.
      </p>
    </header>

    <section
      class="mt-6 rounded-2xl border border-line bg-surface p-4"
      aria-labelledby="assoc-comecar"
    >
      <h2 id="assoc-comecar" class="text-lg font-semibold">Sem receita ainda?</h2>
      <p class="mt-1 text-sm">
        O primeiro passo é a consulta com um médico que prescreve cannabis. Com a receita, o produto
        vem de uma associação, de uma empresa ou da farmácia, e cada caminho tem as suas regras.
        Quem já tem receita pode ir direto à lista abaixo.
      </p>
      <p class="mt-2 text-sm">
        <strong>Não sabe a qual médico ir?</strong> A maioria das associações tem médicos
        prescritores no seu acolhimento e pode indicar um profissional. Fale com a associação do seu
        estado e pergunte pelos médicos dela, antes de procurar por conta própria.
      </p>
    </section>

    <section class="mt-8" aria-labelledby="assoc-lista">
      <h2 id="assoc-lista" class="text-lg font-semibold">Associações por estado</h2>

      <div class="mt-3 max-w-xs">
        <label for="assoc-uf" class="text-sm font-medium">Seu estado</label>
        <select id="assoc-uf" class="wd-input mt-1" (change)="escolher($event)">
          <option value="">Todo o Brasil</option>
          @for (uf of ufs; track uf) {
            <option [value]="uf">{{ uf }}</option>
          }
        </select>
      </div>

      <div class="mt-3 max-w-xs">
        <label for="assoc-cat" class="text-sm font-medium">Tipo de produto (opcional)</label>
        <select id="assoc-cat" class="wd-input mt-1" (change)="escolherCategoria($event)">
          <option value="">Qualquer</option>
          @for (c of categoriasChaves; track c) {
            <option [value]="c">{{ categorias[c] }}</option>
          }
        </select>
      </div>
      @if (categoria()) {
        <p class="mt-2 max-w-2xl text-xs text-fg-muted" role="status">
          Aparecem só as associações que dizem ter {{ nomeCategoria() }} no catálogo delas (lido em
          {{ catalogoEm }}). Outras podem ter também: o catálogo de muitas ainda não foi levantado.
          Fale com a associação para confirmar.
        </p>
      }

      <p class="mt-3 text-sm text-fg-muted" aria-live="polite">{{ resumo() }}</p>

      @if (!algumNoEstado()) {
        <p class="mt-2 text-sm" role="status">
          Ainda não temos associação confirmada no seu estado ({{ uf() }}).
          @if (visiveis().length) {
            Estas atendem outros estados:
          }
        </p>
      }

      <ul class="mt-3 grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        @for (a of ordenadas(); track a.slug) {
          <li
            class="flex flex-col overflow-hidden rounded-2xl border border-line bg-surface"
            [class.hidden]="!mostra(a)"
            [attr.data-uf]="a.uf"
          >
            <div class="flex items-center gap-3 bg-accent/10 px-4 py-3">
              @if (a.logo) {
                <img
                  [src]="'/associacoes/' + a.logo"
                  alt=""
                  width="80"
                  height="56"
                  loading="lazy"
                  class="h-14 w-20 shrink-0 rounded-xl bg-white object-contain p-1.5"
                />
              } @else {
                <span
                  aria-hidden="true"
                  class="grid size-14 shrink-0 place-items-center rounded-full bg-accent/20 text-base font-bold text-accent"
                  >{{ iniciais(a) }}</span
                >
              }
              <div class="min-w-0">
                <h3 class="truncate text-base font-bold">{{ a.nome }}</h3>
                <p class="text-sm text-fg-muted">{{ a.cidade ? a.cidade + ', ' : '' }}{{ a.uf }}</p>
              </div>
            </div>

            <div class="flex flex-1 flex-col gap-2 p-4 text-sm">
              @if (a.outrosEstados) {
                <p>
                  <span
                    class="rounded-full bg-accent/15 px-2.5 py-0.5 text-xs font-bold text-accent"
                    >Atende outros estados</span
                  >
                </p>
              }
              @if (a.resumo) {
                <p>{{ a.resumo }}</p>
              }
              @if (a.categorias.length) {
                <div>
                  <p class="text-xs font-semibold text-fg-muted">
                    A associação informa ter no catálogo
                  </p>
                  <ul class="mt-1 flex flex-wrap gap-1.5">
                    @for (c of a.categorias; track c) {
                      <li
                        class="rounded-full border border-accent/40 bg-accent/10 px-2.5 py-0.5 text-xs font-semibold text-accent"
                      >
                        {{ categorias[c] }}
                      </li>
                    }
                  </ul>
                </div>
              }

              <div class="mt-auto flex flex-wrap gap-1.5 pt-2">
                @if (whatsapp(a); as wa) {
                  <a
                    [href]="wa"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="inline-flex min-h-9 items-center rounded-full bg-accent px-3.5 text-xs font-bold text-accent-fg hover:opacity-90"
                    >WhatsApp</a
                  >
                }
                @if (email(a); as mail) {
                  <a
                    [href]="mail"
                    class="inline-flex min-h-9 items-center rounded-full border border-accent px-3.5 text-xs font-bold text-accent hover:bg-accent/10"
                    >E-mail</a
                  >
                }
                @if (a.telefone; as fone) {
                  <a
                    [href]="telefone(a)"
                    class="inline-flex min-h-9 items-center rounded-full border border-accent px-3.5 text-xs font-bold text-accent hover:bg-accent/10"
                    >Ligar {{ fone }}</a
                  >
                }
                <a
                  [href]="a.site"
                  target="_blank"
                  rel="noopener noreferrer"
                  class="inline-flex min-h-9 items-center rounded-full border border-line px-3.5 text-xs font-bold text-fg hover:border-accent/60"
                  >Site</a
                >
              </div>
            </div>
          </li>
        }
      </ul>

      <p class="mt-6 max-w-2xl text-xs text-fg-muted">
        Ao escrever ou ligar, diga que veio do {{ origem }}: o e-mail e o WhatsApp já saem com essa
        mensagem. Os logotipos são das próprias associações. Lista informativa, sem ordem de
        preferência e sem pagamento de ninguém, levantada em {{ levantadoEm }} a partir dos sites
        das próprias associações. Confirme com a associação se ela atende você e se está regular. O
        weediário não vende produtos, não indica dose e não substitui a consulta médica. Se você
        representa uma associação e quer corrigir ou retirar os dados, fale com a gente pelo
        repositório no GitHub.
      </p>
    </section>
  `,
})
export class AssociacoesPage {
  protected readonly ufs = UFS;
  protected readonly levantadoEm = LEVANTADO_EM;
  protected readonly catalogoEm = CATALOGO_EM;
  protected readonly categorias = CATEGORIAS;
  protected readonly categoriasChaves = CATEGORIAS_CHAVES;
  protected readonly categoria = signal<Categoria | ''>('');
  protected readonly nomeCategoria = computed(() => {
    const c = this.categoria();
    return c ? CATEGORIAS[c].toLowerCase() : '';
  });
  protected readonly origem = ORIGEM;
  protected readonly whatsapp = linkWhatsapp;
  protected readonly email = linkEmail;
  protected readonly telefone = (a: Associacao) => linkTelefone(a) ?? '';
  protected readonly uf = signal('');
  /** A ordem muda com o estado: as do estado vêm primeiro. */
  protected readonly visiveis = computed(() => filtrar(this.uf(), this.categoria()));
  protected readonly algumNoEstado = computed(
    () => !this.uf() || this.visiveis().some((a) => a.uf === this.uf()),
  );
  protected readonly resumo = computed(() => {
    const n = this.visiveis().length;
    const onde = this.uf() ? ` para ${this.uf()}` : ' no Brasil';
    return n ? `${n} ${n === 1 ? 'associação' : 'associações'}${onde}` : '';
  });
  protected readonly ordenadas = computed(() => listar(this.uf()));

  protected iniciais(a: Associacao): string {
    return a.nome
      .split(/\s+/)
      .slice(0, 2)
      .map((p) => p.charAt(0))
      .join('')
      .toUpperCase();
  }

  protected escolher(evento: Event): void {
    this.uf.set((evento.target as HTMLSelectElement).value);
  }

  protected escolherCategoria(evento: Event): void {
    const v = (evento.target as HTMLSelectElement).value;
    this.categoria.set(CATEGORIAS_CHAVES.find((c) => c === v) ?? '');
  }

  protected mostra(a: Associacao): boolean {
    return this.visiveis().includes(a);
  }
}
