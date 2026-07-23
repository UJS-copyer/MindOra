# MindOra 项目状态总览

本文用于记录当前项目迁移结果、启动方式、启动地址，以及前后端各阶段的开发状态，便于后续继续开发和回滚检查。

## 迁移结果确认

- 前端后台已迁移到 `frontend/apps/admin-art`
- 旧后台保留在 `frontend/apps/admin` 作为回退版本
- 前端已完成基础壳迁移，并可正常登录、跳转和渲染主要业务页
- 请求层已接入当前项目的 API 基础地址与 OpenAPI 类型
- 文章、分类标签、资产、用户、角色、菜单、异常页等核心页面已可用
- `reference/` 已纳入忽略，不进入 Git 管理

## 启动方式

### 前台站点前端

仓库根目录执行：

```powershell
npm run dev:site
```

### 后台站点前端

仓库根目录执行：

```powershell
npm run dev --workspace @mindora/admin-art
```

### 后端

进入 `backend/` 后执行：

```powershell
cd backend
mvn -s .mvn\mindora-settings.xml -gs .mvn\mindora-settings.xml -pl mindora-app -am spring-boot:run
```

## 启动地址

### 前台站点

- `http://localhost:5173/`

### 前端后台

- `http://localhost:5175/`

### 后端接口

- `http://localhost:8080/`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## 开发阶段状态

### 后端

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| Stage 0 | 已完成 | 基础工程、模块边界、认证、统一返回、基础测试等已落地 |
| Stage 1 | 已完成 | 内容与后台管理能力已接入，包含文章、分类标签、资产、登录与管理页 |
| Stage 2 | 进行中 | 知识库同步、切片、向量化、索引流程待继续 |
| Stage 3 | 未开始 | RAG 问答与对话链路待实现 |
| Stage 4 | 未开始 | 运维与可观测增强待实现 |

### 前端

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| Stage 0 | 已完成 | 基础工作台壳、路由、请求层、权限基础、主题与布局能力已接入 |
| Stage 1 | 已完成 | 已迁移到 Art Design Pro 方案，并完成主要后台业务页面迁移与验证 |
| Stage 2 | 未开始 | 后续知识库与内容管理增强页面待扩展 |
| Stage 3 | 未开始 | RAG 交互与问答页面待扩展 |
| Stage 4 | 未开始 | 监控、告警、运维视图待扩展 |

## 当前检查结果

- `npm run typecheck --workspace @mindora/admin-art` 通过
- `npm run build --workspace @mindora/admin-art` 通过
- 浏览器冷启动验证 `/#/500` 正常
- 文章编辑、用户管理、角色管理、菜单管理、分类与标签、资产库、404/403/500 页面可正常渲染

## 前台流程

前台站点当前主流程如下：

1. 访问首页 `http://localhost:5173/`
2. 从首页进入文章列表
3. 从文章列表进入文章详情
4. 按分类或标签筛选文章
5. 从详情页返回列表或首页

当前前台站点的目标是提供博客展示与阅读入口，后续可继续补充搜索、标签页、归档和更多内容页。

## 备注

- 当前项目仍保留旧后台与阶段性实现，便于回滚和对照开发。
- 后续如新增阶段，只需在本文件对应表格中继续补充状态即可。
