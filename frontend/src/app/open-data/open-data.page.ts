import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Icon } from '../shared/icon';

const JSON_URL = '/api/v1/open-data/strains';
const CSV_URL = '/api/v1/open-data/strains.csv';

/** Campos do banco aberto: nome no JSON (camelCase), no CSV (snake_case) e o que significa. */
const FIELDS: readonly { json: string; csv: string; meaning: string }[] = [
  { json: 'id', csv: 'id', meaning: 'Identificador da strain no weediário (estável; serve para cruzar versões do arquivo).' },
  { json: 'name', csv: 'name', meaning: 'Nome da genética.' },
  { json: 'breeder', csv: 'breeder', meaning: 'Breeder ou banco de sementes (vazio quando ninguém informou).' },
  { json: 'floweringType', csv: 'flowering_type', meaning: 'PHOTOPERIOD (fotoperíodo) ou AUTOFLOWER (automática).' },
  { json: 'dominance', csv: 'dominance', meaning: 'INDICA, INDICA_DOMINANT, HYBRID, SATIVA_DOMINANT ou SATIVA.' },
  { json: 'floweringSpeed', csv: 'flowering_speed', meaning: 'SHORT (até 55 dias), MEDIUM (56 a 90) ou LONG (91+).' },
  { json: 'seedTypes', csv: 'seed_types', meaning: 'FEMINIZED e/ou REGULAR. No CSV, itens separados por "|".' },
  { json: 'flavors', csv: 'flavors', meaning: 'Sabores e aromas informados pela comunidade ("|" no CSV).' },
  { json: 'effects', csv: 'effects', meaning: 'Efeitos informados pela comunidade ("|" no CSV).' },
  { json: 'genetics', csv: 'genetics', meaning: 'Cruzamento de origem, quando conhecido.' },
  { json: 'status', csv: 'status', meaning: 'VERIFIED (conferida pela moderação ou pelo teste da comunidade) ou UNVERIFIED.' },
  { json: 'source', csv: 'source', meaning: 'DATASET (base pública importada), COMMUNITY (cadastrada por growers) ou BREEDER (programa de breeders).' },
  { json: 'promotedAt', csv: 'promoted_at', meaning: 'Quando a semente de breeder passou no teste da comunidade (UTC).' },
  { json: 'publicDiaries', csv: 'public_diaries', meaning: 'Diários públicos com a strain.' },
  { json: 'growers', csv: 'growers', meaning: 'Growers diferentes com diário público da strain.' },
  { json: 'reviewCount', csv: 'review_count', meaning: 'Avaliações pós-colheita publicadas.' },
  { json: 'vigor, pestResistance, easeOfGrowth, yield, terpeneProfile', csv: 'vigor, pest_resistance, ease_of_growth, yield, terpene_profile', meaning: 'Média de cada critério (1 a 5).' },
  { json: 'overall', csv: 'overall', meaning: 'Média geral das avaliações (1 a 5).' },
  { json: 'avgGramsPerPlant', csv: 'avg_grams_per_plant', meaning: 'Média de gramas secas por planta nas colheitas registradas em diários públicos.' },
  { json: 'breederKind', csv: 'breeder_kind', meaning: 'SEEDBANK (banco de sementes) ou INDEPENDENT (breeder independente); vazio se não informado.' },
  { json: 'generation', csv: 'generation', meaning: 'Geração da genética: F1, F2, F3_PLUS, S1, BX (retrocruzamento), IBL (linhagem estável) ou POLYHYBRID.' },
];

/**
 * Banco aberto de strains: o que é, a licença (ODbL 1.0), os campos, os endpoints e os downloads.
 */
@Component({
  selector: 'app-open-data-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <p class="text-sm font-semibold uppercase tracking-wider text-accent">Feito pela comunidade, para a comunidade</p>
    <h1 class="mt-1 text-2xl font-bold">Banco aberto de strains</h1>
    <p class="text-fg-muted">
      Cada diário, avaliação e strain cadastrada por quem cultiva melhora este banco, e ele é de todo mundo: baixe,
      estude, cruze com outros dados e construa em cima. Sem cadastro, sem custo.
    </p>

    <div class="mt-4 flex flex-wrap gap-2">
      <a class="wd-button" [href]="csvUrl" download="weediario-strains.csv">
        <app-icon name="database" class="size-5" /> Baixar CSV
      </a>
      <a class="wd-button bg-surface-raised text-fg" [href]="jsonUrl" target="_blank" rel="noopener">
        <app-icon name="database" class="size-5" /> Abrir JSON
      </a>
    </div>

    <section class="wd-card mt-6 grid gap-2" aria-labelledby="what-title">
      <h2 id="what-title" class="text-lg font-semibold">O que tem no banco</h2>
      <p>
        Todas as strains oficiais do <a routerLink="/strains" class="text-accent underline">catálogo</a>: as da base pública
        importada, as cadastradas pela comunidade e as sementes de breeders que já passaram no teste da comunidade
        (3 cultivadores de fora do banco e uma avaliação pós-colheita). Sementes ainda em teste ficam de fora.
      </p>
      <p>
        Junto com as características de cada genética vão só números agregados: quantos diários públicos e growers a
        usam, as médias das avaliações e a média de gramas por planta.
      </p>
    </section>

    <section class="wd-card mt-4 grid gap-2 border-accent/40" aria-labelledby="privacy-title">
      <h2 id="privacy-title" class="flex items-center gap-2 text-lg font-semibold"><app-icon name="lock" class="size-5 text-accent" /> Sem dados pessoais</h2>
      <p>
        Nenhum apelido, e-mail, id de diário, id de avaliação, texto de relato ou foto entra no arquivo. Diários privados não
        contam nos números de uso, e avaliações ocultas pela moderação ou de contas suspensas não entram nas médias.
      </p>
    </section>

    <section class="wd-card mt-4 grid gap-2" aria-labelledby="license-title">
      <h2 id="license-title" class="text-lg font-semibold">Licença: ODbL 1.0</h2>
      <p>
        Os dados estão sob a
        <a [href]="licenseUrl" target="_blank" rel="noopener" class="text-accent underline">Open Database License (ODbL) v1.0</a>.
        Você pode copiar, distribuir e adaptar livremente, inclusive para uso comercial, desde que:
      </p>
      <ul class="list-disc pl-5">
        <li><strong>atribua</strong> a fonte: "Banco aberto de strains do weediário, construído pela comunidade de growers";</li>
        <li><strong>compartilhe pela mesma licença</strong> as bases derivadas que você publicar;</li>
        <li><strong>mantenha aberto</strong>: se distribuir a base com restrições técnicas, ofereça também uma versão sem elas.</li>
      </ul>
      <p class="text-sm text-fg-muted">A licença também vem no próprio arquivo JSON e no cabeçalho <code>Link: rel="license"</code> das respostas.</p>
    </section>

    <section class="mt-4" aria-labelledby="api-title">
      <h2 id="api-title" class="text-lg font-semibold">Endpoints</h2>
      <p class="text-sm text-fg-muted">Públicos, sem login e só leitura. A resposta pode ficar em cache por 1 hora.</p>
      <dl class="mt-3 grid gap-2">
        <div class="wd-card">
          <dt class="font-mono text-sm break-all">GET {{ jsonUrl }}</dt>
          <dd class="mt-1 text-sm text-fg-muted">
            JSON com <code>license</code>, <code>licenseUrl</code>, <code>attribution</code>, <code>generatedAt</code>,
            <code>count</code> e a lista <code>strains</code>.
          </dd>
        </div>
        <div class="wd-card">
          <dt class="font-mono text-sm break-all">GET {{ csvUrl }}</dt>
          <dd class="mt-1 text-sm text-fg-muted">CSV em UTF-8 com cabeçalho; listas separadas por "|". Seguro para abrir em planilha.</dd>
        </div>
      </dl>
    </section>

    <section class="mt-6" aria-labelledby="fields-title">
      <h2 id="fields-title" class="text-lg font-semibold">Campos</h2>
      <ul class="mt-3 grid gap-2">
        @for (field of fields; track field.json) {
          <li class="wd-card text-sm">
            <p class="font-mono break-words">{{ field.json }}</p>
            @if (field.csv !== field.json) {
              <p class="font-mono text-xs break-words text-fg-muted">CSV: {{ field.csv }}</p>
            }
            <p class="mt-1">{{ field.meaning }}</p>
          </li>
        }
      </ul>
      <p class="mt-3 text-sm text-fg-muted">Campos sem informação vêm vazios (CSV) ou <code>null</code> (JSON).</p>
    </section>
  `,
})
export class OpenDataPage {
  protected readonly jsonUrl = JSON_URL;
  protected readonly csvUrl = CSV_URL;
  protected readonly licenseUrl = 'https://opendatacommons.org/licenses/odbl/1-0/';
  protected readonly fields = FIELDS;
}
