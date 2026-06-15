

#!/bin/bash
# created by wanghx
# date:20260614
# 这个脚本的作用是，实现docker的一键部署，最后会拉取nginx镜像，启动，使用的端口是8888


# ===================== 工具函数 =====================
# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # 无颜色

info() { echo -e "${GREEN}[INFO] $*${NC}"; }
warn() { echo -e "${YELLOW}[WARN] $*${NC}"; }
error() { echo -e "${RED}[ERROR] $*${NC}"; }

# 执行命令并判断结果
check_ok() {
    if [ $? -eq 0 ]; then
        info "$1 完成"
    else
        error "$1 失败，脚本退出"
        exit 1
    fi
}

# ===================== 前置检查 =====================

# 判断当前执行脚的用户是不是root。
if [ "$(id -u)" != "0" ]; then
    error "请使用 root 用户或 sudo 执行此脚本"
    exit 1
fi

info "==================== 开始安装 Docker ===================="

# 1. 安装依赖工具
warn "正在安装依赖工具..."
sudo dnf install -y dnf-plugins-core >/dev/null 2>&1
check_ok "安装依赖工具 dnf-plugins-core"

# 2. 添加阿里云Docker源
warn "正在添加阿里云 Docker 官方源..."
sudo dnf config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo >/dev/null 2>&1
check_ok "添加阿里云 Docker 源"


# 3. 安装Docker CE
warn "正在安装 Docker CE、CLI、containerd..."
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin >/dev/null 2>&1
check_ok "安装 Docker 核心组件"

# 4. 启动并开机自启
warn "正在设置 Docker 开机自启并启动..."
sudo systemctl enable docker >/dev/null 2>&1
sudo systemctl start docker >/dev/null 2>&1
check_ok "Docker 启动并设置开机自启"

# 5. 配置镜像加速器
warn "正在配置镜像加速器..."
sudo mkdir -p /etc/docker 
sudo tee /etc/docker/daemon.json >/dev/null 2>&1 <<-'EOF'
{
  "registry-mirrors": [
    "https://docker.1ms.run",
    "https://docker.xuanyuan.me",
    "https://dockerproxy.com"
  ],
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
EOF
check_ok "写入镜像加速器配置"

# 修改本地DNS地址
#6. 修改一下DNS地址，否则可以解析不到加速器的网址
warn "正在修改DNS地址..."
sudo sed -i 's+nameserver.*+nameserver 114.114.114.114+' /etc/resolv.conf
check_ok "DNS地址修改"

# 7. 重启生效
warn "正在重启 Docker 使配置生效..."
sudo systemctl daemon-reload >/dev/null 2>&1
sudo systemctl restart docker >/dev/null 2>&1
check_ok "重启 Docker 服务"

# 8. 验证安装
info "==================== 安装结果验证 ===================="
info "Docker 版本："
docker --version

info "Docker Compose 版本："
docker compose version

info "==================== 加速器配置验证 ===================="
docker info | grep -A 5 "Registry Mirrors"

info "========================================================"
info "✅ Docker 安装 + 镜像加速器 全部配置完成！"
info "========================================================"
