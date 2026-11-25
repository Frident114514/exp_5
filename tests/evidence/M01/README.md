# M01 第 1 周验证记录

## 万连接突发复验与优化

[逐次结果和优化判定](spike-2026-11-25/README.md)：10000 条 TCP 连接建立成功，10000 个请求集中放行；扩大名额与短暂等待未达 SLA，已回退。high-concurrency profile 仅保留有界预编译语句缓存；16 项真实 MySQL 回归通过。最终突发成功 4520/10000、503 为 5480、成功 P95 398.96 ms；客户端活动峰值 4287、发出跨度 847.08 ms，尚未证明严格万人同时在途，也未通过成功率门禁。各轮恢复检查均通过。

## 2026-11-25 实际容量初测

[完整实测与原始证据](performance-2026-11-25-182730/README.md)：.NET 10 异步 HTTP 客户端、10000 个虚构账号、独立 MySQL 8.4.13，同机运行。本人信息 100/500/1000 QPS 各 20 秒全部成功，1000 QPS 档成功 P95 为 1.57 ms；10000 请求批量档仅 6474 成功、3526 返回 503，未通过。客户端最大在途 3476，不能宣称验证万人同时在途。恢复档 2000/2000 成功。k6 未执行；MySQL 资源采样误取监控父进程，已披露并修正后续脚本，未纳入资源健康结论。

- 日期：2026-11-22（北京时间）
- 环境：Windows 11、Oracle JDK 21.0.8、Maven 3.9.9、Spring Boot 3.5.16、独立 MySQL Community Server 8.4.13 空库。
- 命令：在 `src/backend/` 设置 [后端环境变量](../../../src/backend/README.md#本地启动)，将数据源指向专用测试库，执行 `mvn -B -ntp verify`。
- [测试源码](../../../src/backend/src/test/java/edu/szu/moments/auth/AuthIntegrationTest.java)；本地 Maven 原始报告位于 `src/backend/target/surefire-reports/edu.szu.moments.auth.AuthIntegrationTest.txt`。

```text
Successfully applied 1 migration to schema `moments_m01_test`, now at version v1
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
```

| 实际通过的测试 | 核对结果 |
| --- | --- |
| 空库启动与迁移 | MySQL 8.4.13 执行 `V1__users.sql`，JPA 校验后启动 HTTP 服务 |
| 注册及密码存储（M01-C02） | HTTP 201；提交 ADMIN 字段仍建 USER；响应无密码，库内保存可用 BCrypt 验证的哈希 |
| 重复注册（M01-C01） | HTTP 409；忽略账号大小写和首尾空白；库内仅 1 行 |
| 无效注册 | 非法账号、超过 72 字节的密码、空白昵称及无效 JSON 均返回 400 |
| 正确登录与本人信息（M01-C02） | HTTP 200；令牌有效期 12 小时；携令牌取回本人信息 |
| 错误凭据（M01-C02） | 错误密码与不存在账号均返回 401，响应相同 |
| 缺失、格式错误和签名被改的令牌（M01-C03） | 本人信息接口均返回 401 |
| 过期、版本失效及禁用账号令牌（M01-C03） | 均拒绝访问；禁用账号也不能登录。测试用 SQL 构造状态 |
| 普通用户越权拦截（M01-C05 部分） | 普通用户访问管理路径返回 403；管理员查询尚未实现 |

Flyway 启动日志提示当前托管版本尚未声明测试 MySQL 8.4；本次在 8.4.13 空库实际完成迁移和请求验证。改密、管理员查询及禁用事务用例留待相应接口实现后执行。

## 2026-11-25 并发资源控制回归

环境：Windows 11、JDK 21.0.8、Maven 3.9.9、MySQL Community Server 8.4.13 独立空库。设置 `MYSQL_BIN` 后，在 `src/backend/` 执行 `./scripts/verify_mysql.ps1`。

默认 profile：6 项并发控制测试 + 10 项真实 HTTP/数据库测试，合计 16 项通过，0 失败、0 错误、0 跳过，verify 打包成功。

启用 `SPRING_PROFILES_ACTIVE=high-concurrency` 后，再用另一独立 MySQL 空库执行同一命令：16 项全部通过、verify 成功。启动日志确认该 profile 已激活，HTTP 处理线程使用 `tomcat-handler-*`。本次归档 [并发测试报告](2026-11-25-concurrency.txt)与 [HTTP 集成报告](2026-11-25-http.txt)。

- 32 个线程竞争 2 个密码请求名额，恰好 2 个通过、30 个被拒绝，释放后可再次进入。
- 令牌桶补充和突发上限使用可控时钟验证；密码与本人信息名额相互独立。
- 客户端读写异常后释放名额；认证查询的数据库资源异常返回 503、附 Retry-After，且不泄露数据库异常文本。
- 真实 HTTP 下占满认证名额返回 429，占满本人查询名额返回 503；释放后恢复正常认证响应。
- 复验全部首周注册、密码、身份查询、权限及过期/篡改/禁用/版本失效令牌用例；JWT DTO 投影可正常构建并查询。
- k6 脚本通过 Node.js ES module 语法检查；本机未安装 k6，未执行容量压测，未宣称通过 1 万并发验收。
