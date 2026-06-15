# Deployment Notes

## Target Topology

- User site: `http://39.106.168.139/`
- Admin site: `http://39.106.168.139/admin/`
- API entry: `http://39.106.168.139/api/*`
- WebSocket entry: `ws://39.106.168.139/ws`

All services are started by Docker Compose:

- `mysql`
- `redis`
- `backend`
- `frontend-user`
- `frontend-admin`
- `nginx`

## First-Time Server Setup

1. Install Git and Docker on the CentOS Stream 9 server:

   ```bash
   bash deploy/scripts/install-docker-centos9.sh
   ```

2. Clone the repository:

   ```bash
   git clone <your-repo-url> whxbill
   cd whxbill
   ```

3. Create the production environment file:

   ```bash
   cp deploy/.env.example deploy/.env
   ```

4. Edit `deploy/.env` and set:

- `MYSQL_ROOT_PASSWORD`
- `WHX_DB_PASSWORD`
- `WHX_JWT_SECRET`
- `WHX_OSS_ENDPOINT`
- `WHX_OSS_BUCKET`
- `WHX_OSS_ACCESS_KEY_ID`
- `WHX_OSS_ACCESS_KEY_SECRET`
- `WHX_OSS_PUBLIC_HOST`

## One-Click Bootstrap

For a freshly reset server, you can bootstrap everything with one script:

```bash
curl -fsSL https://gitee.com/whx1002/whxbill/raw/master/deploy/scripts/bootstrap-server.sh -o bootstrap-server.sh
chmod +x bootstrap-server.sh
DB_PASSWORD='your-db-password' \
JWT_SECRET='your-long-random-jwt-secret' \
OSS_ACCESS_KEY_ID='your-oss-ak' \
OSS_ACCESS_KEY_SECRET='your-oss-sk' \
DOCKER_REGISTRY_MIRROR='https://jhzwb8pw.mirror.aliyuncs.com' \
REPO_URL='https://gitee.com/whx1002/whxbill.git' \
BRANCH='master' \
bash bootstrap-server.sh
```

Optional variables:

- `PROJECT_DIR` default: `/opt/whx-bill`
- `REPO_URL` default: `https://gitee.com/whx1002/whxbill.git`
- `BRANCH` default: `master`
- `SERVER_IP` default: `39.106.168.139`
- `MYSQL_ROOT_PASSWORD` default: same as `DB_PASSWORD`
- `REDIS_PASSWORD` optional
- `OSS_BUCKET` default: `whxleadnews`
- `OSS_ENDPOINT` default: `oss-cn-beijing.aliyuncs.com`
- `OSS_PUBLIC_HOST` default: `https://whxleadnews.oss-cn-beijing.aliyuncs.com`
- `DOCKER_REGISTRY_MIRROR` default: `https://jhzwb8pw.mirror.aliyuncs.com`

This bootstrap flow is optimized for mainland China cloud servers and uses:

- Aliyun Docker CE repo mirror
- your dedicated Docker registry mirror
- Aliyun Maven mirror
- npm mirror registry

## Deploy

Run:

```bash
bash deploy/scripts/deploy.sh
```

This will:

- build backend and frontend images
- start MySQL and Redis
- auto-import SQL on the first MySQL startup
- expose the project on port `80`

## Update

After pushing new code to Git, update the server with:

```bash
bash deploy/scripts/update.sh
```

## Database Backup

Create a MySQL backup with:

```bash
bash deploy/scripts/backup-db.sh
```

Backups are written into `deploy/backups/`.

## Notes

- Aliyun OSS stays enabled in production through environment variables.
- Uploaded file URLs are composed by `public-host/objectKey`.
- STOMP WebSocket endpoint is `/ws`.
- User notification destination remains `/user/queue/notifications`.
