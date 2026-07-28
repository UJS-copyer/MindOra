# 后端结构与本地开发规则

本文是 MindOra 后端后续开发的执行规则。新增模块、调整依赖、启动服务和记录日志时优先参考本文。

## 技术栈基线

- Java 21
- Spring Boot 3.4.x
- Spring Security
- Spring AI
- MyBatis-Plus
- Redis
- MySQL + Flyway
- Maven 多模块工程

除非有明确阶段设计，不新增替代性框架或第二套持久化/认证/AI 抽象。

## Maven 模块

后端根目录是 `backend/`，当前模块职责如下：

- `mindora-app`：Spring Boot 启动模块、组合配置、全局 Web/Security 配置。
- `mindora-common`：通用响应、异常、ID、基础 Web 能力，不放业务逻辑。
- `mindora-user`：登录、Token、用户、角色、权限和后台访问身份。
- `mindora-blog`：文章、草稿、分类、标签、发布和公开读取。
- `mindora-asset`：资产元数据、文件存储、公开资源访问。
- `mindora-knowledge`：知识库同步、解析、版本、切片、索引状态。
- `mindora-rag`：检索、问答、会话、引用和模型调用流程。
- `mindora-task`：异步任务、重试、计划任务、消息消费。
- `mindora-admin`：后台聚合能力预留。
- `mindora-adapter`：外部系统适配器预留。

`mindora-app` 可以依赖业务模块并装配 Bean。业务模块不能反向依赖 `mindora-app`。

## 包结构规则

业务模块采用以下包结构：

```text
com.mindora.<module>
  api.web
  application
  application.port
  domain
  infrastructure
```

规则：

- `api.web` 放 HTTP Controller、请求/响应视图模型和 Web 适配代码。
- `application` 放用例服务、事务边界、编排逻辑。
- `application.port` 放应用层需要的端口接口，例如 repository gateway、密码哈希、Token、外部服务入口。
- `domain` 放领域对象、领域枚举、领域 repository 接口。
- `infrastructure` 放 JDBC、MyBatis-Plus、Redis、文件系统、外部 SDK、Spring AI 等具体实现。

调用方向必须保持：

```text
api.web -> application -> domain
application -> application.port
infrastructure -> application.port / domain
mindora-app -> module configuration
```

禁止：

- Controller 直接访问 `JdbcTemplate`、Mapper、RedisTemplate 或外部 SDK。
- `application` 直接依赖 `JdbcTemplate`、Mapper、RedisTemplate、文件系统或外部 SDK。
- 业务模块直接调用其他模块的 `infrastructure` 包。
- 把业务逻辑下沉到 `mindora-common`。
- 在 `mindora-app` 继续堆业务 Controller；Controller 应归属各自业务模块。

## 依赖规则

- 新依赖优先加到真正使用它的模块。
- 版本优先在 `backend/pom.xml` 的 properties 或 dependency management 中统一。
- Spring Boot starter、Spring AI、MyBatis-Plus、Redis 等基础能力由 `mindora-app` 或实际模块按需引入。
- 业务模块之间通过明确 API、facade、application service 或事件协作，不跨模块访问表、mapper 或 repository 实现。
- 外部 provider 逻辑必须放在 adapter 或 infrastructure 边界后，应用层只依赖端口接口。

## 持久化规则

- Repository 接口优先放在 `domain` 或 `application.port`，具体实现放到 `infrastructure.persistence.*`。
- JDBC/MyBatis-Plus SQL 只能出现在 infrastructure。
- Flyway migration 放在 `mindora-app/src/main/resources/db/migration`。
- 新增表时同步维护测试或契约检查，避免只改 SQL 不改 API/服务层。

## 启动命令

先阅读 `docs/project-status-cn.md` 确认当前阶段和端口。不要使用打包后 jar 启动作为本地开发默认方式。

### 后端

推荐在 IDEA 中运行 `com.mindora.app.MindOraApplication`，JDK 使用 Java 21。

命令行启动时进入 `backend/`：

```powershell
cd backend
$env:LOG_FILE='..\logs\backend-dev.log'
mvn -s .mvn\mindora-settings.xml -gs .mvn\mindora-settings.xml -pl mindora-app -am spring-boot:run
```

后端地址：

- `http://localhost:8080/`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### 前台站点

仓库根目录执行：

```powershell
npm run dev:site
```

地址：

- `http://localhost:5173/`

### 后台站点

仓库根目录执行：

```powershell
npm run dev --workspace @mindora/admin-art
```

地址：

- `http://localhost:5175/`

## 日志位置

本地开发日志统一放在仓库根目录 `logs/`。该目录已被 Git 忽略，不提交。

建议文件名：

- 后端：`logs/backend-dev.log`
- 后端错误辅助日志：`logs/backend-dev.err.log`
- 前台站点：`logs/site-dev.log`
- 前台错误辅助日志：`logs/site-dev.err.log`
- 后台站点：`logs/admin-art-dev.log`
- 后台错误辅助日志：`logs/admin-art-dev.err.log`

后端通过 `LOG_FILE` 环境变量控制文件日志位置。因为后端命令从 `backend/` 执行，所以使用 `..\logs\backend-dev.log` 指向仓库根目录。

前端 Vite 默认在终端输出。需要留档时，在独立终端中重定向输出，例如：

```powershell
npm run dev:site *> logs\site-dev.log
npm run dev --workspace @mindora/admin-art *> logs\admin-art-dev.log
```

## 常驻进程规则

Spring Boot `spring-boot:run` 和 Vite dev server 都是常驻进程。不要把多个常驻服务拼进一个 PowerShell 复合命令中启动，也不要在自动化工具里用会等待输出结束的方式直接运行它们。

推荐做法：

- 人工开发：使用 IDEA 启动后端，用两个独立终端分别启动前台和后台前端。
- 自动化验证：先检查端口，再启动一个服务，验证一个服务，记录明确 PID。
- 后台运行：每个服务使用独立命令和独立日志文件，不把后端、前台、后台合并到同一个命令片段。
- 停止服务：只停止确认过的明确 PID，不批量杀进程。

端口检查：

```powershell
netstat -ano | Select-String ':8080'
netstat -ano | Select-String ':5173'
netstat -ano | Select-String ':5175'
```

运行验证：

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:8080/api/v1/public/health
Invoke-WebRequest -UseBasicParsing http://localhost:8080/v3/api-docs
Invoke-WebRequest -UseBasicParsing http://localhost:5173/
Invoke-WebRequest -UseBasicParsing http://localhost:5175/
```

停止明确 PID：

```powershell
Stop-Process -Id <pid>
```

## 检查命令

后端：

```powershell
cd backend
mvn -s .mvn\mindora-settings.xml -gs .mvn\mindora-settings.xml -DskipTests compile
mvn -s .mvn\mindora-settings.xml -gs .mvn\mindora-settings.xml test
```

前端：

```powershell
npm run typecheck
npm run test
npm run build
```



该扫描应无输出。若应用层需要访问数据库、Redis、文件系统或外部 SDK，先定义端口接口，再在 infrastructure 中实现。

## Git 与安全规则

- 不回滚未确认来源的工作区改动。
- 禁止批量删除文件或目录。
- 需要删除文件时，只删除一个明确路径的文件。
- 不使用 `del /s`、`rd /s`、`rmdir /s`、`Remove-Item -Recurse`、`rm -rf`。
