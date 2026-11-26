# 第一周统一接口约定

## 一、先统一接口约定

本文统一第一周各模块使用的接口路径、请求参数和响应字段。所有路径基于主后端，后端启动方式见 [运行说明](../../src/backend/README.md)；全项目完整接口见 [API 约定](api.md)。本文不增加第一周功能要求。

### 1. 所有人共用的规则

- 后端接口统一以 `/api/v1` 开头，默认本地服务地址为 `http://127.0.0.1:8080`，例如登录地址是 `http://127.0.0.1:8080/api/v1/auth/login`。配置里已经包含 `/api/v1` 时，请勿重复拼接。
- 成功响应为 `{"code":"OK","data":...,"message":""}`；失败响应为 `{"code":"错误码","data":null,"message":"具体原因"}`。页面需要同时判断 HTTP 状态和 code，不能仅凭收到响应就显示成功。
- 登录后的请求带 `Authorization: Bearer <accessToken>`。401 表示未登录或登录失效；403 表示当前账号没有权限。管理员同样使用登录接口，不使用单独的管理员登录地址。
- 列表页码从 1 开始，参数名是 `page`、`pageSize`，默认每页 10 条，最多 50 条；返回字段是 `items`、`page`、`pageSize`、`total`。
- 时间字段按服务端 ISO 8601 时间解析后显示，不直接截取字符串。用户 ID、动态 ID 均使用后端返回值，不能在真实接口模式中写死。
- 小程序广场和详情允许匿名浏览；发布、我的动态、编辑和删除需要登录；后台查询必须是 ADMIN。浏览器开发通过 Vite 代理连接后端，微信真机使用能访问的后端地址，不能把真机的 localhost 当作电脑。

本文所说的“Mock”是本地预先写好的假数据或模拟接口，不能用它证明真实后端已经联通。“登录态”是前端保存的登录令牌、到期时间和当前用户信息。“联通”是前端实际发出 HTTP 请求并收到真实后端的正确响应，不只是网络端口能连接。“响应信封”就是前面统一的 code/data/message 外层对象，列表应读取 data.items。“主工程”是从 `src/backend` 启动的同一个后端进程，不要求分别启动四个模块服务。

### 2. 第一周实际需要使用的接口

以下路径均省略公共前缀 `/api/v1`。

| 使用人 | 方法与路径 | 请求 | 成功时的 data |
| --- | --- | --- | --- |
| M05 | `POST /auth/register` | JSON：`account`、`password`、`nickname` | `{id,account,nickname,role}`；HTTP 201 |
| M05、M08 | `POST /auth/login` | JSON：`account`、`password` | `{accessToken,expiresAt,user}`；user 含 id、account、nickname、role |
| M05、M06、M08 | `GET /users/me` | 携带 token | `{id,account,nickname,role}` |
| M02、M06 | `POST /posts` | 本周纯文字：`{"text":"动态正文","mediaIds":[],"tags":[]}` | `{"id":动态ID}` |
| M02、M06 | `GET /users/me/posts?page=1&pageSize=10` | 携带 token | 动态分页对象 |
| M02、M06 | `PATCH /posts/{postId}` | 本周只改正文：`{"text":"修改后的正文"}` | 修改后的完整动态 |
| M02、M06 | `DELETE /posts/{postId}` | 携带 token，无正文 | null；HTTP 200 |
| M02、M07 | `GET /posts?page=1&pageSize=10` | 可匿名 | 动态分页对象 |
| M02、M07 | `GET /posts/{postId}` | 可匿名 | 完整动态；不存在或已删除返回 404 |
| M08 | `GET /admin/users?keyword=...&page=1&pageSize=10` | ADMIN token；keyword 可省略 | 用户分页对象 |
| M02、M08 | `GET /admin/posts?keyword=...&page=1&pageSize=10` | ADMIN token；keyword 可省略 | 动态分页对象 |

**动态对象字段：**`id`、`author:{id,nickname}`、`text`、`media`、`tags`、`createdAt`、`updatedAt`、`rating:{average,count}`。无评分时为 `average:null,count:0`，页面显示“暂无评分”。M07 本周可以使用媒体占位，只展示已有评分摘要。

**后台用户列表字段：**每项为 `id/account/nickname/role/status/createdAt`。列表包含禁用用户，页面按 status 展示状态。

**账号校验：**account 为 3–32 个英文字母、数字或下划线，服务端去除首尾空白并转小写；nickname 为去除首尾空白后的 1–32 个字符；密码至少 8 个字符，UTF-8 编码最多 72 字节。公开注册只能创建 USER。管理员由组长通过后端环境变量初始化，不在群里提供公共默认密码。

**正文编辑：**PATCH 省略 mediaIds 和 tags 就保留原值，不能为了修改文字而顺带发送空数组。发布时不传 authorId，作者由登录身份确定。

M03 本周整理媒体接口时统一使用：`POST /media`，multipart 字段名 `file`，携带 token；返回包含 `mediaId/id/type/mimeType/size/url/status/createdAt`，size 单位为字节。内容地址为 `GET /media/{mediaId}/content`。这是后端交接约定，不要求 M06/M07 本周提前接图片或视频功能。
