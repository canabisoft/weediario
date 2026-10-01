import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

/**
 * Mostra a chave de acesso uma única vez (o servidor só guarda o hash dela). O formulário com apelido + campo de
 * senha deixa o gerenciador de senhas do navegador oferecer para salvar a chave.
 */
@Component({
  selector: 'app-access-key-reveal',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <form class="grid gap-4" (ngSubmit)="finish()" autocomplete="on">
      <div role="alert" class="rounded-xl border border-warn/50 bg-warn/10 px-3 py-2 text-sm">
        <p class="font-semibold">Esta é a sua chave de acesso. Ela aparece só agora.</p>
        <p class="mt-1">
          É com ela (e o seu apelido) que você entra no weediário — não existe senha. A gente guarda só um hash dela e
          não consegue mostrar de novo. Salve num gerenciador de senhas. Se perder, gere outra com a frase de 12 palavras.
        </p>
      </div>

      <input type="text" name="username" autocomplete="username" class="sr-only" tabindex="-1" aria-hidden="true" [value]="username()" readonly />
      <div>
        <label class="wd-label" for="access-key">Chave de acesso</label>
        <div class="flex gap-2">
          <input
            id="access-key"
            name="accessKey"
            autocomplete="new-password"
            class="wd-input min-w-0 flex-1 font-mono text-sm"
            readonly
            [value]="accessKey()"
            [attr.type]="visible() ? 'text' : 'password'"
          />
          <button type="button" class="wd-button bg-surface-raised text-fg" (click)="visible.set(!visible())">
            {{ visible() ? 'Ocultar' : 'Mostrar' }}
          </button>
        </div>
        <button type="button" class="mt-2 text-sm font-semibold text-accent underline" (click)="copy()">
          {{ copied() ? 'Copiada!' : 'Copiar chave' }}
        </button>
      </div>

      <label class="flex items-start gap-2 text-sm">
        <input type="checkbox" class="mt-0.5 size-5" name="saved" [ngModel]="saved()" (ngModelChange)="saved.set($event)" />
        <span>Salvei minha chave de acesso num lugar seguro.</span>
      </label>

      <button type="submit" class="wd-button" [disabled]="!saved()">Continuar</button>
    </form>
  `,
})
export class AccessKeyReveal {
  readonly username = input.required<string>();
  readonly accessKey = input.required<string>();
  readonly done = output<void>();

  protected readonly visible = signal(false);
  protected readonly copied = signal(false);
  protected readonly saved = signal(false);

  protected async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.accessKey());
      this.copied.set(true);
    } catch {
      // Sem permissão de área de transferência: a pessoa ainda pode mostrar e copiar à mão.
      this.visible.set(true);
    }
  }

  protected finish(): void {
    if (this.saved()) {
      this.done.emit();
    }
  }
}
