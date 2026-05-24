# MyBatis 使用说明

项目已经接入 MyBatis，并提供一个 `demo_user` 示例表、Mapper、Service 和 Controller。

## 默认开发模式

默认使用 H2 内存数据库，兼容 MySQL 语法模式。这样不用先安装 MySQL，也可以直接启动项目验证 MyBatis。

默认配置：

```text
SPRING_DATASOURCE_DRIVER=org.h2.Driver
SPRING_DATASOURCE_URL=jdbc:h2:mem:practice_dev;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
SPRING_DATASOURCE_USERNAME=sa
SPRING_DATASOURCE_PASSWORD=
```

启动时会自动执行：

```text
src/main/resources/schema.sql
src/main/resources/data.sql
```

## 切换到 MySQL

项目已经包含 MySQL 驱动。切换真实 MySQL 时，设置环境变量即可：

```text
SPRING_DATASOURCE_DRIVER=com.mysql.cj.jdbc.Driver
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/practice_dev?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=你的密码
SPRING_SQL_INIT_MODE=never
```

如果需要让项目自动初始化表结构，可以把 `SPRING_SQL_INIT_MODE` 设置为 `always`。

## 文件位置

```text
src/main/java/jimmy/entity/DemoUser.java
src/main/java/jimmy/mapper/DemoUserMapper.java
src/main/resources/mapper/DemoUserMapper.xml
src/main/java/jimmy/service/DemoUserService.java
src/main/java/jimmy/controller/DemoUserController.java
```

## 示例接口

```text
GET  /demo-users
GET  /demo-users/detail?id=1
POST /demo-users?username=test&displayName=Test
```

## 后续开发约定

- 实体类放在 `entity` 包。
- Mapper 接口放在 `mapper` 包。
- XML 文件放在 `src/main/resources/mapper`。
- 业务逻辑放在 `service` 包。
- HTTP 接口放在 `controller` 包。
