# M04 互动模块

本模块负责评分、评论、评分摘要和禁用用户的互动数据清理。接口及跨模块联调约定见 [M04 互动接口与数据库交接](../../../../specs/contracts/interaction-module.md)。当前工作区已经补齐服务端接口和本地 MySQL 集成测试；生产主工程接入、真实 JWT 和小程序双账号联调仍需由 M01、M02、M07 配合完成。

生产主工程接入时，M01 将候选文件 `src/main/resources/db/migration-candidates/m04/interaction.sql` 复制到正式 Flyway 位置并登记版本号。迁移依赖 `users`、`posts` 已创建。主工程须纳入 `edu.szu.moments.interaction` 的实体扫描，并沿用 UTC 时间配置。表结构以 Flyway SQL 为准，JPA 用于映射与校验。

## 已实现接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `PUT` | `/api/v1/posts/{postId}/rating` | 普通登录用户提交 1–5 的整数评分；同一用户再次提交会更新原评分，并返回本次评分、平均分和人数。 |
| `GET` | `/api/v1/posts/{postId}/comments?page=1&pageSize=10` | 公开分页查询，只返回 `ACTIVE` 评论；`page` 从 1 开始，`pageSize` 允许 1–50，按 `createdAt DESC, id DESC` 稳定排序。 |
| `POST` | `/api/v1/posts/{postId}/comments` | 普通登录用户提交评论；首尾空白会去除，按 Unicode 码点限制 1–500 字符。 |

统一错误响应沿用 `ApiResponse`。请求格式或字段非法返回 `400/VALIDATION_ERROR`，未登录或已禁用返回 `401/UNAUTHENTICATED`，作者自评或管理员操作返回 `403/FORBIDDEN`，动态不存在、已删除或作者已禁用返回 `404/NOT_FOUND`。数据库临时不可用或连接池无法取得连接时返回 `503/SERVICE_BUSY`，并带 `Retry-After: 1`。

## 并发与一致性

- `ratings` 使用 `UNIQUE(post_id, user_id)`，评分写入使用 MySQL 原子 `INSERT ... ON DUPLICATE KEY UPDATE`，避免“先查询再插入”的竞态；同一用户对同一动态在并发提交后仍只有一行。
- 评分和评论写入遵循统一锁顺序：当前用户行 → 动态行 → 互动数据。互动可见性检查使用动态行的 `PESSIMISTIC_READ` 共享锁，允许多个用户同时对同一动态互动；动态编辑、删除仍使用 `PESSIMISTIC_WRITE` 排他锁，因此删除会等待正在进行的互动事务结束，避免检查通过后动态立即被删除。用户禁用可以据此与互动写入协调，避免一方看到已禁用状态后继续写入。
- 评分摘要使用按动态批量聚合查询，不逐条查询；评论分页使用 `(post_id, status, created_at, id)` 索引和稳定的时间、ID 排序。
- 当前测试证明的是并发一致性和数据约束，不等同于生产环境的 QPS、连接数或容量达标。正式接入后还需要在主工程和独立压测环境复验。

## 验证候选表结构和接口

准备 JDK 21、Docker。从项目根目录执行：

```sh
bash src/backend/modules/interaction/scripts/verify_schema.sh
```

脚本启动临时 MySQL 8.4 容器并绑定本机随机端口，运行结束时清理容器。测试按顺序应用三份 SQL：测试用 `users`、M02 原始候选迁移、M04 候选迁移。Spring Boot 随后使用 Hibernate 校验实体映射，并运行数据库、解析器、HTTP、权限、删除状态、分页和并发测试。

2026-11-26 在 macOS arm64、JDK 21.0.12、Maven Wrapper 3.9.9、Spring Boot 3.5.16、Docker MySQL 8.4.13 上运行结果为 **M04 18 项通过，0 失败，0 错误，0 跳过**；同一脚本复跑 M02 48 项也全部通过。完整日志、用例映射和未完成联调项见 [M04 证据](../../../../tests/evidence/M04/README.md) 和 [验收台账](../../../../tests/evidence/M04/acceptance-ledger.md)。
