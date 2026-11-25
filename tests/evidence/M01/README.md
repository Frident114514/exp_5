# M01 第 1 周验证记录

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
