#!/bin/bash
cd /home/jimmy/.openclaw/workspace/practice-project-about-develop
export SPRING_DATASOURCE_PASSWORD=***
export SPRING_SQL_INIT_MODE=never
export NACOS_REGISTER_ENABLED=false
export LOCAL_FRONTEND_AUTO_OPEN=false
nohup java -jar target/demo-springboot-1.0-SNAPSHOT.jar --xxl.job.executor.port=-1 > /tmp/demo-app.log 2>&1 &
echo $! > /tmp/demo-app.pid
echo "PID: $!"
