# WHX Bill

WHX Bill is a separated frontend/backend accounting system inspired by Yimu Bill.

## Structure

- `backend`: Spring Boot 3 accounting API
- `frontend-user`: Vue 3 end-user application
- `frontend-admin`: Vue 3 admin application
- `deploy`: Docker Compose, Nginx, and deployment scripts
- `docs`: schema, Postman collection, deployment notes

## Stack

- Backend: Spring Boot 3, MyBatis-Plus, Spring Security, JWT, Redis, WebSocket
- Frontend: Vue 3, Element Plus, Pinia, Vue Router, ECharts, Quill, Tesseract.js
- Storage: MySQL 8, Redis, Aliyun OSS

## Local Development

1. Create MySQL database `whx_bill`.
2. Run `docs/sql/schema.sql` and `docs/sql/seed.sql`.
3. Configure `backend/src/main/resources/application-dev.yml`.
4. Start backend on port `8080`.
5. Install dependencies in `frontend-user` and `frontend-admin`, then start the Vite dev servers.

## Docker Deployment

The recommended production layout is:

- User site: `http://39.106.168.139/`
- Admin site: `http://39.106.168.139/admin/`
- Backend API: `http://39.106.168.139/api/*`
- WebSocket: `ws://39.106.168.139/ws`

Quick deployment flow:

1. Copy `deploy/.env.example` to `deploy/.env`.
2. Fill in the database password, JWT secret, and OSS settings.
3. On the server, run `bash deploy/scripts/install-docker-centos9.sh`.
4. Run `bash deploy/scripts/deploy.sh`.

More deployment details:

- `docs/deployment.md`

Fresh-server one-click bootstrap:

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

For Alibaba Cloud servers in mainland China, the bootstrap script uses:

- Aliyun Docker CE repo mirror
- your dedicated Docker registry mirror
- Aliyun Maven mirror
- npm mirror registry

## Default Accounts

- Admin: `admin / Admin@123456`
- User: `demo / Demo@123456`
