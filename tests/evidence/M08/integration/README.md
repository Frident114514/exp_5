# M08 与 M01 本地联调记录

日期：2026-11-25；执行人：叶天豪（M08）。结论：Vite 真实 API 代理、注册、登录和本人信息已打通；用户列表与动态列表尚不能完成联调。

## 分支与环境

- 原分支：`feat/m08-admin-week1`，提交 `4be6911f518329d4c7543d7434ae9fd163582945`，保持不变。
- 本地测试分支：`test/m08-m01-integration`，从原分支创建，再执行 `git rebase origin/main`；前端提交变为 `a92f820`。
- 远端 `feature-houjiankai-m01` 停留在 `5253ba9`，只有早期 `AuthDemo.java`。实际 M01 主工程已在 `origin/main` 的 `cf5ce59f4c96f4ad402ebf0dbacafa4a0f8c4874`，因此本次以该提交为基底，也带入 main 已有内容。
- rebase 冲突仅涉及 `specs/tasks.md`，保留 M01、M08 两方进度及历史交接记录。没有修改服务端源码。测试分支保留在本地；随后从该基底建立 `merge/m08-admin-web`，整理 Web 工程、文档与证据用于合入 main。
- Node.js 22.23.1；实际运行 Java 25.0.4，Maven 按工程配置 `release 21` 编译；Maven Wrapper 3.9.9；独立 MySQL 8.4.13。
- Vite：`http://127.0.0.1:5173`，`development` 模式；Spring Boot：`http://127.0.0.1:8080`；测试 MySQL：`127.0.0.1:13308`，库名 `m08_integration`。使用独立临时数据目录和随机密钥，未连接已有数据库。

## 真实请求结果

请求链：前端 `/api/v1/...` → Vite `5173` 的 `/api` 代理 → Spring Boot `8080` → 测试 MySQL。路径不重写，POST JSON 和 Bearer 随请求转发。

| 操作 | 实际结果 | 判断 |
| --- | --- | --- |
| 经 Vite 注册临时账号 | `201 / OK`，初始角色均为 USER | 真实请求体与注册接口可用 |
| 经 Vite 登录普通用户、测试管理员 | `200 / OK`，返回令牌、有效期和用户信息 | 两类真实登录响应能被现有前端解析 |
| 登录后经 Vite 获取 `/users/me` | `200 / OK` | Bearer 转发正常；普通用户的直接请求与代理响应完全一致 |
| 无令牌、无效令牌、错误密码 | `401 / UNAUTHENTICATED` | 无令牌的直接请求与代理响应完全一致；错误密码由前端正确报告 |
| USER 请求 `/admin/users`、`/admin/posts` | `403 / FORBIDDEN` | 服务端拒绝越权，前端普通用户不建立管理员会话 |
| ADMIN 请求 `/admin/users`、`/admin/posts` | `404`，默认错误响应无 `code` | 当前主工程没有相应端点，前端报告 `INVALID_RESPONSE`，没有列表成功结论 |
| 恢复前端管理员会话 | 重新调用真实 `/users/me`，`200` | 现有认证状态复核成功 |

管理员测试账号是通过 API 注册后，仅在本次独立测试库把该临时账号的 `role` 更新为 `ADMIN`，再重新登录得到真实令牌。这个步骤只是测试数据准备；后台尚未实现管理员初始化。账号密码与令牌未写入仓库。

## 验证与复验

| 检查 | 实际结果 |
| --- | --- |
| 后台 `sh ./mvnw -B -ntp -Dmaven.repo.local=<临时 Maven 缓存> verify` | 9 个测试，0 失败、0 错误、0 跳过；BUILD SUCCESS |
| 前端 `npm test` | 4 个测试文件、25 个测试全部通过；包括原有 demo DOM 集成 |
| 前端 `npm run build` | 严格类型检查和生产构建通过 |
| 前端 `npm run lint`、`npm run format:check` | 通过 |
| 真实 HTTP 检查 | 15 次状态检查符合实测预期；其中 2 次预期 404 用于确认缺失接口，不代表列表功能通过 |
| 现有前端模块真实联调 | Vite SSR 加载实际 `services.ts`、请求层和认证状态；内存 Storage、真实 HTTP；6 组断言通过 |

脱敏实测汇总见 [results.json](./results.json)，前端回归结果见 [frontend-tests.json](./frontend-tests.json)（仅将本机测试文件路径替换为仓库路径），合入检查见 [merge-checks.json](./merge-checks.json)。真实模块测试使用临时脚本，不增加生产代码或默认测试对后台的依赖。该测试覆盖请求与状态逻辑，没有执行浏览器视觉验收或浏览器刷新导航验收。

Flyway 对 MySQL 8.4 提示其已测试版本范围较旧；本次用户表迁移、JPA 校验和 9 项后台测试实际成功。该结果仅对应上述本地版本组合。

## 当前限制与复测

M01 目前实现注册、登录和本人信息；用户管理端点未实现。M02 动态模块虽已存在于仓库，其依赖和配置尚未接入本次启动的后端主工程，因此 `/admin/posts` 未注册。管理员初始化、用户列表及动态模块接入由对应后台负责人继续完成。

复测时按 [后台运行说明](../../../../src/backend/README.md) 配置独立数据库与 JWT 密钥并启动 `8080`，在 `src/admin-web/` 执行 `npm run dev`，保持 `ADMIN_API_PROXY_TARGET=http://127.0.0.1:8080`、`VITE_ADMIN_API_BASE_URL=/api/v1`。访问 `http://127.0.0.1:5173/login`，使用后台提供的管理员账号；本次临时账号保存在本机私有临时文件中，未作为正式账号初始化方案交付。

本阶段为本地技术验证，尚未获得对接成员验收；用户查询、动态查询、禁用、删除、改密和小程序关联流程保持未完成。
