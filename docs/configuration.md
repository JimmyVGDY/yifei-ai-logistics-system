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
| `REDIS_HOST` | `127.0.0.1` | Redis 地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | 空 | Redis 密码 |
| `REDIS_DATABASE` | `0` | Redis 数据库编号 |
| `RABBITMQ_HOST` | `127.0.0.1` | RabbitMQ 地址 |
| `RABBITMQ_PORT` | `5672` | RabbitMQ AMQP 端口 |
| `RABBITMQ_USERNAME` | `guest` | RabbitMQ 用户名 |
| `RABBITMQ_PASSWORD` | `guest` | RabbitMQ 密码 |
| `BLOOM_FILTER_EXPECTED_INSERTIONS` | `100000` | 布隆过滤器预计写入数量 |
| `BLOOM_FILTER_FALSE_POSITIVE_PROBABILITY` | `0.01` | 布隆过滤器误判率 |

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

## Redis

项目默认连接本机 Redis：

```text
127.0.0.1:6379
```

可直接注入：

- `StringRedisTemplate`
- `RedisTemplate`
- Spring Cache 后续扩展

## RabbitMQ

项目默认连接本机 RabbitMQ：

```text
AMQP: 127.0.0.1:5672
Management: http://127.0.0.1:15672
```

默认创建：

```text
Exchange: practice.demo.exchange
Queue: practice.demo.queue
Routing Key: practice.demo.routing-key
```

## Bloom Filter

布隆过滤器使用 Guava 实现，适合做防缓存穿透、快速存在性判断等场景。

默认参数：

```text
expectedInsertions = 100000
falsePositiveProbability = 0.01
```
