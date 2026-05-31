# Sentinel Dashboard Docker 镜像
# 基于公开 Sentine l JAR 构建，与宿主机版本保持一致
FROM openjdk:8-jre-slim

LABEL maintainer="jimmy"
LABEL description="Sentinel Dashboard 1.8.8"

WORKDIR /app

# 从宿主机复制 JAR（Docker 上下文为项目根目录）
COPY docker/sentinel/sentinel-dashboard-1.8.8.jar sentinel-dashboard.jar

EXPOSE 8858

ENTRYPOINT ["java", "-Dserver.port=8858", "-Dcsp.sentinel.dashboard.server=localhost:8858", "-jar", "sentinel-dashboard.jar"]
