import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Desenho de cada fase do guia (etapas 0 a 8): médico, livro e tenda nas três primeiras; a planta nasce em Germinar. SVG próprio, sem imagem e sem style inline (a CSP
 * não deixa): só atributos de apresentação. Decorativo, por isso fica escondido de leitores de tela.
 */
@Component({
  selector: 'app-guia-planta',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg viewBox="0 0 80 100" class="h-full w-full" aria-hidden="true" focusable="false">
      @switch (etapa()) {
        @case (7) {
          <!-- Colheita: ramo pendurado para secar -->
          <path d="M8 18 H72" stroke="#8b6b4a" stroke-width="3" stroke-linecap="round" />
          <path d="M40 18 V46" stroke="#22a06b" stroke-width="3" />
          <path d="M40 30 q-14 -4 -20 6 q10 6 20 -6z" fill="#10b981" />
          <path d="M40 34 q14 -4 20 6 q-10 6 -20 -6z" fill="#34d399" />
          <circle cx="40" cy="54" r="9" fill="#a3e635" />
          <circle cx="32" cy="50" r="6" fill="#bef264" />
          <circle cx="48" cy="52" r="6" fill="#bef264" />
        }
        @case (8) {
          <!-- Secar e curar: frasco -->
          <rect x="20" y="38" width="40" height="52" rx="9" fill="#1e293b" stroke="#34d399" stroke-width="2" />
          <rect x="26" y="28" width="28" height="13" rx="3" fill="#34d399" />
          <circle cx="40" cy="70" r="10" fill="#a3e635" />
          <circle cx="33" cy="74" r="6" fill="#bef264" />
          <circle cx="47" cy="74" r="6" fill="#bef264" />
        }
        @case (0) {
          <!-- Regularização: médico -->
          <circle cx="40" cy="26" r="12" fill="#f1c9a5" />
          <path d="M28 22 q12 -14 24 0 q-12 -5 -24 0z" fill="#3b2f2f" />
          <path d="M22 92 V60 q0 -14 18 -14 q18 0 18 14 V92 Z" fill="#f8fafc" />
          <path d="M40 46 V92" stroke="#cbd5e1" stroke-width="2" />
          <path d="M30 48 q-2 18 10 22 q12 -4 10 -22" fill="none" stroke="#10b981" stroke-width="3" stroke-linecap="round" />
          <circle cx="40" cy="72" r="3.5" fill="#10b981" />
          <rect x="47" y="62" width="9" height="9" rx="1.5" fill="#fff" stroke="#ef4444" stroke-width="1.5" />
          <path d="M51.5 64 V69 M49 66.5 H54" stroke="#ef4444" stroke-width="1.5" />
        }
        @case (1) {
          <!-- Planejar: livro aberto -->
          <path d="M8 30 q16 -6 32 4 V90 q-16 -10 -32 -4 Z" fill="#f8fafc" stroke="#94a3b8" stroke-width="2" stroke-linejoin="round" />
          <path d="M72 30 q-16 -6 -32 4 V90 q16 -10 32 -4 Z" fill="#e2e8f0" stroke="#94a3b8" stroke-width="2" stroke-linejoin="round" />
          <path d="M14 42 q10 -3 20 2 M14 52 q10 -3 20 2 M14 62 q10 -3 20 2" stroke="#10b981" stroke-width="2.5" fill="none" stroke-linecap="round" />
          <path d="M46 44 q10 -5 20 -2 M46 54 q10 -5 20 -2 M46 64 q10 -5 20 -2 M46 74 q8 -4 14 -2" stroke="#94a3b8" stroke-width="2.5" fill="none" stroke-linecap="round" />
        }
        @case (2) {
          <!-- Montar: tenda de cultivo com luz e ventilador -->
          <rect x="12" y="22" width="56" height="70" rx="4" fill="#1e293b" stroke="#475569" stroke-width="2" />
          <rect x="18" y="30" width="44" height="56" rx="2" fill="#0f172a" />
          <path d="M40 22 V92" stroke="#475569" stroke-width="1.5" stroke-dasharray="3 3" />
          <rect x="24" y="36" width="32" height="7" rx="2" fill="#fde047" />
          <path d="M28 43 L24 54 M52 43 L56 54 M40 43 V56" stroke="#fde68a" stroke-width="1.5" opacity="0.7" />
          <circle cx="58" cy="30" r="5" fill="none" stroke="#94a3b8" stroke-width="1.5" />
          <path d="M58 25 V35 M53 30 H63" stroke="#94a3b8" stroke-width="1.5" />
          <path d="M28 86 H52 L50 78 H30 Z" fill="#5b3a29" />
        }
        @default {
          <path d="M20 84 H60 L56 98 H24 Z" fill="#5b3a29" />
          <path d="M20 84 H60" stroke="#7a5238" stroke-width="3" stroke-linecap="round" />
          @switch (etapa()) {
            @case (3) {
              <ellipse cx="40" cy="80" rx="5" ry="3.5" fill="#d6b88c" />
              <path d="M40 80 q2 -6 6 -8" stroke="#bef264" stroke-width="2" fill="none" stroke-linecap="round" />
            }
            @case (4) {
              <path d="M40 84 V68" stroke="#22a06b" stroke-width="3" />
              <path d="M40 70 q-12 -2 -16 6 q10 4 16 -6z" fill="#34d399" />
              <path d="M40 70 q12 -2 16 6 q-10 4 -16 -6z" fill="#10b981" />
            }
            @case (5) {
              <path d="M40 84 V30" stroke="#22a06b" stroke-width="3.5" />
              <path d="M40 74 q-18 -4 -24 8 q14 6 24 -8z" fill="#10b981" />
              <path d="M40 64 q18 -4 24 8 q-14 6 -24 -8z" fill="#34d399" />
              <path d="M40 54 q-18 -4 -24 8 q14 6 24 -8z" fill="#34d399" />
              <path d="M40 44 q18 -4 24 8 q-14 6 -24 -8z" fill="#10b981" />
              <path d="M40 34 q-14 -3 -19 7 q11 5 19 -7z" fill="#34d399" />
              <path d="M40 34 q14 -3 19 7 q-11 5 -19 -7z" fill="#10b981" />
            }
            @case (6) {
              <path d="M40 84 V24" stroke="#22a06b" stroke-width="3.5" />
              <path d="M40 74 q-18 -4 -24 8 q14 6 24 -8z" fill="#10b981" />
              <path d="M40 62 q18 -4 24 8 q-14 6 -24 -8z" fill="#34d399" />
              <path d="M40 50 q-16 -4 -22 7 q13 6 22 -7z" fill="#34d399" />
              <circle cx="40" cy="22" r="8" fill="#a3e635" />
              <circle cx="33" cy="32" r="5" fill="#bef264" />
              <circle cx="47" cy="34" r="5" fill="#bef264" />
              <circle cx="34" cy="44" r="4" fill="#bef264" />
              <circle cx="46" cy="46" r="4" fill="#bef264" />
            }
          }
        }
      }
    </svg>
  `,
})
export class GuiaPlanta {
  /** Etapa do guia, de 0 a 8. */
  readonly etapa = input.required<number>();
}
