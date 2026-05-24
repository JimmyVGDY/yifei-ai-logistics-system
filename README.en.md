# practice-project-about-develop

A Spring Boot practice project with basic integrations for Nacos, Sentinel and Elasticsearch.

## Stack

- Java 8
- Spring Boot 2.7.18
- Spring Cloud 2021.0.9
- Spring Cloud Alibaba 2021.0.6.0
- Nacos Discovery / Config
- Sentinel
- Spring Data Elasticsearch
- Spring Data Redis
- RabbitMQ
- Guava Bloom Filter
- MyBatis
- MySQL 8.4 / H2
- Maven

## Run

Start Nacos, Sentinel Dashboard and Elasticsearch first, then run:

```bash
mvn spring-boot:run
```

Default application URL:

```text
http://127.0.0.1:8080
```

Infrastructure status endpoint:

```text
GET http://127.0.0.1:8080/infra/status
```

## Documentation

- [Architecture](docs/architecture.md)
- [Configuration](docs/configuration.md)
- [Local Development](docs/local-development.md)
- [MyBatis](docs/mybatis.md)
