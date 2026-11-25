# 服务端主工程 · M01 第 1 周

Java 21、Spring Boot 3.5.16、Spring Security、Spring Data JPA、Flyway、MySQL 8.4 LTS。首周交付注册、登录、本人信息和 JWT 权限校验。改密、管理员初始化及用户管理按后续周次实现；M02 仍按 [独立模块指南](modules/posts/README.md) 构建。

## 本地启动

创建空数据库 `moments`，为项目建立专用账号并授予该库的建表及读写权限。以下命令从 `src/backend/` 执行。PowerShell 7 示例：

```powershell
$env:MOMENTS_DB_URL = 'jdbc:mysql://127.0.0.1:3306/moments'
$env:MOMENTS_DB_USER = 'moments'
$env:MOMENTS_DB_PASSWORD = Read-Host 'MySQL password' -MaskInput
$env:MOMENTS_JWT_SECRET = [Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
.\mvnw.cmd spring-boot:run
```

`MOMENTS_JWT_SECRET` 须是至少 32 字节随机值的 Base64 编码。设置好后在同一环境下启动；需要重启后继续接受旧令牌时，须沿用同一密钥。`.env.example` 列出配置项，Spring Boot 不会自动加载 `.env`。Maven Wrapper 固定 Maven 3.9.9，macOS/Linux 可用 `sh ./mvnw spring-boot:run`。首次启动由 Flyway 执行 `V1__users.sql`，JPA 验证表结构。默认端口 8080，可用 `SERVER_PORT` 覆盖。

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

high-concurrency profile 在每个数据库连接启用最多 32 条预编译语句缓存（SQL 长度上限 1024），复用查询准备，不缓存用户状态或响应。连接池仍为 20、本人处理名额仍为 64。[实际突发对比](../../tests/evidence/M01/spike-2026-11-25/README.md)中最终成功 4520/10000，仍未满足万人容量门禁；不可把配置启用视为验收通过。

所有初始参数均可通过 [.env.example](.env.example) 中的环境变量调整。注册和登录共享每实例 10 请求/秒、突发 20、并行 4 的预算，超限返回 429。本人信息并行上限为 64，超限返回 503；两种响应都带 `Retry-After: 1`。客户端应退避并加入随机抖动，避免立即循环重试。数据库暂时不可用也返回 503。

Tomcat 工作线程 100、工作队列 100、连接数 2048；数据库连接池最多 20，获取连接等待 2 秒。JDBC 建连超时 3 秒、读超时 10 秒，JPA 查询超时提示 5 秒。依据部署机 CPU、数据库连接预算、响应分位数及压测结果调整，不按在线人数等量增加线程或连接。

[M01 k6 压测指南](../../tests/performance/README.md)区分登录计算与携令牌查询负载。[并发设计决策](../../specs/decisions/0004-m01-concurrency-bounds.md)说明单实例限流、即时令牌失效与横向扩展边界。

专项探索 1 万请求同时到达时，可设置 `SPRING_PROFILES_ACTIVE=high-concurrency` 启动。该配置使用 Java 21 虚拟线程，Tomcat 连接上限初始为 16384，连接积压上限 512；平台线程数与工作队列参数在虚拟线程模式下不作为并发控制。认证和本人信息的应用名额仍然有效。连接数上限不等于成功处理能力；压测机网络、操作系统连接资源及服务端资源均须记录。按实测逐步调整 `MOMENTS_ME_CONCURRENCY` 与连接池预算，不把数据库连接池设置成 10000。
