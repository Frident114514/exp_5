# M02 测试证据（2026-11-18）

范围：动态、标签、检索、作者权限、管理员内容接口及 M02 数据库事务。执行者：Codex，按锡汉唐的 M02 任务开发。环境：macOS arm64、JDK 21.0.10、Maven 3.9.16、Spring Boot 3.5.16、独立临时 MySQL 8.4.9。

## 实际结果

| 执行 | 实际结果 | 证据 |
| --- | --- | --- |
| 编译 | 23 个生产 Java 源文件编译成功 | [编译日志](2026-11-18-compilation.log) |
| 首次测试 | 48 个测试，47 通过、1 失败、0 错误、0 跳过 | [首次日志](2026-11-18-attempt-1.log) |
| 修复后完整复验 | 48 个测试全部通过，0 失败/错误/跳过；Maven verify 和 JAR 打包成功 | [复验日志](2026-11-18-attempt-2.log) |
| 用例明细 | 24 个输入/日期单元测试 + 24 个 MySQL/MockMvc 集成测试 | [JSON 明细](test-results.json)、[JUnit XML](junit-results.xml) |

两次执行均在新建 MySQL 空库中经 Flyway 创建测试依赖和 M02 表，Hibernate 校验映射；结束后关闭并清理临时实例。JAR 已检查，不包含 TestPorts、TestApplication 或 fixtures 测试资源。

## 失败、修复与回归

首次失败为 `supportsConcurrentTagCreationAcrossUsers`：两个用户同时首次创建“共同标签”，动态保存 2 条，标签 1 条，但动态标签关联只有 1 条。原因是 MySQL REPEATABLE READ 下，标签 upsert 等待另一事务后，随后的普通查询仍使用之前的快照，未读取到对方新建标签。

修复：`TagRepository.findByNameIn` 改为 `PESSIMISTIC_WRITE` 锁定读取，获取当前已提交数据。回归测试增加事务快照屏障，确保两个事务均建立旧快照后再并发创建；固定顺序处理标签，保持唯一约束。修复后重跑全部 48 个测试，通过。原失败日志保留。

## 与个人必做用例的对应

| 用例 | 自动化场景 | 结论与边界 |
| --- | --- | --- |
| M02-C01 内容校验 | rejectsEmptyContentTooManyImagesAndTagsWithoutPartialPosts、rejectsMixedOrMultipleVideos、PostInputTest | M02 输入与媒体组合校验通过；图片/视频只使用元数据夹具 |
| M02-C02 作者权限 | rejectsOtherAuthorsAndWrongRoles、anonymousMayReadButCannotWriteOrViewMine | M02 角色/作者检查通过；真实 JWT 鉴权由 M01 接入后验证 |
| M02-C03 媒体归属 | rejectsForeignBoundAndMissingMedia、creationBindingFailureRollsBackPostsTagsAndMedia、editBindingFailureRollsBackTextTagsAndBindings | 元数据归属/冲突拒绝和真实 MySQL 事务回滚通过；真实 M03 文件服务待联调 |
| M02-C04 日期标签 | filtersBeijingInclusiveDaysAndTagIntersection、tagSearchIsCaseSensitiveAndSupportsEmoji | 起止日微秒边界、单边日期、标签交集、空结果通过 |
| M02-C05 分页排序 | stabilizesPagingForIdenticalCreationTimes | 相同创建时刻按 ID 倒序，静态数据跨页无重复遗漏 |
| M02-C06 删除一致性 | authorDeleteHidesEveryPostReadAndSharedAccessEntry、adminDeleteAndAuditAreAtomic、authorDisableParticipatesInCallerTransactionAndCanRollback、filtersDisabledAuthorsEvenBeforePostsAreHidden | M02 列表/详情/PostAccess、删除与调用方回滚通过；真实媒体读取和评分评论入口仍未验证 |

额外覆盖：PATCH 省略字段与清空语义、创建时间保留、数据库外键/唯一约束、标签并发、后台关键词字面检索、批量依赖调用、依赖缺失时返回错误且编辑回滚。

## 重现命令

在项目根目录运行：

```sh
JAVA_HOME='/Applications/PyCharm.app/Contents/jbr/Contents/Home' python3 src/backend/modules/posts/scripts/run_mysql_tests.py
```

其他环境将 JAVA_HOME 换成实际 JDK 21 位置，MYSQL_BIN 可指定 MySQL 8.4 的 bin 目录。脚本不会连接已有数据库。详见 [模块运行说明](../../../src/backend/modules/posts/README.md)。

## 尚未执行的系统级验收

- M01 的 JWT、改密/禁用令牌撤销、真实管理日志和总事务。
- M03 的实际图片/视频文件、HTTP Range、解绑后的文件访问和清理。
- M04 的真实评分聚合、评论与已删除动态的互动拒绝。
- M06/M07/M08 小程序与后台页面真实联调。
- 对接成员交叉验收、最终全组 ER 图与正式 Flyway 版本合入。

测试使用的身份、媒体、评分和日志实现位于 `src/test/`，不能代表上述成员已实现。当前证据证明 M02 在约定依赖下的模块行为，不构成整套系统验收。
