# MindOra

MindOra is a personal knowledge management platform for writing, storing,
retrieving, and asking questions over a personal knowledge base.

## Stage 0 Scope

- Spring Boot 3 modular monolith under `backend/`
- Maven backend build with Java 21
- Vue 3 + Vite npm workspace under `frontend/`
- Ubuntu VM hosted infrastructure through Docker Compose
- MySQL, Redis, Qdrant, RocketMQ, and local file storage configuration
- Environment template and startup documentation

## Current Infrastructure

Docker runs on the Ubuntu VM, not on the Windows development machine.

| Component           | Address                             |
| ------------------- | ----------------------------------- |
| Ubuntu VM           | `root@192.168.222.128`              |
| MySQL               | `192.168.222.128:3306`              |
| Redis               | `192.168.222.128:6379`              |
| Qdrant              | `http://192.168.222.128:6333`       |
| RocketMQ NameServer | `192.168.222.128:9876`              |
| Storage root        | `/data/docker-data/mindora/storage` |

## Backend

Run backend commands from the Maven root:

```powershell
cd backend
mvn -s .mvn\mindora-settings.xml -gs .mvn\mindora-settings.xml test
mvn -s .mvn\mindora-settings.xml -gs .mvn\mindora-settings.xml -pl mindora-app -am spring-boot:run
```

The local Maven settings keep dependency cache inside the workspace at
`.m2/repository` so the build does not depend on user-level repository
permissions.

The Spring Boot entry point is
`backend/mindora-app/src/main/java/com/mindora/app/MindOraApplication.java`.
Use this class as the IDEA run configuration main class.

Stage 1 uses Flyway to create the content tables in MySQL:

- `category`
- `tag`
- `blog_article`
- `blog_article_tag`

Development seed data is disabled by default. Enable it only for local
debugging:

```powershell
cd backend
$env:MINDORA_SEED_ENABLED='true'
mvn -s .mvn\mindora-settings.xml -gs .mvn\mindora-settings.xml -pl mindora-app -am spring-boot:run
```

Build and run the backend container image from `backend/`:

```powershell
docker build -t mindora-app:local .
docker run --rm -p 8080:8080 `
  -e MYSQL_HOST=192.168.222.128 `
  -e MYSQL_PORT=3306 `
  -e MYSQL_DATABASE=mindora `
  -e MYSQL_USER=mindora `
  -e MYSQL_PASSWORD=mindora_password `
  -e MINDORA_SEED_ENABLED=true `
  mindora-app:local
```

## Frontend

Install dependencies and run checks from the repository root:

```powershell
npm install --cache .npm-cache
npm test --workspace @mindora/api-client
npm run typecheck
npm run lint
npm run format:check
```

Run the public site and admin shell:

```powershell
npm run dev --workspace @mindora/site
npm run dev --workspace @mindora/admin
```

## Ubuntu VM Compose

Copy or sync `compose.yaml` and `.env` to the VM deployment directory:

```bash
ssh root@192.168.222.128
cd /data/proj/MindOra/deploy/ubuntu-vm
docker compose -p mindora --env-file .env -f compose.yaml up -d
docker compose -p mindora ps
```

If containers are already running, inspect them before restarting:

```bash
cd /data/proj/MindOra/deploy/ubuntu-vm
docker compose -p mindora ps
```

## Infrastructure Checks

Run these on the Ubuntu VM:

```bash
docker exec mindora-mysql mysqladmin ping -h localhost -pmindora_root_password
docker exec mindora-redis redis-cli ping
curl http://127.0.0.1:6333/healthz
docker logs --tail=50 mindora-rmqnamesrv
docker logs --tail=50 mindora-rmqbroker
```

## Deletion Safety

Do not batch delete files or directories in this repository. Avoid commands such
as `Remove-Item -Recurse`, `rm -rf`, `rmdir /s`, `rd /s`, and `del /s`.

When a file must be removed, remove one explicit file path at a time.
