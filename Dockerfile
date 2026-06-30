# 物流管理平台 Docker 镜像
# 构建: docker build -t logistics-app:latest .
# 运行: docker run -p 8080:8080 logistics-app:latest
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre

LABEL maintainer="jimmy"
LABEL description="物流管理系统 Spring Boot 应用"

WORKDIR /app

# 创建日志和上传目录
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r appuser \
    && useradd -r -g appuser -d /app -s /usr/sbin/nologin appuser \
    && mkdir -p /app/logs /app/uploads \
    && chown -R appuser:appuser /app

COPY --from=builder /workspace/target/demo-springboot-1.0-SNAPSHOT.jar app.jar
RUN chown appuser:appuser app.jar

EXPOSE 8080
USER appuser

# JVM 参数可通过环境变量覆盖
ENV JAVA_OPTS="-Xms256m -Xmx512m"

HEALTHCHECK --interval=30s --timeout=5s --retries=3 --start-period=60s \
  CMD curl -fsS http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
