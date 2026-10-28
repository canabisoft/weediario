import { prepareForUpload } from './upload-image';

describe('prepareForUpload', () => {
  it('sends small files in a server format untouched', async () => {
    const png = new File([new Uint8Array(2048)], 'print.png', { type: 'image/png' });

    expect(await prepareForUpload(png)).toBe(png);
  });

  it('falls back to the original file when the browser cannot decode it', async () => {
    // jsdom não decodifica imagens: simula o navegador que não lê HEIC.
    const heic = new File([new Uint8Array(4096)], 'IMG_0001.HEIC', { type: 'image/heic' });

    expect(await prepareForUpload(heic)).toBe(heic);
  });
});
