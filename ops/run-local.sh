#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

if [ ! -f "target/demo-springboot-1.0-SNAPSHOT.jar" ]; then
    mvn clean package -DskipTests -q
fi

export SPRING_SQL_INIT_MODE=never
export NACOS_REGISTER_ENABLED=false
export LOCAL_FRONTEND_AUTO_OPEN=false
nohup java -jar target/demo-springboot-1.0-SNAPSHOT.jar --xxl.job.enabled=false > /tmp/demo-app.log 2>&1 &
echo $! > /tmp/demo-app.pid
echo "PID: $!"
