# 0001 技术栈与运行方式

- 日期：2026-11-17
- 状态：已决定
- 决策：项目采用下列技术栈，三端以 HTTP API 协作。

| 部分 | 技术 |
| --- | --- |
| 用户小程序 | uni-app、Vue 3、TypeScript，构建微信小程序 |
| 管理后台 | Vue 3、TypeScript、Vite、Element Plus |
| 服务端 | Java 21、Spring Boot 3.5、Spring Web、Spring Security、Spring Data JPA、Flyway、Maven |
| 数据库 | MySQL 8.4 LTS |
| 认证 | 账号密码、BCrypt、JWT |
| 媒体 | 服务端本地文件目录；MP4 通过支持 HTTP Range 的接口播放 |
| 测试 | JUnit 5、Spring Boot Test、Vitest |
| 统计图加分项 | Apache ECharts |

这样分工后，小程序、管理后台和服务端可按 `../contracts/api.md` 并行开发；Flyway 负责数据库结构版本管理。开发环境使用本地 MySQL，课堂演示使用微信开发者工具与本机服务端。真机演示时配置可访问的 HTTPS 服务地址。

依赖的具体补丁版本在创建工程时写入各端构建文件，组员使用锁文件或 Maven Wrapper 保持一致。变更核心技术时新建决策记录，并同步 `../design.md` 与项目根目录 `README.md`。
