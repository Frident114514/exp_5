# M01 认证模块示例

当前目录保存服务端账号认证的最小示例，覆盖：

- 用户名非空和密码长度校验；
- 登录结果判断；
- 用户状态和 ADMIN 角色校验。

AuthDemo.java 不依赖第三方库，可以直接编译检查：

javac .\src\main\java\edu\szu\moments\auth\AuthDemo.java

后续接入 Spring Boot 时，将这些规则迁移到 Spring Security、用户实体和 JWT 过滤器中。
