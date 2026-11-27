# 用户小程序（M05 / M06 / M07 合并工程）

uni-app + Vue 3 + TypeScript，构建微信小程序。本目录是 M05、M06、M07 三份第一周代码合并后的**唯一小程序工程**，此前各自独立的三套脚手架已统一到这里。

## 页面与归属

| 页面 | 路径 | 负责人 | 说明 |
| --- | --- | --- | --- |
| 首页 | `pages/home/index` | M05 | 登录状态 + 广场/发布/我的动态/个人资料入口（入口页） |
| 广场 | `pages/square/index` | M07 | 公开列表、下拉刷新、分页 |
| 详情 | `pages/detail/index` | M07 | 图文/媒体占位、评分摘要、已删除状态 |
| 发布 | `pages/publish/index` | M06 | 纯文字发布 |
| 我的动态 | `pages/my-posts/index` | M06 | 本人列表、进入编辑、删除 |
| 编辑 | `pages/edit/index` | M06 | 正文编辑 |
| 登录 | `pages/login/index` | M05 | 账号密码登录、`redirect` 回跳 |
| 注册 | `pages/register/index` | M05 | 角色固定 USER |
| 个人资料 | `pages/profile/index` | M05 | 本人信息、改密、退出 |
| 修改密码 | `pages/password/index` | M05 | 改密后强制重新登录 |

路由登记在 `src/config/routes.ts`，与 `src/pages.json` 双向校验（`tests/routes.spec.ts`）。

## 安装与运行

```sh
cd src/miniapp
pnpm install
pnpm dev:mp-weixin      # 产物 dist/dev/mp-weixin，导入微信开发者工具
pnpm build:mp-weixin    # 产物 dist/build/mp-weixin
pnpm dev:h5             # 浏览器预览（仅看样式）
```

服务端地址默认 `http://127.0.0.1:8080/api/v1`。复制 `.env.local.example` 为 `.env.local` 可改 `VITE_API_BASE_URL`（也兼容 M05 的 `VITE_MINIAPP_API_BASE_URL`），改后需重启编译。

H5 未显式配置 API 地址时使用同源 `/api/v1`，Vite 开发代理默认转发至 `http://127.0.0.1:8080`；可通过 `VITE_DEV_API_TARGET` 修改代理目标。生产 H5 需配置同源反向代理或允许跨域的完整 API 地址，开发代理不会随构建产物发布。

## 校验与测试（三套入口都保留）

| 命令 | 内容 |
| --- | --- |
| `pnpm type-check` | `vue-tsc --noEmit`，覆盖 src 与 tests |
| `pnpm test` | Vitest：M05 的请求层/登录态/守卫/表单/组件 + M06 的 mock 与规则（119 项） |
| `pnpm test:node` | M06 的 node:test 契约测试（14 项，`tests-node/`） |
| `pnpm test:m07` | M07 的 node:test 测试（26 项，`node tests/index.ts`） |
| `pnpm verify` | 依次执行 type-check、test、test:node、test:m07 |

## 统一后的共享层

- `src/api/http.ts`：唯一 HTTP 出口。`request<T>()` 解开 `{code,data,message}` 抛 `ApiError`；`requestRaw()` 供两套历史封装接线（各自只解包一次）。自动带 `Authorization`、统一 401 清理与登录页回跳。
- `src/api/transport.ts`：`authTransport`（给 M07 的 `createClient`）、`envelopeTransport`（给 M06 的 `createHttpPostApi`）。
- `src/api/types.ts`：合并两套类型名（`ApiEnvelope`/`PageView`/`PostView`/`PostApi` 与 `ApiResponse`/`PageResult`/`PostSummary`/`PostDetail`/`API_ERROR_CODES`），同形状的用别名统一；`updatedAt` 必填。
- `src/api/posts.ts`：M07 的列表/详情封装，已改为走 `authTransport`，因此会自动带登录令牌。
- `src/config/env.ts`：统一服务端地址与常量（`REQUEST_TIMEOUT`、`DEFAULT_PAGE_SIZE`）。
- `src/utils/format.ts`：合并两套时间/评分格式化，`formatDateTime` 兼容空值。
- `src/stores/auth.ts`：登录态（`moments.auth.v1`），请求层按需加载，避免纯 Node 测试被 vue 依赖卡住。

## 第一周接口接线

- **M06**：发布、本人列表、编辑和删除默认通过 `envelopeTransport` 调用真实后端，与 M05/M07 共用 `moments.auth.v1` 登录态和 HTTP 请求层。仅显式设置 `VITE_MINIAPP_USE_MOCK=true` 时使用模拟数据。
- **M07**：`pages/square`、`pages/detail` 已接真实列表/详情接口；后续筛选、评分、评论按计划追加。
- 媒体（图片/视频）、标签、评分提交、评论本周不做，页面使用占位。

## 已知限制

- 微信开发者工具的导入与真机验证需在各自电脑完成；本目录只保证 `type-check`、三套测试与 `build:mp-weixin` 通过。
- `manifest.json` 的 `mp-weixin.appid` 为空，导入时用测试号或游客模式。
