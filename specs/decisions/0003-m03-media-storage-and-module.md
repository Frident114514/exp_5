# 0003 M03 媒体模块独立组织、存储安全与跨模块联调

- 日期：2026-11-18
- 状态：已用于 M03 独立实现及与 M02 的模块联调；主工程整合与前端联调待相关成员确认
- 参与人：潘铭伟（M03），对接锡汉唐（M02）、姚语嫣（M06）、王誉（M07）
- 背景与问题：当前 `src/backend/` 主工程与 M01 认证模块尚未提交，而 M02 动态模块已定义 `MediaPort` 与 `PostAccess` 并在 `src/backend/modules/posts/` 完成独立构建。需要实现 M03 的媒体上传、安全存储、HTTP Range 视频读取、24 小时超时清理，并直接与 M02 的 `posts-module` 实现真实端口对接。

## 候选方案

1. 在 M03 中复制一份动态表和 `MediaPort` 定义，完全脱离 M02 编译。
2. 在 `src/backend/modules/media/` 建立 `edu.szu.moments:media-module`（Java 21 / Spring Boot 3.5.16），直接依赖 `edu.szu.moments:posts-module`，实现 `MediaPort` 并调用 `PostAccess`，同时提供 `POST /api/v1/media` 与 `GET /api/v1/media/{mediaId}/content`。

## 最终选择与原因

选择方案 2：

- 直接实现 `edu.szu.moments.posts.MediaPort` 并调用 `edu.szu.moments.posts.PostAccess`，消除重复定义，确保 M02 动态创建、部分编辑、删除与 M03 媒体上传、绑定、解绑、公开读取在编译期和运行期完全契合。
- 存储键采用 `yyyy/MM/dd/{uuid}.{ext}`，通过文件头魔数（Magic Bytes）与声明 MIME 双重校验确定安全扩展名，不信任客户端原始文件名。
- 上传过程注册 Spring `TransactionSynchronization`，若数据库保存失败或事务回滚，自动清理已落盘文件；若磁盘写入失败，清理临时文件并拒绝写库，保证磁盘与数据库状态一致。

## 对代码、接口、测试和交付的影响

- 生产代码位于 `src/backend/modules/media/`，候选迁移 SQL 位于 `src/backend/modules/media/src/main/resources/db/migration-candidates/m03/media.sql`。
- 测试脚本 `src/backend/modules/media/scripts/run_media_tests.py` 支持独立临时 MySQL 8.4 实例，并在未安装本地 `mysqld` 的开发机上自动支持 H2（MySQL 兼容模式）执行全套单元与跨模块（M02+M03）集成测试。
- 细节见 [M03 实施约定](../contracts/media-module.md) 与 [媒体模块说明](../../src/backend/modules/media/README.md)。
