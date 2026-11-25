# M05 用户小程序工程（uni-app + Vue 3 + TypeScript）

成员 05 维护。工程位置 `src/miniapp/`，构建微信小程序，同时保留 H5 预览用于快速验证样式。

## 当前状态（M05-S1，2026-11-25）

已完成并可运行：

- uni-app CLI 工程（Vite 5 + Vue 3 + TypeScript），`build:mp-weixin` 与 `build:h5` 均构建成功，类型检查通过。
- 设计令牌（颜色、字号、间距、圆角、阴影、控件高度）与全局基础样式。
- 公共组件 `AppButton`、`FormField`、`StateView`，以及占位页组件 `PlaceholderPage`。
- 表单校验规则 `utils/validate.ts`、统一反馈 `utils/feedback.ts`、跳转封装 `utils/navigate.ts`。
- 全队路由注册表 `config/routes.ts`（M05/M06/M07 共 9 个页面）与服务端地址配置 `config/env.ts`。
- 账号认证接口登记与出入参类型 `api/types.ts`、`api/auth.ts`。
- Vitest 单元测试 41 项，全部通过。

**尚未实现（属于后续阶段，不要当成已有能力）：**

- M05-S2：注册/登录/个人资料/修改密码页面、token 携带与 401 处理、登录后返回原页面、登录态存储。
- 当前 `pages/login`、`pages/profile`、`pages/password`、`pages/register` 以及 M06/M07 的页面都是占位页。
- 真实接口联调（依赖 M01 认证服务）、微信开发者工具内的手工验收、真机验证。

## 目录

```text
miniapp/
├── package.json / vite.config.ts / vitest.config.ts / tsconfig.json
├── index.html                       # H5 入口
├── .env.example                     # 本端环境变量样例
├── src/
│   ├── main.ts / App.vue
│   ├── pages.json                   # 页面注册（与 config/routes.ts 一一对应）
│   ├── manifest.json                # 小程序 appid 与编译配置
│   ├── uni.scss                     # 设计令牌与 mixin（自动注入每个页面）
│   ├── styles/base.scss             # 全局元素样式与布局工具类
│   ├── config/
│   │   ├── env.ts                   # 服务端地址解析与 URL 拼接
│   │   └── routes.ts                # 路由注册表（路径、标题、负责人、参数、登录要求）
│   ├── api/
│   │   ├── types.ts                 # 统一响应外壳、错误码、账号对象与分页
│   │   └── auth.ts                  # 认证端点方法与路径、出入参类型、令牌常量
│   ├── utils/
│   │   ├── validate.ts              # 表单校验规则（纯函数）
│   │   ├── feedback.ts              # toast / loading / 确认框
│   │   └── navigate.ts              # 统一跳转、返回与登录引导
│   ├── components/
│   │   ├── AppButton.vue            # 按钮（含防重复提交）
│   │   ├── FormField.vue            # 输入项（标签、错误、密码显隐）
│   │   ├── StateView.vue            # 加载/空/错误/正常四态
│   │   └── PlaceholderPage.vue      # 未实现页面的统一占位
│   └── pages/
│       ├── home/index.vue           # ★ 可运行示例页：令牌、按钮、表单、状态、路由表
│       ├── login|register|profile|password/   # M05 账号页（S2 实现）
│       ├── plaza|post-detail/       # M07 浏览与互动（S2 实现）
│       └── publish|my-posts/        # M06 发布与我的动态（S2 实现）
└── tests/                           # Vitest 单元测试
```

## 运行命令

需要 Node.js 20 或 22 LTS（开发机实测 Node 24.21.0 也可构建），首次执行：

```sh
cd src/miniapp
npm install
```

| 命令 | 作用 |
| --- | --- |
| `npm run dev:mp-weixin` | 开发模式编译微信小程序，输出到 `dist/dev/mp-weixin` |
| `npm run dev:h5` | 浏览器预览，默认 `http://localhost:5173`，只用于看样式 |
| `npm run build:mp-weixin` | 生产构建，输出到 `dist/build/mp-weixin` |
| `npm run type-check` | `vue-tsc --noEmit` 类型检查 |
| `npm run test` | Vitest 单元测试（41 项） |

### 导入微信开发者工具

1. 先执行 `npm run dev:mp-weixin`（或 `build:mp-weixin`）。
2. 微信开发者工具 → 导入项目 → 目录选择 `src/miniapp/dist/dev/mp-weixin`（构建产物用 `dist/build/mp-weixin`）。
3. AppID 填自己的小程序 AppID；没有时选择"测试号"。仓库里 `manifest.json` 的 `mp-weixin.appid` 为空，构建产物会回退为 `touristappid`。
4. 在"详情 → 本地设置"勾选**不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书**，否则无法访问本机 `http://127.0.0.1:8080`。
5. 首屏为 `pages/home/index` 示例页，可看到设计令牌、按钮、表单校验、四种页面状态和全部路由。

修改 `manifest.json` 的 `mp-weixin.appid` 后需要重新构建；`dist/` 已被 `.gitignore` 忽略，不提交构建产物。

## 公共层怎么用（M06/M07 必读）

细节约定见项目 [M05 小程序实施约定](../../specs/contracts/miniapp-module.md)，这里只列最常用的四条：

1. **样式**：颜色、字号、间距一律用 `src/uni.scss` 的变量（如 `$brand-primary`、`$space-md`），不要写死 `#2f6feb`、`32rpx`。全局工具类在 `styles/base.scss`（`.page`、`.section`、`.text-caption` 等）。
2. **按钮**：提交类操作使用 `<AppButton :loading="submitting">`，它内置防重复提交；不要自己写防抖或用原生 `button`。
3. **表单**：`<FormField v-model="form.account" :error="errors.account" @blur="validate('account')" />`，校验函数来自 `utils/validate.ts`，失焦校验、输入时清除错误。
4. **页面状态**：列表和详情统一用 `<StateView :status="state" @retry="load">`，失败必须有重试入口。

新增页面时**先登记** `config/routes.ts` 与 `src/pages.json`（两者的对应关系有单测校验），再写页面文件。`package.json`、`pages.json`、`uni.scss` 由 M05 统一合入。

## 已知限制

- 本机未安装微信开发者工具，`build:mp-weixin` 产物已验证可生成，但**未在开发者工具内实际导入运行，也未截图**；该项待有工具的环境复验。
- 未做 tabBar：底部导航需要图标资源，等 M07 定下广场入口后再统一加，避免图标缺失导致构建告警。
- 请求层（token 携带、401 清理、错误解析）与登录态存储属于 M05-S2，目前 `api/` 只有类型与端点登记。
- `manifest.json` 中 `appid` 为空，正式演示前需填入课程申请的 AppID。
