import type { ApiClient } from '@mindora/api-client';
import { describe, expect, it, vi } from 'vitest';
import { uploadCoverAsset } from './asset-upload';

describe('uploadCoverAsset', () => {
  it('uploads the selected file as a blog cover and returns the asset', async () => {
    const asset = {
      id: 'asset-1',
      fileName: 'cover.png',
      mimeType: 'image/png',
      size: 4,
      assetType: 'blog_cover',
      publicUrl: '/api/v1/public/assets/asset-1',
      createdAt: '2026-07-14T00:00:00Z'
    };
    const uploadAsset = vi.fn(async () => ({
      code: 'success',
      message: 'OK',
      data: asset,
      traceId: 'trace-asset'
    }));
    const api = { uploadAsset } satisfies Pick<ApiClient, 'uploadAsset'>;
    const file = new File(['data'], 'cover.png', { type: 'image/png' });

    await expect(uploadCoverAsset(api, file)).resolves.toEqual(asset);
    expect(uploadAsset).toHaveBeenCalledWith(file, 'blog_cover');
  });
});
