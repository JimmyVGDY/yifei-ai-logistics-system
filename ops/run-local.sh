#!/bin/bash
# 物流管理平台 — 本地开发启动（Linux/WSL）
# 自动启动 XXL-Job 调度中心 + 应用
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$(dirname "$SCRIPT_DIR")"

echo "🚀 物流管理平台 本地开发启动"
echo ""

# 启动 XXL-Job 调度中心（如果没在运行）
echo "[1/3] XXL-Job 调度中心"
if curl -s http://127.0.0.1:8081/xxl-job-admin > /dev/null 2>&1; then
    echo "  ✅ 已在运行 :8081"
else
    echo "  启动中..."
    java -jar /mnt/f/Development/Middleware/xxl-job/xxl-job-admin-2.4.0.jar \
      --spring.datasource.url=jdbc:mysql://127.0.0.1:3306/xxl_job?useUnicode=true\&characterEncoding=utf8\&serverTimezone=Asia/Shanghai\&useSSL=false\&allowPublicKeyRetrieval=true \
      --spring.datasource.username=root \
      --spring.datasource.password= \
      --server.port=8081 \
      > /tmp/xxl-job.log 2>&1 &
    sleep 5
    echo "  ✅ 已启动"
fi

# 启动应用（连接 XXL-Job）
echo "[2/3] 启动应用..."
export NACOS_REGISTER_ENABLED=false
export SPRING_SQL_INIT_MODE=never
export LOCAL_FRONTEND_AUTO_START=false
export LOCAL_FRONTEND_AUTO_OPEN=false

nohup java -jar target/demo-springboot-1.0-SNAPSHOT.jar \
  --xxl.job.admin.addresses=http://127.0.0.1:8081/xxl-job-admin \
  --xxl.job.executor.port=9999 \
  > /tmp/demo-app.log 2>&1 &

echo $! > /tmp/demo-app.pid
echo "  PID: $(cat /tmp/demo-app.pid)"
echo "  XXL-Job: http://127.0.0.1:8081/xxl-job-admin"
