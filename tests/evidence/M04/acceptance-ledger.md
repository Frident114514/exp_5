# M04 验收台账

本台账只记录已实际执行的测试。测试适配器可以证明 M04 的业务逻辑和数据库行为，但不能替代生产 JWT、主工程和小程序的真实联调。未执行或待他人确认的项目明确写为“未执行/待联调”。

## 环境

- 执行人：梁国浩；本地 Codex 辅助执行与记录整理。
- 时间：2026-11-26。
- 环境：macOS arm64、JDK 21.0.12、Maven Wrapper 3.9.9、Spring Boot 3.5.16、Docker MySQL 8.4.13。
- 命令：`bash src/backend/modules/interaction/scripts/verify_schema.sh`。
- M04 测试方法总结果：18 项通过，0 失败，0 错误，0 跳过。另随同一脚本复跑 M02 48 项，48 项通过。
- 统一证据：[2026-11-26-interaction-service.log](2026-11-26-interaction-service.log)。

## 已执行用例

| 用例编号 | 执行人 | 环境 | 操作步骤 | 实际结果 | 证据 | 复验情况 |
| --- | --- | --- | --- | --- | --- | --- |
| M04-C01 | 梁国浩 | 上述临时 MySQL；HTTP 测试适配器 | 以 0、6、4.5、`"4"`、未知字段和合法 1、5 调用评分接口。 | 1、5 接受；其他输入返回 `400/VALIDATION_ERROR`。 | `rejectsMalformedScoresAndEnforcesActorPermissions`；`InteractionPayloadParserTest` | 2026-11-26 同一命令复验通过。 |
| M04-C02 | 梁国浩 | 上述临时 MySQL；12 线程并发 | 同一用户并发提交不同分值；清空评分后由 12 个用户并发提交 3 分。 | 同一用户同一动态最终只有 1 行；多用户平均分为 3.0、人数为 12。 | `concurrentSubmissionsKeepUniqueRatingAndCorrectAggregate`；原始日志 | 2026-11-26 通过；仅证明一致性，不证明生产容量。 |
| M04-C03 | 梁国浩 | 上述临时 MySQL；HTTP 测试适配器 | 先查询无评分动态，再由两个用户评分，随后执行禁用用户清理。 | 无评分为 `average:null,count:0`；平均分和人数更新；清理删除该用户评分。 | `ratingSubmissionUpdatesOneRowAndReturnsLatestAggregate`；`disabledUserCleanupRemovesRatingsAndHidesCommentsInCallerTransaction` | 2026-11-26 通过；M01 正式监听器待接入。 |
| M04-C04 | 梁国浩 | 上述临时 MySQL；HTTP 测试适配器 | 提交空白、501 字符、非字符串、1 字符和 500 个表情码点评论。 | 拒绝空白、超长和非字符串；有效边界接受并按 Unicode 码点计数。 | `validatesAndPagesCommentsWithStableVisibilityRules`；`InteractionPayloadParserTest` | 2026-11-26 同一命令复验通过。 |
| M04-C05 | 梁国浩 | 上述临时 MySQL；HTTP 测试适配器 | 使用作者、管理员、未登录、已禁用用户访问评分/评论；将动态改为 `DELETED` 后访问三个接口。 | 作者和管理员返回 403；未登录和禁用用户按契约返回 401；已删除动态的读写返回 404。 | `rejectsMalformedScoresAndEnforcesActorPermissions`；`deletedPostsAndDisabledActorsCannotInteract` | 2026-11-26 同一命令复验通过；真实 JWT 待联调。 |
| M04-C06 | 梁国浩 | 上述临时 MySQL；HTTP 测试适配器 | 插入多条同时间评论和一条 `DELETED` 评论，按 `page=1&pageSize=2` 查询，并验证非法分页参数。 | 只返回 `ACTIVE`；按 `created_at DESC,id DESC` 稳定分页；非法分页返回 400。 | `validatesAndPagesCommentsWithStableVisibilityRules`；`commentQueryCanPageStablyAndExcludeSoftDeletedRows` | 2026-11-26 同一命令复验通过。 |
| M04-C07 | 梁国浩 | JDK 21；互动异常处理单元测试 | 分别构造数据库连接故障、瞬时数据库超时和事务连接不可用异常，调用互动模块异常映射。 | 均返回 `503/SERVICE_BUSY` 和 `Retry-After: 1`，允许客户端退避重试。 | `InteractionErrorHandlerTest.mapsTemporaryDatabaseFailuresToRetryableServiceBusy`；原始日志 | 2026-11-26 复验通过。 |
| M04-C08 | 梁国浩 | 上述临时 MySQL；两个事务线程 | 一个事务持有动态互动共享可见性锁，另一个用户对同一动态提交评分。 | 第二个用户的互动在首个事务释放共享锁前完成；共享读取没有将同一动态的互动串行化。 | `differentUsersCanInteractWithSamePostWhileSharedVisibilityLockIsHeld`；原始日志 | 2026-11-26 复验通过；仅证明锁语义，不证明生产容量。 |
| M04-C09 | 梁国浩 | 上述临时 MySQL；两个事务线程 | 一个事务持有动态互动共享可见性锁，另一个事务尝试删除该动态。 | 删除操作等待互动事务释放锁后完成，动态最终为 `DELETED`。 | `postDeletionWaitsForInteractionTransactionToFinish`；原始日志 | 2026-11-26 复验通过；证明删除互斥语义，不代表生产容量。 |

## 待联调台账

| 用例编号 | 执行人 | 环境 | 操作步骤 | 实际结果 | 证据 | 复验情况 |
| --- | --- | --- | --- | --- | --- | --- |
| M04-I01 | 待指定 | M01 主工程 + 真实 MySQL + 真实 JWT | 用户乙登录后给用户甲动态评分，再次修改评分，核对动态详情摘要。 | 未执行；不能记为通过。 | — | 待 M01/M02 提供可运行主工程和测试账号。 |
| M04-I02 | 待指定 | M01 主工程 + M07 小程序；两个真实账号 | 用户乙从小程序详情页评论，刷新并分页查看；用户甲验证本人不能评论。 | 未执行；不能记为通过。 | — | 待 M07 页面接入三个接口后复验。 |
| M04-I03 | 待指定 | M01 禁用流程 + 同一数据库事务 | 禁用有评分和评论的用户，验证动态隐藏、评论隐藏、评分删除和 JWT 失效整体提交或回滚。 | 未执行；当前仅有 M04 内部清理入口测试。 | — | 待 M01 接入同步 `UserDisabled` 监听器后复验。 |
| M04-P01 | 待指定 | 独立压测端 + 主工程部署环境 | 运行评分写入、评论读取和混合业务的并发/容量测试，记录 QPS、P95、错误率和数据库资源。 | 未执行；当前并发测试只验证数据一致性。 | — | 待全组确定容量指标和部署环境。 |
