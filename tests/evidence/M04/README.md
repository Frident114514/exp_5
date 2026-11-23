# M04 第 1 周测试记录

2026 年 11 月 23 日由 Codex 在梁国浩的工作区执行。环境为 macOS arm64、JDK 21.0.12、Maven 3.9.16、Spring Boot 3.5.16，数据库为 Docker 中的临时 MySQL 8.4.13。代码基于当日 `origin/main` 的 `78643a0`。

## 候选迁移和 JPA 实测

在项目根目录执行以下命令。

```sh
bash src/backend/modules/interaction/scripts/verify_schema.sh
```

临时空库按测试用 `users` → M02 原始 `posts` 候选脚本 → M04 候选脚本的顺序执行 3 个 Flyway 迁移，随后由 Hibernate 验证 JPA 映射。**6 项通过，0 失败，0 错误，0 跳过。** [运行日志摘录](2026-11-23-schema.log)保留了迁移顺序、测试计数与打包结果。

| 实测范围 | 测试方法 | 实际结果 |
| --- | --- | --- |
| JPA 读取和 UTC 时间映射 | `scoreAndCommentRoundTripThroughJpa` | 可读取评分和评论映射，正确读取含表情的评论和微秒时间。 |
| 评分唯一性和原记录更新 | `rejectsDuplicateRatingAndAllowsUpdatingOriginalRow` | 重复插入被拒绝。更新保留原记录 ID，其他用户仍可独立评分。 |
| 分值和外键 | `enforcesScoreRangeAndForeignKeys` | 拒绝 0、6 及不存在的用户和动态 ID，接受 1 和 5。 |
| 评论长度和状态 | `enforcesCommentLengthAndStatus` | 拒绝空串、超长和非法状态，接受 1 字符以及 500 个表情码点。 |
| 分页和隐藏评论 | `commentQueryCanPageStablyAndExcludeSoftDeletedRows` | 同一创建时间的评论按 ID 倒序稳定分页，过滤 `DELETED` 评论。 |
| 动态软删除的关联行 | `postSoftDeletionKeepsInteractionRowsForVisibilityCheck` | 更新动态状态后原评分和评论仍在库中，为后续由 `PostAccess` 控制访问提供数据基础。 |

Flyway 在运行时报告对 MySQL 8.4 的支持版本警告。本次三步迁移和 Hibernate 校验成功，正式主工程的依赖版本由 M01 合入时复核。

## M02 回归

在同一环境另启一个临时 MySQL 空库，通过 `M02_TEST_DB_URL`、`M02_TEST_DB_USER` 和 `M02_TEST_DB_PASSWORD` 指向该测试库，运行 `mvn -B -ntp -f src/backend/modules/posts/pom.xml verify`。[运行日志摘录](2026-11-23-m02-regression.log)显示 M02 现有 48 项全部通过，包含已删除动态的读取检查和写入锁定检查。M02 回归使用其现有测试适配器。

## 待后续验证

评分、评论 HTTP 用例、生产认证、真实禁用事务和小程序联调待后续实现后执行。`M04-C01` 至 `M04-C06` 的完整服务用例保持未执行。
