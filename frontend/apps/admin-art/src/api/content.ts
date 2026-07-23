import request from '@/utils/http'
import type { ArticleDraftInput, Asset, BlogArticle, BlogCategory, BlogTag } from '@mindora/types'

export function listAdminArticles() {
  return request.get<BlogArticle[]>({ url: '/api/v1/admin/articles' })
}

export function createArticle(input: ArticleDraftInput) {
  return request.post<BlogArticle>({ url: '/api/v1/admin/articles', data: input })
}

export function updateArticle(id: string, input: ArticleDraftInput) {
  return request.put<BlogArticle>({ url: `/api/v1/admin/articles/${id}`, data: input })
}

export function publishArticle(id: string) {
  return request.post<BlogArticle>({ url: `/api/v1/admin/articles/${id}/publish` })
}

export function unpublishArticle(id: string) {
  return request.post<BlogArticle>({ url: `/api/v1/admin/articles/${id}/unpublish` })
}

export function listCategories() {
  return request.get<BlogCategory[]>({ url: '/api/v1/admin/categories' })
}

export function createCategory(name: string) {
  return request.post<BlogCategory>({ url: '/api/v1/admin/categories', data: { name } })
}

export function updateCategory(id: string, name: string) {
  return request.put<BlogCategory>({ url: `/api/v1/admin/categories/${id}`, data: { name } })
}

export function deleteCategory(id: string) {
  return request.del<void>({ url: `/api/v1/admin/categories/${id}` })
}

export function listTags() {
  return request.get<BlogTag[]>({ url: '/api/v1/admin/tags' })
}

export function createTag(name: string) {
  return request.post<BlogTag>({ url: '/api/v1/admin/tags', data: { name } })
}

export function updateTag(id: string, name: string) {
  return request.put<BlogTag>({ url: `/api/v1/admin/tags/${id}`, data: { name } })
}

export function deleteTag(id: string) {
  return request.del<void>({ url: `/api/v1/admin/tags/${id}` })
}

export function listAssets() {
  return request.get<Asset[]>({ url: '/api/v1/admin/assets' })
}

export function uploadAsset(file: File, assetType = 'blog_asset') {
  const form = new FormData()
  form.set('file', file)
  form.set('assetType', assetType)
  return request.post<Asset>({ url: '/api/v1/admin/assets', data: form })
}

export function assetUrl(asset: Asset) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || import.meta.env.VITE_API_URL || ''
  if (asset.publicUrl.startsWith('http://') || asset.publicUrl.startsWith('https://')) {
    return asset.publicUrl
  }
  return `${baseUrl}${asset.publicUrl}`
}
