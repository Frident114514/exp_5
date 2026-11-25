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
