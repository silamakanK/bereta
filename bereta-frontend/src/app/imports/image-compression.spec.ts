import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { UnreadableImageError, compressPhoto, isHeic } from './image-compression';

describe('image-compression', () => {
  it('recognises HEIC photos by type or extension', () => {
    expect(isHeic(new File(['x'], 'IMG_1.HEIC'))).toBe(true);
    expect(isHeic(new File(['x'], 'photo', { type: 'image/heif' }))).toBe(true);
    expect(isHeic(new File(['x'], 'photo.jpg', { type: 'image/jpeg' }))).toBe(false);
  });

  it('rejects an image the browser cannot decode', async () => {
    await expect(compressPhoto(new File(['pas une image'], 'cahier.jpg', { type: 'image/jpeg' })))
      .rejects.toBeInstanceOf(UnreadableImageError);
  });
});

describe('compressPhoto', () => {
  const drawn: number[][] = [];

  beforeEach(() => {
    drawn.length = 0;
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
      fillStyle: '', fillRect: vi.fn(), drawImage: (_: unknown, x: number, y: number, w: number, h: number) => drawn.push([w, h]),
    } as unknown as CanvasRenderingContext2D);
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(function (this: HTMLCanvasElement, done: BlobCallback, type?: string) {
      done(new Blob([`${this.width}x${this.height}`], { type }));
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('resizes the longest side to 2000 px and encodes in JPEG', async () => {
    const close = vi.fn();
    vi.stubGlobal('createImageBitmap', vi.fn(async () => ({ width: 4000, height: 3000, close })));
    const blob = await compressPhoto(new File(['x'], 'cahier.png', { type: 'image/png' }));
    expect(blob.type).toBe('image/jpeg');
    expect(drawn).toEqual([[2000, 1500]]);
    expect(close).toHaveBeenCalled();
  });

  it('falls back to an <img> element and keeps small photos as they are', async () => {
    vi.stubGlobal('createImageBitmap', vi.fn(async () => { throw new Error('format inconnu'); }));
    // jsdom ne fournit pas decode() : on l'ajoute le temps du test.
    Object.defineProperty(HTMLImageElement.prototype, 'decode', { value: vi.fn(async () => undefined), configurable: true });
    vi.spyOn(HTMLImageElement.prototype, 'naturalWidth', 'get').mockReturnValue(800);
    vi.spyOn(HTMLImageElement.prototype, 'naturalHeight', 'get').mockReturnValue(600);
    URL.createObjectURL = vi.fn(() => 'blob:photo');
    URL.revokeObjectURL = vi.fn();
    await compressPhoto(new File(['x'], 'IMG.HEIC', { type: 'image/heic' }));
    expect(drawn).toEqual([[800, 600]]);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:photo');
    delete (HTMLImageElement.prototype as { decode?: unknown }).decode;
  });

  it('fails clearly when the canvas cannot encode', async () => {
    vi.stubGlobal('createImageBitmap', vi.fn(async () => ({ width: 10, height: 10, close: vi.fn() })));
    vi.mocked(HTMLCanvasElement.prototype.toBlob).mockImplementation((done: BlobCallback) => done(null));
    await expect(compressPhoto(new File(['x'], 'a.jpg', { type: 'image/jpeg' }))).rejects.toBeInstanceOf(UnreadableImageError);
  });
});
