import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from '../core/auth/auth.service';

/**
 * Senha nova + confirmação. Só emite a senha quando as duas batem e o tamanho está dentro do limite; o resto
 * (senhas comuns, apelido dentro da senha) é conferido pelo servidor, que responde com a mensagem certa.
 * Sem regra de maiúscula/símbolo: comprimento protege mais e uma frase curta é fácil de lembrar.
 */
@Component({
  selector: 'app-new-password',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <div class="grid gap-3">
      <div>
        <label class="wd-label" [for]="id() + '-password'">{{ label() }}</label>
        <div class="flex gap-2">
          <input
            class="wd-input flex-1"
            [id]="id() + '-password'"
            [name]="id() + '-password'"
            [type]="visible() ? 'text' : 'password'"
            autocomplete="new-password"
            autocapitalize="none"
            spellcheck="false"
            [attr.maxlength]="maxLength"
            [ngModel]="password()"
            (ngModelChange)="password.set($event)"
          />
          <button type="button" class="wd-button bg-surface-raised px-3 text-fg" (click)="visible.set(!visible())">
            {{ visible() ? 'Ocultar' : 'Mostrar' }}
          </button>
        </div>
        <p class="mt-1 text-xs" [class.text-fg-muted]="!tooShort()" [class.text-danger]="tooShort()">
          Pelo menos {{ minLength }} caracteres. Uma frase curta é ótima ("três vasos na varanda").
        </p>
      </div>
      <div>
        <label class="wd-label" [for]="id() + '-confirm'">Repita a senha</label>
        <input
          class="wd-input"
          [id]="id() + '-confirm'"
          [name]="id() + '-confirm'"
          [type]="visible() ? 'text' : 'password'"
          autocomplete="new-password"
          autocapitalize="none"
          spellcheck="false"
          [attr.maxlength]="maxLength"
          [ngModel]="confirmation()"
          (ngModelChange)="confirmation.set($event)"
        />
        @if (mismatch()) {
          <p class="mt-1 text-xs text-danger">As duas senhas não batem.</p>
        }
      </div>
    </div>
  `,
})
export class NewPassword {
  readonly id = input('new');
  readonly label = input('Senha');
  /** Senha válida, ou {@code null} enquanto não está pronta. */
  readonly changed = output<string | null>();

  protected readonly minLength = PASSWORD_MIN_LENGTH;
  protected readonly maxLength = PASSWORD_MAX_LENGTH;
  protected readonly password = signal('');
  protected readonly confirmation = signal('');
  protected readonly visible = signal(false);
  protected readonly tooShort = computed(() => this.password().length > 0 && [...this.password()].length < PASSWORD_MIN_LENGTH);
  protected readonly mismatch = computed(() => this.confirmation().length > 0 && this.confirmation() !== this.password());
  private readonly value = computed(() => {
    const password = this.password();
    const length = [...password].length;
    return length >= PASSWORD_MIN_LENGTH && length <= PASSWORD_MAX_LENGTH && password === this.confirmation() ? password : null;
  });

  constructor() {
    effect(() => this.changed.emit(this.value()));
  }
}
