# MindOra 开发环境启动说明

## 运行方式

MindOra 采用本地开发、VM 运行基础设施的方式：

```text
Ubuntu VM Docker：MySQL、Redis、Qdrant、RocketMQ
Windows IDEA：Spring Boot 后端
Windows VS Code：Vue 前端
```

后端和前端都不部署到 VM Docker 中。

## 1. 启动 Ubuntu VM

VM 地址：`192.168.222.128`。

Windows 的 VMware VMnet8 应为：

```text
192.168.222.1/24
```

检查 SSH：

```powershell
Test-NetConnection 192.168.222.128 -Port 22
ssh root@192.168.222.128 "hostname && whoami"
```

如果 VMnet8 变成 `169.254.*`，请使用管理员 PowerShell 修复：

```powershell
netsh interface ip set address name="VMware Network Adapter VMnet8" static 192.168.222.1 255.255.255.0
```

## 2. 启动 VM 基础服务

SSH 进入 VM：

```powershell
ssh root@192.168.222.128
```

启动 Docker 基础设施：

```bash
cd /data/proj/MindOra/deploy/ubuntu-vm
docker compose -p mindora up -d
docker compose -p mindora ps
```

服务地址：

```text
MySQL：192.168.222.128:3306
Redis：192.168.222.128:6379
Qdrant：http://192.168.222.128:6333
RocketMQ NameServer：192.168.222.128:9876
```

快速检查：

```bash
docker exec mindora-mysql mysqladmin ping -h localhost -pmindora_root_password
docker exec mindora-redis redis-cli ping
curl http://127.0.0.1:6333/healthz
```

数据库信息：

```text
数据库：mindora
用户：mindora
密码：mindora_password
```

后端启动时会通过 Flyway 自动执行数据库迁移，不需要手动建表。

## 3. IDEA 启动后端

在 IDEA 打开项目目录：

```text
E:\CodeX\Project\Website\backend
```

后端模块入口：

```text
backend/mindora-app/src/main/java/com/mindora/app/MindOraApplication.java
```

IDEA 启动配置：

```text
Main class：com.mindora.app.MindOraApplication
Working directory：E:\CodeX\Project\Website\backend
Port：8080
```

后端通过环境变量连接 VM 服务：

```text
MYSQL_HOST=192.168.222.128
REDIS_HOST=192.168.222.128
QDRANT_URL=http://192.168.222.128:6333
ROCKETMQ_NAMESRV_ADDR=192.168.222.128:9876
```

本地文件存储使用 Windows 路径：

```text
MINDORA_STORAGE_ROOT=E:\CodeX\Project\Website\data\storage
```

后端健康检查：

```text
http://localhost:8080/api/v1/public/health
```

也可以在 PowerShell 中运行测试：

```powershell
cd E:\CodeX\Project\Website\backend
mvn -s .mvn\mindora-settings.xml -gs .mvn\mindora-settings.xml test
```

## 4. VS Code 启动前端

在 VS Code 打开项目根目录：

```text
E:\CodeX\Project\Website
```

首次启动安装依赖：

```powershell
npm install --cache .npm-cache
```

启动公开站点：

```powershell
npm run dev:site
```

访问：`http://localhost:5173`。

启动后台管理：

```powershell
npm run dev:admin-art
```

访问：`http://localhost:5175`。

前端请求约定（**重要，2026-10-18 定**）：

- 两个前端一律使用**相对路径** `/api/...`，由 **vite 开发服务器代理**转发到 `http://localhost:8080`（见各自 `vite.config.ts` 的 `/api` 规则）；
- **不要**在 `.env` 里把 `VITE_API_BASE_URL` 配成绝对地址（如 `http://localhost:8080`）：那会让浏览器按**跨源**处理，登录态 Cookie 会变成第三方 Cookie，还多一层 CORS 配置；
- 生产同理：Nginx 托管前端静态文件，并把 `/api` 反代到后端——**同域**部署，不需要 CORS、不需要 `SameSite=None`；
- 后台管理端（5175）已按此配置；公开站点（5173）在 Stage 3.6a 的 a0 任务里一并改为相对路径 + 代理。

## 日常启动顺序

```text
1. 启动 Ubuntu VM
2. 检查 VMnet8 和 SSH
3. 启动 VM Docker 基础服务
4. IDEA 启动 Spring Boot 后端
5. VS Code 启动 site/admin-art 前端
```

停止开发时只需停止 IDEA、VS Code 中的本地服务；VM 基础容器保持 `unless-stopped` 即可。

## 本地联调注意事项（VM 环境，2026-10 实测）

### 端口：**可直连**（Docker 发布端口会绕过 ufw）

2026-10-13 实测订正：宿主机**可以直连** VM 的 3306 / 6379 / 6333，`mindora-local.yml` 里用的就是 `192.168.222.128` 直连地址。

- **为什么 ufw 列表里没有 3306/6333 却仍可访问**：Docker 在 `nat`/`FORWARD` 链写自己的 iptables 规则（DNAT 到容器），而 ufw 的规则作用在 `INPUT` 链上，因此**发布端口默认绕过 ufw**（Docker 的已知行为）。
- 因此此前「直连超时、必须走隧道」的判断是**误判**：真正原因是 Docker 网桥丢失 IPv4（见下一节），与防火墙无关。
- **安全提醒**：这意味着 MySQL / Qdrant / Redis 对同网段是开放的。若确实要关闭，需在 compose 里把端口绑定到回环（`127.0.0.1:3306:3306`）或加 `DOCKER-USER` 链规则；仅加 ufw 规则**不起作用**。
- **SSH 隧道仍是可选备选**（远程访问、或已按上面方式真正关闭端口时）：
  ```powershell
  ssh -N -L 13306:127.0.0.1:3306 -L 16333:127.0.0.1:6333 -L 16379:127.0.0.1:6379 root@192.168.222.128
  ```
  本地 3306 / 6379 可能已被宿主机自身的 mysqld / redis-server 占用，故隧道端口落在 13306 / 16333 / 16379；此时需用环境变量覆盖：
  ```powershell
  $env:SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:13306/mindora?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true'
  $env:SPRING_DATA_REDIS_HOST='127.0.0.1'
  $env:SPRING_DATA_REDIS_PORT='16379'
  $env:QDRANT_URL='http://127.0.0.1:16333'
  ```

### 容器端口全部超时？先看 Docker 网桥有没有 IPv4（已复现三次）

症状：`docker ps` 全部 Up、`docker exec` 正常，但宿主机连容器端口全部超时（注意：**`3306` 的 TCP 连接可能仍能建立**，因为 docker-proxy 在监听，只是到容器的转发路径断了）。根因是自定义网桥（`br-<hash>`）在运行期丢失 IPv4 地址与路由。排查与修复：

```bash
ip -4 -br addr show br-<hash>   # 期望有 inet 172.19.0.1/16；只有 IPv6 link-local 即为该问题
ip route | grep 172             # 期望有 172.19.0.0/16
# 首选修复（最小侵入，不用重启容器）：
sudo ip addr add 172.19.0.1/16 dev br-<hash>     # /16 路由随地址自动出现
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:6333/readyz   # 期望 200
# 备选（网桥状态更乱时）：重启 daemon，容器按 unless-stopped 自动拉起
sudo systemctl restart docker
```

注意：`docker restart <单个容器>` 修不好这个问题；该故障是在 docker 未重启的情况下于运行期发生的（已排除 NetworkManager / systemd-networkd，触发者仍未定位——根治需加看门狗）。

### 单实例约束

Lucene 关键词索引是单写者，**不能同时运行两个后端实例**，否则第二个实例启动失败（`Lock held by another program: .../lucene/keyword/write.lock`）。重启后端前先确认旧进程已退出。
