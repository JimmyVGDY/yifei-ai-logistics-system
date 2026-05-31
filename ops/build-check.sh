#!/bin/bash
# 物流管理平台 — 编译检查（Linux/WSL）
# 用法: bash ops/build-check.sh
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$(dirname "$SCRIPT_DIR")"

echo "╔══════════════════════════════════╗"
echo "║   物流管理平台 — 编译检查        ║"
echo "╚══════════════════════════════════╝"
echo ""

echo "[1/2] 编译项目..."
if *** clean compile -B -q 2>&1; then
    echo ""
    echo "✅ 编译通过"
else
    echo ""
    echo "❌ 编译失败"
    exit 1
fi

echo "   检查时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "   项目路径: $(pwd)"
