# M04 第 1 周互动接口与数据库交接

适用周次为 2026 年 11 月 20 日至 26 日。表结构候选已随 M04 模块提供。本文依据 [API 约定](api.md)、[数据模型](data-model.md) 与 [M02 动态模块交接](posts-module.md)。本文给出的响应是接口设计样例，供 M01、M02、M07 确认后在后续周实现和联调。

## 数据表与约束

| 表 | 字段 | 约束与用途 |
| --- | --- | --- |
| `ratings` | `id`、`post_id`、`user_id`、`score`、`created_at`、`updated_at` | `UNIQUE(post_id, user_id)` 保证每个用户对每条动态只有一条评分。`score` 为 `INT`，数据库限制 1–5，服务端还须拒绝 JSON 小数和字符串。重复评分更新原行，保留 `id` 和 `created_at`。 |
| `comments` | `id`、`post_id`、`user_id`、`content`、`status`、`created_at` | 只支持一级评论。`content` 为 `VARCHAR(500)`，数据库按字符限制 1–500。`status` 为 `ACTIVE` 或 `DELETED`，公开分页只返回 `ACTIVE`。 |

两表通过有符号 `BIGINT` 外键引用 `users.id`、`posts.id`，采用 InnoDB、`utf8mb4` 与 UTC `DATETIME(6)`。评论分页使用 `(post_id, status, created_at DESC, id DESC)` 索引。两表均有 `user_id` 索引用于处理禁用用户。候选脚本须在 `users`、`posts` 之后执行。M01 负责登记正式 Flyway 版本号并安排合入顺序。

## HTTP 接口设计

| 方法与路径 | 权限和参数 | `data` 字段 |
| --- | --- | --- |
| `PUT /api/v1/posts/{postId}/rating` | 已登录且非作者的普通用户。请求体 `{ "score": 5 }`，值为 1–5 的 JSON 整数。 | `{ "score": 5, "rating": { "average": 4.5, "count": 2 } }`。`score` 是本次登录用户保存的分值。 |
| `POST /api/v1/posts/{postId}/comments` | 已登录且非作者的普通用户。请求体 `{ "content": "这个角度很好看" }`。去除首尾空白后按 Unicode 码点计算 1–500 字符，拒绝纯空白。 | `{ "id": 301, "author": { "id": 22, "nickname": "小周" }, "createdAt": "2026-11-23T12:00:00Z" }`。 |
| `GET /api/v1/posts/{postId}/comments?page=1&pageSize=10` | 公开。`page` 默认 1 且从 1 开始。`pageSize` 默认 10，允许 1–50。 | `{ "items": [...], "page": 1, "pageSize": 10, "total": 1 }`。每项含 `id`、`author`、`content`、`createdAt`。`total` 为当前动态可见评论数。空页返回 `items: []`。 |

`postId` 必须为正整数。评论按 `createdAt DESC, id DESC` 排序，同一时间以 ID 倒序打破并列。评分摘要在动态详情和列表统一使用 `rating.average`、`rating.count`。平均分按有效评分计算，四舍五入保留一位小数。无评分返回 `{"average":null,"count":0}`。评论展示应按纯文本处理。

### 评论分页成功样例

```json
{
  "code": "OK",
  "data": {
    "items": [{"id":301,"author":{"id":22,"nickname":"小周"},"content":"这个角度很好看","createdAt":"2026-11-23T12:00:00Z"}],
    "page": 1,
    "pageSize": 10,
    "total": 1
  },
  "message": ""
}
```

### 统一错误响应

| HTTP 状态与错误码 | 互动场景 |
| --- | --- |
| `400 VALIDATION_ERROR` | 路径、分页或请求体参数无效。例如 `score` 为 `4.5`、`"4"` 或 `6`，评论为空白或超过 500 字符。 |
| `401 UNAUTHENTICATED` | 提交时未登录、令牌已失效或登录用户已禁用。 |
| `403 FORBIDDEN` | 动态作者评价本人动态，或管理员身份尝试评分、评论。 |
| `404 NOT_FOUND` | 动态不存在、已删除或作者已禁用。评分提交、评论提交和评论列表都遵守此规则。 |
| `409 CONFLICT` | 确实出现数据状态冲突，客户端可刷新后重试。合法的再次评分走更新。 |

```json
{"code":"NOT_FOUND","data":null,"message":"动态不存在或已不可见"}
```

## 跨模块交接

| 对接方 | 现有入口与 M04 接法 | 待确认或后续验证 |
| --- | --- | --- |
| M02 动态 | 评论列表调用 `PostAccess.requireVisible(long)`。提交在事务内先由 M01 的 `IdentityPort.lockActiveActor(Actor)` 锁定当前用户，再调用 `PostAccess.lockVisibleForInteraction(long)` 检查可见性并锁定动态。根据返回的作者 ID 与当前登录用户 ID 判定能否互动。 | 已按 `PostAccess` 源码核对签名和事务边界。请 M02 复核软删除后保留关联行、经可见性检查统一返回 404 的约定。 |
| M02 评分摘要 | M04 后续实现 `RatingPort.summarize(Set<Long>)`。批量聚合并为每个请求 ID 返回 `RatingSummary`，无评分补 `null` 和 `0`，供列表和详情复用。 | M02 复核 `average` 与 `count` 字段以及空结果。真实评分聚合在接入后复验。 |
| M01 认证与禁用 | 互动用户 ID 来自已验证 JWT。M01 发起禁用事务，锁用户行，调用 M02 的 `AuthorPosts.hideByAuthor(long)`，再调用 M04 的互动清理入口。M04 在同一事务将该用户发表的评论设为 `DELETED`，删除该用户发出的评分。 | 请 M01 确认清理调用签名和正式 Flyway 版本号。禁用、动态隐藏、互动清理、令牌失效和审计须参与同一事务。 |
| M07 小程序 | 用三个端点和表内字段接入详情页。无评分显示“暂无评分”。评论刷新从第一页开始，动态不可见时按 404 展示。 | 请 M07 核对评分提交后的 `data.rating`、评论分页字段与错误提示。待服务和页面接入后，用不同账号真实联调。 |

动态删除由 M02 将 `posts.status` 改为 `DELETED`，数据库保留原评分和评论。M04 使用 M02 的读取和写入入口约束访问。写入与动态删除通过同一动态行锁协调。评分修改与首次写入共用用户到动态的锁顺序，数据库唯一约束兜底。禁用用户的清理范围按发出互动的 `user_id` 处理。
