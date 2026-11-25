# API 开发约定 v1

本文件统一小程序、管理后台和服务端的接口形状。新增字段或改动路径时先更新本文档，再同步调用方。

M02 动态接口的部分编辑规则、完整样例、内部依赖和事务边界见 [动态模块实施与交接约定](posts-module.md)。当前认证、媒体、评分与前端的真实适配仍待联调。

## 通用规则

- 基础路径：`/api/v1`；JSON 使用 UTF-8，时间使用带时区的 ISO 8601 字符串。
- 注册、登录、动态列表、详情、评论列表及已发布媒体内容可公开访问；其余接口需在 `Authorization: Bearer <JWT>` 中传令牌；`/admin/**` 仅管理员可访问。
- 列表参数：`page` 从 1 开始，`pageSize` 默认 10、最大 50。统一返回 `items`、`page`、`pageSize`、`total`。
- 成功响应：`{ "code": "OK", "data": ..., "message": "" }`。失败响应：`{ "code": "ERROR_CODE", "data": null, "message": "可读说明" }`，同时使用合适的 HTTP 状态码。
- 登录返回 `accessToken`、`expiresAt`、`user`；令牌有效期 12 小时。改密与禁用用户通过 `tokenVersion` 使旧令牌失效。
- 服务端以登录身份判断作者或管理员权限，不接受客户端传入的 `authorId` 作为授权依据。

## 端点清单

| 需求 | 方法与路径 | 权限 | 请求要点 | 响应要点 |
| --- | --- | --- | --- | --- |
| U01 | `POST /auth/register` | 公开 | `account`、`password`、`nickname` | 用户 ID、基本资料；角色固定为 USER |
| U01 | `POST /auth/login` | 公开 | `account`、`password` | `accessToken`、`expiresAt`、用户角色 |
| U01 | `GET /users/me` | 用户 | 无 | 本人信息 |
| U01 | `PUT /users/me/password` | 用户 | 原密码、新密码 | 成功状态 |
| U02 | `POST /media` | 用户 | `multipart/form-data` 的 `file` | `mediaId`、类型、内容地址 |
| U02、U05 | `GET /media/{mediaId}/content` | 公开 | 无 | 已发布且未删除的图片或视频内容；视频支持 Range 请求 |
| U02 | `POST /posts` | 用户 | `text`、`mediaIds`、`tags` | 新动态 ID |
| U03 | `GET /users/me/posts` | 用户 | 分页 | 本人的动态列表 |
| U03 | `PATCH /posts/{postId}` | 作者 | 可修改的文本、媒体、标签 | 更新后动态 |
| U03 | `DELETE /posts/{postId}` | 作者 | 无 | 成功状态 |
| U04、U05 | `GET /posts` | 公开 | `from`、`to`、`tag`、分页 | 按发布时间倒序的动态列表 |
| U05 | `GET /posts/{postId}` | 公开 | 无 | 动态详情、作者、媒体、标签、评分摘要 |
| U05 | `PUT /posts/{postId}/rating` | 非作者用户 | `score`：1–5 整数 | 当前评分、平均分与人数；再次提交会更新评分 |
| U05 | `GET /posts/{postId}/comments` | 公开 | 分页 | 评论列表 |
| U05 | `POST /posts/{postId}/comments` | 非作者用户 | `content`：1–500 字符 | 新评论 ID、作者、时间 |
| A01 | `GET /admin/users` | 管理员 | 关键词、分页 | 用户列表 |
| A01 | `DELETE /admin/users/{userId}` | 管理员 | 无 | 成功状态 |
| A02 | `GET /admin/posts` | 管理员 | 关键词、作者、日期、标签、分页 | 动态列表 |
| A02 | `DELETE /admin/posts/{postId}` | 管理员 | 无 | 成功状态 |

`from`、`to` 使用 `YYYY-MM-DD`，按北京时间的自然日筛选，包含起止日期；`from` 不得晚于 `to`。`tag` 一次传一个标签，日期与标签条件同时存在时取交集。删除接口统一返回 HTTP 200 和 `code: "OK"`。

`POST /posts`、`PATCH /posts/{postId}` 的请求体形状为 `{ "text": "...", "mediaIds": [33], "tags": ["校园"] }`。文本与媒体至少有一项；媒体只允许 1–9 张图片或 1 个 MP4 视频，标签最多 5 个。上传图片限 JPEG、PNG、WebP、单张 10 MB；视频限 MP4、50 MB。

## 账号接口（M01 第 1 周）

- 账号去除首尾空白后须为 3–32 个英文字母、数字或下划线，统一转为小写；注册与登录都使用同一规则。
- 注册密码至少 8 个 Unicode 字符，UTF-8 编码最多 72 字节，不能全为空白；密码首尾空白会保留。昵称去除首尾空白后须为 1–32 个 Unicode 字符。
- 注册返回 HTTP 201；公开注册固定为 `USER`。登录返回 HTTP 200；凭据错误、账号不存在或账号已禁用，均返回 401 `UNAUTHENTICATED`。账号重复返回 409 `CONFLICT`。
- 注册和 `GET /users/me` 返回的 `data` 均为 `{ "id": 7, "account": "demo_m01", "nickname": "演示用户", "role": "USER" }`；登录将同一对象置于 `data.user` 中。
- 启动与完整请求样例见 [后端运行说明](../../src/backend/README.md)。改密、管理员初始化和用户管理在后续周次实现。

## 关键对象示例

```json
{
  "id": 101,
  "author": { "id": 7, "nickname": "小明" },
  "text": "周末的校园",
  "media": [{ "id": 33, "type": "IMAGE", "url": "/api/v1/media/33/content" }],
  "tags": ["校园", "周末"],
  "createdAt": "2026-11-17T10:00:00+08:00",
  "rating": { "average": 4.5, "count": 2 }
}
```

## 错误与权限边界

| HTTP 状态 | 错误码 | 情况 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 参数、文件类型或大小不符合规则 |
| 401 | `UNAUTHENTICATED` | 未登录或凭证失效 |
| 403 | `FORBIDDEN` | 非作者修改动态，或普通用户访问管理接口 |
| 404 | `NOT_FOUND` | 动态、用户或媒体不存在，或已删除 |
| 409 | `CONFLICT` | 重复操作或资源状态冲突 |
| 413 | `FILE_TOO_LARGE` | 上传文件超过限制 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 上传了不支持的文件格式 |

## 实现和联调检查

建立工程后补齐每个接口的完整请求和响应样例，并核对：JWT 失效处理、图片与视频读取、作者权限、管理员权限、日期边界、重复评分更新、已删除动态返回 404。
