/** Côté le plus long après compression (≈ 2000 px) et qualité JPEG : envoi rapide sur mobile, coût de lecture réduit. */
const MAX_SIDE = 2000;
const JPEG_QUALITY = 0.8;

export class UnreadableImageError extends Error {}

export function isHeic(file: File): boolean {
  return /image\/hei[cf]/i.test(file.type) || /\.hei[cf]$/i.test(file.name);
}

/**
 * Photo → JPEG redimensionné. Convertit aussi le HEIC quand le navigateur sait le lire (Safari sur iPhone) :
 * l'API de lecture n'accepte pas ce format. Lève {@link UnreadableImageError} si l'image ne peut pas être décodée.
 */
export async function compressPhoto(file: File): Promise<Blob> {
  const image = await decode(file);
  const scale = Math.min(1, MAX_SIDE / Math.max(image.width, image.height));
  const width = Math.max(1, Math.round(image.width * scale));
  const height = Math.max(1, Math.round(image.height * scale));
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext('2d');
  if (!context) throw new UnreadableImageError('Canvas indisponible');
  context.fillStyle = '#fff'; // fond blanc pour les PNG transparents
  context.fillRect(0, 0, width, height);
  context.drawImage(image.source, 0, 0, width, height);
  image.release();
  return new Promise<Blob>((resolve, reject) =>
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new UnreadableImageError('Compression impossible'))),
      'image/jpeg', JPEG_QUALITY));
}

interface DecodedImage {
  source: CanvasImageSource;
  width: number;
  height: number;
  release: () => void;
}

async function decode(file: File): Promise<DecodedImage> {
  if (typeof createImageBitmap === 'function') {
    try {
      // imageOrientation : applique la rotation EXIF des photos prises au téléphone.
      const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' });
      return { source: bitmap, width: bitmap.width, height: bitmap.height, release: () => bitmap.close() };
    } catch {
      // Repli sur <img> ci-dessous (certains navigateurs décodent le HEIC uniquement ainsi).
    }
  }
  const url = URL.createObjectURL(file);
  try {
    const element = new Image();
    element.decoding = 'async';
    element.src = url;
    await element.decode();
    return { source: element, width: element.naturalWidth, height: element.naturalHeight, release: () => undefined };
  } catch {
    throw new UnreadableImageError('Image illisible');
  } finally {
    URL.revokeObjectURL(url);
  }
}
