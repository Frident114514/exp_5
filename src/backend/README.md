# 服务端主工程 · M01 第 1 周

Java 21、Spring Boot 3.5.16、Spring Security、Spring Data JPA、Flyway、MySQL 8.4 LTS。现已交付注册、登录、本人信息、改密、管理员环境变量初始化及用户查询/禁用。主工程通过 build-helper 纳入 M02/M03/M04 的生产源码并统一启动，不需要先 install 独立模块；独立模块工程继续保留用于负责人自测。

## 本地启动

创建空数据库 `moments`，为项目建立专用账号并授予该库的建表及读写权限。以下命令从 `src/backend/` 执行。PowerShell 7 示例：

```powershell
$env:MOMENTS_DB_URL = 'jdbc:mysql://127.0.0.1:3306/moments'
$env:MOMENTS_DB_USER = 'moments'
$env:MOMENTS_DB_PASSWORD = Read-Host 'MySQL password' -MaskInput
$env:MOMENTS_JWT_SECRET = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
.\mvnw.cmd spring-boot:run
```

`MOMENTS_JWT_SECRET` 须是至少 32 字节随机值的 Base64 编码。设置好后在同一环境下启动；需要重启后继续接受旧令牌时，须沿用同一密钥。`.env.example` 列出配置项，Spring Boot 不会自动加载 `.env`。Maven Wrapper 固定 Maven 3.9.9，macOS/Linux 可用 `sh ./mvnw spring-boot:run`。首次启动由 Flyway 执行 `V1__users.sql` 与 `V2__admin_operation_logs.sql`，JPA 验证表结构。默认端口 8080，可用 `SERVER_PORT` 覆盖。

## 账号接口验证

在另一个 PowerShell 7 窗口发请求。以下密码为虚构示例，重复执行时换一个账号：

```powershell
$api = 'http://127.0.0.1:8080/api/v1'
$credentials = @{ account = 'demo_m01'; password = 'ExamplePass-01' }
$registration = $credentials + @{ nickname = '演示用户' }
Invoke-RestMethod -Method Post "$api/auth/register" -ContentType 'application/json; charset=utf-8' -Body ($registration | ConvertTo-Json)
$login = Invoke-RestMethod -Method Post "$api/auth/login" -ContentType 'application/json' -Body ($credentials | ConvertTo-Json)
Invoke-RestMethod "$api/users/me" -Headers @{ Authorization = "Bearer $($login.data.accessToken)" }
```

注册示例响应（HTTP 201）：

```json
{"code":"OK","data":{"id":7,"account":"demo_m01","nickname":"演示用户","role":"USER"},"message":""}
```

登录返回 `code: "OK"`，`data` 包含 `accessToken`、`expiresAt` 和 `user`；令牌自签发起有效 12 小时。`GET /users/me` 返回当前令牌对应用户的公开资料。错误密码、缺少或失效令牌均返回 HTTP 401；普通用户访问 `/api/v1/admin/**` 返回 HTTP 403。详细字段规则见 [API 契约](../../specs/contracts/api.md#账号接口m01-第-1-周)。

## 集成测试

让 `MOMENTS_DB_URL` 指向专用的 MySQL 8.4 测试库，设置对应用户名和密码，再执行 `.\mvnw.cmd -B -ntp verify`（macOS/Linux 用 `sh ./mvnw`）。测试会启动随机端口的 HTTP 服务、运行 Flyway，并写入随机账号、修改测试账号状态。不要使用含实际数据的库。Maven 报告位于 `target/surefire-reports/`；验收记录见 [M01 测试证据](../../tests/evidence/M01/README.md)。

Windows 可设置 `MYSQL_BIN` 指向 MySQL 8.4 的 bin 目录，再运行 `./scripts/verify_mysql.ps1`。脚本启动独立临时实例、随机端口和空库，运行 verify 后停止该实例并恢复环境变量；日志与数据保留在输出的临时目录。

## 并发配置和压测

可选 `MOMENTS_DIAGNOSTICS_ENABLED=true` 启用有界 M01 试验观测，默认关闭。请求头 `X-M01-Trial`（1–40 位字母数字下划线或短横线）、`X-M01-Request-Id`（0–59999）关联来源与单调时间；最多 16 个试验，溢出可见。`GET /api/v1/internal/m01-diagnostics?trial=...` 仅管理员可读，关闭时不注册该控制器。只保存编号、时间与状态，不保存账号/JWT/密码。结果区间是应用过滤器区间，不能当网络完整到达或发送时间。见 [实际对账](../../tests/evidence/M01/performance-2026-11-25-205558/README.md)。

high-concurrency profile 在每个数据库连接启用最多 32 条预编译语句缓存（SQL 长度上限 1024），复用查询准备，不缓存用户状态或响应。连接池仍为 20、本人处理名额仍为 64。[实际突发对比](../../tests/evidence/M01/spike-2026-11-25/README.md)中最终成功 4520/10000，仍未满足万人容量门禁；不可把配置启用视为验收通过。

所有初始参数均可通过 [.env.example](.env.example) 中的环境变量调整。注册和登录共享每实例 10 请求/秒、突发 20、并行 4 的预算，超限返回 429。本人信息并行上限为 64，超限返回 503；两种响应都带 `Retry-After: 1`。客户端应退避并加入随机抖动，避免立即循环重试。数据库暂时不可用也返回 503。

Tomcat 工作线程 100、工作队列 100、连接数 2048；数据库连接池最多 20，获取连接等待 2 秒。JDBC 建连超时 3 秒、读超时 10 秒，JPA 查询超时提示 5 秒。依据部署机 CPU、数据库连接预算、响应分位数及压测结果调整，不按在线人数等量增加线程或连接。

[M01 k6 压测指南](../../tests/performance/README.md)区分登录计算与携令牌查询负载。[并发设计决策](../../specs/decisions/0004-m01-concurrency-bounds.md)说明单实例限流、即时令牌失效与横向扩展边界。

专项探索 1 万请求同时到达时，可设置 `SPRING_PROFILES_ACTIVE=high-concurrency` 启动。该配置使用 Java 21 虚拟线程，Tomcat 连接上限初始为 16384，连接积压上限 512；平台线程数与工作队列参数在虚拟线程模式下不作为并发控制。认证和本人信息的应用名额仍然有效。连接数上限不等于成功处理能力；压测机网络、操作系统连接资源及服务端资源均须记录。按实测逐步调整 `MOMENTS_ME_CONCURRENCY` 与连接池预算，不把数据库连接池设置成 10000。

### 当前可用的高并发版本

以 `SPRING_PROFILES_ACTIVE=high-concurrency` 启动，保持本人查询处理名额 64、数据库连接池 20；新增最多 1024 个等待者、最长 200 ms 的准入等待。超出等待人数或超时返回 503 和 Retry-After；等待计入请求延迟，取消/中断归还等待预算。默认 profile 仍立即拒绝，密码认证限流保持不变。诊断默认关闭；所有诊断、JFR、原始记录、对照脚本均保留。

首次启动后先按隔离压测报告预热再施加峰值；容量仅适用于携有效 JWT 的本人信息查询。冷启动、登录密码计算及全系统混合负载分别验收，不能沿用本人查询成绩。最新实测见 tests/evidence/M01/usable-2026-11-25/README.md；可用档位以该报告的重复验收结果为准。


## 修改密码与管理员用户管理

`PUT /api/v1/users/me/password` 携带当前 JWT，JSON 为 `{"oldPassword":"ExamplePass-01","newPassword":"NewExamplePass-02"}`。成功返回 `{"code":"OK","data":null,"message":""}`，旧密码不能再登录，全部旧令牌失效；客户端应清除本地 token，再使用新密码登录。密码校验、BCrypt 强度和即时失效语义保持一致；改密共享注册/登录的密码工作预算，超限 429。

在启动服务的终端配置以下环境变量，再执行启动命令：

```powershell
$env:MOMENTS_ADMIN_ACCOUNT = 'project_admin'
$env:MOMENTS_ADMIN_PASSWORD = Read-Host 'Initial administrator password' -MaskInput
$env:MOMENTS_ADMIN_NICKNAME = '项目管理员'
```

仅首次创建；已有同名管理员不会被重置密码或重新启用，普通用户同名则启动失败。初始化成功后可移除这三项配置，保留数据库记录即可。不提供默认密码。管理员使用同一 `/auth/login` 登录；M08 现有用户查询页面可直接调用 `/admin/users`，响应字段已对齐其解析器。

- 查询：`GET /api/v1/admin/users?keyword=demo&page=1&pageSize=10`。返回 `items/page/pageSize/total`，每项含 `id/account/nickname/role/status/createdAt`，包含禁用用户；账号或昵称字面子串匹配，按 ID 倒序。
- 禁用：`DELETE /api/v1/admin/users/{userId}`。保留用户行，状态置 DISABLED、tokenVersion 加 1，写入管理日志；重复请求不重复写日志。禁止本人或 ADMIN，避免误删管理员。M08 当前页面尚无禁用按钮，接口可先用 HTTP 客户端联调。
- 登记迁移 V2 为管理日志，M02 动态迁移合入时须使用后续版本号，不能占用主工程 V2。

禁用事务内按 ID 升序持有用户行锁，复核管理员状态及令牌版本，同步发布 `edu.szu.moments.auth.UserDisabled`。`UserDisabledCleanup` 已在同一事务调用动态隐藏与评论/评分清理，失败整体回滚；不要重复注册清理监听器。正式迁移使用 V3 动态、V4 媒体、V5 互动，V1/V2 保持不变。主工程公开 GET 动态列表/详情/评论和已发布媒体，管理路径仍限定 ADMIN。multipart 文件上限 50 MB、请求上限 52 MB，业务图片限制仍为 10 MB。

2026-11-26 主工程接入后，独立 MySQL 8.4.13 上 31 项测试全部通过、打包成功；真实禁用清理及失败回滚见 [测试证据](../../tests/evidence/M01/integration-2026-11-26/README.md)。各模块接续任务、接口字段与验收范围见 [成员交接](../../specs/handoff-2026-11-26.md)。完整页面与媒体等流程仍由对应负责人联调。

## 好友与管理员统计

M01 新增好友申请/处理/解除、真实业务每日活跃记录、管理员统计接口；启动自动执行 V6。字段、错误码、分页、统计口径和 M02/M05/M08 接入方式见 [完整约定](../../specs/contracts/m01-social-statistics.md)，[真实 MySQL 回归记录](../../tests/evidence/M01/social-statistics-2026-11-27/README.md)。原有准入配置和诊断脚本保持可用。
