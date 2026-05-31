# 物流管理平台 Docker 镜像
# 构建: docker build -t logistics-app:latest .
# 运行: docker run -p 8080:8080 logistics-app:latest
FROM openjdk:8-jre-slim

LABEL maintainer="jimmy"
LABEL description="物流管理系统 Spring Boot 应用"

WORKDIR /app

# 创建日志和上传目录
RUN mkdir -p /app/logs /app/uploads

COPY target/demo-springboot-1.0-SNAPSHOT.jar app.jar

EXPOSE 8080

# JVM 参数可通过环境变量覆盖
ENV JAVA_OPTS="-Xms256m -Xmx512m"

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
