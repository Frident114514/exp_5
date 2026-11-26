# M04 评分评论测试证据

## 本次执行摘要

- 执行日期：2026-11-26。
- 执行人：梁国浩，使用本地 Codex 辅助执行和整理记录。
- 环境：macOS arm64、JDK 21.0.12、Maven Wrapper 3.9.9、Spring Boot 3.5.16、Docker MySQL 8.4.13。
- 执行命令：`bash src/backend/modules/interaction/scripts/verify_schema.sh`。
- 结果：InteractionSchemaTest 6 项、InteractionHttpIntegrationTest 8 项、InteractionPayloadParserTest 3 项、InteractionErrorHandlerTest 1 项，共 **18 项通过，0 失败，0 错误，0 跳过**。
- 原始日志：[2026-11-26-interaction-service.log](2026-11-26-interaction-service.log)。
- 正式用例台账：[acceptance-ledger.md](acceptance-ledger.md)。

脚本使用临时 MySQL 8.4 数据库，按测试用 `users` → M02 `posts` 候选迁移 → M04 `interaction` 候选迁移的顺序初始化，结束时清理容器。Flyway 对 MySQL 8.4 的支持版本警告不影响本次迁移和测试结果；正式主工程接入时仍由 M01 复核依赖和版本登记。

## 已执行范围

| 范围 | 实际结果 | 代码证据 |
| --- | --- | --- |
| 评分新增、重复评分修改、平均分、无评分 | 通过；重复提交保留原记录 ID，摘要按一位小数返回，无评分为 `average: null, count: 0`。 | `ratingSubmissionUpdatesOneRowAndReturnsLatestAggregate` |
| 评分范围和请求体 | 通过；拒绝 0、6、小数、字符串、未知字段和非对象请求体。 | `rejectsMalformedScoresAndEnforcesActorPermissions`、`InteractionPayloadParserTest` |
| 评论提交和边界 | 通过；首尾空白会去除，拒绝空白、501 字符和非字符串，接受 500 个 Unicode 码点。 | `validatesAndPagesCommentsWithStableVisibilityRules`、`InteractionPayloadParserTest` |
| 评论分页和隐藏状态 | 通过；支持 `page`、`pageSize`，按创建时间和 ID 稳定排序，只返回 `ACTIVE`。 | `validatesAndPagesCommentsWithStableVisibilityRules`、`commentQueryCanPageStablyAndExcludeSoftDeletedRows` |
| 作者、管理员、未登录、已禁用用户 | 通过；按契约返回 401 或 403。 | `rejectsMalformedScoresAndEnforcesActorPermissions`、`deletedPostsAndDisabledActorsCannotInteract` |
| 已删除动态 | 通过；评分、评论提交和评论列表均返回 404，关联互动行保留在数据库中。 | `deletedPostsAndDisabledActorsCannotInteract`、`postSoftDeletionKeepsInteractionRowsForVisibilityCheck` |
| 禁用用户清理 | 通过模块内事务适配器测试；评论改为 `DELETED`，评分被删除。M01 正式 `UserDisabled` 监听器尚未接入。 | `disabledUserCleanupRemovesRatingsAndHidesCommentsInCallerTransaction` |
| 并发评分一致性 | 通过 12 个并发请求测试；同一用户同一动态最终 1 行，多用户并发评分的平均分和人数正确。该结果不代表生产 QPS 容量达标。 | `concurrentSubmissionsKeepUniqueRatingAndCorrectAggregate` |
| 动态行锁语义 | 互动使用共享可见性锁时，其他用户可以并行互动；动态删除仍会等待互动事务结束，避免检查通过后立即删除。 | `differentUsersCanInteractWithSamePostWhileSharedVisibilityLockIsHeld`、`postDeletionWaitsForInteractionTransactionToFinish` |
| 临时数据库故障响应 | 通过异常映射测试；连接故障、数据库超时和无法取得事务连接均返回 `503/SERVICE_BUSY`，带 `Retry-After: 1`。 | `InteractionErrorHandlerTest.mapsTemporaryDatabaseFailuresToRetryableServiceBusy` |

## M02 回归记录

本次验证脚本同时在独立临时 MySQL 上复跑 M02 现有 48 项，结果为 48 项通过；M02 结果也记录在同一份原始日志中。2026-11-23 的历史复跑记录仍见 [2026-11-23-m02-regression.log](2026-11-23-m02-regression.log)。

## 未完成项

- 尚未接入 M01 的正式 JWT `IdentityPort` 和 `UserDisabled` 同步监听器；当前认证和禁用清理测试使用本模块测试适配器。
- 尚未与 M07 小程序使用两个真实账号完成端到端联调。
- 尚未执行独立压测，不能据此宣称互动接口满足某个 QPS、在线人数或响应时间指标。
- M02/M01/M07 的接口复核和交叉验收待各负责人确认，未执行项不记为通过。
