#!/bin/bash
# ==========================================
# 物流管理平台 — 查看应用日志
# 用法: bash ops/logs.sh            # 查看所有日志
#       bash ops/logs.sh app        # 只看应用日志
#       bash ops/logs.sh -f         # 实时跟踪
#       bash ops/logs.sh app 100    # 最近100行
# ==========================================
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

SERVICE=${1:-""}
FLAG=${2:-"--tail=200"}

# 如果第一个参数是 -f，则实时跟踪全部
if [ "$SERVICE" = "-f" ]; then
    docker compose logs -f
    exit 0
fi

# 如果第一个参数是数字，则调整 tail 行数
if [[ "$SERVICE" =~ ^[0-9]+$ ]]; then
    docker compose logs --tail="$SERVICE"
    exit 0
fi

if [ -z "$SERVICE" ]; then
    docker compose logs "$FLAG"
elif [ "$2" = "-f" ]; then
    docker compose logs -f "$SERVICE"
else
    docker compose logs "$SERVICE" "$FLAG"
fi
