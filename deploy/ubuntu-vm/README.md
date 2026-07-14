# MindOra Ubuntu VM Deployment Notes

This directory documents the existing MindOra infrastructure on the Ubuntu VM.

## VM

- Host: `192.168.222.128`
- User: `root`
- OS: Ubuntu Live Server 24.04
- Compose project: `mindora`
- Deployment directory: `/data/proj/MindOra/deploy/ubuntu-vm`
- Data directory: `/data/docker-data/mindora`

## Containers

Expected running containers:

```text
mindora-mysql
mindora-redis
mindora-qdrant
mindora-rmqnamesrv
mindora-rmqbroker
```

All containers should use `restart=unless-stopped`.

## Compose Commands

Run commands from the VM deployment directory:

```bash
cd /data/proj/MindOra/deploy/ubuntu-vm
docker compose -p mindora --env-file .env -f compose.yaml up -d
docker compose -p mindora ps
```

For read-only status checks:

```bash
cd /data/proj/MindOra/deploy/ubuntu-vm
docker compose -p mindora ps
docker compose -p mindora logs --tail=80 rmqnamesrv rmqbroker
```

## Service Checks

```bash
docker exec mindora-mysql mysqladmin ping -h localhost -pmindora_root_password
docker exec mindora-redis redis-cli ping
curl http://127.0.0.1:6333/healthz
docker logs --tail=50 mindora-rmqnamesrv
docker logs --tail=50 mindora-rmqbroker
```

Windows backend development should use these addresses:

```text
MySQL: 192.168.222.128:3306
Redis: 192.168.222.128:6379
Qdrant: http://192.168.222.128:6333
RocketMQ NameServer: 192.168.222.128:9876
Local storage root: /data/docker-data/mindora/storage
```

The broker must advertise the VM IP for Windows clients. Keep
`ROCKETMQ_BROKER_IP=192.168.222.128` in `.env` unless the VM address changes.
