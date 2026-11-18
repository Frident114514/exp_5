# M02 动态、标签与检索模块

成员：锡汉唐。模块包名：`edu.szu.moments.posts`。Java 21、Spring Boot 3.5.16、Spring Data JPA、MySQL 8.4。

## 已提供的代码

- 八个动态接口：创建、部分编辑、作者删除、本人列表、公开列表、详情、后台查询、管理员删除。
- 文本/图片/视频组合、标签规范化、日期与标签交集、稳定分页、作者和管理员角色检查。
- 动态、标签及关联表候选迁移；独立 MySQL 8.4 测试和一次性测试环境脚本。
- `PostAccess`（动态状态检查/互动锁）和 `AuthorPosts`（禁用用户时隐藏动态）供其他模块调用。

本模块为 JAR，当前不含整个系统的启动类、JWT 登录、真实文件读写或评分服务。`src/test/` 中的用户、媒体、评分和日志适配器只是测试夹具，不会进入生产包。完整系统启动与真实联调需 M01/M03/M04 提供对应实现。

## 目录

```text
posts/
├── pom.xml
├── src/main/java/edu/szu/moments/posts/
│   ├── PostController.java          # 8 个 HTTP 接口
│   ├── PostService.java             # 动态业务、事务和批量组装
│   ├── PostPayloadParser.java       # 输入和 PATCH 语义
│   ├── PostFilter.java              # 日期/分页解析
│   ├── PostSpecifications.java      # 可组合查询
│   ├── PostEntity.java / TagEntity.java
│   ├── AuthorRecord.java            # users 的只读映射
│   ├── *Repository.java            # JPA 持久化
│   ├── IdentityPort / MediaPort / RatingPort / AdminAuditPort
│   └── PostAccess / AuthorPosts     # 对其他成员的内部入口
├── src/main/resources/db/migration-candidates/m02/posts.sql
├── src/test/                       # 自动化测试和隔离夹具
└── scripts/run_mysql_tests.py       # 临时 MySQL 建立、执行与清理
```

## 本地验证

先准备 JDK 21、Maven 3.6.3+、MySQL 8.4，以及 Python 3。进入本目录执行：

```sh
python3 scripts/run_mysql_tests.py
```

脚本会在 `/tmp` 创建独立数据目录，启动仅监听 `127.0.0.1` 的随机端口 MySQL；创建随机凭据和 `m02_test` 测试库，执行 Flyway 空库迁移与 `mvn verify`，最后关闭实例并清理临时文件。不会连接现有 MySQL 服务。

- `MYSQL_BIN` 可指定 MySQL 8.4 的 bin 目录，默认 `/opt/homebrew/opt/mysql@8.4/bin`。
- `JAVA_HOME` 可指定 JDK 21。当前工作机可使用 `/Applications/PyCharm.app/Contents/jbr/Contents/Home`；脚本在未设置 JAVA_HOME 时会尝试这个位置。
- 示例：`JAVA_HOME='/Applications/PyCharm.app/Contents/jbr/Contents/Home' python3 scripts/run_mysql_tests.py`。
- 首次执行需要下载 Maven 依赖。自动化报告位于 `target/surefire-reports/`；交付证据位于项目 `tests/evidence/M02/`。
- 脚本适用于 macOS / Linux 的本地 MySQL 工具；其他环境可自行创建空测试库，通过 `M02_TEST_DB_URL`、`M02_TEST_DB_USER`、`M02_TEST_DB_PASSWORD` 提供连接后执行 `mvn verify`。只使用专门的测试库，测试会重置夹具数据。

单独编译或安装模块：

```sh
mvn -DskipTests package
mvn -DskipTests install
```

上述两条命令跳过测试，不能作为测试通过的证据。

## M01 主工程如何接入

1. 主工程使用 Java 21 / Spring Boot 3.5，将本模块作为 Maven 依赖或 reactor 子模块引入。
2. 导入 `PostsModuleConfiguration`。该配置注册本模块组件、实体和仓库；主工程需显式登记自身及其他模块的实体/仓库扫描范围，避免对同一仓库重复注册。
3. 提供 `IdentityPort`、`MediaPort`、`RatingPort`、`AdminAuditPort` 的真实 Spring Bean；不能把测试夹具复制到生产目录。
4. 配置统一的数据源与事务管理器，JPA 设置 `hibernate.jdbc.time_zone=UTC`、`hibernate.type.preferred_instant_jdbc_type=TIMESTAMP`，关闭 Open Session in View，Flyway 管理结构、`ddl-auto=validate`。
5. 用户表先存在，`users.id` 为有符号 BIGINT。将本模块候选 SQL 按实际合入顺序登记为正式 `V序号__posts.sql` 后加入主工程 Flyway 目录。
6. 认证链公开 GET 动态列表/详情；其余请求要求有效 JWT，`/admin/**` 限管理员。`IdentityPort` 仍要核对角色、账号状态和令牌版本。
7. 接入真实媒体/评分/日志服务并运行项目级联调，再由 M06/M07/M08 验证页面操作；未完成之前不把本模块测试当作整系统验收。

内部端口签名、锁顺序、PATCH 行为与请求响应样例见项目 [M02 实施约定](../../../../specs/contracts/posts-module.md)。测试状态以 [M02 工作记录](../../../../成员任务/02_动态标签与检索服务.md) 为准。

## 技术依据

依赖补丁版本和 Java 21 兼容范围核对自 [Spring Boot 3.5 系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)。组合查询使用 Spring Data JPA Specification，事务内动态锁采用 `@Lock(PESSIMISTIC_WRITE)`；生产发布前仍由各模块负责人共同验证跨模块事务与锁顺序。
