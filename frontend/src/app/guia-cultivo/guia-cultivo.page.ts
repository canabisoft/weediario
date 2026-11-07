import { DOCUMENT, KeyValuePipe, Location } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { skip } from 'rxjs';
import { AuthService } from '../core/auth/auth.service';
import { GuiaBlocos } from './guia-blocos';
import { COMPRAS, ETAPAS, FOTOS } from './guia-cultivo.data';
import { GuiaEstado } from './guia-estado';
import { GuiaLojas } from './guia-lojas';
import { GuiaPlanta } from './guia-planta';

type EstadoNo = 'feito' | 'atual' | 'todo';

/**
 * Guia de cultivo em casa, em nove etapas. Página pública e pré-renderizada: o texto de TODAS as etapas está no HTML
 * (dentro do popup, fechado), para quem lê sem JavaScript e para os robôs; no navegador só a etapa escolhida aparece.
 * <p>
 * Sem analytics e sem redirecionador de links. As miniaturas dos vídeos são cópias locais; o player do YouTube
 * (youtube-nocookie) só é criado quando a pessoa clica no vídeo, e a CSP libera só esse endereço em frame-src.
 * O progresso fica no aparelho (GuiaEstado).
 */
@Component({
  selector: 'app-guia-cultivo-page',
  imports: [KeyValuePipe, RouterLink, GuiaBlocos, GuiaLojas, GuiaPlanta],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './guia-cultivo.page.html',
})
export class GuiaCultivoPage {
  protected readonly etapas = ETAPAS;
  protected readonly fotos = FOTOS;
  protected readonly compras = COMPRAS;
  protected readonly estado = inject(GuiaEstado);
  protected readonly auth = inject(AuthService);

  private readonly document = inject(DOCUMENT);
  private readonly location = inject(Location);
  private readonly rota = inject(ActivatedRoute);
  private readonly injector = inject(Injector);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly destruir = inject(DestroyRef);
  private readonly dialogo = viewChild<ElementRef<HTMLDialogElement>>('dialogo');
  private readonly conteudo = viewChild<ElementRef<HTMLElement>>('conteudo');
  private gatilho: Element | null = null;

  /** Vídeo que está tocando (um por vez); null = nenhum. */
  protected readonly tocando = signal<string | null>(null);

  /** Só ids no formato do YouTube viram endereço do player; qualquer outra coisa não gera iframe. */
  protected readonly urlTocando = computed<SafeResourceUrl | null>(() => {
    const id = this.tocando();
    return id && /^[\w-]{11}$/.test(id)
      ? this.sanitizer.bypassSecurityTrustResourceUrl(
          `https://www.youtube-nocookie.com/embed/${id}?autoplay=1&rel=0`,
        )
      : null;
  });

  /** Etapa aberta no popup; null = fechado. */
  protected readonly aberta = signal<number | null>(null);

  protected readonly concluidaPorEtapa = computed(() =>
    ETAPAS.map((e) => e.checklist.every((_, j) => this.estado.marcado(e.id, j))),
  );
  protected readonly concluidas = computed(() => this.concluidaPorEtapa().filter(Boolean).length);

  /** Etapa que a pessoa abriu por último; sem isso, a primeira que ainda não terminou. */
  protected readonly destaque = computed(() => {
    const atual = this.estado.atual();
    if (atual !== null) {
      return atual;
    }
    const proxima = this.concluidaPorEtapa().indexOf(false);
    return proxima === -1 ? ETAPAS.length - 1 : proxima;
  });

  /** Fase que está no meio da tela (a linha se preenche até ela e a planta mostra a sua fase); null até a pessoa rolar. */
  private readonly vista = signal<number | null>(null);
  protected readonly faseVista = computed(() => this.vista() ?? this.destaque());

  protected readonly status = computed(() => {
    const feitas = this.concluidas();
    if (feitas === 0 && this.estado.feitas().size === 0) {
      return 'Role para ver as fases da planta e toque em "Ler a etapa". Marque os itens para acompanhar o seu progresso.';
    }
    return feitas === ETAPAS.length
      ? 'Trilha completa. Bom cultivo e boa colheita.'
      : `${feitas} de ${ETAPAS.length} etapas concluídas.`;
  });

  constructor() {
    // Link direto para uma etapa: /guia-de-cultivo#etapa-3. Só no navegador (o popup não existe no HTML pronto).
    afterNextRender(() => {
      this.seguirFragmento(this.rota.snapshot.fragment);
      this.acompanharRolagem();
    });
    // Com a página já aberta, mudar o #etapa-N (link do app, endereço editado) também troca a etapa. O replaceState que
    // a própria página faz não passa pelo roteador, então não há laço.
    this.rota.fragment
      .pipe(skip(1), takeUntilDestroyed())
      .subscribe((f) => this.seguirFragmento(f));
  }

  /** Quando uma fase passa pela faixa do meio da tela, ela vira a "fase vista". Sem IntersectionObserver, fica no destaque. */
  private acompanharRolagem(): void {
    const janela = this.document.defaultView;
    if (!janela || typeof janela.IntersectionObserver === 'undefined') {
      return;
    }
    const observador = new janela.IntersectionObserver(
      (entradas) => {
        for (const entrada of entradas) {
          const i = Number((entrada.target as HTMLElement).dataset['i']);
          if (entrada.isIntersecting && i >= 0 && i < ETAPAS.length) {
            this.vista.set(i);
          }
        }
      },
      { rootMargin: '-45% 0px -50% 0px' },
    );
    this.host.nativeElement
      .querySelectorAll<HTMLElement>('li[data-linha]')
      .forEach((li) => observador.observe(li));
    this.destruir.onDestroy(() => observador.disconnect());
  }

  private seguirFragmento(fragmento: string | null): void {
    const m = /^etapa-(\d)$/.exec(fragmento ?? '');
    if (m && Number(m[1]) < ETAPAS.length) {
      if (this.aberta() !== Number(m[1])) {
        this.abrir(Number(m[1]));
      }
    } else if (this.aberta() !== null) {
      this.fechar();
    }
  }

  protected itensFeitos(i: number): number {
    return ETAPAS[i].checklist.filter((_, j) => this.estado.marcado(i, j)).length;
  }

  protected estadoNo(i: number): EstadoNo {
    if (i === this.destaque() && this.estado.atual() !== null) {
      return 'atual';
    }
    if (this.concluidaPorEtapa()[i]) {
      return 'feito';
    }
    return i === this.destaque() ? 'atual' : 'todo';
  }

  protected descricaoNo(i: number): string {
    const e = ETAPAS[i];
    const marcas: string[] = [];
    if (this.concluidaPorEtapa()[i]) {
      marcas.push('concluída');
    }
    if (i === this.destaque()) {
      marcas.push('etapa atual');
    }
    return `Ler a etapa ${e.id}: ${e.curto}${marcas.length ? ', ' + marcas.join(', ') : ''}`;
  }

  protected abrir(i: number, origem?: Event): void {
    if (i < 0 || i >= ETAPAS.length) {
      return;
    }
    this.tocando.set(null);
    const dialogo = this.dialogo()?.nativeElement;
    if (dialogo && !dialogo.open) {
      const alvo = origem?.currentTarget;
      this.gatilho = alvo instanceof Element ? alvo : this.document.activeElement;
      // jsdom (testes) não implementa showModal; os navegadores sim.
      if (typeof dialogo.showModal === 'function') {
        dialogo.showModal();
      } else {
        dialogo.setAttribute('open', '');
      }
    }
    this.aberta.set(i);
    this.estado.definirAtual(i);
    this.rolarAoTopo();
    this.location.replaceState(`${this.caminhoAtual()}#etapa-${i}`);
  }

  protected fechar(): void {
    const dialogo = this.dialogo()?.nativeElement;
    if (dialogo?.open) {
      if (typeof dialogo.close === 'function') {
        dialogo.close();
      } else {
        dialogo.removeAttribute('open');
        this.aoFechar();
      }
    }
  }

  /**
   * Volta o conteúdo ao topo depois que a etapa nova aparece (antes, a altura ainda é a da etapa anterior). Em link
   * direto (#etapa-N) o navegador também rola até o id da etapa quando ela fica visível; por isso repete mais
   * duas vezes, logo depois.
   */
  private rolarAoTopo(): void {
    const topo = () => this.conteudo()?.nativeElement.scrollTo?.({ top: 0 });
    afterNextRender(topo, { injector: this.injector });
    this.document.defaultView?.requestAnimationFrame(() => topo());
    this.document.defaultView?.setTimeout(topo, 150);
  }

  /** Disparado pelo evento "close" do <dialog> (botão, Esc ou fundo). */
  protected tocar(id: string): void {
    this.tocando.set(id);
  }

  protected aoFechar(): void {
    this.tocando.set(null);
    this.aberta.set(null);
    this.location.replaceState(this.caminhoAtual());
    if (this.gatilho instanceof HTMLElement || this.gatilho instanceof SVGElement) {
      this.gatilho.focus();
    }
    this.gatilho = null;
  }

  /** Clique no fundo escuro: o próprio <dialog> é o alvo (o conteúdo ocupa toda a caixa). */
  protected aoClicar(evento: MouseEvent): void {
    if (evento.target === this.dialogo()?.nativeElement) {
      this.fechar();
    }
  }

  protected limparProgresso(): void {
    this.estado.limpar();
  }

  private caminhoAtual(): string {
    return this.location.path().split('#')[0];
  }
}
