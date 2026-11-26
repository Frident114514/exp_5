# M05 测试证据

范围：小程序工程可构建性、公共层与账号模块的纯逻辑（配置/路由/校验/请求层/登录态/守卫/模拟接口）与类型安全。执行者：成员 05。环境：Windows 11、Node v24.21.0、npm 11.19.0、uni-app 3.0.0-5030420261029002、Vite 5.2.8、Vue 3.4、TypeScript 4.9.4、Vitest 2.1.9。

页面交互（真实点击、跳转、开发者工具内表现）尚未执行，见各阶段"尚未执行的验收"。

## M05-S2 实际结果（2026-11-26）

| 执行 | 实际结果 | 证据 |
| --- | --- | --- |
| 单元测试 | 9 个测试文件、**91 项全部通过**（0 失败 / 0 跳过）：校验 12、地址拼接 9、路由一致性 12、接口路径 4、契约常量 8、请求层 13、登录态 10、登录守卫 12、模拟接口 11 | [S2 验证日志](2026-11-26-verify.log)、[JUnit XML](junit-results-s2.xml) |
| 类型检查 | `vue-tsc --noEmit` 无输出、退出码 0 | [S2 验证日志](2026-11-26-verify.log) |
| 微信小程序构建 | `build -p mp-weixin` 成功，退出码 0 | [S2 验证日志](2026-11-26-verify.log) |
| H5 构建 | `build` 成功，退出码 0 | [S2 验证日志](2026-11-26-verify.log) |

S2 覆盖的功能点：

| 功能点 | 测试文件 | 结论与边界 |
| --- | --- | --- |
| 请求成功路径 | `http.spec.ts` | 只返回 `data`、拼接基础地址与编码查询串、自动带 `Bearer` 令牌、登录接口 `withAuth:false` 不带令牌、POST 自动带 JSON 头 |
| 请求失败路径 | `http.spec.ts` | 服务端 `code`/`message` 原样抛出；无 message 时按状态码兜底；网络异常归一为 `status 0 / NETWORK_ERROR` |
| 401 处理 | `http.spec.ts` | 带令牌的 401 会清空登录态并跳登录页（带回跳地址）；已在登录页不重复跳；登录接口自身 401 不跳转；未登录 401 只清理不跳转 |
| 登录态 | `auth-store.spec.ts` | 写入/缓存、重启恢复、过期会话丢弃、损坏缓存不抛异常、`clear()` 清理、`setUser` 不改令牌、未登录 `setUser` 不造登录态、按时间边界判断过期、管理员识别 |
| 登录守卫与回跳 | `navigate.spec.ts` | 站内路径白名单（拒绝外链/`//`/缺前导斜杠/锚点）、登录后 `reLaunch` 回原页、非法 redirect 退回广场、未登录跳登录页并编码当前页、已登录不跳转、登录页不重复压栈、参数解码容错 |
| 接口路径契约 | `api-endpoints.spec.ts` | 动态/媒体/互动共 10 个端点与 `api.md` 一致；`postId` 非法时发请求前抛错 |
| 模拟接口 | `mock.spec.ts` | demo 登录、错误密码 401、`/users/me` 令牌校验、改密原密码错误 400、动态列表分页/标签筛选/北京时间日期筛选、详情 404、未登录发布 401、空内容 400 |

## 与个人必做用例的对应（部分覆盖）

| 用例 | 当前状态 | 说明 |
| --- | --- | --- |
| M05-C01 注册与登录 | 未执行 | 需要真实 `POST /auth/*` 接口与开发者工具内操作；模拟接口只证明前端流程，不能代替 |
| M05-C02 登录态 | 部分通过 | 有效/过期/缺失令牌与 401 清理跳转由 `http.spec.ts`、`auth-store.spec.ts` 覆盖；受保护页面的实际拦截待开发者工具验证 |
| M05-C03 修改密码 | 部分通过 | 新旧密码规则、改密后清理登录态、原密码错误提示的代码路径已实现并有校验/登录态单测；真实接口与"旧令牌再访问"待联调 |
| M05-C04 返回原流程 | 部分通过 | redirect 生成、安全校验、登录后回跳由 `navigate.spec.ts` 覆盖；页面实操与输入保留待验证 |
| M05-C05 请求异常 | 部分通过 | 服务端错误/网络异常映射与按钮防重复提交已实现并单测；断网实操待验证 |
| M05-C06 公共页面回归 | 未执行 | M06/M07 页面仍为占位页，无可回归内容 |

## M05-S1 实际结果（2026-11-25）

| 执行 | 实际结果 | 证据 |
| --- | --- | --- |
| 单元测试 | 4 个测试文件、**41 项全部通过**（0 失败 / 0 跳过）：校验规则 12、地址拼接 9、路由一致性 12、契约常量 8 | [S1 验证日志](2026-11-25-verify.log)、[JUnit XML](junit-results.xml) |
| 类型检查 | `vue-tsc --noEmit` 无输出、退出码 0 | [S1 验证日志](2026-11-25-verify.log) |
| 微信小程序构建 | `build -p mp-weixin` 成功，退出码 0；产物 `dist/build/mp-weixin` 含 `app.json`，注册 9 个页面（home、login、register、profile、password、plaza、post-detail、publish、my-posts） | [S1 验证日志](2026-11-25-verify.log) |
| H5 构建 | `build` 成功，退出码 0，用于快速核对样式 | [S1 验证日志](2026-11-25-verify.log) |

构建日志中的 `The CJS build of Vite's Node API is deprecated` 是 Vite 5 对 CJS 入口的提示，不影响产物；Sass `mixed-decls` 警告已在修复项 4 中消除。

## S1 覆盖的功能点

| 功能点 | 覆盖方式 | 结论与边界 |
| --- | --- | --- |
| 表单校验规则 | `tests/validate.spec.ts`：账号 4–20 位、密码 8–32 位含字母数字、确认密码、昵称按 Unicode 码点计数、改密新旧密码不得相同、空白视为未填 | 规则与 `specs/contracts/miniapp-module.md` 第 5 节一致；服务端更严格时以服务端提示为准（待 M01 确认 Q2） |
| 服务端地址拼接 | `tests/env.spec.ts`：末尾斜杠归一化、空白回退默认值、`buildApiUrl`、`buildMediaUrl`、`absoluteMediaUrl` 不重复拼 `/api/v1` | 覆盖了实际发现的前缀重复缺陷（见修复项 2） |
| 路由登记一致性 | `tests/routes.spec.ts`：`config/routes.ts` 与 `src/pages.json` 双向一一对应、导航标题一致、负责人取值、登录要求、必填参数缺失报错、可选参数为空不拼接、中文参数编码 | 新增页面只改一边会导致本组用例失败 |
| 认证契约常量 | `tests/auth-contract.spec.ts`：4 个端点路径与 `api.md` U01 行一致、方法与权限标记、`/api/v1` 前缀、令牌 12 小时、7 个错误码、分页默认值与上限 | 只校验小程序端登记与契约文档一致，不代表服务端已实现 |

## 修复记录（S1 过程中发现）

S1 过程中实际遇到并修复的问题，均为本人代码问题，保留过程以便复现：

1. **块注释提前闭合**：`PlaceholderPage.vue` 注释里写 `` `pages/**/index.vue` ``，其中 `**/` 终止了 `/** */` 注释，导致 `vue-tsc` 报 `TS1443`、`build:mp-weixin` 解析失败。改为 `` `pages/<页面>/index.vue` ``。
2. **模板属性内的英文引号**：`pages/register/index.vue` 的 `:todos="[...]"` 里出现 `"账号已被注册"`，双引号提前结束属性值，编译报 `Unterminated string constant`。改用中文书名号「」。
3. **事件参数类型不匹配**：`FormField` 的 `@input`/`@blur` 处理函数声明为 `{ detail: { value: string } }`，与 uni-app 组件声明的 `Event` 不兼容（`TS2322`）。改为接收 `Event` 后从 `detail.value` 安全取值。
4. **测试配置缺 Node 类型**：`vitest.config.ts` 使用 `node:url` 报 `TS2307`。补充 `@types/node` 开发依赖并在 `tsconfig.json` 的 `types` 中加入 `node`。
5. **Sass mixed-decls 警告**：`button-reset` mixin 内含嵌套的 `&::after`，其后又输出声明，Sass 1.77 提示未来行为变更。把 `&::after` 重置移到组件样式块末尾，构建设告警消除。
6. **小程序端占位文字样式不生效**：`FormField` 用 `placeholder-class="form-field__placeholder"`，但该样式写在 scoped 块内，编译后选择器带 `.data-v-*` 后缀，而微信给占位节点加的是裸类名，令牌颜色静默回退为平台默认灰——构建与单测都不会报错，是检查 `dist/build/mp-weixin` 产物时发现的。改为全局类 `.form-placeholder`（`styles/base.scss`），并在 `app.wxss` 中确认输出 `.form-placeholder{color:#c0c4cc}`、`FormField.wxml` 的 `placeholder-class` 为裸类名。

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

## 工具内运行记录（微信开发者工具，2026-11-25）

S1 期间尝试用 CLI 导入被工具以"不存在此 AppID"拒绝（无 AppID 模式只能走图形界面），随后**由组员在图形界面手动导入** `src/miniapp/dist/build/mp-weixin` 并成功运行。工具日志可证：

- `WeappLog/logs/2026-11-25-16-36-20-881.log`：`DevtoolsFileWatcher] d:/project-softweare/exp_5/src/miniapp/dist/build/mp-weixin` —— 工程在工具中被打开；
- `WeappLog/logs/2026-11-25-23-57-06-969.log`：编译缓存与模拟器日志引用了我们全部 9 个页面产物（`pages/{home,login,register,profile,password,plaza,post-detail,publish,my-posts}/index.json`），并有 `[SimulatorService] simulator app compile`，该日志中没有我们页面的编译错误（出现的 ERROR 均为工具内部 `backendManager`/`BackendInitEnv` 消息）。

边界说明：

- 那次运行的是 **S1 代码**（账号页当时是占位页）；S2 代码于 2026-11-26 13:05 重新构建，**尚未在工具内复验**。
- 没有截图，因此不计入"界面已验收"；工具日志只能证明工程被打开、页面被编译、模拟器启动。

## 尚未执行的验收

- **S2 页面在开发者工具内的实际操作与截图**：S1 时组员已导入并运行过（见上节），但 S2 的登录/注册/资料/改密页面尚未在工具内点过，也没有截图。
- **真实接口联调**：M01 的认证、M02 的动态、M03 的媒体、M04 的评分评论都还没跑过；当前只有契约、类型、模拟数据与单元测试。账号流程的真实注册→登录→改密→重新登录待 S4 进行。
- **真机预览**：需要可用 AppID 与局域网/HTTPS 服务地址，待 M01 认证服务启动后与 S4 联调一起做。
- **账号流程用例 M05-C01～C06**：C02/C03/C04/C05 已有单元测试覆盖对应逻辑（见上表"部分覆盖"），但页面级实操未执行；C01、C06 完全未执行。
- **与 M06/M07 的联合验收**：另外两位成员尚未开始页面实现，跨页面回归待其提交后进行。

## 复现命令（S2 全量）

```sh
cd src/miniapp
npm install
npm run test        # 91 项
npm run type-check
npm run build:mp-weixin
npm run build:h5
```

模拟接口模式下另可手工验证页面流程：`.env.local` 设 `VITE_MINIAPP_USE_MOCK=true`，用 `demo/demo1234` 登录。模拟结果不计入本证据的"通过"结论。
