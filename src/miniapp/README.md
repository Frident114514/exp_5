# 多媒体朋友圈小程序 · M06 第一周

本工程完成 M06 第一周的纯文本动态发布、我的动态分页列表、本人编辑和删除流程。运行时默认调用真实后端；Mock 服务只用于显式离线测试。

## 第一周范围

- `POST /api/v1/posts`：提交 `text`、`mediaIds: []`、`tags: []`。
- `GET /api/v1/users/me/posts`：读取本人动态，页码从 1 开始。
- `PATCH /api/v1/posts/{postId}`：第一周只提交 `text`，省略媒体和标签字段。
- `DELETE /api/v1/posts/{postId}`：删除前显示确认。
- 已实现加载、空列表、失败、重试、提交中、提交成功和提交失败状态。
- 图片、视频、上传进度和标签输入属于第二周，不在当前范围。

## 页面入口

| 页面 | 路径 | 参数 |
| --- | --- | --- |
| 我的动态 | `/pages/my-posts/index` | 无 |
| 发布动态 | `/pages/publish/index` | 无 |
| 编辑动态 | `/pages/edit/index` | `id`：动态 ID |

## 包管理器约定

本小程序工程统一使用 **pnpm**，以 `pnpm-lock.yaml` 为唯一锁文件：

```powershell
pnpm install
```

不要在本目录创建或提交 `package-lock.json`；修改依赖后使用 pnpm 更新 `pnpm-lock.yaml`。管理后台 `src/admin-web` 是独立工程，继续使用 npm 和它自己的 `package-lock.json`。
## 登录态与真实接口

真实模式通过 `src/api/session.ts` 读取 M05 登录态。M05 登录成功后应调用：

```ts
saveAuthSession({ accessToken, expiresAt, user })
```

统一存储键为 `moments.auth.session`，同时兼容旧格式的 token/user 分键。真实请求会自动附加 `Authorization: Bearer <accessToken>`，并判断 HTTP 状态与 `{code,data,message}` 响应信封。
只有显式设置 `VITE_MINIAPP_USE_MOCK=true` 时才启用本地 Mock。
## 本地运行

要求 Node.js 20 或 22 LTS；当前代码已使用 Node.js 24 完成类型检查。

```powershell
cd src/miniapp
pnpm install
pnpm dev:h5
```

微信小程序构建：

```powershell
pnpm build:mp-weixin
```

构建产物位于 `dist/build/mp-weixin`，使用微信开发者工具导入该目录。本地接口联调时，在开发者工具中关闭合法域名校验。

## 测试与检查

```powershell
pnpm test
pnpm typecheck
```

`tests/` 使用 Vitest 验证正文规则和 Mock API 契约。`tests-node/` 是当前 Codex 环境无法启动 Vitest 子进程时使用的等价 Node 回归脚本，可由 TypeScript 编译后直接执行。

## 与 M05 的交接

- 页面、API 类型、Mock 服务和第一周业务逻辑可复用。
- `package.json`、入口、路由和当前局部样式是 M06 为独立开发临时建立的基础文件。
- M05 合入正式 uni-app 工程后，应由 M05 统一维护请求封装、登录状态、401 处理和路由表；M06 页面迁入其目录，并把 `runtime.ts` 中的 Mock 实例替换为真实 `PostApi` 适配器。
- `httpPostApi.ts` 已按契约预留真实接口调用层，等待注入 M05 的统一 HTTP 传输实现。