import {
  ChangeDetectionStrategy,
  Component,
  DOCUMENT,
  DestroyRef,
  ElementRef,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  linkedSignal,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { Icon } from './icon';
import { PhotoSource } from './photo-source';

export interface LightboxPhoto {
  readonly thumbnailUrl: string;
  readonly fullUrl: string;
}

/** Posição da foto: deslocamento (px, a partir do centro da tela) e escala. */
export interface View {
  readonly x: number;
  readonly y: number;
  readonly scale: number;
}

export const MIN_SCALE = 1;
export const MAX_SCALE = 5;
export const DOUBLE_TAP_SCALE = 2.5;
const IDENTITY: View = { x: 0, y: 0, scale: 1 };
const TAP_SLOP_PX = 8;
const DOUBLE_TAP_MS = 300;
const SWIPE_NAV_PX = 60;
const SWIPE_CLOSE_PX = 110;

/**
 * Nova escala mantendo parado o ponto da foto que está sob {@code point} (dedos ou cursor).
 * {@code point} é relativo ao centro da tela, o mesmo referencial do deslocamento.
 */
export function zoomAround(view: View, point: { x: number; y: number }, scale: number): View {
  const next = Math.min(MAX_SCALE, Math.max(MIN_SCALE, scale));
  const ratio = next / view.scale;
  return { scale: next, x: point.x - (point.x - view.x) * ratio, y: point.y - (point.y - view.y) * ratio };
}

/** Impede arrastar a foto ampliada para além das bordas (e centraliza o eixo em que ela cabe na tela). */
export function clampView(view: View, frame: { width: number; height: number }, viewport: { width: number; height: number }): View {
  const maxX = Math.max(0, (frame.width * view.scale - viewport.width) / 2);
  const maxY = Math.max(0, (frame.height * view.scale - viewport.height) / 2);
  return { scale: view.scale, x: Math.min(maxX, Math.max(-maxX, view.x)), y: Math.min(maxY, Math.max(-maxY, view.y)) };
}

interface Gesture {
  readonly kind: 'swipe' | 'pan' | 'pinch';
  readonly startX: number;
  readonly startY: number;
  readonly startView: View;
  readonly startDistance: number;
  moved: boolean;
}

/**
 * Visualização em tela cheia das fotos de um update, para inspecionar tricomas, pistilos e folhas.
 * <p>
 * Abre na hora com a miniatura (já em cache do card) e troca pela versão de 1600px, baixada sob demanda só
 * quando a foto é aberta. Zoom por pinça, toque duplo, roda do mouse ou teclado; arrastar com zoom move a
 * foto, sem zoom desliza para a próxima/anterior e, para baixo, fecha.
 */
@Component({
  selector: 'app-image-lightbox',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  templateUrl: './image-lightbox.html',
  host: { '(document:keydown)': 'onKeydown($event)' },
})
export class ImageLightbox {
  private readonly source = inject(PhotoSource);
  private readonly document = inject(DOCUMENT);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly photos = input.required<readonly LightboxPhoto[]>();
  readonly startIndex = input(0);
  readonly isPublic = input(true);
  readonly alt = input('Foto do cultivo');
  readonly closed = output<void>();

  protected readonly index = linkedSignal(() => this.startIndex());
  protected readonly current = computed(() => this.photos()[this.index()]);
  protected readonly hasMany = computed(() => this.photos().length > 1);

  protected readonly thumbSrc = signal<string | null>(null);
  protected readonly fullSrc = signal<string | null>(null);
  protected readonly fullLoaded = signal(false);
  protected readonly fullFailed = signal(false);
  /** Largura/altura da foto, conhecida quando a miniatura (ou a completa) carrega. */
  protected readonly ratio = signal<number | null>(null);

  protected readonly view = signal<View>(IDENTITY);
  protected readonly animating = signal(false);
  protected readonly zoomed = computed(() => this.view().scale > 1.01);
  protected readonly transform = computed(() => {
    const { x, y, scale } = this.view();
    return `translate3d(${x}px, ${y}px, 0) scale(${scale})`;
  });
  /** Maior retângulo com a proporção da foto que cabe na tela; assim o clique fora dele é "fora da imagem". */
  protected readonly frameWidth = computed(() => {
    const ratio = this.ratio();
    return ratio ? `min(100vw, calc(100dvh * ${ratio}))` : '100vw';
  });

  private readonly frame = viewChild.required<ElementRef<HTMLElement>>('frame');
  private readonly closeButton = viewChild.required<ElementRef<HTMLButtonElement>>('closeButton');

  private readonly pointers = new Map<number, { x: number; y: number }>();
  private gesture: Gesture | null = null;
  private lastTap = { time: 0, x: 0, y: 0 };

  constructor() {
    effect((onCleanup) => {
      const photo = this.current();
      const isPublic = this.isPublic();
      this.resetView(false);
      this.fullLoaded.set(false);
      this.fullFailed.set(false);
      this.ratio.set(null);
      if (!photo) {
        return;
      }
      const thumb = this.source.resolve(photo.thumbnailUrl, isPublic).subscribe({
        next: (src) => this.thumbSrc.set(src),
        error: () => this.thumbSrc.set(null),
      });
      // Só aqui a versão em alta é pedida: nunca no feed nem na linha do tempo.
      const full = this.source.resolve(photo.fullUrl, isPublic).subscribe({
        next: (src) => this.fullSrc.set(src),
        error: () => this.fullFailed.set(true),
      });
      onCleanup(() => {
        thumb.unsubscribe();
        full.unsubscribe();
        this.thumbSrc.set(null);
        this.fullSrc.set(null);
      });
    });

    const body = this.document.body;
    const previousOverflow = body.style.overflow;
    const previousFocus = this.document.activeElement as HTMLElement | null;
    body.style.overflow = 'hidden';
    afterNextRender(() => this.closeButton().nativeElement.focus());
    inject(DestroyRef).onDestroy(() => {
      body.style.overflow = previousOverflow;
      previousFocus?.focus?.();
    });
  }

  close(): void {
    this.closed.emit();
  }

  next(): void {
    this.go(1);
  }

  previous(): void {
    this.go(-1);
  }

  zoomBy(factor: number): void {
    this.animateTo(zoomAround(this.view(), { x: 0, y: 0 }, this.view().scale * factor));
  }

  protected measure(event: Event): void {
    const image = event.target as HTMLImageElement;
    if (!this.ratio() && image.naturalWidth && image.naturalHeight) {
      this.ratio.set(image.naturalWidth / image.naturalHeight);
    }
  }

  protected onFullLoad(event: Event): void {
    this.measure(event);
    this.fullLoaded.set(true);
  }

  protected onKeydown(event: KeyboardEvent): void {
    switch (event.key) {
      case 'Escape':
        this.close();
        break;
      case 'ArrowRight':
        this.next();
        break;
      case 'ArrowLeft':
        this.previous();
        break;
      case '+':
      case '=':
        this.zoomBy(1.5);
        break;
      case '-':
        this.zoomBy(1 / 1.5);
        break;
      case 'Tab':
        this.trapFocus(event);
        return;
      default:
        return;
    }
    event.preventDefault();
  }

  protected onWheel(event: WheelEvent): void {
    event.preventDefault();
    this.animating.set(false);
    const factor = Math.exp(-event.deltaY * 0.0025);
    this.setView(zoomAround(this.view(), this.fromCenter(event), this.view().scale * factor));
  }

  protected onPointerDown(event: PointerEvent): void {
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
    this.pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
    this.animating.set(false);
    this.beginGesture();
  }

  protected onPointerMove(event: PointerEvent): void {
    if (!this.pointers.has(event.pointerId) || !this.gesture) {
      return;
    }
    this.pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
    const gesture = this.gesture;
    const [first, second] = [...this.pointers.values()];

    if (gesture.kind === 'pinch' && second) {
      gesture.moved = true;
      const mid = this.fromCenter({ clientX: (first.x + second.x) / 2, clientY: (first.y + second.y) / 2 });
      const scale = gesture.startView.scale * (distance(first, second) / gesture.startDistance);
      // Zoom em volta do ponto médio inicial + deslocamento do ponto médio = pinça que também arrasta.
      const zoomed = zoomAround(gesture.startView, { x: gesture.startX, y: gesture.startY }, scale);
      this.setView({ ...zoomed, x: zoomed.x + mid.x - gesture.startX, y: zoomed.y + mid.y - gesture.startY });
      return;
    }

    const dx = first.x - gesture.startX;
    const dy = first.y - gesture.startY;
    if (Math.hypot(dx, dy) > TAP_SLOP_PX) {
      gesture.moved = true;
    }
    if (!gesture.moved) {
      return;
    }
    if (gesture.kind === 'pan') {
      this.setView({ ...gesture.startView, x: gesture.startView.x + dx, y: gesture.startView.y + dy });
    } else {
      // Sem zoom: a foto acompanha o dedo, só no eixo dominante, para indicar o que vai acontecer ao soltar.
      const horizontal = Math.abs(dx) > Math.abs(dy);
      this.view.set({ scale: 1, x: horizontal && this.hasMany() ? dx : 0, y: horizontal ? 0 : Math.max(0, dy) });
    }
  }

  protected onPointerUp(event: PointerEvent): void {
    if (!this.pointers.delete(event.pointerId) || !this.gesture) {
      return;
    }
    const gesture = this.gesture;
    if (this.pointers.size > 0) {
      // Levantou um dos dedos da pinça: continua arrastando com o outro, sem contar como toque.
      this.beginGesture();
      this.gesture!.moved = true;
      return;
    }
    this.gesture = null;

    if (!gesture.moved && event.type === 'pointerup') {
      this.onTap(event);
      return;
    }
    if (gesture.kind === 'swipe') {
      const dx = event.clientX - gesture.startX;
      const dy = event.clientY - gesture.startY;
      if (this.hasMany() && Math.abs(dx) > SWIPE_NAV_PX && Math.abs(dx) > Math.abs(dy)) {
        this.go(dx < 0 ? 1 : -1);
        return;
      }
      if (dy > SWIPE_CLOSE_PX && dy > Math.abs(dx)) {
        this.close();
        return;
      }
    }
    this.animateTo(this.view().scale < 1.05 ? IDENTITY : this.view());
  }

  private onTap(event: PointerEvent): void {
    const now = event.timeStamp;
    const isDoubleTap =
      now - this.lastTap.time < DOUBLE_TAP_MS &&
      Math.hypot(event.clientX - this.lastTap.x, event.clientY - this.lastTap.y) < 30;
    if (isDoubleTap) {
      this.lastTap = { time: 0, x: 0, y: 0 };
      this.animateTo(this.zoomed() ? IDENTITY : zoomAround(this.view(), this.fromCenter(event), DOUBLE_TAP_SCALE));
      return;
    }
    this.lastTap = { time: now, x: event.clientX, y: event.clientY };
    if (!this.zoomed() && !this.isOnImage(event)) {
      this.close();
    }
  }

  private beginGesture(): void {
    const points = [...this.pointers.values()];
    const view = this.view();
    if (points.length >= 2) {
      const [a, b] = points;
      const mid = this.fromCenter({ clientX: (a.x + b.x) / 2, clientY: (a.y + b.y) / 2 });
      this.gesture = { kind: 'pinch', startX: mid.x, startY: mid.y, startView: view, startDistance: Math.max(1, distance(a, b)), moved: true };
    } else if (points.length === 1) {
      const [p] = points;
      this.gesture = { kind: view.scale > 1.01 ? 'pan' : 'swipe', startX: p.x, startY: p.y, startView: view, startDistance: 0, moved: false };
    }
  }

  private go(step: number): void {
    const count = this.photos().length;
    if (count > 1) {
      this.index.set((this.index() + step + count) % count);
    } else {
      this.animateTo(IDENTITY);
    }
  }

  private resetView(animate: boolean): void {
    this.animating.set(animate);
    this.view.set(IDENTITY);
  }

  private animateTo(view: View): void {
    this.animating.set(true);
    this.setView(view);
  }

  private setView(view: View): void {
    const frame = this.frame().nativeElement;
    const viewport = { width: this.document.documentElement.clientWidth, height: this.document.documentElement.clientHeight };
    this.view.set(clampView(view, { width: frame.offsetWidth, height: frame.offsetHeight }, viewport));
  }

  private fromCenter(point: { clientX: number; clientY: number }): { x: number; y: number } {
    const root = this.document.documentElement;
    return { x: point.clientX - root.clientWidth / 2, y: point.clientY - root.clientHeight / 2 };
  }

  private isOnImage(event: PointerEvent): boolean {
    const rect = this.frame().nativeElement.getBoundingClientRect();
    return event.clientX >= rect.left && event.clientX <= rect.right && event.clientY >= rect.top && event.clientY <= rect.bottom;
  }

  /** aria-modal não prende o foco sozinho: o Tab circula só entre os botões do lightbox. */
  private trapFocus(event: KeyboardEvent): void {
    const buttons = Array.from(this.host.nativeElement.querySelectorAll<HTMLElement>('button:not([disabled])'));
    if (!buttons.length) {
      return;
    }
    const first = buttons[0];
    const last = buttons[buttons.length - 1];
    const active = this.document.activeElement;
    if (event.shiftKey && (active === first || !buttons.includes(active as HTMLElement))) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && (active === last || !buttons.includes(active as HTMLElement))) {
      event.preventDefault();
      first.focus();
    }
  }
}

function distance(a: { x: number; y: number }, b: { x: number; y: number }): number {
  return Math.hypot(a.x - b.x, a.y - b.y);
}
