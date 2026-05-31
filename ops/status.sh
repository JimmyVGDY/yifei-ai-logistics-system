#!/bin/bash
# ==========================================
# 物流管理平台 — 服务状态检查
# 用法: bash ops/status.sh
# ==========================================
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

echo "📊 物流管理平台服务状态"
echo "═══════════════════════════════════════"

# 检查 Docker 容器
echo ""
echo "【容器状态】"
docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null || echo "  无运行容器"

# 检查关键端口
echo ""
echo "【端口检查】"
check_port() {
    local port=$1
    local name=$2
    if ss -tlnp | grep -q ":$port "; then
        echo "  ✅ $name :$port 运行中"
    else
        echo "  ❌ $name :$port 未运行"
    fi
}
check_port 3306 "MySQL    "
check_port 6379 "Redis    "
check_port 5672 "RabbitMQ "
check_port 9200 "ES       "
check_port 8848 "Nacos    "
check_port 8858 "Sentinel "
check_port 8080 "应用     "

# 检查应用健康
echo ""
echo "【应用健康检查】"
HEALTH=$(curl -s --max-time 3 http://localhost:8080/actuator/health 2>/dev/null)
if echo "$HEALTH" | grep -q '"status":"UP"'; then
    echo "  ✅ 应用健康 - $HEALTH"
elif echo "$HEALTH" | grep -q '"status":"DOWN"'; then
    echo "  ⚠️  应用 DOWN - 依赖服务可能未就绪"
else
    echo "  ❌ 应用无响应"
fi
