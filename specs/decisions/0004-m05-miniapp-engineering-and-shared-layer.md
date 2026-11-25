# 0004 M05 小程序工程组织、公共层与状态管理

- 日期：2026-11-25
- 状态：已决定（M05-S1 已落地工程与公共层；账号页面和请求层在 M05-S2 按此实现）
- 参与人：成员 05（实现）；对接成员 06、成员 07（页面开发）、成员 01（认证接口）
- 背景与问题：仓库中 `src/miniapp/` 只有 `.gitkeep`，而 M06/M07 的账号、发布、广场页面都要落在同一个小程序工程里。三人并行开发需要先解决三件事：工程怎么建、公共文件谁改、页面之间共享的样式/请求/登录态怎么组织。当前没有可用的真实认证接口，只有 `specs/contracts/api.md` 的约定。

## 候选方案

1. **HBuilderX 图形化工程**：按 DCloud 默认方式建项目，用 HBuilderX 运行到微信开发者工具。
2. **uni-app CLI（Vite）+ npm 脚本工程**：`@dcloudio/vite-plugin-uni` 提供构建，`npm run dev:mp-weixin` / `build:mp-weixin` 由命令行完成，产物目录直接导入微信开发者工具。
3. **状态管理**：Pinia（官方推荐）或在 `src/stores/` 用 Vue `reactive` 写轻量单例，配合 `uni.setStorageSync` 持久化。
4. **样式体系**：`uni.scss` 设计令牌（自动注入所有页面）或页面内 CSS 变量 / 各页自定义。

## 最终选择与原因

采用方案 2 + 方案 3 的轻量 store + `uni.scss` 令牌：

- **CLI 工程**：构建、类型检查和单元测试都能用 npm 脚本执行并留下日志，符合本项目"每个阶段有可复现命令和证据"的协作要求；不依赖图形工具，M06/M07 拉取代码后 `npm install` 即可开发。上游版本固定在 `@dcloudio/* 3.0.0-5030420261029002`、`vite 5.2.8`，与官方 `uni-preset-vue#vite-ts` 模板一致，避免自行选版踩兼容问题。
- **轻量 store 而非 Pinia**：登录态只需保存令牌、过期时间和用户资料，用 `reactive` 单例 + `uni.setStorageSync` 约 40 行即可，少一层依赖；`api/auth.ts` 已固定缓存键 `moments.auth.v1`，将来若换 Pinia 只需替换 store 实现，页面调用方式不变。此选择在 S2 落地，届时更新本文档状态。
- **`uni.scss` 令牌**：uni-app 会把该文件自动注入每个页面的 `<style lang="scss">`，页面无需 import 即可使用 `$brand-primary` 等变量，这是让三个成员界面一致的最低成本做法；CSS 变量方案在微信小程序基础库上兼容性虽好，但需要每个页面显式引用根节点，反而更容易漏。
- **路由双重登记**：`config/routes.ts` 提供带类型的路径与参数，`pages.json` 负责真正编译。两者用单测校验一致性（41 项测试中的 12 项），防止"改了路由表忘记改 pages.json"导致跳转失败。公共文件集中到 M05，M06/M07 只写自己的页面目录，避免三人同时改同一文件。

## 对代码、接口、测试和交付的影响

- 新增 `src/miniapp/` 工程（uni-app + Vue 3 + TS），公共层为 `config/`、`api/`、`utils/`、`components/`、`uni.scss`、`styles/base.scss`，约定见 [M05 小程序实施约定](../contracts/miniapp-module.md)。
- 认证接口只登记路径、方法与出入参类型，请求执行封装留给 M05-S2；`oldPassword`/`newPassword` 字段名与账号密码规则属于待 M01 确认项，已在约定文档第 9 节列出。
- 测试分两层：Vitest 覆盖纯逻辑（校验规则、地址拼接、路由一致性、契约常量），页面行为必须在微信开发者工具手工验证并记录；本机未安装该工具，S1 只证明可构建，不宣称页面已运行通过。
- 交付方面，`package.json` 锁定的依赖版本、运行命令与已知限制写入 [小程序 README](../../src/miniapp/README.md)，供 M03 汇总运行说明、M08 合入报告。
