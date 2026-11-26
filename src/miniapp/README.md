# M05 用户小程序工程（uni-app + Vue 3 + TypeScript）

成员 05 维护。工程位置 `src/miniapp/`，构建微信小程序，同时保留 H5 预览用于快速验证样式。

## 当前状态（M05-S2，2026-11-26）

已完成并可运行：

- **工程与样式**：uni-app CLI（Vite 5 + Vue 3 + TypeScript），`build:mp-weixin` 与 `build:h5` 构建成功；设计令牌（颜色/字号/间距/圆角/阴影）与全局基础样式。
- **公共组件**：`AppButton`（含防重复提交）、`FormField`（标签/错误/密码显隐）、`StateView`（加载/空/错误/正常）、`PlaceholderPage`。
- **请求层**：`api/http.ts` 统一拼接地址、附加令牌、解析 `{code,data,message}`、抛 `ApiError`、401 清理登录态并回跳登录页、网络异常归一。
- **登录态**：`stores/auth.ts` 缓存令牌与用户资料（`moments.auth.v1`），支持启动恢复、过期判断、角色识别、退出清理。
- **账号页面**：注册、登录、个人资料、修改密码（表单校验、加载态、错误提示、登录后回原页面、改密后强制重新登录）。
- **业务接口模块（给 M06/M07 用）**：`api/posts.ts`、`api/media.ts`、`api/interactions.ts`，含完整类型与端点登记。
- **本地模拟接口**：`api/mock.ts`，服务端未就绪时可开关切换到样例数据。
- **单元测试**：91 项（9 个文件）全部通过。

**尚未完成（不要当成已有能力）：**

- 与 M01/M02/M03/M04 的**真实接口联调**尚未进行：目前只有契约、类型、模拟数据与单元测试，没有跑过真实服务端。
- **微信开发者工具内的实际运行与截图**未做（本机未安装该工具），只验证到构建产物生成。
- M06/M07 的页面（广场、详情、发布、我的动态）仍是占位页，由对应成员实现。
- 没有 tabBar；`manifest.json` 的 `mp-weixin.appid` 为空。

## 目录

```text
miniapp/
├── package.json / vite.config.ts / vitest.config.ts / tsconfig.json
├── index.html                       # H5 入口
├── .env.example                     # 本端环境变量样例
├── src/
│   ├── main.ts / App.vue            # App.vue 启动时恢复登录态
│   ├── pages.json                   # 页面注册（与 config/routes.ts 一一对应）
│   ├── manifest.json               # 小程序 appid 与编译配置
│   ├── uni.scss                     # 设计令牌与 mixin（自动注入每个页面）
│   ├── styles/base.scss             # 全局元素样式与工具类
│   ├── config/
│   │   ├── env.ts                   # 服务端地址、媒体地址、mock 开关
│   │   └── routes.ts                # 路由注册表（路径、标题、负责人、参数、登录要求）
│   ├── api/
│   │   ├── http.ts                  # 统一请求：令牌、响应解析、401、错误归一
│   │   ├── types.ts                 # 响应外壳、错误码、账号/动态/媒体/评论/评分类型
│   │   ├── auth.ts                  # 注册、登录、本人资料、改密
│   │   ├── posts.ts                 # 动态列表/详情/增删改、本人动态
│   │   ├── media.ts                 # 上传、媒体地址
│   │   ├── interactions.ts          # 评分、评论
│   │   └── mock.ts                  # 本地模拟接口（开关控制）
│   ├── stores/auth.ts               # 登录态（缓存、过期、角色、清理）
│   ├── utils/
│   │   ├── validate.ts              # 表单校验规则（纯函数）
│   │   ├── feedback.ts              # toast / loading / 确认框
│   │   └── navigate.ts              # 跳转、登录守卫、回跳安全校验
│   ├── components/                  # AppButton / FormField / StateView / PlaceholderPage
│   └── pages/
│       ├── home/index.vue           # 产品首页：登录状态 + 广场/发布/我的动态/个人资料入口
│       ├── devkit/index.vue         # 开发参考：设计令牌、组件、校验、页面四态、路由表
│       ├── login|register|profile|password/   # M05 账号页（已实现）
│       ├── plaza|post-detail/       # M07 浏览与互动（占位）
│       └── publish|my-posts/        # M06 发布与我的动态（占位）
└── tests/                           # Vitest（91 项）
```

## 运行命令

需要 Node.js 20 或 22 LTS（开发机实测 Node 24.21.0 也可构建），首次执行：

```sh
cd src/miniapp
npm install
```

| 命令 | 作用 |
| --- | --- |
| `npm run dev:mp-weixin` | 开发模式编译微信小程序，输出到 `dist/dev/mp-weixin`（带 watch） |
| `npm run dev:h5` | 浏览器预览，默认 `http://localhost:5173`，只用于看样式 |
| `npm run build:mp-weixin` | 生产构建，输出到 `dist/build/mp-weixin` |
| `npm run type-check` | `vue-tsc --noEmit` 类型检查 |
| `npm run test` | Vitest 单元测试（91 项） |

### 演示账号（仅模拟接口模式）

本机 `src/miniapp/.env.local`（已在 `.gitignore` 中，不会提交）已把 `VITE_MINIAPP_USE_MOCK` 设为 `true`，重新编译即可直接用下面账号登录；换真实服务端时改为 `false` 后重新编译。

`VITE_MINIAPP_USE_MOCK=true` 时可用：

| 账号 | 密码 | 角色 |
| --- | --- | --- |
| `demo` | `demo1234` | 普通用户 |
| `admin` | `admin1234` | 管理员 |

真实服务端就绪后关闭开关，用数据库里实际注册的账号登录。

## 接口层怎么用（M06/M07 必读）

完整约定见 [M05 小程序实施约定](../../specs/contracts/miniapp-module.md) 第 8 节。四条底线：

1. **不要直接 `uni.request`**，用 `api/` 里的函数；它已处理令牌、响应外壳、错误与 401。
2. **错误统一按 `ApiError` 处理**：`catch (e) { if (isApiError(e)) toastError(e.message) }`，`message` 可直接展示。
3. **需要登录的入口先 `requireLogin()`**，它会把当前页作为 `redirect` 传给登录页，登录后自动回跳；公开页面不要调用。
4. **样式只用 `uni.scss` 变量与全局工具类**，提交按钮用 `<AppButton :loading="xxx">`，列表状态用 `<StateView>`。

```ts
import { listPosts, getPost } from '@/api/posts'
import { uploadMedia } from '@/api/media'
import { ratePost, listComments, createComment } from '@/api/interactions'
import { createPost, updatePost, deletePost, listMyPosts } from '@/api/posts'
import { requireLogin } from '@/utils/navigate'
import { isApiError } from '@/api/http'
import { toastError } from '@/utils/feedback'

// 广场（公开）
const page = await listPosts({ page: 1, pageSize: 10, tag: '校园' })

// 发布（需登录）
if (requireLogin()) {
  const media = await uploadMedia(tempFilePath)
  await createPost({ text: '周末的校园', mediaIds: [media.mediaId], tags: ['校园'] })
}

// 出错时
try {
  await getPost(9999)
} catch (error) {
  toastError(isApiError(error) ? error.message : '加载失败')
}
```

模拟接口模式：复制 `.env.example` 为 `.env.local`，把 `VITE_MINIAPP_USE_MOCK` 改为 `true` 后重新编译。模拟数据在内存中、媒体没有真实文件，只用于调页面。

## 导入微信开发者工具

1. 先执行 `npm run dev:mp-weixin`（或 `build:mp-weixin`）。
2. 微信开发者工具 → 导入项目 → 目录选择 `src/miniapp/dist/dev/mp-weixin`（构建产物用 `dist/build/mp-weixin`）。
3. AppID 填自己的小程序 AppID；没有时选「无 AppID」或「测试号」。仓库里 `manifest.json` 的 `mp-weixin.appid` 为空，构建产物会回退为 `touristappid`，**CLI 导入会被工具以"不存在此 AppID"拒绝，无 AppID 模式只能在图形界面选择**。
4. 在"详情 → 本地设置"勾选**不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书**，否则无法访问本机 `http://127.0.0.1:8080`。
5. 首屏是产品首页（`pages/home/index`）：上方显示登录状态，下面是广场、发布动态、我的动态、个人资料四个入口；组件与样式基线示例在 `pages/devkit/index`（首页底部"组件与样式基线"入口）。点「登录」用 `demo / demo1234` 可走完整登录 → 个人资料 → 修改密码流程。

`dist/` 已被 `.gitignore` 忽略，不提交构建产物。

## 新增页面流程

① 在 `config/routes.ts` 登记（路径/标题/负责人/登录要求/参数）；② 在 `src/pages.json` 增加同路径页面项与 `navigationBarTitleText`；③ 新建 `src/pages/<页面>/index.vue`；④ `npm run test` 确认一致性用例通过；⑤ 新增依赖交给 M05 合入 `package.json`。`package.json`、`pages.json`、`uni.scss`、`config/`、`api/`、`utils/` 由 M05 统一合入。

## 已知限制

- 本机未安装微信开发者工具：`build:mp-weixin` 产物已验证可生成，但**未在工具内实际运行**，也未截图；该项待有工具的环境复验。
- 真实接口未联调：`api/` 的路径与字段来自 `specs/contracts/api.md`，服务端实际实现可能仍需对齐（见契约文档第 9 节 Q1–Q8，其中 Q7 是 `PATCH` 在小程序端的可用性风险）。
- 未做 tabBar：底部导航需要图标资源，等 M07 定下广场入口后再统一加。
- `manifest.json` 中 `appid` 为空，正式演示前需填入课程申请的 AppID 或使用游客模式。
