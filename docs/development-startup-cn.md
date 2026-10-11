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

前端 API 默认连接本地后端：

```text
http://localhost:8080
```

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

### 端口：ufw 未放行 3306 / 6333，用 SSH 隧道

VM 的 ufw 只放行 22 / 6379 / 8080 / 9876 / 10909-10912，宿主机直连 MySQL（3306）与 Qdrant（6333）会超时。两种做法：

- **推荐（本次采用）**：SSH 隧道，不改 VM 防火墙：
  ```powershell
  ssh -N -L 13306:127.0.0.1:3306 -L 16333:127.0.0.1:6333 -L 16379:127.0.0.1:6379 user@192.168.222.128
  ```
  本地 3306 / 6379 已被宿主机自身的 mysqld / redis-server 占用，所以隧道端口落在 13306 / 16333 / 16379，并在启动后端时用环境变量覆盖：
  ```powershell
  $env:SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:13306/mindora?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true'
  $env:SPRING_DATA_REDIS_HOST='127.0.0.1'
  $env:SPRING_DATA_REDIS_PORT='16379'
  $env:QDRANT_URL='http://127.0.0.1:16333'
  ```
- **直连**：在 VM 上执行 `sudo ufw allow 3306/tcp` 与 `sudo ufw allow 6333/tcp`（会扩大暴露面，仅在可信网络下使用）。

### 容器端口全部超时？先看 Docker 网桥有没有 IPv4

症状：`docker ps` 全部 Up、`docker exec` 正常，但宿主机连容器端口（含已放行的 6379）与容器 IP 全部超时。原因通常是自定义网桥（`br-<hash>`）丢失 IPv4 地址与路由。排查：

```bash
ip addr show br-<hash>   # 期望有 inet 172.19.0.1/16；若只有 IPv6 link-local 即为该问题
ip route | grep 172
sudo systemctl restart docker   # 修复；容器按 unless-stopped 自动拉起，数据卷不受影响
```

注意：`docker restart <单个容器>` 修不好这个问题，必须重启 daemon。

### 单实例约束

Lucene 关键词索引是单写者，**不能同时运行两个后端实例**，否则第二个实例启动失败（`Lock held by another program: .../lucene/keyword/write.lock`）。重启后端前先确认旧进程已退出。
