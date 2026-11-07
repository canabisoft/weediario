import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { MyProfile } from '../core/api/api.models';
import { AuthService } from '../core/auth/auth.service';
import { ETAPAS } from './guia-cultivo.data';
import { GuiaCultivoPage } from './guia-cultivo.page';

const ME: MyProfile = {
  id: 'u1',
  username: 'quintal',
  hasAvatar: false,
  hasRecoveryPhrase: true,
  role: 'GROWER',
  termsAccepted: true,
  currentTermsVersion: '1.0',
  profileHidden: false,
};

describe('GuiaCultivoPage', () => {
  function render(member = false) {
    localStorage.clear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    if (member) {
      TestBed.inject(AuthService).me.set(ME);
    }
    const fixture = TestBed.createComponent(GuiaCultivoPage);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const tick = () => fixture.detectChanges();
    return { fixture, root, tick };
  }

  const artigo = (root: HTMLElement, i: number) => root.querySelector<HTMLElement>(`#etapa-${i}`)!;
  const visivel = (root: HTMLElement, i: number) => !artigo(root, i).classList.contains('hidden');

  it('mostra a trilha com as 9 etapas, na linha do tempo', () => {
    const { root } = render();

    expect(root.querySelector('h1')?.textContent).toContain('Como cultivar em casa gastando pouco');
    expect(root.querySelectorAll('ol li[data-linha]').length).toBe(9);
    expect(root.querySelectorAll('ol button[data-etapa]').length).toBe(9);
  });

  it('põe o texto de TODAS as etapas no HTML (SEO e leitura sem JavaScript), mas só mostra a escolhida', () => {
    const { root, tick } = render();

    for (const e of ETAPAS) {
      expect(artigo(root, e.id).textContent).toContain(e.curto);
      expect(visivel(root, e.id), `etapa ${e.id} começa escondida`).toBe(false);
    }

    root
      .querySelectorAll<HTMLElement>('ol button[data-etapa]')[3]
      .dispatchEvent(new MouseEvent('click', { bubbles: true }));
    tick();

    expect(visivel(root, 3)).toBe(true);
    expect(ETAPAS.filter((e) => visivel(root, e.id)).map((e) => e.id)).toEqual([3]);
    expect(root.querySelector('dialog')?.hasAttribute('open')).toBe(true);
    expect(root.querySelector('dialog')?.getAttribute('aria-labelledby')).toBe('guia-t-3');
  });

  it('a etapa clicada vira a atual e é lembrada', () => {
    const { root, tick } = render();

    root.querySelectorAll<HTMLElement>('ol button[data-etapa]')[5].click();
    tick();

    expect(
      root
        .querySelector('ol button[data-etapa][aria-label*="etapa atual"]')
        ?.getAttribute('aria-label'),
    ).toContain('etapa 5');
    expect(JSON.parse(localStorage.getItem('wd-guia-cultivo')!).atual).toBe(5);
  });

  it('marcar todos os itens de uma etapa conclui a etapa e atualiza o progresso', () => {
    const { root, tick } = render();
    root.querySelectorAll<HTMLElement>('ol button[data-etapa]')[0].click();
    tick();

    expect(root.textContent).toContain('Role para ver as fases');
    for (const caixa of Array.from(
      artigo(root, 0).querySelectorAll<HTMLInputElement>('input[type="checkbox"]'),
    )) {
      caixa.click();
      tick();
    }

    expect(root.textContent).toContain('1 de 9 etapas concluídas.');
    expect(root.querySelector('ol button[data-etapa]')?.getAttribute('aria-label')).toContain(
      'concluída',
    );
    expect(root.textContent).toContain('Limpar o meu progresso');
  });

  it('o botão "Fechar" fecha o popup', () => {
    const { root, tick } = render();
    root.querySelectorAll<HTMLElement>('ol button[data-etapa]')[1].click();
    tick();
    expect(root.querySelector('dialog')?.hasAttribute('open')).toBe(true);

    Array.from(root.querySelectorAll<HTMLButtonElement>('dialog button'))
      .find((b) => b.textContent?.trim() === 'Fechar')!
      .click();
    tick();

    expect(root.querySelector('dialog')?.hasAttribute('open')).toBe(false);
  });

  it('navega para a etapa seguinte e a anterior sem fechar', () => {
    const { root, tick } = render();
    root.querySelectorAll<HTMLElement>('ol button[data-etapa]')[2].click();
    tick();

    Array.from(artigo(root, 2).querySelectorAll<HTMLButtonElement>('nav button')).pop()!.click();
    tick();
    expect(visivel(root, 3)).toBe(true);
    expect(visivel(root, 2)).toBe(false);

    Array.from(artigo(root, 3).querySelectorAll<HTMLButtonElement>('nav button'))[0].click();
    tick();
    expect(visivel(root, 2)).toBe(true);
  });

  it('antes do clique não carrega nada de terceiros: sem iframe, script, imagem externa nem link http', () => {
    const { root } = render();

    expect(root.querySelectorAll('iframe, script, embed, object, link').length).toBe(0);
    for (const img of Array.from(root.querySelectorAll('img'))) {
      expect(img.getAttribute('src'), 'imagem só do próprio site').toMatch(
        /^\/guia-cultivo\/(video\/[\w-]{11}|[a-z]+)\.jpg$/,
      );
    }
    for (const a of Array.from(root.querySelectorAll('a[href]'))) {
      expect(a.getAttribute('href')).not.toMatch(/^http:/i);
    }
  });

  it('todo link para fora abre em outra aba sem passar referência', () => {
    const { root } = render();
    const externos = Array.from(root.querySelectorAll<HTMLAnchorElement>('a[href^="https://"]'));

    expect(externos.length).toBeGreaterThan(40);
    for (const a of externos) {
      expect(a.getAttribute('target'), a.href).toBe('_blank');
      expect(a.getAttribute('rel'), a.href).toContain('noopener');
      expect(a.getAttribute('rel'), a.href).toContain('noreferrer');
    }
  });

  it('os vídeos são miniaturas locais: nenhum iframe até clicar, e só então o player nocookie', () => {
    const { root, fixture } = render();
    const botoes = root.querySelectorAll<HTMLButtonElement>('button[aria-label^="Assistir:"]');

    expect(botoes.length).toBe(ETAPAS.flatMap((e) => e.videos).length);
    expect(root.querySelectorAll('iframe').length).toBe(0);
    for (const img of Array.from(root.querySelectorAll<HTMLImageElement>('button[aria-label^="Assistir:"] img'))) {
      expect(img.getAttribute('src')).toMatch(/^\/guia-cultivo\/video\/[\w-]{11}\.jpg$/);
    }

    botoes[0].click();
    fixture.detectChanges();

    const quadros = root.querySelectorAll<HTMLIFrameElement>('iframe');
    expect(quadros.length).toBe(1);
    expect(quadros[0].getAttribute('src')).toMatch(/^https:\/\/www\.youtube-nocookie\.com\/embed\/[\w-]{11}\?/);
  });

  it('a última etapa leva ao diário: visitante cria conta, membro inicia um diário', () => {
    const visitante = render(false);
    expect(artigo(visitante.root, 8).querySelector('a[href="/cadastro"]')).not.toBeNull();
    expect(artigo(visitante.root, 8).querySelector('a[href="/diarios/novo"]')).toBeNull();

    const membro = render(true);
    expect(artigo(membro.root, 8).querySelector('a[href="/diarios/novo"]')).not.toBeNull();
    expect(artigo(membro.root, 8).querySelector('a[href="/cadastro"]')).toBeNull();
  });

  it('diz que o weediário não vende nem recebe comissão e que o conteúdo não é aconselhamento', () => {
    const { root } = render();

    expect(root.textContent).toContain('não recebe comissão');
    expect(root.textContent).toContain('Não é aconselhamento jurídico nem médico');
  });

  it('abre a etapa do #etapa-N e acompanha a mudança do endereço com a página já aberta', async () => {
    localStorage.clear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: '', component: GuiaCultivoPage }]),
      ],
    });
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/#etapa-3');
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
    const root = harness.routeNativeElement as HTMLElement;

    expect(visivel(root, 3)).toBe(true);

    await harness.navigateByUrl('/#etapa-6');
    harness.detectChanges();
    expect(visivel(root, 6)).toBe(true);
    expect(visivel(root, 3)).toBe(false);

    await harness.navigateByUrl('/');
    harness.detectChanges();
    expect(root.querySelector('dialog')?.hasAttribute('open')).toBe(false);
  });
});
