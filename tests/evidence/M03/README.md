# M03 测试证据（2026-11-19）

范围：媒体上传校验（JPEG/PNG/WebP/MP4）、安全存储键与防目录穿越、HTTP Range（200/206/416）读取、未发布/已删动态媒体防泄露、与 M02 动态模块的真实 `MediaPort`/`PostAccess` 事务绑定/解绑、24 小时超时未绑定清理、流异常与数据库回滚文件清理。  
执行者：潘铭伟（M03）。环境：Windows 11、JDK 21.0.7、Maven 3.9.12、Spring Boot 3.5.16、Flyway 11.7.2 + H2（`MODE=MySQL`）空库迁移（联合 M02 `posts-module`）。

## 实际结果

| 执行 | 实际结果 | 证据 |
| --- | --- | --- |
| 前置依赖构建 | M02 `posts-module` 编译安装至本地 Maven 仓库成功 | [完整执行日志](2026-11-19-verify.log) |
| 单元与 Range 测试 | `MediaInputAndRangeTest`：17 个测试全部通过（0 失败/0 错误/0 跳过） | [单元测试报告](edu.szu.moments.mediatest.MediaInputAndRangeTest.txt)、[XML](TEST-edu.szu.moments.mediatest.MediaInputAndRangeTest.xml) |
| M02+M03 跨模块集成测试 | `MediaIntegrationTest`：8 个测试全部通过（0 失败/0 错误/0 跳过） | [集成测试报告](edu.szu.moments.mediatest.MediaIntegrationTest.txt)、[XML](TEST-edu.szu.moments.mediatest.MediaIntegrationTest.xml) |
| 合计与打包 | **共 25 个测试全部通过**，`media-module-0.1.0-SNAPSHOT.jar` 打包成功；已校验生产 JAR 不含 `TestMediaApplication`、`TestCollaborators` 或 `fixtures` 测试资源 | [完整执行日志](2026-11-19-verify.log) |

## 与个人必做用例的对应

| 用例 | 自动化测试方法 | 结论与边界 |
| --- | --- | --- |
| M03-C01 文件边界 | `acceptsValidJpegPngWebpAndMp4AtExactSizeBoundary`、`rejectsImageAndVideoExceedingConfiguredLimitsWith413`、`C01_uploadsValidFormatsAndRejectsOversizedFilesWithoutResidue` | 图片 10 MB / 视频 50 MB（测试环境设为 2048B / 4096B）边界准确，超限返回 `413 FILE_TOO_LARGE` 且无数据库或磁盘残留 |
| M03-C02 类型校验 | `rejectsNullOrEmptyFileWith400`、`rejectsUnsupportedDeclaredMimeWith415`、`rejectsSpoofedMagicBytesAndMismatchedExtensionsWith415`、`C02_rejectsInvalidTypeSpoofedMimeAndUnauthorizedRolesWithoutResidue` | 空文件返回 `400`，非法格式、伪造魔数及扩展名不匹配返回 `415 UNSUPPORTED_MEDIA_TYPE`，未登录返回 `401`，管理员返回 `403` |
| M03-C03 文件读取 | `C03_blocksPendingAndDeletedPostMediaWhileAllowingPublishedMedia`、`C03_hidesMediaWhenAdminDeletesPostOrAuthorIsDisabled` | 仅 `ACTIVE` 且父动态/作者均为 `ACTIVE`（通过 M02 `PostAccess.requireVisible` 校验）可公开读取；`PENDING`、作者删动态、管理员删动态、作者禁用均返回 `404` |
| M03-C04 视频 Range | `parsesValidByteRangesCorrectly`、`rejectsInvalidOrUnsatisfiableRangeWith416`、`C04_supportsFullAndByteRangeStreamingForPublishedMp4And416ForInvalidRange` | 无 Range 返回 `200 OK`，`bytes=0-199` / `bytes=400-899` 返回 `206 Partial Content` 与准确字节切片，越界 `bytes=2000-2500` 返回 `416` 与 `Content-Range: bytes */1200` |
| M03-C05 归属与绑定 | `C05_enforcesMediaOwnershipPreventsReuseAndSupportsEditKeepAndReplace` | 联合 M02 `POST /posts` 与 `PATCH /posts/{postId}` 验证：盗用他人媒体返回 `403`，重复绑定返回 `409`，编辑保留/替换/移除解绑为 `PENDING` 并立即停止公开访问 |
| M03-C06 清理和失败 | `C06_cleansOnlyExpiredPendingAndEditRemovedMediaWhileKeepingActiveAndFreshMedia`、`C06_cleansUpFilesOnStreamFailureAndDatabaseTransactionRollback` | 推进虚拟时钟 24 小时后仅清理超时未绑定及编辑移除后超时的文件，未超时与活跃文件完好；流中断与事务回滚均自动清理物理文件 |

## 重现命令

在项目根目录执行：

```sh
python src/backend/modules/media/scripts/run_media_tests.py
```

## 尚未执行的系统级验收

- M01 真实 JWT 认证上下文与正式 Flyway 总库合并迁移。
- M06 / M07 微信小程序真实图片选择上传、`<video>` 组件拖动播放及界面联调。
- 本机未启动 MySQL 8.4 守护进程（Docker 未运行且未安装本地 `mysqld`），本次集成测试在 H2 `MODE=MySQL` 模式下完成；在已安装 MySQL 8.4 的环境可由 `run_media_tests.py` 自动使用临时 MySQL 实例复验。
