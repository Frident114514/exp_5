# M03 媒体上传、视频读取与清理模块

成员：潘铭伟（M03）。模块包名：`edu.szu.moments.media`。Java 21、Spring Boot 3.5.16、Spring Data JPA、Flyway。

## 已提供的代码与能力

- **两个 HTTP 接口**：
  - `POST /api/v1/media`：校验登录普通用户身份，完成 JPEG/PNG/WebP（≤ 10 MB）与 MP4（≤ 50 MB）的扩展名、MIME 与文件头魔数（Magic Bytes）双重校验，生成 `yyyy/MM/dd/{uuid}.{ext}` 安全存储键，处理磁盘写入失败与数据库回滚文件清理。
  - `GET /api/v1/media/{mediaId}/content`：公开读取已发布（`ACTIVE`）且父动态可见（调用 M02 `PostAccess.requireVisible` 核验）的图片与视频，支持 RFC 9110 `HTTP Range` 请求（`200 OK`、`206 Partial Content`、`416 Range Not Satisfiable`），拒绝 `PENDING`、`DELETED` 或已删动态/禁用作者的媒体。
- **M02 `MediaPort` 完整实现**：
  - `lockAndInspect`：按媒体 ID 升序悲观写锁并核验物理文件存在性。
  - `replaceBindings`：与 M02 动态创建/部分编辑共用同一数据库事务，支持保留、新增绑定与移除解绑（移除项恢复为 `PENDING` 并更新时间戳，立即停止公开访问）。
  - `findByPostIds`：按 `sort_order ASC, id ASC` 批量组装动态的 `MediaView` 列表。
- **超时未绑定媒体清理**：
  - `MediaService.cleanupExpiredPending()` 与 `@Scheduled` 定时任务 `MediaCleanupTask`，自动清理超过 24 小时未绑定（含上传后未绑定及编辑移除解绑后超时）的 `PENDING` 媒体文件与记录，不影响活跃绑定媒体。

## 目录结构

```text
media/
├── pom.xml
├── src/main/java/edu/szu/moments/media/
│   ├── MediaController.java          # POST /api/v1/media 与 GET /api/v1/media/{mediaId}/content
│   ├── MediaService.java             # 上传、读取、超时清理及 M02 MediaPort 实现
│   ├── MediaInspector.java           # 文件大小、MIME、扩展名与文件头魔数校验
│   ├── RangeParser.java              # RFC 9110 HTTP Range 解析与 416 判定
│   ├── MediaStorage.java             # 安全存储键生成、防目录穿越与 RandomAccessFile 分段读取
│   ├── MediaCleanupTask.java         # 24 小时超时未绑定媒体定时清理任务
│   ├── MediaEntity.java              # media 表 JPA 实体
│   ├── MediaRepository.java          # 悲观锁与批量查询仓储
│   ├── MediaProperties.java          # moments.media.* 配置绑定
│   ├── MediaModels / MediaFailure / MediaErrorHandler
│   └── MediaModuleConfiguration.java # 模块装配入口
├── src/main/resources/db/migration-candidates/m03/media.sql
├── src/test/                         # 单元测试与 M02+M03 跨模块集成测试
└── scripts/run_media_tests.py        # 自动化测试执行脚本
```

## 本地验证

进入本目录或项目根目录执行：

```sh
python src/backend/modules/media/scripts/run_media_tests.py
```

- 若本机配置了 `MYSQL_BIN`（MySQL 8.4），脚本会自动拉起独立临时 MySQL 实例执行 Flyway 迁移（`V1` 测试依赖 + `V2` M02 `posts.sql` + `V3` M03 `media.sql`）与 `mvn verify`；
- 若当前开发机未安装本地 `mysqld`，脚本会自动安装前置 `posts-module` 并基于内置 H2（`MODE=MySQL`）完成 Flyway 空库迁移与全部 26 项单元/跨模块集成测试。
- 也可直接指定外部测试库环境变量 `M03_TEST_DB_URL`、`M03_TEST_DB_USER`、`M03_TEST_DB_PASSWORD` 后运行 `mvn verify`。

## M01 主工程如何接入

1. 在服务端主工程引入 `edu.szu.moments:posts-module` 与 `edu.szu.moments:media-module`。
2. 导入 `@Import({PostsModuleConfiguration.class, MediaModuleConfiguration.class})`（如需开启定时清理，在主启动类添加 `@EnableScheduling`）。
3. 将 `src/main/resources/db/migration-candidates/m03/media.sql` 在 `users` 与 `posts` 迁移之后登记为正式 `V序号__media.sql`。
4. 通过环境变量配置 `MEDIA_STORAGE_DIR`（映射到 `moments.media.storage-dir`）及 Spring Multipart 限制（`spring.servlet.multipart.max-file-size=55MB`）。
