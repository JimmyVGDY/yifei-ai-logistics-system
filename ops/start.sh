#!/bin/bash
# ==========================================
# 物流管理平台 — 一键启动
# 用法: bash ops/start.sh
# ==========================================
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

echo "🚀 启动物流管理平台全栈服务..."

# 检查 .env 文件
if [ -f ".env" ]; then
    echo "   已加载 .env 环境变量"
    export $(grep -v '^#' .env | grep -v '^$' | xargs)
elif [ -f ".env.example" ]; then
    echo "⚠️  未找到 .env，使用 .env.example 默认值"
    export $(grep -v '^#' .env.example | grep -v '^$' | xargs)
fi

# 确保 JAR 包存在
if [ ! -f "target/demo-springboot-1.0-SNAPSHOT.jar" ]; then
    echo "📦 首次启动，正在构建应用..."
    mvn clean package -DskipTests -q
    echo "   构建完成"
fi

# 启动全部服务
echo "   Docker Compose 启动中..."
docker compose up -d

echo ""
echo "⏳ 等待服务就绪（最多 120 秒）..."
for i in $(seq 1 24); do
    if curl -s http://localhost:${APP_PORT:-8080}/actuator/health | grep -q 'UP'; then
        echo "✅ 所有服务启动完成！"
        echo ""
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "  应用: http://localhost:${APP_PORT:-8080}"
        echo "  看板: http://localhost:${APP_PORT:-8080}/logistics/dashboard"
        echo "  Nacos: http://localhost:${NACOS_PORT:-8848}/nacos"
        echo "  RabbitMQ: http://localhost:${RABBITMQ_MGMT_PORT:-15672}"
        echo "  Sentinel: http://localhost:${SENTINEL_PORT:-8858}"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        exit 0
    fi
    sleep 5
done

echo "⚠️  等待超时，请检查日志: docker compose logs app"
exit 1
