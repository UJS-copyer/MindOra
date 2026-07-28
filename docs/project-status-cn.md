# MindOra 项目状态总览

本文用于记录当前项目迁移结果、启动方式、启动地址，以及前后端各阶段的开发状态，便于后续继续开发和回滚检查。

最近维护：2026-07-28

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

后端结构、模块边界、日志和常驻进程规则见 `docs/backend-development-rules-cn.md`。启动服务前先确认端口未被占用，常驻进程请在 IDEA 或独立终端中运行。

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

推荐在 IDEA 中运行 `com.mindora.app.MindOraApplication`。命令行启动时进入 `backend/` 后执行：

```powershell
cd backend
$env:LOG_FILE='..\logs\backend-dev.log'
mvn -s .mvn\mindora-settings.xml -gs .mvn\mindora-settings.xml -pl mindora-app -am spring-boot:run
```

本地运行日志统一写入仓库根目录 `logs/`，例如 `logs/backend-dev.log`、`logs/site-dev.log`、`logs/admin-art-dev.log`。不要把后端、前台、后台三个常驻服务拼到一个 PowerShell 复合命令中启动。

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

下一阶段按照 `docs/design-03-data-and-sync-flow.md`、`docs/design-06-data-model-and-api-boundaries.md` 和 `docs/design-07-implementation-roadmap.md` 执行，目标是先完成一条可运行的 Gitee Markdown 同步、解析、切片、向量化和 Milvus 入库闭环，再按阶段补充完整 RAG 能力。

建议执行顺序：

1. 从 `feature/stage-1-content-admin` 当前提交创建新的 Stage 2 分支，保留 Stage 1 回滚点。
2. 先检查 `backend/mindora-knowledge`、`backend/mindora-rag`、适配器模块和现有数据库迁移，避免重复设计。
3. 先完成 Gitee 数据源配置、手动同步、Markdown 解析、文档/版本记录和切片状态。
4. 再接入 embedding 适配器、Milvus 写入和 Lucene 索引，所有外部服务调用必须可替换、可测试、可重试。
5. 后端契约稳定后，在 `frontend/apps/admin-art` 增加数据源、同步任务、知识文档、索引状态和失败重试页面。
6. 最后补充“已发布博客文章 -> 知识文档 -> 切片 -> 向量索引”的异步流程，并继续推进后续完整 RAG 阶段能力。
