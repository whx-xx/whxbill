#!/bin/bash
# ====================================================
# WHX Bill System - 终极一键部署脚本
# ====================================================

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info() { echo -e "${GREEN}[INFO] $*${NC}"; }
warn() { echo -e "${YELLOW}[WARN] $*${NC}"; }
error() { echo -e "${RED}[ERROR] $*${NC}"; }

# 前置检查
if [ "$(id -u)" != "0" ]; then
    error "请使用 root 用户执行此脚本"
    exit 1
fi

info "1. 设置文件与目录权限..."
chmod -R 755 /opt/whx-bill-runtime/frontend-user/dist
chmod -R 755 /opt/whx-bill-runtime/frontend-admin/dist
chmod 644 /opt/whx-bill-runtime/backend/backend-1.0.0.jar

info "2. 自动识别系统大版本并配置镜像源..."

# 提取操作系统的 ID 和主版本号 (例如 ID=centos, VERSION_ID=9)
if [ -f /etc/os-release ]; then
    . /etc/os-release
    OS_NAME=$ID
    OS_VERSION=${VERSION_ID%%.*} # 砍掉小数点后面的数字，只留大版本号
else
    error "无法读取 /etc/os-release，系统识别失败！"
    exit 1
fi

# 判断如果是 CentOS 或者其兼容分支，则执行动态换源
if [[ "$OS_NAME" == "centos" || "$OS_NAME" == "rhel" || "$OS_NAME" == "almalinux" || "$OS_NAME" == "rocky" ]]; then
    info ">> 检测到 $OS_NAME 系系统 (大版本: $OS_VERSION)，正在生成对应版本的 USTC 专属源..."
    
    mkdir -p /etc/yum.repos.d/backup
    mv -f /etc/yum.repos.d/*.repo /etc/yum.repos.d/backup/ 2>/dev/null

    # 💡 核心黑科技：使用不带引号的 EOF 允许读取变量，同时用 \$ 转义系统自带变量
    cat << EOF > /etc/yum.repos.d/centos-base.repo
[baseos-wanghx]
name=CentOS Stream \$releasever - BaseOS
baseurl=https://mirrors.ustc.edu.cn/centos-stream/${OS_VERSION}-stream/BaseOS/\$basearch/os/
gpgcheck=1
enabled=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-centosofficial-SHA256

[appstream-wanghx]
name=CentOS Stream \$releasever - AppStream
baseurl=https://mirrors.ustc.edu.cn/centos-stream/${OS_VERSION}-stream/AppStream/\$basearch/os/
gpgcheck=1
enabled=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-centosofficial-SHA256
EOF

    cat << EOF > /etc/yum.repos.d/centos-crb.repo
[crb]
name=CentOS Stream \$releasever - CRB
baseurl=https://mirrors.ustc.edu.cn/centos-stream/${OS_VERSION}-stream/CRB/\$basearch/os/
gpgcheck=1
enabled=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-centosofficial-SHA256
EOF

    cat << EOF > /etc/yum.repos.d/epel.repo
[epel]
name=Extra Packages for Enterprise Linux \$releasever - \$basearch
baseurl=https://mirrors.ustc.edu.cn/epel/${OS_VERSION}/Everything/\$basearch/
gpgcheck=1
enabled=1
gpgkey=file:///etc/pki/rpm-gpg/RPM-GPG-KEY-EPEL-${OS_VERSION}
EOF
    
    dnf clean all >/dev/null 2>&1
    dnf makecache >/dev/null 2>&1
    info ">> $OS_VERSION 版本源配置并刷新完成！"

# 加入防爆屏机制：如果这台机器是 Ubuntu 等其他系统，跳过换源步骤
elif [[ "$OS_NAME" == "ubuntu" || "$OS_NAME" == "debian" ]]; then
    warn "检测到非 CentOS 系统 ($OS_NAME)，跳过 USTC 换源步骤..."
else
    warn "未知或未专门适配的系统 ($OS_NAME)，跳过换源，将尝试直接使用系统自带源..."
fi

info "3. 检查并安装 Docker 环境..."
if ! command -v docker &> /dev/null; then
    warn "Docker 未安装，正在安装依赖并配置阿里云源..."
    dnf install -y dnf-plugins-core >/dev/null 2>&1
    dnf config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo >/dev/null 2>&1
    dnf install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin >/dev/null 2>&1
    systemctl enable docker >/dev/null 2>&1
    systemctl start docker >/dev/null 2>&1
    
    warn "配置 Docker 专属加速器..."
    mkdir -p /etc/docker
    cat << 'INNER_EOF' > /etc/docker/daemon.json
{
  "registry-mirrors": [
    "https://jhzwb8pw.mirror.aliyuncs.com"
  ],
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
INNER_EOF
    sed -i 's+nameserver.*+nameserver 114.114.114.114+' /etc/resolv.conf
    systemctl daemon-reload && systemctl restart docker
    info "Docker 安装完毕！"
else
    info "Docker 已安装，跳过此步。"
fi

info "4. 生成项目配置文件..."
cd /opt/whx-bill-runtime/

# 写入 .env
cat << 'EOF' > .env
MYSQL_ROOT_PASSWORD=WhxBill_Db_Pass2026!
WHX_DB_HOST=whx-bill-mysql
WHX_DB_PORT=3306
WHX_DB_NAME=whx_bill
WHX_DB_USERNAME=root
WHX_DB_PASSWORD=WhxBill_Db_Pass2026!
WHX_REDIS_HOST=whx-bill-redis
WHX_REDIS_PORT=6379
WHX_REDIS_PASSWORD=WhxBill_Redis_Pass2026!
WHX_REDIS_DATABASE=0
WHX_JWT_SECRET=WhxBillSuperSecretKey2026PleaseChangeThis!
WHX_CORS_ALLOWED_ORIGINS=*
WHX_OSS_ENDPOINT=oss-cn-beijing.aliyuncs.com
WHX_OSS_BUCKET=whx-bill-bucket
WHX_OSS_ACCESS_KEY_ID=temp-id
WHX_OSS_ACCESS_KEY_SECRET=temp-secret
WHX_OSS_PUBLIC_HOST=https://whx-bill-bucket.oss-cn-beijing.aliyuncs.com
EOF

# 写入 Nginx 配置 (已修复代理 / 问题)
cat << 'EOF' > nginx/runtime.conf
server {
    listen 80;
    server_name localhost;
    gzip on;
    gzip_min_length 1k;
    gzip_comp_level 6;
    gzip_types text/plain application/javascript application/x-javascript text/css application/xml text/javascript;

    location /api/ {
        proxy_pass http://backend:8080; 
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location /admin/ {
        root /usr/share/nginx/html;
        index index.html index.htm;
        try_files $uri $uri/ /admin/index.html;
    }

    location / {
        root /usr/share/nginx/html/user;
        index index.html index.htm;
        try_files $uri $uri/ /index.html;
    }
}
EOF

# 写入 docker-compose.yml
cat << 'EOF' > docker-compose.yml
services:
  mysql:
    image: docker.m.daocloud.io/library/mysql:8.0
    container_name: whx-bill-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    command:
      - --default-authentication-plugin=mysql_native_password
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
    volumes:
      - mysql-data:/var/lib/mysql
      - ./docs/sql/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro
      - ./docs/sql/seed.sql:/docker-entrypoint-initdb.d/02-seed.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -p$$MYSQL_ROOT_PASSWORD --silent"]
      interval: 10s
      timeout: 5s
      retries: 15

  redis:
    image: docker.m.daocloud.io/library/redis:7
    container_name: whx-bill-redis
    restart: unless-stopped
    environment:
      WHX_REDIS_PASSWORD: ${WHX_REDIS_PASSWORD}
    command:
      - /bin/sh
      - -c
      - |
        if [ -n "$$WHX_REDIS_PASSWORD" ]; then
          exec redis-server --appendonly yes --requirepass "$$WHX_REDIS_PASSWORD";
        else
          exec redis-server --appendonly yes;
        fi
    volumes:
      - redis-data:/data
    healthcheck:
      test:
        [
          "CMD-SHELL",
          "if [ -n \"$$WHX_REDIS_PASSWORD\" ]; then redis-cli -a \"$$WHX_REDIS_PASSWORD\" ping; else redis-cli ping; fi"
        ]
      interval: 10s
      timeout: 3s
      retries: 10

  backend:
    image: docker.m.daocloud.io/library/eclipse-temurin:17-jre
    container_name: whx-bill-backend
    restart: unless-stopped
    volumes:
      - ./backend/backend-1.0.0.jar:/app/app.jar:ro
    environment:
      TZ: Asia/Shanghai 
      SPRING_PROFILES_ACTIVE: prod
      WHX_DB_HOST: ${WHX_DB_HOST}
      WHX_DB_PORT: ${WHX_DB_PORT}
      WHX_DB_NAME: ${WHX_DB_NAME}
      WHX_DB_USERNAME: ${WHX_DB_USERNAME}
      WHX_DB_PASSWORD: ${WHX_DB_PASSWORD}
      WHX_REDIS_HOST: ${WHX_REDIS_HOST}
      WHX_REDIS_PORT: ${WHX_REDIS_PORT}
      WHX_REDIS_PASSWORD: ${WHX_REDIS_PASSWORD}
      WHX_REDIS_DATABASE: ${WHX_REDIS_DATABASE}
      WHX_JWT_SECRET: ${WHX_JWT_SECRET}
      WHX_CORS_ALLOWED_ORIGINS: ${WHX_CORS_ALLOWED_ORIGINS}
      WHX_OSS_ENDPOINT: ${WHX_OSS_ENDPOINT}
      WHX_OSS_BUCKET: ${WHX_OSS_BUCKET}
      WHX_OSS_ACCESS_KEY_ID: ${WHX_OSS_ACCESS_KEY_ID}
      WHX_OSS_ACCESS_KEY_SECRET: ${WHX_OSS_ACCESS_KEY_SECRET}
      WHX_OSS_PUBLIC_HOST: ${WHX_OSS_PUBLIC_HOST}
    command: java -jar /app/app.jar
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy

  nginx:
    image: docker.m.daocloud.io/library/nginx:alpine
    container_name: whx-bill-nginx
    restart: unless-stopped
    ports:
      - "80:80"
    volumes:
      - ./nginx/runtime.conf:/etc/nginx/conf.d/default.conf:ro
      - ./frontend-user/dist:/usr/share/nginx/html/user:ro
      - ./frontend-admin/dist:/usr/share/nginx/html/admin:ro
    depends_on:
      - backend

volumes:
  mysql-data:
  redis-data:
EOF

info "5. 启动 Docker 容器组合..."
docker compose down
docker compose up -d

info "6. 等待 MySQL 启动并强制转换数据编码 (解决乱码坑)..."
warn "倒计时 20 秒，等待数据库就绪..."
sleep 20
docker exec -i whx-bill-mysql mysql -uroot -p'WhxBill_Db_Pass2026!' --default-character-set=utf8mb4 whx_bill < ./docs/sql/seed.sql

info "========================================================"
info "🎉 WHX Bill 项目一键部署完成！"
info "========================================================"