# M02 动态模块实施与交接约定

状态：M02 的实现依据；M01/M03/M04/M06/M07/M08 的适配与真实联调待各负责人确认。日期：2026-11-18。

## 工程与边界

代码放在 `src/backend/modules/posts/`，以 Java 21 / Spring Boot 3.5.16 的 Maven JAR 独立编译。主工程、登录/JWT、文件服务、评分服务尚未提交，所以动态模块通过明确的 Java 接口接入这些能力。测试适配器只存在于 `src/test/`，不打入生产 JAR。M01 主工程后续导入 `PostsModuleConfiguration` 并提供四个端口实现；本模块不注册全局 SecurityFilterChain。

## HTTP 约定补充

沿用 `api.md` 的 8 个动态接口与响应信封。列表和详情均返回 id、author、text、media、tags、createdAt、updatedAt、rating。创建返回 `{id}`，编辑返回完整详情，删除返回 `data: null`。时间返回 UTC 的 ISO 8601（Z），筛选自然日按 Asia/Shanghai 解释。

- POST 缺省 text 为 `""`，mediaIds/tags 为 `[]`；显式 null、类型错误、未知字段（包括 authorId）返回 400。
- PATCH 为部分更新：省略字段保留原值，空数组清空关联；text 为空字符串可清空文字。更新后文字与媒体仍须至少一项非空。显式 null、空对象或未知字段返回 400。
- text 上限 10000 个 Unicode 码点，保存去除首尾空白后的文本；这是本次为可控输入与 TEXT 存储补充的上限。
- mediaIds 为正整数，最多 9 个，不接受重复 ID；只允许 1–9 张图片或 1 个视频。
- tags 最多输入 5 项，每项去除首尾空白后为 1–20 个 Unicode 码点；重复项合并。标签区分大小写，数据库使用 utf8mb4_0900_bin；返回标签按名称排序。
- 查询 `from/to` 必须为严格 YYYY-MM-DD；边界取北京时间起始日 00:00（包含）至结束日次日 00:00（不包含），不得倒置。单独使用起始或结束日期也可。
- `page` 从 1 开始，`pageSize` 默认 10、最大 50。按 createdAt DESC、id DESC 排序；静态数据下分页无重复遗漏。
- 公开列表支持日期/单标签；我的列表限定当前用户；后台列表增加 `keyword`（正文的字面包含检索，%/_ 不作通配符）及 `authorId`。后台仅查询可见动态；已删除记录不返回。
- 用户禁用和动态删除均对公开列表、详情、本人列表、后台列表生效。媒体/互动读取还必须调用下文 PostAccess 校验。
- 未登录返回 401、角色/作者不符返回 403、不可见动态或不存在媒体返回 404、已绑定其他动态媒体返回 409；缺少依赖返回值或依赖异常不可伪装成成功。

## 内部端口与事务

| 接口 | 实现责任 | 约定 |
| --- | --- | --- |
| IdentityPort | M01 | 从已验证 JWT / SecurityContext 得到 Actor；校验 ACTIVE 和 tokenVersion；lockActiveActor 在当前事务锁定用户行，再次检查状态与角色，不能信任请求中的作者 ID |
| MediaPort | M03 | 批量锁定并读取媒体归属/类型/状态；在同一事务替换绑定；批量返回媒体视图，按 sort_order 排序；清理也遵守行锁 |
| RatingPort | M04 | 一次接收所有 postId，返回每个动态的平均分与人数；无评分为 null/0，避免逐条查询 |
| AdminAuditPort | M01 | 在同一个数据库事务记录管理员删除动态，失败抛异常并回滚动态删除 |
| PostAccess（M02 提供） | M03/M04 调用 | 读取可见动态/作者；互动写入使用事务内锁定方法，确保与删除互斥；本类不依赖媒体、评分服务，避免循环依赖 |
| AuthorPosts（M02 提供） | M01 调用 | hideByAuthor 要求已有事务，由禁用用户流程调用；失败随调用方整体回滚 |

同一数据源、同一事务管理器；端口写方法不得使用 REQUIRES_NEW 或另开连接提交。修改流程锁顺序为用户 → 动态 → 媒体（媒体 ID 升序）。禁用用户先锁定目标用户行，再隐藏动态及处理互动。评分/评论先锁定当前操作用户，再锁定动态。生产适配由各负责人实现后进行真实竞态复验。

编辑移除媒体时由 M03 解除绑定，改为 PENDING；重新读取需按当前动态有效状态鉴权，未绑定媒体不得公开读取。M02 删除仅修改动态状态，M03/M04 在读取或写入时通过 PostAccess 检查，因此不能绕过动态可见性。

## 数据库迁移

本模块维护 posts、tags、post_tags；通过只读映射使用 users 的 id、nickname、status，不写入用户数据。users.id 须为有符号 BIGINT，先创建用户表。正文采用 TEXT，时间为 UTC DATETIME(6)，status 为 ACTIVE/DELETED。索引和外键见 SQL。

候选脚本为 `src/main/resources/db/migration-candidates/m02/posts.sql`。不放入默认 Flyway 目录、不预占全组版本号；M01 合入前登记正式 V 编号并复制脚本。独立测试将测试用用户表作为 V1、该脚本作为 V2，在临时 MySQL 8.4 上从空库执行，仅用于测试顺序验证。

## 成功与失败样例

### 创建与编辑

```http
POST /api/v1/posts
Authorization: Bearer <真实登录令牌>
Content-Type: application/json

{"text":"校园日记","mediaIds":[],"tags":["校园"," 校园 "]}
```

```json
{"code":"OK","data":{"id":101},"message":""}
```

```http
PATCH /api/v1/posts/101
Content-Type: application/json

{"text":"更新后的校园日记"}
```

mediaIds 和 tags 保留原值，createdAt 不变。成功返回完整动态对象。

### 列表（评分/媒体字段由实际服务提供）

```http
GET /api/v1/posts?from=2026-11-18&to=2026-11-18&tag=校园&page=1&pageSize=10
```

```json
{"code":"OK","data":{"items":[{"id":101,"author":{"id":7,"nickname":"测试用户甲"},"text":"校园日记","media":[],"tags":["校园"],"createdAt":"2026-11-18T02:00:00Z","updatedAt":"2026-11-18T02:00:00Z","rating":{"average":null,"count":0}}],"page":1,"pageSize":10,"total":1},"message":""}
```

### 后台筛选与删除

```http
GET /api/v1/admin/posts?keyword=校园&authorId=7&from=2026-11-18&page=1
DELETE /api/v1/admin/posts/101
```

删除需管理员令牌，成功响应 `{"code":"OK","data":null,"message":""}`。

### 失败

- 非作者修改/删除：HTTP 403，`{"code":"FORBIDDEN","data":null,"message":"只能管理本人动态"}`。
- 已删除动态：HTTP 404，`{"code":"NOT_FOUND","data":null,"message":"动态不存在或已不可见"}`。
- 空内容：HTTP 400，`{"code":"VALIDATION_ERROR","data":null,"message":"文字和媒体至少填写一项"}`。
- 媒体绑定冲突：HTTP 409，`{"code":"CONFLICT","data":null,"message":"媒体已绑定其他动态"}`。

示例用于联调，均为虚构数据；测试实际结果见 `tests/evidence/M02/`。

