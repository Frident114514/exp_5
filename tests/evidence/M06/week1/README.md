# M06 第一周验证记录

日期：2026-11-26

范围：纯文本发布、我的动态分页、文本编辑、删除、Mock 失败与重试。真实 M02/M03 接口不在本次验证范围。

## 已执行

| 检查 | 命令或方式 | 结果 |
| --- | --- | --- |
| Vue/TypeScript 类型检查 | `node node_modules/vue-tsc/bin/vue-tsc.js --noEmit` | 通过 |
| 第一周业务与 Mock 回归 | TypeScript 编译后直接运行 `tests-node/core.test.ts` | 11 项通过 |
| SFC 结构编译 | `@vue/compiler-sfc` 逐项编译 7 个 Vue 文件 | 全部通过 |
| JSON 配置解析 | 解析 `package.json`、`pages.json`、`manifest.json` | 全部通过 |

## 未能执行

- `vitest run`：当前 Codex 沙箱禁止 Node 启动 esbuild 子进程，报 `spawn EPERM`。
- `pnpm build:mp-weixin`：同一子进程限制导致 Vite 无法加载配置；不是业务代码或类型错误。
- 微信开发者工具页面走查：需要 M05 的正式小程序基础工程或在本机普通终端完成构建后导入。

## 待真实联调

- M02 动态接口合入主后端并提供真实 JWT。
- M05 小程序公共请求、登录和路由层合入。
- M06 将 `runtime.ts` 的 Mock `PostApi` 替换为真实 HTTP 适配器，并复做 M06-C05、M06-C06。