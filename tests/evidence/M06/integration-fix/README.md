# M06 合并冲突与真实接口接线修复

日期：2026-11-26

依据：管理员《统一接口与相关问题》中的 M06 两项要求，以最新 `main` 的 `1fcdb38` 为基线处理。

## 本次修改

- `PostCard.vue` 改名为 `OwnPostCard.vue`，我的动态页同步修改引用；M07 广场继续使用独立的 `FeedPostCard.vue`。
- `runtime.ts` 默认使用真实 `PostApi`，不再固定返回 Mock；只有显式设置 `VITE_MINIAPP_USE_MOCK=true` 才切回 Mock。
- 真实请求读取 M05 登录态并附加 `Authorization: Bearer <accessToken>`，统一判断 HTTP 状态和 `{code,data,message}` 响应信封。
- 新增 `session.ts`：统一会话键为 `moments.auth.session`，同时兼容 token/user 分键；只有 token 时通过 `GET /users/me` 补取用户。
- 我的动态页和编辑页使用真实登录用户 ID，不再读取 Mock 演示用户 6006。
- 小程序包管理器统一为 pnpm；保留并只使用 `pnpm-lock.yaml`，忽略 `package-lock.json`。管理员 Web 后台仍使用 npm 和 `src/admin-web/package-lock.json`。

## 已执行

| 检查 | 命令 | 结果 |
| --- | --- | --- |
| Vue / TypeScript 类型检查 | `vue-tsc --noEmit` | 通过 |
| 登录态、Mock 与真实 PostApi 适配回归 | Node 编译后执行 `tests-node/core.test.ts` | 14 项通过，0 失败 |
| 锁文件检查 | 检查 `src/miniapp` 和 `src/admin-web` | miniapp 仅 pnpm；admin-web 保留 npm |

## 未执行

- Vitest 与 `pnpm build:mp-weixin`：当前 Codex 沙箱禁止 Node 启动 esbuild 子进程，报 `spawn EPERM`；需在普通本机终端复跑。
- 真实账号页面联调：当前本机 `127.0.0.1:3306` 和 `127.0.0.1:8080` 均未监听，缺少 MySQL 和运行中的后端；需按 M01 README 启动后完成发布、本人列表、编辑、删除四项验证。

## M05 交接

M05 登录成功后调用 `saveAuthSession({ accessToken, expiresAt, user })`，统一键为 `moments.auth.session`。如果 M05 已使用其他键，当前读取层也兼容常见的 `accessToken` 与 `currentUser` 分键，但正式合并后应只保留一套写入路径。