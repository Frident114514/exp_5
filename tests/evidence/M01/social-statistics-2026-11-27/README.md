# M01 好友与统计验证

日期：2026-11-27。基线 `cf27e85` 加本次 M01 修改；Java 21、Spring Boot 3.5.16、独立 MySQL 8.4.13、随机端口及临时凭据。未使用日常开发数据库。

## 结果

| 工程 | 用例数 | 结果 | 证据 |
| --- | ---: | --- | --- |
| 主工程 | 39 | 0 失败、错误、跳过；verify 和 JAR 打包成功 | [报告](main)、[日志](main-verify.log) |
| 动态独立模块 | 48 | 0 失败、错误、跳过；install 成功 | [报告](posts)、[日志](module-combined-verify.log) |
| 互动接口、解析、错误处理 | 12 | 0 失败、错误、跳过 | [报告](interaction)、[合跑日志](module-combined-verify.log) |
| 互动建表独立空库 | 6 | 0 失败、错误、跳过；verify 成功 | [报告](interaction/edu.szu.moments.interaction.InteractionSchemaTest.txt)、[日志](interaction-schema-verify.log) |

互动两组不能共用已经迁移的库：原有 InteractionSchemaTest 要求本次确实执行 3 条迁移，因此合跑时该组得到 0 而失败。保留原失败日志；随后为这 6 项单独重建空库，全部通过。没有删断言或将失败计为通过。动态模块数据库连接显式设置 UTC；初次缺少该设置导致北京时间边界用例失败，修正测试环境后 48 项通过。首次 M01 集成测试将既有发帖、评论接口的 HTTP 200 误期望为 201，已修正测试，未改动原接口状态码。

## 新增场景

`src/backend/src/test/java/edu/szu/moments/auth/SocialStatisticsIntegrationTest.java` 包含 8 项组合测试：

1. 搜索、发送申请、双向列表、同意、好友查询、解除、重新申请及旧申请重放。
2. 未登录、管理员、非接收者、非法参数、禁用目标、同事务好友清理及旧 JWT 拒绝。
3. 两用户同时互发申请，恰好一次成功、一次 409，数据库只有一条 PENDING。
4. 同意与拒绝竞争，恰好一次成功；过期 tokenVersion 不能写入关系。
5. 成功登录、发布、编辑、评分和评论实际记入活动；失败操作及只读 GET 不记入。
6. 北京时间午夜边界、同日去重和业务事务回滚。
7. 管理员统计权限、日期校验、默认 30 天补齐、真实数量和历史未知值。
8. 发布、评论、评分后总量增加；删除后当前可见量减少，历史新增量保留；禁用不减少注册总量。

既有账号、改密、禁用关联清理与回滚、准入和诊断测试一并回归。V1–V6 在主工程空库执行成功。

## 复现

在 `src/backend` 下设置 Java 21，执行：

```powershell
$env:JAVA_HOME='你的 JDK 21 路径'
$env:Path="$env:JAVA_HOME/bin;$env:Path"
./scripts/verify_mysql.ps1 -MysqlBin '你的 MySQL 8.4 bin 路径'
```

脚本启动隔离空库、执行 `mvnw.cmd -B -ntp verify` 并关闭实例。独立模块验证时，各使用新的临时数据库，通过 `M02_TEST_DB_URL/USER/PASSWORD`、`M04_TEST_DB_URL/USER/PASSWORD` 提供连接，JDBC URL 指定 `connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true`。动态模块执行 `mvnw.cmd -f modules/posts/pom.xml install`；互动模块在自己的目录分别执行 `../../mvnw.cmd '-Dtest=InteractionHttpIntegrationTest,InteractionPayloadParserTest,InteractionErrorHandlerTest' verify` 与 `../../mvnw.cmd '-Dtest=InteractionSchemaTest' verify`，两次使用不同空库。不得指向共享开发库。

## 接入交接

- M05：按 [接口约定](../../../../specs/contracts/m01-social-statistics.md) 接入用户搜索、申请处理、好友列表与解除。好友服务使用现有 JWT；管理员不能操作普通用户好友接口。
- M02：好友动态筛选使用有序用户对 `friendships` 的 EXISTS，要求双方 ACTIVE，在 SQL 分页之前筛选；也可调用 `FriendRelations.areFriends`。
- M08：使用 `GET /api/v1/admin/statistics` 绘制统计图；日期按北京时间，`null` 为未知，启用当日可能仅覆盖部分时间。当前总量与历史新增量分开呈现。

本次交付完成 M01 后端和功能测试；上述其他成员的页面及好友动态筛选由对应模块接入验收。原有压力客户端、准入试验、JFR、逐请求记录及诊断对照完整保留，本次没有新增容量测量。
