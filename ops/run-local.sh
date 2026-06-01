#!/bin/bash
# 物流管理平台 - 本地开发启动脚本（Linux/WSL）
# 会自动拉起 XXL-Job 调度中心，并在执行器端口冲突时切换备用端口。
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$(dirname "$SCRIPT_DIR")"

echo "物流管理平台本地开发启动"
echo

echo "[1/4] 检查 XXL-Job 调度中心"
if curl -s http://127.0.0.1:8081/xxl-job-admin >/dev/null 2>&1; then
  echo "  调度中心已运行：http://127.0.0.1:8081/xxl-job-admin"
else
  echo "  调度中心未运行，尝试启动..."
  java -jar /mnt/f/Development/Middleware/xxl-job/xxl-job-admin-2.4.0.jar \
    --spring.datasource.url=jdbc:mysql://127.0.0.1:3306/xxl_job?useUnicode=true\&characterEncoding=utf8\&serverTimezone=Asia/Shanghai\&useSSL=false\&allowPublicKeyRetrieval=true \
    --spring.datasource.username=root \
    --spring.datasource.password= \
    --server.port=8081 \
    >/tmp/xxl-job.log 2>&1 &
  sleep 5
  echo "  调度中心启动命令已发送。"
fi

echo
echo "[2/4] 检查后端端口和 XXL-Job 执行器端口"
if (ss -ltn 2>/dev/null || netstat -ltn 2>/dev/null) | grep -q ":8080 "; then
  echo "  8080 端口已被占用，后端可能已经启动。请先关闭重复进程，或改用其他 APP_PORT。"
  exit 1
fi

XXL_EXECUTOR_PORT=9999
if (ss -ltn 2>/dev/null || netstat -ltn 2>/dev/null) | grep -q ":9999 "; then
  echo "  9999 端口已被占用，改用备用端口 10099。"
  XXL_EXECUTOR_PORT=10099
fi
if (ss -ltn 2>/dev/null || netstat -ltn 2>/dev/null) | grep -q ":${XXL_EXECUTOR_PORT} "; then
  echo "  端口 ${XXL_EXECUTOR_PORT} 仍被占用，请关闭重复启动的后端或手动指定其他端口。"
  exit 1
fi

echo
echo "[3/4] 检查应用 JAR"
if [ ! -f target/demo-springboot-1.0-SNAPSHOT.jar ]; then
  echo "  未找到 JAR，开始打包..."
  mvn clean package -DskipTests
else
  echo "  JAR 已存在。"
fi

echo
echo "[4/4] 启动后端应用"
export NACOS_REGISTER_ENABLED=false
export SPRING_SQL_INIT_MODE=never
export LOCAL_FRONTEND_AUTO_START=false
export LOCAL_FRONTEND_AUTO_OPEN=false

nohup java -jar target/demo-springboot-1.0-SNAPSHOT.jar \
  --xxl.job.enabled=true \
  --xxl.job.admin.addresses=http://127.0.0.1:8081/xxl-job-admin \
  --xxl.job.executor.port="${XXL_EXECUTOR_PORT}" \
  >/tmp/demo-app.log 2>&1 &

echo $! >/tmp/demo-app.pid
echo "  PID: $(cat /tmp/demo-app.pid)"
echo "  应用地址：http://127.0.0.1:8080"
echo "  XXL-Job：http://127.0.0.1:8081/xxl-job-admin"
echo "  执行器端口：${XXL_EXECUTOR_PORT}"
