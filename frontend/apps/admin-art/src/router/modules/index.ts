import { AppRouteRecord } from '@/types/router'
import { dashboardRoutes } from './dashboard'
import { contentRoutes } from './content'
import { resultRoutes } from './result'
import { exceptionRoutes } from './exception'
import { systemRoutes } from './system'

/**
 * 导出所有模块化路由
 */
export const routeModules: AppRouteRecord[] = [
  dashboardRoutes,
  contentRoutes,
  systemRoutes,
  resultRoutes,
  exceptionRoutes
]
