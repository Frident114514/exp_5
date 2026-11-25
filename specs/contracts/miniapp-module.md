# M05 小程序工程与公共层实施约定

状态：M05-S1（2026-11-25）已落地工程、设计令牌、公共组件、校验规则、路由表与认证 API 类型；账号页面与请求层属 M05-S2，尚未实现。维护人：成员 05；对接人：成员 06、成员 07（页面与公共层使用），成员 01（认证接口）。

本文件是 `api.md`、`data-model.md` 在小程序端的补充约定：**新增字段或改动路径先改本文档，再改代码，并通知受影响的成员**。只描述已经存在的约定；标"待确认"的项必须在联调前由对应负责人给出结论。

## 1. 工程与公共文件归属

- 工程位于 `src/miniapp/`，uni-app CLI（Vite 5）+ Vue 3 + TypeScript，构建微信小程序，H5 仅用于快速看样式。
- 锁定版本：`@dcloudio/* 3.0.0-5030420261029002`、`vue ^3.4.21`、`vite 5.2.8`、`typescript ^4.9.4`、`sass 1.77.8`、`vitest ^2.1.9`。升级需先在此登记并复验构建。
- 公共文件由 M05 统一合入，M06/M07 不要直接改动：`package.json`、`vite.config.ts`、`vitest.config.ts`、`tsconfig.json`、`src/pages.json`、`src/manifest.json`、`src/uni.scss`、`src/styles/base.scss`、`src/config/*`、`src/api/*`、`src/utils/*`。
- 各自维护：`src/pages/plaza/`、`src/pages/post-detail/`（M07）；`src/pages/publish/`、`src/pages/my-posts/`（M06）；`src/pages/login|register|profile|password/`（M05）。页面私有样式写在页面 `<style scoped>`。
- 页面需要新的公共能力（新组件、新校验规则、新端点）时，向 M05 提需求，由 M05 在公共文件中实现，避免三人同时改同一文件。

## 2. 设计令牌（`src/uni.scss`）

`uni.scss` 由 uni-app 自动注入每个页面的 SCSS，页面可直接使用变量，无需 `@import`。**禁止在页面里写死颜色、字号和间距**，否则后续统一改风格会漏改。

| 类别 | 变量 | 值 |
| --- | --- | --- |
| 品牌 | `$brand-primary` / `$brand-primary-dark` / `$brand-primary-light` | `#2F6FEB` / `#1F55C0` / `#EAF1FE` |
| 语义 | `$color-success` / `$color-warning` / `$color-danger` | `#12A150` / `#E08A00` / `#E5484D` |
| 语义浅底 | `$color-success-light` / `$color-warning-light` / `$color-danger-light` | `#E8F7EE` / `#FDF4E3` / `#FDECEC` |
| 文字 | `$text-primary` / `$text-secondary` / `$text-tertiary` / `$text-placeholder` / `$text-inverse` | `#1D2129` / `#4E5969` / `#86909C` / `#C0C4CC` / `#FFFFFF` |
| 背景 | `$bg-page` / `$bg-card` / `$bg-muted` | `#F4F5F7` / `#FFFFFF` / `#F7F8FA` |
| 描边 | `$border-color` / `$divider-color` | `#E5E6EB` / `#F0F1F3` |
| 字号 | `$font-size-caption` / `body` / `subtitle` / `title` / `headline` | `22` / `28` / `32` / `36` / `44` rpx |
| 行高 | `$line-height-base` | `1.6` |
| 间距 | `$space-xs` / `sm` / `md` / `lg` / `xl` | `8` / `16` / `24` / `32` / `48` rpx |
| 圆角 | `$radius-sm` / `md` / `lg` / `pill` | `8` / `16` / `24` / `999` rpx |
| 控件 | `$control-height` / `$control-height-lg` / `$control-radius` | `88rpx` / `96rpx` / `$radius-md` |
| 阴影 | `$shadow-card` / `$shadow-float` | `0 4rpx 16rpx rgba(29,33,41,.06)` / `0 8rpx 24rpx rgba(29,33,41,.12)` |
| 动效 | `$duration-fast` / `$duration-base` | `150ms` / `250ms` |
| 字体 | `$font-family-base` | 系统字体栈（PingFang SC / Microsoft YaHei 等） |

Mixin：`card($padding)`、`ellipsis`、`ellipsis-lines($lines)`、`flex-center`、`flex-between`、`button-reset`。

全局工具类（`src/styles/base.scss`）：`.page`、`.page--plain`、`.section`、`.section--flush`、`.section__title`、`.section__desc`、`.text-caption`、`.text-secondary`、`.text-strong`、`.text-ellipsis`、`.divider`、`.row`、`.stack`、`.safe-bottom`。

取值口径：屏幕宽度按 750rpx 设计；字号只用上表五档；间距只用 8rpx 阶梯。

## 3. 公共组件

### AppButton

| Prop | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `type` | `'primary' \| 'secondary' \| 'ghost' \| 'danger'` | `primary` | 视觉类型 |
| `size` | `'normal' \| 'large'` | `normal` | 88rpx / 96rpx |
| `disabled` | `boolean` | `false` | 禁用 |
| `loading` | `boolean` | `false` | 显示加载圈并屏蔽点击 |
| `block` | `boolean` | `true` | 是否占满一行 |
| `throttle` | `number` | `400` | 两次有效点击的最小间隔 ms，`0` 关闭 |
| `text` | `string` | `''` | 无插槽时的文字 |

事件：`click`。防重复提交规则：`loading` 或 `disabled` 为真时不触发；间隔小于 `throttle` 的点击被忽略。提交类按钮必须把请求状态绑定到 `loading`。

### FormField

| Prop | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `modelValue` | `string` | — | v-model 值 |
| `label` | `string` | `''` | 标签 |
| `type` | `'text' \| 'password' \| 'number'` | `text` | `password` 自动提供"显示/隐藏" |
| `placeholder` | `string` | `请输入` | — |
| `error` | `string` | `''` | 错误文案，非空时边框变红并显示 |
| `hint` | `string` | `''` | 辅助说明，有错误时让位 |
| `required` | `boolean` | `false` | 显示红色 `*` |
| `maxlength` | `number` | `-1` | `-1` 不限制（与微信 input 一致） |
| `disabled` | `boolean` | `false` | — |
| `clearable` | `boolean` | `true` | 有值时显示"清空" |

事件：`update:modelValue`、`blur`、`confirm`，都回传当前字符串值。

### StateView

| Prop | 类型 | 默认 | 说明 |
| --- | --- | --- | --- |
| `status` | `'loading' \| 'empty' \| 'error' \| 'ready'` | — | 当前状态 |
| `title` | `string` | 按状态取值 | `加载中…` / `暂无内容` / `加载失败` |
| `description` | `string` | `''` | 补充说明 |
| `retryText` | `string` | `重新加载` | 错误态按钮文案 |
| `compact` | `boolean` | `false` | 页面内小区域用紧凑模式 |

事件：`retry`。`status === 'ready'` 时渲染默认插槽内容。

### PlaceholderPage

用于尚未实现的页面：`owner`、`title`、`description`、`todos`（字符串数组）。M06/M07 实现页面时直接替换该页面文件内容，保留路由登记。

## 4. 页面状态与交互约定

| 场景 | 使用状态 | 要求 |
| --- | --- | --- |
| 首次加载列表/详情 | `loading` | 显示骨架或加载态，不能是空白页 |
| 请求成功但无数据 | `empty` | 说明"为什么没有"以及如何产生内容（如"还没有动态，去发布第一条"） |
| 请求失败 | `error` | 显示可读原因并提供重试按钮，不能只弹 toast 后停在空白 |
| 正常 | `ready` | 渲染内容 |

- 失败提示统一走 `utils/feedback.ts`：`toastError`（3 秒、无图标，可容纳服务端 message）、`toastSuccess`（1.5 秒）、`showLoading` / `hideLoading` 必须成对出现，异常分支也要关闭。
- 破坏性操作（删除动态等）使用 `confirmAction` 二次确认。
- 分页列表切换筛选条件时必须重置到第 1 页，并丢弃旧请求的迟到结果（M07 在实现时保证）。
- 公开页面（广场、详情、评论列表）不强制登录；需要登录的操作引导到登录页并携带 `redirect`。

## 5. 表单校验约定（`src/utils/validate.ts`）

校验函数是纯函数：返回 `null` 通过，返回字符串为可直接展示的中文提示。时机：**失焦与提交时校验，输入过程中清除旧错误**，不边打字边报错。

| 字段 | 规则 | 函数 |
| --- | --- | --- |
| 账号 | 4–20 位字母、数字或下划线 | `validateAccount` |
| 登录密码 | 仅必填 + 不超过 32 位（不拦历史弱密码） | `validateLoginPassword` |
| 注册/改密密码 | 8–32 位，含字母和数字，不含空白 | `validatePasswordStrength` |
| 确认密码 | 与首次输入一致 | `validatePasswordConfirm` |
| 昵称 | 1–20 个 Unicode 码点（emoji 按 1 个计） | `validateNickname` |
| 改密新密码 | 满足强度且不得与原密码相同 | `validateNewPassword` |

前端校验只是体验优化，**服务端仍是最终判定方**（`design.md` 第 8 节）。账号与密码的取值规则是 M05 提出的前端约定，需与 M01 的认证实现一致，见第 9 节待确认项。

## 6. 路由与参数登记

路径在 `src/config/routes.ts` 与 `src/pages.json` 双向登记，`tests/routes.spec.ts` 会校验两者一致、标题一致；只改一边会测试失败。跳转统一用 `buildRouteUrl(name, params)` 或 `utils/navigate.ts` 的 `openRoute`，不要手写路径字符串。

| 路由名 | 页面路径 | 负责人 | 参数 | 登录 | 依赖接口 |
| --- | --- | --- | --- | --- | --- |
| `home` | `/pages/home/index` | M05 | — | 否 | — |
| `login` | `/pages/login/index` | M05 | 可选 `redirect`（登录后返回的完整路径，需 `encodeURIComponent`） | 否 | `POST /auth/login` |
| `register` | `/pages/register/index` | M05 | 可选 `redirect` | 否 | `POST /auth/register` |
| `profile` | `/pages/profile/index` | M05 | — | 是 | `GET /users/me` |
| `password` | `/pages/password/index` | M05 | — | 是 | `PUT /users/me/password` |
| `plaza` | `/pages/plaza/index` | M07 | 可选 `tag`（单个标签）、`from`/`to`（`YYYY-MM-DD`，北京时间自然日，含起止） | 否 | `GET /posts` |
| `postDetail` | `/pages/post-detail/index` | M07 | 必填 `postId`（正整数） | 否 | `GET /posts/{postId}`、评分评论接口 |
| `publish` | `/pages/publish/index` | M06 | 可选 `postId`（有值表示编辑本人动态） | 是 | `POST /posts`、`PATCH /posts/{postId}`、`POST /media` |
| `myPosts` | `/pages/my-posts/index` | M06 | — | 是 | `GET /users/me/posts` |

参数约定：

- 必填参数缺失时 `buildRouteUrl` 直接抛错，页面也用 `onLoad` 校验，缺失时显示错误状态而不是静默空白。
- 可选参数为空字符串或 `undefined` 时不拼接到 URL。
- 日期与标签的筛选语义以 `api.md` 为准：`from` 不得晚于 `to`，日期与标签同时存在取交集。

新增页面流程：① 在 `config/routes.ts` 登记（路径/标题/负责人/登录要求/参数）；② 在 `src/pages.json` 增加同路径页面项与 `navigationBarTitleText`；③ 新建 `src/pages/<页面>/index.vue`；④ 运行 `npm run test` 确认一致性用例通过；⑤ 如新增依赖，交给 M05 合入 `package.json`。

## 7. 服务端地址与构造规则

环境变量（与 `src/.env.example` 一致，M05 端样例见 `src/miniapp/.env.example`）：

| 变量 | 示例 | 说明 |
| --- | --- | --- |
| `VITE_MINIAPP_API_BASE_URL` | `http://127.0.0.1:8080/api/v1` | API 基础地址，含 `/api/v1` |
| `VITE_MINIAPP_MEDIA_ORIGIN` | `http://127.0.0.1:8080` | 媒体源，仅补协议与主机 |

- 值在**编译期**注入，修改后必须重启 `dev:mp-weixin` 或重新构建；未配置时回退到上述本机默认值。
- 拼接一律用 `config/env.ts`：`buildApiUrl('/auth/login')`、`buildMediaUrl(33)` → `…/api/v1/media/33/content`、`absoluteMediaUrl('/api/v1/media/33/content')`。
- 服务端返回的媒体 `url` 已含 `/api/v1`，补全时只加协议与主机，不能再用 `buildApiUrl` 拼一次。
- 真机调试时 `127.0.0.1` 指向手机自身，必须换成电脑局域网 IP 或 https 服务地址；开发者工具需关闭域名校验。

## 8. 认证 API 类型（`api/types.ts`、`api/auth.ts`）

成功响应 `{ "code": "OK", "data": ..., "message": "" }`；失败响应 `{ "code": "ERROR_CODE", "data": null, "message": "可读说明" }` 并配合 HTTP 状态码。错误码常量与 `api.md` 表格一致：`VALIDATION_ERROR`、`UNAUTHENTICATED`、`FORBIDDEN`、`NOT_FOUND`、`CONFLICT`、`FILE_TOO_LARGE`、`UNSUPPORTED_MEDIA_TYPE`。

| 端点 | 方法 | 权限 | 请求 | 响应 data |
| --- | --- | --- | --- | --- |
| `/auth/register` | POST | 公开 | `account`、`password`、`nickname` | 用户资料，角色固定 `USER` |
| `/auth/login` | POST | 公开 | `account`、`password` | `accessToken`、`expiresAt`、`user` |
| `/users/me` | GET | 用户 | — | 用户资料 |
| `/users/me/password` | PUT | 用户 | `oldPassword`、`newPassword` | `null` |

`AuthUser`：`id`、`account`、`nickname`、`role`（`USER`/`ADMIN`）、`status?`、`createdAt?`。令牌有效期 12 小时（`TOKEN_EXPIRE_HOURS`）；本地登录态缓存键 `AUTH_STORAGE_KEY = moments.auth.v1`，改版时递增版本号使旧缓存失效。

M05-S2 的请求层将统一处理：自动附加 `Authorization: Bearer <token>`、解析 `code`/`message`、`401 UNAUTHENTICATED` 清理登录态并引导重新登录、`403` 提示无权限、网络异常归一为 `NETWORK_ERROR`。M06/M07 只调用封装后的函数，不直接写 `uni.request`。

## 9. 待确认事项（联调前必须关闭）

| 编号 | 事项 | 责任方 | 当前处理 |
| --- | --- | --- | --- |
| Q1 | 改密请求字段名是否为 `oldPassword` / `newPassword` | M01 | 按此实现，联调时确认；若改为其他名字只改 `api/types.ts` |
| Q2 | 账号规则（4–20 位字母数字下划线）与密码规则（8–32 位含字母数字）服务端是否一致 | M01 | 前端预校验按此实现，服务端更严格时以服务端提示为准 |
| Q3 | 登录响应 `user` 是否包含 `role`、`status` | M01 | 已按包含实现类型，缺失 `role` 时前端按 `USER` 处理并隐藏管理入口 |
| Q4 | 注册成功后是否直接返回登录令牌 | M01 | 按 `api.md` 仅返回用户资料，注册成功后跳登录页 |
| Q5 | 广场列表的卡片字段与评分摘要为空时的展示 | M02、M07 | M07 在实现时按 `rating: null` / `count: 0` 处理，不显示伪造的 0 分 |
| Q6 | 底部 tabBar 是否需要及其图标来源 | M07、M05 | 暂不加 tabBar，待广场入口确定后统一加 |

## 10. 验证方式

在 `src/miniapp/` 执行：

```sh
npm run test          # 41 项单元测试：校验规则、地址拼接、路由一致性、契约常量
npm run type-check    # vue-tsc 类型检查
npm run build:mp-weixin
npm run build:h5
```

测试证据见 [M05 测试证据](../../tests/evidence/M05/README.md)。单元测试只覆盖纯逻辑；页面行为必须在微信开发者工具中手工验证，未执行的用例保持"未执行"，不得据未经运行的推断宣称通过。
