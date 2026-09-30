import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ImageLightbox, LightboxPhoto, MAX_SCALE, clampView, zoomAround } from './image-lightbox';

const PHOTOS: LightboxPhoto[] = [
  { thumbnailUrl: '/p/1?size=THUMBNAIL', fullUrl: '/p/1?size=FULL' },
  { thumbnailUrl: '/p/2?size=THUMBNAIL', fullUrl: '/p/2?size=FULL' },
  { thumbnailUrl: '/p/3?size=THUMBNAIL', fullUrl: '/p/3?size=FULL' },
];

describe('zoom math', () => {
  it('keeps the point under the fingers still while zooming', () => {
    const point = { x: 100, y: -40 };
    const zoomed = zoomAround({ x: 0, y: 0, scale: 1 }, point, 2);

    // O ponto da foto sob o dedo: (point - deslocamento) / escala deve ser o mesmo antes e depois.
    expect((point.x - zoomed.x) / zoomed.scale).toBeCloseTo(point.x);
    expect((point.y - zoomed.y) / zoomed.scale).toBeCloseTo(point.y);
  });

  it('never zooms out below the fitted size nor beyond the maximum', () => {
    expect(zoomAround({ x: 0, y: 0, scale: 1 }, { x: 0, y: 0 }, 0.2).scale).toBe(1);
    expect(zoomAround({ x: 0, y: 0, scale: 1 }, { x: 0, y: 0 }, 50).scale).toBe(MAX_SCALE);
  });

  it('stops panning at the edges of the zoomed photo', () => {
    const frame = { width: 400, height: 300 };
    const viewport = { width: 400, height: 800 };

    const clamped = clampView({ x: 9999, y: 9999, scale: 2 }, frame, viewport);

    expect(clamped.x).toBe(200); // (400 × 2 − 400) / 2
    expect(clamped.y).toBe(0); // 600px de altura cabem nos 800px da tela: fica centralizada
  });
});

describe('ImageLightbox', () => {
  let fixture: ComponentFixture<ImageLightbox>;
  let closed: number;

  function open(isPublic: boolean, startIndex = 0): void {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    fixture = TestBed.createComponent(ImageLightbox);
    fixture.componentRef.setInput('photos', PHOTOS);
    fixture.componentRef.setInput('startIndex', startIndex);
    fixture.componentRef.setInput('isPublic', isPublic);
    closed = 0;
    fixture.componentInstance.closed.subscribe(() => closed++);
    fixture.detectChanges();
  }

  const images = () => Array.from(fixture.nativeElement.querySelectorAll('img') as NodeListOf<HTMLImageElement>);
  const key = (name: string) => {
    document.dispatchEvent(new KeyboardEvent('keydown', { key: name }));
    fixture.detectChanges();
  };

  afterEach(() => {
    fixture?.destroy();
    document.body.style.overflow = '';
  });

  it('shows the cached thumbnail at once and requests only the current full-resolution photo', () => {
    open(true);

    const sources = images().map((image) => image.getAttribute('src'));
    expect(sources).toEqual(['/p/1?size=THUMBNAIL', '/p/1?size=FULL']);
    expect(fixture.nativeElement.textContent).toContain('Carregando alta resolução');
  });

  it('reveals the full-resolution image once it loads', () => {
    open(true);
    const full = images()[1];
    expect(full.classList).toContain('opacity-0');

    full.dispatchEvent(new Event('load'));
    fixture.detectChanges();

    expect(full.classList).not.toContain('opacity-0');
    expect(fixture.nativeElement.textContent).not.toContain('Carregando alta resolução');
  });

  it('navigates with the arrow keys and wraps around', () => {
    open(true, 2);

    key('ArrowRight');
    expect(images()[1].getAttribute('src')).toBe('/p/1?size=FULL');
    key('ArrowLeft');
    expect(images()[1].getAttribute('src')).toBe('/p/3?size=FULL');
    expect(fixture.nativeElement.textContent).toContain('3 / 3');
  });

  it('closes with Escape and with the close button', () => {
    open(true);

    key('Escape');
    (fixture.nativeElement.querySelector('button[aria-label="Fechar"]') as HTMLButtonElement).click();

    expect(closed).toBe(2);
  });

  it('locks page scroll while open and restores it when closed', () => {
    open(true);
    expect(document.body.style.overflow).toBe('hidden');

    fixture.destroy();

    expect(document.body.style.overflow).toBe('');
  });

  it('downloads photos of private diaries with the authenticated API instead of a bare img url', () => {
    const createObjectURL = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:foto');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    open(false);
    const http = TestBed.inject(HttpTestingController);

    expect(images()).toHaveLength(0);
    http.expectOne('/p/1?size=THUMBNAIL').flush(new Blob(['x']));
    http.expectOne('/p/1?size=FULL').flush(new Blob(['y']));
    fixture.detectChanges();

    expect(images().map((image) => image.getAttribute('src'))).toEqual(['blob:foto', 'blob:foto']);
    expect(createObjectURL).toHaveBeenCalledTimes(2);
    http.verify();
    vi.restoreAllMocks();
  });
});
