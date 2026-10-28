/** Maior lado enviado: acima dos 1600px que o servidor guarda, para ele ainda ter margem ao redimensionar. */
export const UPLOAD_MAX_SIDE = 2048;
const UPLOAD_QUALITY = 0.9;
/** Arquivos leves já aceitos pelo servidor vão como estão (ex.: um print em PNG). */
const KEEP_AS_IS_BYTES = 1024 * 1024;
const SERVER_FORMATS = ['image/jpeg', 'image/png', 'image/webp'];

/**
 * Prepara a foto no aparelho antes do envio: decodifica e regrava em JPEG de no máximo {@link UPLOAD_MAX_SIDE}px.
 * <p>
 * Celular gera foto de 5–12 MB (e HEIC, no iPhone e em vários Android), e o servidor só guarda 1600px em WebP.
 * Reduzir aqui encurta o envio no 4G de minutos para segundos e converte HEIC quando o navegador consegue lê-lo.
 * A rotação do EXIF é aplicada pelo navegador ao decodificar. Se ele não decodificar o arquivo, o original vai
 * como está e o servidor responde com a mensagem de formato.
 */
export async function prepareForUpload(file: File): Promise<Blob> {
  if (file.size <= KEEP_AS_IS_BYTES && SERVER_FORMATS.includes(file.type)) {
    return file;
  }
  try {
    const image = await decode(file);
    try {
      const scale = Math.min(1, UPLOAD_MAX_SIDE / Math.max(image.width, image.height));
      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.round(image.width * scale));
      canvas.height = Math.max(1, Math.round(image.height * scale));
      const context = canvas.getContext('2d');
      if (!context) {
        return file;
      }
      // Fundo branco: PNG transparente não vira preto no JPEG.
      context.fillStyle = '#fff';
      context.fillRect(0, 0, canvas.width, canvas.height);
      context.drawImage(image, 0, 0, canvas.width, canvas.height);
      const blob = await new Promise<Blob | null>((resolve) =>
        canvas.toBlob(resolve, 'image/jpeg', UPLOAD_QUALITY),
      );
      // Nunca troca por algo maior quando o original já seria aceito.
      return blob && (blob.size < file.size || !SERVER_FORMATS.includes(file.type)) ? blob : file;
    } finally {
      if ('close' in image) {
        image.close();
      }
    }
  } catch {
    return file;
  }
}

async function decode(file: File): Promise<ImageBitmap | HTMLImageElement> {
  if (typeof createImageBitmap === 'function') {
    try {
      return await createImageBitmap(file, { imageOrientation: 'from-image' });
    } catch {
      // Safari antigo não aceita as opções ou o formato via bitmap; tenta pelo <img>.
    }
  }
  const url = URL.createObjectURL(file);
  try {
    const image = new Image();
    image.src = url;
    await image.decode();
    return image;
  } finally {
    URL.revokeObjectURL(url);
  }
}
