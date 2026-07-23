import { AppRouteRecord } from '@/types/router'

export const contentRoutes: AppRouteRecord = {
  name: 'Content',
  path: '/content',
  component: '/index/index',
  meta: {
    title: 'menus.content.title',
    icon: 'ri:article-line',
    roles: ['R_SUPER', 'R_ADMIN']
  },
  children: [
    {
      path: 'articles',
      name: 'ContentArticles',
      component: '/content/articles',
      meta: {
        title: 'menus.content.articles',
        keepAlive: true,
        fixedTab: true,
        roles: ['R_SUPER', 'R_ADMIN'],
        authList: [
          { title: '新增', authMark: 'add' },
          { title: '编辑', authMark: 'edit' },
          { title: '发布', authMark: 'publish' },
          { title: '下线', authMark: 'unpublish' }
        ]
      }
    },
    {
      path: 'editor',
      name: 'ContentEditor',
      component: '/content/editor',
      meta: {
        title: 'menus.content.editor',
        keepAlive: false,
        roles: ['R_SUPER', 'R_ADMIN']
      }
    },
    {
      path: 'taxonomy',
      name: 'ContentTaxonomy',
      component: '/content/taxonomy',
      meta: {
        title: 'menus.content.taxonomy',
        keepAlive: true,
        roles: ['R_SUPER', 'R_ADMIN'],
        authList: [
          { title: '新增', authMark: 'add' },
          { title: '编辑', authMark: 'edit' },
          { title: '删除', authMark: 'delete' }
        ]
      }
    },
    {
      path: 'assets',
      name: 'ContentAssets',
      component: '/content/assets',
      meta: {
        title: 'menus.content.assets',
        keepAlive: true,
        roles: ['R_SUPER', 'R_ADMIN'],
        authList: [{ title: '上传', authMark: 'upload' }]
      }
    }
  ]
}
