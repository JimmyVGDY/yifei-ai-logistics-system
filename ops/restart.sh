#!/bin/bash
# ==========================================
# 物流管理平台 — 重启应用（中间件不动）
# 用法: bash ops/restart.sh
# ==========================================
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

echo "🔄 重启应用服务..."
docker compose build app
docker compose up -d --no-deps app

echo "⏳ 等待应用就绪..."
for i in $(seq 1 12); do
    if curl -s http://localhost:${APP_PORT:-8080}/actuator/health | grep -q 'UP'; then
        echo "✅ 应用重启完成"
        exit 0
    fi
    sleep 5
done
echo "⚠️  等待超时"
exit 1
