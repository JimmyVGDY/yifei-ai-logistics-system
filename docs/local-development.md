# 本地开发指南

## 前置要求

- JDK 8
- Maven 3.6+
- Nacos 2.x
- Sentinel Dashboard
- Elasticsearch 7.x

当前项目基于 Spring Boot `2.7.18`，依赖版本通过 `pom.xml` 中的 Spring Cloud 与 Spring Cloud Alibaba BOM 统一管理。

## 启动基础组件

### Nacos

下载并启动 Nacos 后，确认控制台能打开：

```text
http://127.0.0.1:8848/nacos
```

默认账号密码：

```text
nacos / nacos
```

### Sentinel Dashboard

启动 Dashboard，并保持地址为：

```text
127.0.0.1:8858
```

如端口不同，设置环境变量 `SENTINEL_DASHBOARD`。

### Elasticsearch

确认 Elasticsearch 可以访问：

```text
http://127.0.0.1:9200
```

如地址不同，设置环境变量 `ELASTICSEARCH_URIS`。

## 启动应用

```bash
mvn spring-boot:run
```

启动后访问：

```text
http://127.0.0.1:8080/infra/status
```

## 常用验证接口

```text
GET http://127.0.0.1:8080/infra/status
GET http://127.0.0.1:8080/infra/nacos/services
GET http://127.0.0.1:8080/infra/sentinel/ping
GET http://127.0.0.1:8080/infra/elasticsearch/client
GET http://127.0.0.1:8080/actuator/health
```

## IDEA 使用建议

1. 使用 JDK 8 打开项目。
2. 等待 Maven 依赖加载完成。
3. 运行 `jimmy.DemoApplication`。
4. 如果本地没有启动 Nacos、Sentinel 或 Elasticsearch，先启动这些基础服务，或者改成本机实际地址。
