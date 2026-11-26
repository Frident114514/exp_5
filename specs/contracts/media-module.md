# M03 媒体模块实施与交接约定

状态：M03（潘铭伟）的实现与自测依据；与 M02 动态模块已完成端口级与 HTTP 级联调验证，M01 真实 JWT 及 M06/M07 小程序页面待联调确认。日期：2026-11-18。

## 工程与边界

代码位于 `src/backend/modules/media/`，以 Java 21 / Spring Boot 3.5.16 的 Maven JAR（`edu.szu.moments:media-module:0.1.0-SNAPSHOT`）独立构建，包名为 `edu.szu.moments.media`。

- 模块直接依赖 `edu.szu.moments:posts-module:0.1.0-SNAPSHOT`，实现 M02 定义的 `MediaPort`（`lockAndInspect`、`replaceBindings`、`findByPostIds`），并在公开读取媒体内容时调用 M02 提供的 `PostAccess.requireVisible(postId)` 核验动态与作者可见性。
- 登录身份通过 `IdentityPort` 获取；M01 主工程后续只需导入 `PostsModuleConfiguration` 与 `MediaModuleConfiguration`，无需重写媒体上传、读取、绑定或清理逻辑。
- 生产 JAR 不含任何模拟身份或测试夹具；测试适配器仅位于 `src/test/`。

## HTTP 接口约定补充

遵循 `api.md` 的统一路径前缀 `/api/v1` 与响应信封 `{ "code": "OK", "data": ..., "message": "" }`。

### 1. 上传媒体：`POST /api/v1/media`

- **对应需求**：U02
- **权限**：普通登录用户（`Role.USER` 且账号状态 `ACTIVE`）。未登录返回 `401 UNAUTHENTICATED`；管理员调用返回 `403 FORBIDDEN`（遵循 `design.md` 第 8 节权限矩阵：管理员不可发布动态或上传媒体）。
- **请求格式**：`multipart/form-data`，字段名为 `file`。
- **类型与大小校验规则**：
  - **图片（`IMAGE`）**：仅接受 `image/jpeg`（`.jpg`/`.jpeg`，文件头魔数 `FF D8 FF`）、`image/png`（`.png`，文件头魔数 `89 50 4E 47 0D 0A 1A 0A`）、`image/webp`（`.webp`，文件头魔数 `RIFF....WEBP`）；单张上限 **10 MB**（`10,485,760` 字节）。
  - **视频（`VIDEO`）**：仅接受 `video/mp4`（`.mp4`，偏移 4..7 字节为 `ftyp` 签名）；单个上限 **50 MB**（`52,428,800` 字节）。
  - **双重校验**：同时检查扩展名/声明 MIME 与二进制文件头魔数（Magic Bytes）。任一不匹配、伪装 MIME 或不支持格式返回 `415 UNSUPPORTED_MEDIA_TYPE`；空文件或缺失 `file` 部分返回 `400 VALIDATION_ERROR`；超出对应类型体积上限返回 `413 FILE_TOO_LARGE`。
- **存储键与原子性清理**：
  - 服务端按 `yyyy/MM/dd/{uuid}.{ext}` 生成相对存储键 `storage_key`，绝不直接使用客户端原始文件名，并强制校验归一化路径位于配置的 `moments.media.storage-dir` 目录内。
  - 上传成功时写入 `media` 表：`post_id = NULL`、`status = 'PENDING'`、`sort_order = 0`。
  - 若文件流写入失败，立即删除半成品临时文件且不写数据库；若数据库事务回滚或保存失败，通过 `TransactionSynchronization` 自动删除已写入的磁盘文件，保证磁盘与数据库无残留。

### 2. 读取媒体内容与视频 Range：`GET /api/v1/media/{mediaId}/content`

- **对应需求**：U02、U05
- **权限与可见性控制**：
  - 公开接口，但**仅允许读取状态为 `ACTIVE`、已绑定 `post_id` 且通过 `PostAccess.requireVisible(postId)` 校验（即父动态为 `ACTIVE` 且作者为 `ACTIVE`）的媒体**。
  - 对于不存在的 `mediaId`、处于 `PENDING`（待绑定/已解绑）或 `DELETED` 状态的媒体、父动态已删除或作者已禁用的媒体、以及底层物理文件缺失的情况，统一返回 `404 NOT_FOUND`，防止未发布或已删除媒体泄露。
- **HTTP Range 行为（遵循 RFC 9110）**：
  - 响应头固定包含 `Accept-Ranges: bytes`、`Content-Type: <mimeType>`、`Content-Disposition: inline`。
  - **无 `Range` 请求头**：返回 `200 OK`、`Content-Length: <fileSize>` 及完整二进制内容。
  - **合法 `Range: bytes=start-end`（含 `bytes=start-`、`bytes=-suffix`）**：返回 `206 Partial Content`、`Content-Range: bytes <start>-<end>/<fileSize>`、`Content-Length: <end - start + 1>` 及对应区间字节流，支持小程序 `<video>` 播放与进度条拖动。
  - **不可满足或格式非法的 `Range` 请求头**（如 `start >= fileSize`、`start > end`、多区间请求或非 `bytes=` 单位）：返回 `416 Range Not Satisfiable`，携带 `Content-Range: bytes */<fileSize>` 与错误体 `{"code":"RANGE_NOT_SATISFIABLE","data":null,"message":"请求字节范围不可满足"}`。

## 内部端口实现与清理规则

| 能力 / 方法 | 调用方 | 行为与事务约定 |
| --- | --- | --- |
| `MediaPort.lockAndInspect(mediaIds)` | M02 `PostService` | 在当前事务内按 `id ASC` 执悲观写锁（`FOR UPDATE`），校验物理文件存在性并返回 `MediaCandidate`（若物理文件丢失则不作为有效候选返回，触发 404 回滚） |
| `MediaPort.replaceBindings(actorId, postId, mediaIds, now)` | M02 `PostService` | 与动态创建/编辑共用同一事务；将原属于 `postId` 但不在新列表中的媒体解绑为 `PENDING`（`post_id = NULL, sort_order = 0, updated_at = now`），将新列表中的媒体按顺序设为 `ACTIVE`（`post_id = postId, sort_order = i, updated_at = now`）；若越权、已删除或已绑定其他动态则抛 `PostFailure` 回滚整体事务 |
| `MediaPort.findByPostIds(postIds)` | M02 `PostService` | 批量查询指定动态集合下的 `ACTIVE` 媒体，按 `sort_order ASC, id ASC` 返回 `MediaView(id, type, "/api/v1/media/" + id + "/content")` |
| `MediaService.cleanupExpiredPending()` | `MediaCleanupTask` / 运维测试 | 在事务内锁定 `status = 'PENDING'` 且 `updated_at <= now - 24h` 的媒体记录（含上传后超时未绑定及编辑移除解绑后超时的媒体），删除本地物理文件并将记录状态更新为 `DELETED` |

## 数据库迁移

模块维护 `media` 表，候选迁移脚本位于 `src/backend/modules/media/src/main/resources/db/migration-candidates/m03/media.sql`。前置依赖为 `users(id)` 与 `posts(id)`。正式 Flyway 版本号由 M01 合入主工程前统一登记。

## 成功与失败样例（供 M06 / M07 对接）

### 1. 图片上传成功（JPEG / PNG / WebP，M06）

```http
POST /api/v1/media
Authorization: Bearer <真实登录令牌>
Content-Type: multipart/form-data; boundary=---boundary

file=@campus.jpg (或 .png / .webp)
```

```json
{
  "code": "OK",
  "data": {
    "mediaId": 33,
    "id": 33,
    "type": "IMAGE",
    "mimeType": "image/jpeg",
    "size": 245760,
    "url": "/api/v1/media/33/content",
    "status": "PENDING",
    "createdAt": "2026-11-18T12:00:00Z"
  },
  "message": ""
}
```

### 2. 动态绑定图片调用样例（与 M02 `POST /posts` 对接）

图片上传返回 `mediaId`（如 `33`、`34`）后，客户端提交动态创建请求，M02 在同一数据库事务中调用 `MediaPort.replaceBindings` 完成归属校验与绑定：

```http
POST /api/v1/posts
Authorization: Bearer <真实登录令牌>
Content-Type: application/json

{
  "text": "周末的校园",
  "mediaIds": [33, 34],
  "tags": ["校园", "周末"]
}
```

```json
{
  "code": "OK",
  "data": {
    "id": 101
  },
  "message": ""
}
```

绑定后查询动态详情 `GET /api/v1/posts/101` 返回的媒体字段与 `api.md` 一致：

```json
{
  "code": "OK",
  "data": {
    "id": 101,
    "author": { "id": 7, "nickname": "小明" },
    "text": "周末的校园",
    "media": [
      { "id": 33, "type": "IMAGE", "url": "/api/v1/media/33/content" },
      { "id": 34, "type": "IMAGE", "url": "/api/v1/media/34/content" }
    ],
    "tags": ["周末", "校园"],
    "createdAt": "2026-11-18T12:01:00Z",
    "updatedAt": "2026-11-18T12:01:00Z",
    "rating": { "average": null, "count": 0 }
  },
  "message": ""
}
```

### 3. 视频上传成功（MP4，M06）

```http
POST /api/v1/media
Authorization: Bearer <真实登录令牌>
Content-Type: multipart/form-data; boundary=---boundary
```

```json
{
  "code": "OK",
  "data": {
    "mediaId": 35,
    "id": 35,
    "type": "VIDEO",
    "mimeType": "video/mp4",
    "size": 1048576,
    "url": "/api/v1/media/35/content",
    "status": "PENDING",
    "createdAt": "2026-11-18T12:00:00Z"
  },
  "message": ""
}
```

### 视频 Range 分段读取（M07）

```http
GET /api/v1/media/33/content
Range: bytes=0-1023
```

```http
HTTP/1.1 206 Partial Content
Content-Type: video/mp4
Accept-Ranges: bytes
Content-Range: bytes 0-1023/1048576
Content-Length: 1024
Content-Disposition: inline

<1024 bytes binary stream>
```

### 常见失败响应

- 文件超限（图片 > 10 MB 或视频 > 50 MB）：HTTP 413，`{"code":"FILE_TOO_LARGE","data":null,"message":"上传文件超过大小限制"}`
- 格式不支持或伪装 MIME：HTTP 415，`{"code":"UNSUPPORTED_MEDIA_TYPE","data":null,"message":"不支持的文件格式，仅允许 JPEG/PNG/WebP 图片或 MP4 视频"}`
- 读取 PENDING 或已删除动态的媒体：HTTP 404，`{"code":"NOT_FOUND","data":null,"message":"媒体不存在或已不可见"}`
- Range 越界：HTTP 416（含 `Content-Range: bytes */1048576`），`{"code":"RANGE_NOT_SATISFIABLE","data":null,"message":"请求字节范围不可满足"}`
