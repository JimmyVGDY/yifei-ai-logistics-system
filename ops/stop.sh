#!/bin/bash
# ==========================================
# 物流管理平台 — 停止所有服务
# 用法: bash ops/stop.sh
# ==========================================
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

echo "🛑 停止物流管理平台..."
docker compose down
echo "✅ 所有服务已停止"
