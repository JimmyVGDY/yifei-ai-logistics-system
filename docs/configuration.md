# 配置说明

项目使用 `bootstrap.yml` 管理应用名、环境和 Nacos，使用 `application.yml` 管理端口、Sentinel、Elasticsearch 和监控端点。

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | 当前运行环境 |
| `SERVER_PORT` | `8080` | 应用端口 |
| `NACOS_SERVER_ADDR` | `127.0.0.1:8848` | Nacos 服务地址 |
| `NACOS_USERNAME` | `nacos` | Nacos 用户名 |
| `NACOS_PASSWORD` | `nacos` | Nacos 密码 |
| `NACOS_DISCOVERY_NAMESPACE` | 空 | Nacos 注册发现命名空间 |
| `NACOS_DISCOVERY_GROUP` | `DEFAULT_GROUP` | Nacos 注册发现分组 |
| `NACOS_CONFIG_NAMESPACE` | 空 | Nacos 配置中心命名空间 |
| `NACOS_CONFIG_GROUP` | `DEFAULT_GROUP` | Nacos 配置中心分组 |
| `SENTINEL_DASHBOARD` | `127.0.0.1:8858` | Sentinel Dashboard 地址 |
| `SENTINEL_API_PORT` | `8719` | Sentinel 客户端通信端口 |
| `ELASTICSEARCH_URIS` | `http://127.0.0.1:9200` | Elasticsearch 地址 |
| `ELASTICSEARCH_USERNAME` | 空 | Elasticsearch 用户名 |
| `ELASTICSEARCH_PASSWORD` | 空 | Elasticsearch 密码 |

## Nacos 配置命名

默认应用名是 `practice-project-about-develop`，默认环境是 `dev`。

在 Nacos 配置中心可以创建：

```text
Data ID: practice-project-about-develop-dev.yaml
Group: DEFAULT_GROUP
```

常见配置示例：

```yaml
demo:
  message: hello from nacos
```

## Sentinel

启动应用并访问接口后，应用会向 Sentinel Dashboard 上报资源信息。当前示例资源名：

```text
infraSentinelPing
```

可以在 Dashboard 中对该资源添加流控规则。

## Elasticsearch

Spring Boot 会根据 `spring.elasticsearch.uris` 自动创建 Elasticsearch 客户端。业务代码中可以继续使用：

- `ElasticsearchOperations`
- Spring Data Elasticsearch Repository
