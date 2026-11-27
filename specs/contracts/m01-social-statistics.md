# M01 好友与统计接口

基础路径 `/api/v1`，使用既有 JWT、`{code,data,message}` 和 `items/page/pageSize/total`；分页从 1 开始，每页 1–50。本文对应第二周必做扩展，好友动态筛选与页面由 M02/M05 接入。

## 好友接口

仅 ACTIVE 普通用户使用，ADMIN 返回 403。用户 ID 正整数；不存在、禁用或管理员目标返回 404。所有写操作在事务中按用户 ID 升序锁定双方并复核操作者 tokenVersion；客户端不能代替别人发申请。

| 方法及路径 | 输入 | data |
| --- | --- | --- |
| GET /users/search | keyword 必填，去首尾空白 1–64 字符；page/pageSize | 分页；每项 id/account/nickname/relationship/requestId |
| POST /friend-requests | {"userId":7} | id/requester/recipient/status/createdAt/handledAt；HTTP 201 |
| GET /friend-requests | direction=incoming 或 outgoing（默认 incoming）；page/pageSize | 本人的待处理申请分页，最新 ID 在前 |
| PUT /friend-requests/{requestId} | {"action":"ACCEPT"} 或 {"action":"REJECT"} | 更新后的申请 |
| GET /friends | page/pageSize | 好友基本资料 id/account/nickname，ID 倒序 |
| DELETE /friends/{userId} | 无 | null；重复解除成功；目标已禁用仍可解除 |

申请中的 requester/recipient 以及好友基本资料均为 id/account/nickname/role，role 固定 USER。relationship 为 NONE/PENDING_SENT/PENDING_RECEIVED/FRIEND，只有待处理关系提供 requestId，其余为 null。搜索不包含本人、管理员和禁用用户，不返回密码、令牌版本或管理员数据；按账号或昵称的字面子串匹配，忽略大小写，ID 倒序。

不能添加自己（400）。已有好友或任一方向存在待处理申请，返回 409；交叉申请不会自动成为好友。只能由接收人处理（其他人 403），已处理或取消申请再次处理返回 409。拒绝或解除后允许新申请，产生新的 requestId，旧请求不能处理新申请。申请状态 PENDING/ACCEPTED/REJECTED/CANCELLED；列表只列 PENDING。禁用用户时，同事务解除其好友关系并取消双方待处理申请，不删除历史申请。好友列表过滤禁用用户。

M02 可在同一数据源使用 `friendships(user_low,user_high)`；两列为升序 ID，行存在表示双方是好友。筛选用 EXISTS，不能先分页后过滤；两端用户必须 ACTIVE。跨模块也可调用 `FriendRelations.areFriends(a,b)`。此接口不改变普通动态的公开可见性。

## 活跃与统计

仅 ADMIN 可用：`GET /admin/statistics?from=2026-11-01&to=2026-11-27`。日期严格为 YYYY-MM-DD、北京时间自然日、包含两端。省略时 to 默认为北京时间今天，from 默认为 to 前 29 天；from<=to，最多 366 天，to 不得晚于今天。不合法返回 400。

data 为：
```json
{
  "timezone":"Asia/Shanghai",
  "trackingStartedAt":"2026-11-27T00:00:00Z",
  "totals":{"userCount":2,"postCount":3,"commentCount":4,"ratingCount":2},
  "daily":[{"date":"2026-11-27","newUsers":2,"newPosts":3,"activeUsers":2}]
}
```

totals 不受日期范围影响：userCount 为所有已注册 USER（含禁用）；其余为当前可见动态、评论和有效评分数（排除禁用作者/互动用户与已删除动态/评论）。newUsers/newPosts 是区间内创建量，包含后来禁用的用户与逻辑删除的动态，不能与当前可见总量混淆。

活跃用户定义为北京时间当天成功登录、发布动态、编辑动态、评分或评论的去重普通用户；同一用户多次操作只计 1。失败、GET 浏览、注册、好友操作、管理员操作不计。每日仅保存 userId/day/首次与最后活动时间，不存正文、token、密码或完整访问日志。记录与业务提交同事务，回滚不计数；历史禁用不改写已经发生的活跃量。

trackingStartedAt 是 V6 迁移启用记录的 UTC 时刻。启用当日数据可能不完整；该日以前 activeUsers 返回 null（未知），之后没有活动返回 0，不反推或伪造历史 DAU。daily 按日期升序，补齐区间所有日期。数据库时间仍为 UTC，由服务端转换北京时间日期。

M08 负责图表和日期筛选；M05 负责好友页面；M02 负责好友动态筛选。新增服务端接口不代表这些页面已完成验收。
