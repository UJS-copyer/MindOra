import type { ApiClient } from '@mindora/api-client';
import type { Asset } from '@mindora/types';

export async function uploadCoverAsset(
  api: Pick<ApiClient, 'uploadAsset'>,
  file: File
): Promise<Asset> {
  const response = await api.uploadAsset(file, 'blog_cover');
  return response.data;
}
