# practice-project-about-develop

A logistics management system built with Spring Boot + Vue 3, integrating Nacos, Sentinel, Elasticsearch, Redis, RabbitMQ, MyBatis, Sa-Token, and Bloom Filter. Designed as a full-stack practice project covering end-to-end business workflows, middleware integration, permission control, and a management frontend.

## Stack

- Java 8
- Spring Boot 2.7.18
- Spring Cloud 2021.0.9 / Spring Cloud Alibaba 2021.0.6.0
- Nacos (Discovery / Config) · Sentinel · Spring Data Elasticsearch
- Spring Data Redis · RabbitMQ · Guava Bloom Filter
- MyBatis · MySQL 8.4 / H2
- Sa-Token (Auth & RBAC)
- Vue 3 · Vite · Element Plus
- Docker Compose · Prometheus · Grafana · Filebeat · Kibana · XXL-Job

## Features

- Sa-Token login, session management, RBAC menu & button-level permissions.
- Logistics order CRUD, pagination, keyword/time-range search.
- Waybill partial-fill support (cargo name, weight, volume, planned time can be added later).
- Full module management: customers, orders, drivers, vehicles, dispatches, tasks, tracks, exceptions, fees, users, roles, uploads.
- Redis order detail cache with LRU eviction and cache-backfill on query.
- Bloom Filter for order number pre-check to reduce invalid lookups.
- RabbitMQ order-creation events → auto-initialize logistics tracking.
- Elasticsearch order search index and keyword search API.
- Sentinel rate limiting on high-frequency endpoints.
- Operation audit log: change summaries, sanitized parameters, error messages for troubleshooting.
- Log security: userId, IP, phone, email, ID card masking (`LogMaskUtils`), sensitive parameter filtering.
- Excel export for all modules, customer data import from `.xlsx`.
- Docker Compose 13-service orchestration (MySQL, Redis, RabbitMQ, ES, Nacos, Sentinel, App, Prometheus, Grafana, Filebeat, Kibana, XXL-Job).

## Quick Start

Ensure MySQL, Nacos, Sentinel, Elasticsearch, Redis, and RabbitMQ are running locally, then:

```bash
mvn spring-boot:run
```

App: `http://127.0.0.1:8080` · Frontend: `http://127.0.0.1:5173`

Default admin: `admin` / `963311213`

Check infrastructure health:

```
GET http://127.0.0.1:8080/infra/status
```

## Documentation

- [Getting Started](docs/getting-started.md) — 10-minute onboarding guide
- [Development Guide](docs/development-guide.md) — coding standards, Git workflow, code review checklist
- [Architecture](docs/architecture.md) — project structure and layer conventions
- [Configuration](docs/configuration.md) — environment variables and component config
- [Local Development](docs/local-development.md) — middleware setup and deployment
- [Logistics API](docs/logistics-api.md) — all API endpoints
- [Auth API](docs/auth-api.md) — login, session, logout
- [User API](docs/user-api.md) — user management
- [Role & Permission API](docs/role-permission-api.md) — RBAC model and permission config
- [Logistics Database](docs/logistics-database.md) — table design and relationships
- [MyBatis Guide](docs/mybatis.md) — SQL conventions and dynamic SQL boundaries
- [Frontend Guide](docs/frontend.md) — Vue 3 project structure
- [Incremental Migration](docs/incremental-migration.md) — database migration scripts
- [Trace & Audit](docs/trace-context-audit.md) — trace context and audit identifiers
- [Requirements Mapping](docs/requirements-mapping.md) — feature-to-requirement coverage
