# M05 小程序工程与公共层实施约定

状态：M05-S1（2026-11-25）已落地工程、设计令牌、公共组件、校验规则、路由表与认证 API 类型；M05-S2（2026-11-26）已落地统一请求层、登录态 store、注册/登录/个人资料/修改密码四个页面，以及 posts/media/interactions 接口模块。真实接口联调与开发者工具内验收尚未进行。维护人：成员 05；对接人：成员 06、成员 07（页面与接口层使用），成员 01/02/03/04（对应后端接口）。

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
| `devkit` | `/pages/devkit/index` | M05 | — | 否 | — |
| `login` | `/pages/login/index` | M05 | 可选 `redirect`（登录后返回的完整路径，需 `encodeURIComponent`） | 否 | `POST /auth/login` |
| `register` | `/pages/register/index` | M05 | 可选 `redirect` | 否 | `POST /auth/register` |
| `profile` | `/pages/profile/index` | M05 | — | 是 | `GET /users/me` |
| `password` | `/pages/password/index` | M05 | — | 是 | `PUT /users/me/password` |
| `plaza` | `/pages/plaza/index` | M07 | 可选 `tag`（单个标签）、`from`/`to`（`YYYY-MM-DD`，北京时间自然日，含起止） | 否 | `GET /posts` |
| `postDetail` | `/pages/post-detail/index` | M07 | 必填 `postId`（正整数） | 否 | `GET /posts/{postId}`、评分评论接口 |
| `publish` | `/pages/publish/index` | M06 | 可选 `postId`（有值表示编辑本人动态） | 是 | `POST /posts`、`PATCH /posts/{postId}`、`POST /media` |
| `myPosts` | `/pages/my-posts/index` | M06 | — | 是 | `GET /users/me/posts` |

参数约定：

- `home` 是产品首页（登录状态 + 广场/发布/我的动态/个人资料入口）；`devkit` 是开发参考页（设计令牌、组件、校验、页面四态、路由表），不属于产品流程，上线前可隐藏。

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

## 8. 请求层与接口层

### 8.1 统一请求 `api/http.ts`

所有请求必须走这里，页面和业务模块**不要直接写 `uni.request`**。

| 规则 | 行为 |
| --- | --- |
| 地址 | `buildApiUrl(path + query)`，基础地址取 `VITE_MINIAPP_API_BASE_URL`；查询参数自动 `encodeURIComponent`，空值不拼接 |
| 令牌 | 默认附加 `Authorization: Bearer <token>`；登录、注册传 `withAuth: false` |
| 成功判定 | HTTP 2xx **且** 响应体 `code === "OK"`，只返回 `data` |
| 失败判定 | 其余情况抛 `ApiError { status, code, message }`；`message` 优先取服务端文案，缺失按状态码兜底 |
| 401 处理 | 清空登录态；若该请求原本带着令牌、且当前不在登录/注册页，则跳登录页并带回跳地址（2 秒内只跳一次，避免并发请求重复跳转） |
| 登录/注册自身的 401 | 只把错误交给页面（账号密码错误），不清理状态、不跳转 |
| 未登录时的 401 | 只清理状态，不跳转，保证公开页面能继续浏览 |
| 网络异常 | 统一为 `status: 0`、`code: "NETWORK_ERROR"`、文案 `网络连接失败，请检查网络后重试` |
| 超时 | 默认 15000ms；媒体上传单独用 60000ms |

状态码兜底文案：400 提交内容不符合要求 / 401 登录状态已失效 / 403 没有权限 / 404 内容不存在或已删除 / 409 操作冲突 / 413 文件过大 / 415 不支持的格式 / 5xx 服务器繁忙。

对外函数：

- `request(options)`、`get / post / put / patch / del`；
- `isApiError(value)`：页面区分"接口错误"与未知异常；
- `apiErrorFromResponse(status, body)`、`isUnauthorizedError(error)`、`notifyUnauthorized(path, token)`：给上传等非 `request()` 通道复用同一套解析与 401 处理。

### 8.2 业务接口模块（M06/M07 直接调用）

| 模块 | 函数 | 端点 | 后端负责人 | 使用人 |
| --- | --- | --- | --- | --- |
| `api/auth.ts` | `register`、`login`、`fetchMe`、`changePassword` | `/auth/register`、`/auth/login`、`/users/me`、`/users/me/password` | M01 | M05 |
| `api/posts.ts` | `listPosts`、`getPost`、`createPost`、`updatePost`、`deletePost`、`listMyPosts` | `GET /posts`、`GET /posts/{id}`、`POST /posts`、`PATCH /posts/{id}`、`DELETE /posts/{id}`、`GET /users/me/posts` | M02 | M06、M07 |
| `api/media.ts` | `uploadMedia`、`mediaDisplayUrl`、`mediaContentUrl` | `POST /media`、`GET /media/{id}/content` | M03 | M06（上传）、M07（展示） |
| `api/interactions.ts` | `ratePost`、`listComments`、`createComment` | `PUT /posts/{id}/rating`、`GET/POST /posts/{id}/comments` | M04 | M07 |

请求与响应类型集中在 `api/types.ts`（`Post`、`MediaItem`、`Comment`、`RatingSummary`、`PostPayload` 等），字段与 `api.md` 的示例对象一致。

```ts
// M07 广场列表
const page = await listPosts({ page: 1, pageSize: 10, tag: '校园', from: '2026-11-01' })

// M06 上传后发布
const media = await uploadMedia(tempFilePath)
const post = await createPost({ text: '周末的校园', mediaIds: [media.mediaId], tags: ['校园'] })

// M07 评分与评论
await ratePost(post.id, { score: 5 })
await createComment(post.id, { content: '拍得真好' })
```

失败统一用 `catch` 拿 `ApiError`，`error.message` 可直接 `toastError`；`postId` 非法时 `requirePostId` 会在发请求前抛错，避免拼出 `/posts/undefined`。

### 8.3 登录态 `stores/auth.ts`

| 成员 | 说明 |
| --- | --- |
| `authStore.token` / `expiresAt` / `user` | 响应式只读 getter |
| `authStore.isLoggedIn` | 有令牌且未过期 |
| `authStore.isAdmin` | `role === 'ADMIN'`，用于隐藏后台入口 |
| `authStore.load()` | 应用启动时恢复缓存（`App.vue` 的 `onLaunch` 已调用），只执行一次 |
| `authStore.setSession(session)` | 登录/注册成功后写入并持久化 |
| `authStore.setUser(user)` | 刷新 `/users/me` 后只更新资料 |
| `authStore.clear()` | 退出登录、改密成功、收到 401 时调用 |

缓存键 `AUTH_STORAGE_KEY = moments.auth.v1`，令牌有效期 `TOKEN_EXPIRE_HOURS = 12`。`expiresAt` 为空或无法解析时按"未过期"处理，最终以服务端 401 为准。

### 8.4 登录守卫与回跳（M05-C04）

- 需要登录的页面/操作入口调用 `requireLogin(redirect?)`：已登录返回 `true`；未登录跳登录页并返回 `false`。
- 回跳参数 `redirect` 是**站内完整路径**（含查询串，`encodeURIComponent` 编码），例如 `/pages/publish/index`。只接受 `/pages/<目录>/<页面>` 形式，外链、`//` 开头、缺前导斜杠、带锚点的值一律拒绝（`isSafeInternalPath`）。
- 登录成功后 `navigateAfterLogin(redirect)`：合法则 `reLaunch` 回原页面，否则进入广场；`reLaunch` 清空页面栈，避免登录页残留在返回路径。
- 改密成功后：`authStore.clear()` + `reLaunch` 到登录页，必须用新密码重新登录。
- 退出登录：`authStore.clear()` + `reLaunch` 到首页。
- 公开页面（广场、详情、评论列表）**不要**调用 `requireLogin`，未登录必须能浏览。

### 8.5 本地模拟接口（可选，服务端未就绪时用）

在 `src/miniapp/.env.local` 写 `VITE_MINIAPP_USE_MOCK=true` 后重新编译，`api/http.ts` 会优先命中 `api/mock.ts` 的假数据；关闭开关即走真实服务端，业务代码不用改。

- 演示账号：`demo` / `demo1234`（普通用户）、`admin` / `admin1234`（管理员）。
- 覆盖端点：账号 4 个 + `GET /posts`、`GET /posts/{id}`、`POST/PATCH/DELETE /posts`、`GET /users/me/posts`、评分、评论列表/发表、`POST /media`。
- 限制：数据在内存里，重启即恢复；媒体地址指向 `/api/v1/media/{id}/content` 但**没有真实文件**，图片/视频会加载失败，页面必须做占位；模拟结果**不能作为联调证据**。

## 9. 待确认事项（联调前必须关闭）

| 编号 | 事项 | 责任方 | 当前处理 |
| --- | --- | --- | --- |
| Q1 | 改密请求字段名是否为 `oldPassword` / `newPassword` | M01 | 按此实现，联调时确认；若改为其他名字只改 `api/types.ts` |
| Q2 | 账号规则（4–20 位字母数字下划线）与密码规则（8–32 位含字母数字）服务端是否一致 | M01 | 前端预校验按此实现，服务端更严格时以服务端提示为准 |
| Q3 | 登录响应 `user` 是否包含 `role`、`status` | M01 | 已按包含实现类型，缺失 `role` 时前端按 `USER` 处理并隐藏管理入口 |
| Q4 | 注册成功后是否直接返回登录令牌 | M01 | 按 `api.md` 仅返回用户资料，注册成功后跳登录页 |
| Q5 | 广场列表的卡片字段与评分摘要为空时的展示 | M02、M07 | M07 在实现时按 `rating: null` / `count: 0` 处理，不显示伪造的 0 分 |
| Q6 | 底部 tabBar 是否需要及其图标来源 | M07、M05 | 暂不加 tabBar，待广场入口确定后统一加 |
| Q7 | 微信小程序 `wx.request` 官方支持的方法不含 `PATCH`，而 `api.md` 的编辑接口是 `PATCH /posts/{postId}` | M01、M02、M05 | 请求层按契约透传 `PATCH`；联调时必须实测，若基础库不支持则改为 `POST` + `X-HTTP-Method-Override: PATCH`（需服务端配合）并同步改契约 |
| Q8 | 真实接口返回的时间格式是否统一为带时区 ISO 8601 | M01–M04 | 前端只做展示，不解析服务端时间做业务判断；`AuthUser.createdAt` 按原样展示 |

## 10. 验证方式

在 `src/miniapp/` 执行：

```sh
npm run test          # 91 项单元测试（9 个文件）
npm run type-check    # vue-tsc 类型检查
npm run build:mp-weixin
npm run build:h5
```

覆盖范围：设计令牌以外的纯逻辑 —— 表单校验规则、服务端地址拼接、路由表与 `pages.json` 一致性、接口路径与 `api.md` 一致性、请求成功/失败/401/网络异常、"登录态写入与恢复/过期"、登录守卫与回跳安全校验、模拟接口数据形状。

测试证据见 [M05 测试证据](../../tests/evidence/M05/README.md)。单元测试只覆盖纯逻辑；页面行为必须在微信开发者工具中手工验证，未执行的用例保持"未执行"，不得据未经运行的推断宣称通过。
