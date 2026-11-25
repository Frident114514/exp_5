# M05 测试证据（2026-11-25，M05-S1）

范围：小程序工程可构建性、公共层纯逻辑（设计令牌以外的配置/路由/校验/契约常量）与类型安全。执行者：成员 05。环境：Windows 11、Node v24.21.0、npm 11.19.0、uni-app 3.0.0-5030420261029002、Vite 5.2.8、Vue 3.4、TypeScript 4.9.4、Vitest 2.1.9。

本阶段是"设计和基础环境"，因此证据只证明**工程能构建、公共层逻辑正确**；账号页面交互（M05-C01～C06）尚未实现，保留"未执行"。

## 实际结果

| 执行 | 实际结果 | 证据 |
| --- | --- | --- |
| 单元测试 | 4 个测试文件、**41 项全部通过**（0 失败 / 0 跳过）：校验规则 12、地址拼接 9、路由一致性 12、契约常量 8 | [验证日志](2026-11-25-verify.log)、[JUnit XML](junit-results.xml) |
| 类型检查 | `vue-tsc --noEmit` 无输出、退出码 0 | [验证日志](2026-11-25-verify.log) |
| 微信小程序构建 | `build -p mp-weixin` 成功，退出码 0；产物 `dist/build/mp-weixin` 含 `app.json`，注册 9 个页面（home、login、register、profile、password、plaza、post-detail、publish、my-posts） | [验证日志](2026-11-25-verify.log) |
| H5 构建 | `build` 成功，退出码 0，用于快速核对样式 | [验证日志](2026-11-25-verify.log) |

构建日志中的 `The CJS build of Vite's Node API is deprecated` 是 Vite 5 对 CJS 入口的提示，不影响产物；Sass `mixed-decls` 警告已在修复项 4 中消除。

## 覆盖的功能点

| 功能点 | 覆盖方式 | 结论与边界 |
| --- | --- | --- |
| 表单校验规则 | `tests/validate.spec.ts`：账号 4–20 位、密码 8–32 位含字母数字、确认密码、昵称按 Unicode 码点计数、改密新旧密码不得相同、空白视为未填 | 规则与 `specs/contracts/miniapp-module.md` 第 5 节一致；服务端更严格时以服务端提示为准（待 M01 确认 Q2） |
| 服务端地址拼接 | `tests/env.spec.ts`：末尾斜杠归一化、空白回退默认值、`buildApiUrl`、`buildMediaUrl`、`absoluteMediaUrl` 不重复拼 `/api/v1` | 覆盖了实际发现的前缀重复缺陷（见修复项 2） |
| 路由登记一致性 | `tests/routes.spec.ts`：`config/routes.ts` 与 `src/pages.json` 双向一一对应、导航标题一致、负责人取值、登录要求、必填参数缺失报错、可选参数为空不拼接、中文参数编码 | 新增页面只改一边会导致本组用例失败 |
| 认证契约常量 | `tests/auth-contract.spec.ts`：4 个端点路径与 `api.md` U01 行一致、方法与权限标记、`/api/v1` 前缀、令牌 12 小时、7 个错误码、分页默认值与上限 | 只校验小程序端登记与契约文档一致，不代表服务端已实现 |

## 首次失败与修复记录

S1 过程中实际遇到并修复的问题，均为本人代码问题，保留过程以便复现：

1. **块注释提前闭合**：`PlaceholderPage.vue` 注释里写 `` `pages/**/index.vue` ``，其中 `**/` 终止了 `/** */` 注释，导致 `vue-tsc` 报 `TS1443`、`build:mp-weixin` 解析失败。改为 `` `pages/<页面>/index.vue` ``。
2. **模板属性内的英文引号**：`pages/register/index.vue` 的 `:todos="[...]"` 里出现 `"账号已被注册"`，双引号提前结束属性值，编译报 `Unterminated string constant`。改用中文书名号「」。
3. **事件参数类型不匹配**：`FormField` 的 `@input`/`@blur` 处理函数声明为 `{ detail: { value: string } }`，与 uni-app 组件声明的 `Event` 不兼容（`TS2322`）。改为接收 `Event` 后从 `detail.value` 安全取值。
4. **测试配置缺 Node 类型**：`vitest.config.ts` 使用 `node:url` 报 `TS2307`。补充 `@types/node` 开发依赖并在 `tsconfig.json` 的 `types` 中加入 `node`。
5. **Sass mixed-decls 警告**：`button-reset` mixin 内含嵌套的 `&::after`，其后又输出声明，Sass 1.77 提示未来行为变更。把 `&::after` 重置移到组件样式块末尾，构建设告警消除。

以上修复后重新执行四项检查，全部退出码 0；日志为同一版本产物，未保留中间失败日志（失败信息在本节按实际输出记录）。

## 重现命令

在 `src/miniapp/` 执行：

```sh
npm install
npm run test
npm run type-check
npm run build:mp-weixin
npm run build:h5
```

工程依赖 `npm` 缓存可写；本机首次安装时 `postinstall` 无法在受限沙箱内 spawn，使用 `npm install --ignore-scripts` 完成安装（esbuild 的平台二进制由可选依赖提供，不受影响）。正常开发机直接 `npm install` 即可。

## 尚未执行的验收

- **微信开发者工具导入运行与截图**：本机未安装该工具，`build:mp-weixin` 产物已生成但未在工具内打开，故 S1 的"在微信开发者工具导入运行"一项保留未完成，步骤已写入 [小程序 README](../../../src/miniapp/README.md)。
- **真机预览**：需要可用 AppID 与局域网/HTTPS 服务地址，待 M01 认证服务启动后与 S4 联调一起做。
- **账号流程用例 M05-C01～C06**：注册/登录、登录态、改密、返回原流程、请求异常、公共页面回归均依赖 M05-S2 的页面与请求层，当前保持"未执行"。
- **与 M06/M07 的联合验收**：另外两位成员尚未开始页面实现，跨页面回归待其提交后进行。
