# MindOra 项目状态总览

本文用于记录当前项目迁移结果、启动方式、启动地址，以及前后端各阶段的开发状态，便于后续继续开发和回滚检查。

最近维护：2026-07-26

## 迁移结果确认

- 前端后台已迁移到 `frontend/apps/admin-art`
- 旧后台保留在 `frontend/apps/admin` 作为回退版本
- 前端已完成基础壳迁移，并可正常登录、跳转和渲染主要业务页
- 请求层已接入当前项目的 API 基础地址与 OpenAPI 类型
- 文章、分类标签、资产、用户、角色、菜单、异常页等核心页面已可用
- `reference/` 已纳入忽略，不进入 Git 管理

## Git 集成状态

- 当前 Stage 1 集成分支：`feature/stage-1-content-admin`
- 当前集成提交：`62a5cab`
- 后端内容分支已合并：`d5b160d`，来源为 `feature/stage-1-backend-content`
- 前端内容分支已合并：`62a5cab`，来源为 `feature/stage-1-frontend-content`
- 当前集成分支工作区已检查，无未提交内容
- `frontend/apps/admin` 仍保留为旧后台回退版本，后续新后台页面继续写入 `frontend/apps/admin-art`

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
| Stage 2 | 准备中 | 知识库同步、切片、向量化、索引流程已列入路线图，尚未进入实现 |
| Stage 3 | 未开始 | RAG 问答与对话链路待实现 |
| Stage 4 | 未开始 | 运维与可观测增强待实现 |

### 前端

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| Stage 0 | 已完成 | 基础工作台壳、路由、请求层、权限基础、主题与布局能力已接入 |
| Stage 1 | 已完成 | 已迁移到 Art Design Pro 方案，并完成主要后台业务页面迁移与验证 |
| Stage 2 | 准备中 | 等待知识库后端 API 契约稳定后，在 Art Design Pro 后台补充同步、文档、索引和任务状态页面 |
| Stage 3 | 未开始 | RAG 交互与问答页面待扩展 |
| Stage 4 | 未开始 | 监控、告警、运维视图待扩展 |

## 当前检查结果

- `npm run typecheck --workspace @mindora/admin-art` 通过
- `npm run build --workspace @mindora/admin-art` 通过
- `cd backend; mvn -s .mvn\mindora-settings.xml -gs .mvn\mindora-settings.xml -pl mindora-app -am test` 通过，18 个测试全部通过
- 浏览器冷启动验证 `/#/500` 正常
- 文章编辑、用户管理、角色管理、菜单管理、分类与标签、资产库、404/403/500 页面可正常渲染
- 前端控制模式已接入；后端已提供 `/api/v1/admin/me` 和 `/api/v1/admin/menus`
- 当前后端管理接口仍以 `SUPER_ADMIN` 访问控制为主，动态后端权限模式尚未切换为 `VITE_ACCESS_MODE=backend`

## 前台流程

前台站点当前主流程如下：

1. 访问首页 `http://localhost:5173/`
2. 从首页进入文章列表
3. 从文章列表进入文章详情
4. 按分类或标签筛选文章
5. 从详情页返回列表或首页

当前前台站点的目标是提供博客展示与阅读入口，后续可继续补充搜索、标签页、归档和更多内容页。

## 下一阶段：Stage 2 知识库闭环

下一阶段按照 `docs/design-03-data-and-sync-flow.md`、`docs/design-06-data-model-and-api-boundaries.md` 和 `docs/design-07-implementation-roadmap.md` 执行，目标是先完成一条可运行的 Gitee Markdown 同步、解析、切片、向量化和 Qdrant 入库闭环，再补充博客发布到知识库的同步。

建议执行顺序：

1. 从 `feature/stage-1-content-admin` 当前提交创建新的 Stage 2 分支，保留 Stage 1 回滚点。
2. 先检查 `backend/mindora-knowledge`、`backend/mindora-rag`、适配器模块和现有数据库迁移，避免重复设计。
3. 先完成 Gitee 数据源配置、手动同步、Markdown 解析、文档/版本记录和切片状态。
4. 再接入 embedding 适配器和 Qdrant 写入，所有外部服务调用必须可替换、可测试、可重试。
5. 后端契约稳定后，在 `frontend/apps/admin-art` 增加数据源、同步任务、知识文档、索引状态和失败重试页面。
6. 最后补充“已发布博客文章 -> 知识文档 -> 切片 -> 向量索引”的异步流程。

### 下一阶段提示词

```text
继续 MindOra 项目的 Stage 2：Knowledge Base Loop。

当前基线：
- 当前集成分支是 feature/stage-1-content-admin
- Stage 1 已完成并已合并 backend-content 和 frontend-content 分支
- 当前后台主应用是 frontend/apps/admin-art，旧 frontend/apps/admin 只作为回退版本
- reference/ 已排除 Git 管理
- 不使用 superpower、brainstorm 或复杂 red-green 流程，按正常开发、测试、提交方式执行
- 禁止使用批量删除命令：del /s、rd /s、rmdir /s、Remove-Item -Recurse、rm -rf

请先检查当前 Git 状态和分支历史，然后从当前 Stage 1 集成提交创建独立的 Stage 2 分支，不能修改 Stage 1 回滚点。

请先阅读并遵循：
- docs/design-03-data-and-sync-flow.md
- docs/design-06-data-model-and-api-boundaries.md
- docs/design-07-implementation-roadmap.md
- docs/project-status-cn.md

Stage 2 第一目标是实现可运行的知识库闭环，优先完成 Gitee Markdown 同步：
1. Gitee 数据源配置
2. 手动同步入口
3. 增量同步和全量同步的基础模型
4. Markdown 解析，忽略 frontmatter 作为正文内容
5. 图片引用解析和资源关联
6. Knowledge Document、Knowledge Document Version 及同步状态
7. 可配置的基础切片策略
8. embedding provider 适配器，默认预留 Alibaba Cloud Bailian text-embedding-v4
9. Qdrant 向量写入适配器
10. 索引状态、失败原因、重试次数和单文档/批量重建索引

实现要求：
- 优先复用现有 mindora-knowledge、mindora-rag、adapter 模块和数据库迁移风格
- 外部 Gitee、embedding、Qdrant 调用必须放在清晰的 adapter 边界后
- 先实现服务层和持久化测试，再接 HTTP API
- 使用当前 OpenAPI 生成链路更新契约和 frontend/packages/types/src/generated/openapi.d.ts
- 后台页面只写入 frontend/apps/admin-art，使用现有 api-client、Element Plus、Pinia 和 Art Design Pro 约定
- 前端页面至少覆盖数据源、手动同步、同步任务、知识文档、索引状态、失败重试
- Stage 2 第一轮不实现 RAG 对话页面，不提前扩展 Stage 3
- 不引入模板自己的 pnpm-lock.yaml，不破坏当前 npm workspace
- 每完成一个可运行的小闭环就运行相关测试、类型检查和构建
- 维护 docs/project-status-cn.md，记录新分支、接口、启动方式、测试结果和当前完成度

完成标准：
- 可以配置一个 Gitee 数据源并触发手动同步
- Markdown 可以形成文档和版本记录
- 文档可以切片并写入 Qdrant，状态可查询
- 失败任务有明确错误信息和重试入口
- 后台 Art Design Pro 页面可以查看同步、文档和索引状态
- 后端单元测试、集成测试、前端 typecheck 和 build 均通过
```

## 备注

- 当前项目仍保留旧后台与阶段性实现，便于回滚和对照开发。
- 后续如新增阶段，只需在本文件对应表格中继续补充状态即可。
