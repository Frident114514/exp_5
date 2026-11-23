# M04 互动模块

第 1 周交付 `ratings`、`comments` 的 JPA 映射、Flyway 候选迁移和接口交接文档。接口及联调安排见 [M04 互动接口与数据库交接](../../../../specs/contracts/interaction-module.md)。

生产主工程接入时，M01 将候选文件 `src/main/resources/db/migration-candidates/m04/interaction.sql` 复制到正式 Flyway 位置并登记版本号。迁移依赖 `users`、`posts` 已创建。主工程须纳入 `edu.szu.moments.interaction` 的实体扫描，并沿用 UTC 时间配置。表结构以 Flyway SQL 为准，JPA 用于映射与校验。

## 验证候选表结构

准备 JDK 21、Maven 和已启动的 Docker。从项目根目录执行：

```sh
bash src/backend/modules/interaction/scripts/verify_schema.sh
```

脚本启动临时 MySQL 8.4 容器并绑定本机随机端口，运行结束时清理容器。测试按顺序应用三份 SQL：测试用 `users`、M02 原始候选迁移、M04 候选迁移。测试编号 V1、V2、V3 仅用于本次临时库。Spring Boot 随后使用 Hibernate 校验实体映射，并检查数据库约束及查询行为。

验证范围是数据库和 JPA 映射。评分、评论 HTTP 行为和真实认证联调对应第 2–4 周的交付。第 1 周的实测记录见 [M04 证据](../../../../tests/evidence/M04/README.md)。
