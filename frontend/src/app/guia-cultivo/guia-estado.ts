import { isPlatformBrowser } from '@angular/common';
import { Injectable, PLATFORM_ID, inject, signal } from '@angular/core';

const CHAVE = 'wd-guia-cultivo';
const ETAPAS = 9;
const ITEM = /^\d-\d{1,2}$/;

interface Guardado {
  feitas: string[];
  atual: number | null;
}

/**
 * Progresso do guia de cultivo: quais itens do checklist a pessoa marcou e qual etapa abriu por último.
 * <p>
 * Fica só neste aparelho (localStorage), como o tema: é preferência de uso, não dado pessoal, e nunca vai para o
 * servidor. Sem conta, sem cookie e sem rastreio. A Política de Privacidade diz isso (seção 2).
 * <p>
 * O que vem do localStorage é tratado como não confiável: só entram chaves no formato "etapa-item" e a etapa atual
 * precisa estar entre 0 e 8.
 */
@Injectable({ providedIn: 'root' })
export class GuiaEstado {
  private readonly navegador = isPlatformBrowser(inject(PLATFORM_ID));

  readonly feitas = signal<ReadonlySet<string>>(new Set());
  readonly atual = signal<number | null>(null);

  constructor() {
    if (this.navegador) {
      this.ler();
    }
  }

  marcado(etapa: number, item: number): boolean {
    return this.feitas().has(`${etapa}-${item}`);
  }

  alternar(etapa: number, item: number): void {
    const chave = `${etapa}-${item}`;
    const novo = new Set(this.feitas());
    if (!novo.delete(chave)) {
      novo.add(chave);
    }
    this.feitas.set(novo);
    this.gravar();
  }

  definirAtual(etapa: number): void {
    this.atual.set(etapa);
    this.gravar();
  }

  limpar(): void {
    this.feitas.set(new Set());
    this.atual.set(null);
    this.gravar();
  }

  private ler(): void {
    try {
      const bruto = localStorage.getItem(CHAVE);
      if (!bruto) {
        return;
      }
      const dados = JSON.parse(bruto) as Partial<Guardado>;
      const feitas = Array.isArray(dados.feitas)
        ? dados.feitas.filter((c): c is string => typeof c === 'string' && ITEM.test(c))
        : [];
      this.feitas.set(new Set(feitas));
      const atual = dados.atual;
      this.atual.set(
        typeof atual === 'number' && Number.isInteger(atual) && atual >= 0 && atual < ETAPAS
          ? atual
          : null,
      );
    } catch {
      // Armazenamento indisponível ou texto corrompido: começa do zero, sem quebrar a página.
    }
  }

  private gravar(): void {
    if (!this.navegador) {
      return;
    }
    try {
      const dados: Guardado = { feitas: [...this.feitas()], atual: this.atual() };
      localStorage.setItem(CHAVE, JSON.stringify(dados));
    } catch {
      // Modo privado: o progresso vale só nesta sessão.
    }
  }
}
