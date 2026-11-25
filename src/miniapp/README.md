# 用户小程序（uni-app + Vue 3 + TypeScript）

多媒体朋友圈展示系统 · 成员 M07 负责的动态广场与详情页。
**当前范围：第一周（M07-S1 设计 + M07-S2 基础页面）** —— 公开浏览的列表页、详情页，以及加载 / 空结果 / 接口失败 / 已删除四种状态。筛选、评分提交、评论、登录引导属于后续阶段。

## 1. 环境要求

| 项 | 要求 |
| --- | --- |
| Node.js | 18+（推荐 20.19+ / 22+；本目录的零依赖测试脚本需要 22.6+ 才能直接运行 `.ts`） |
| 包管理器 | npm 或 pnpm |
| 微信开发者工具 | 稳定版，导入编译产物目录 |
| 服务端 | 本机 Spring Boot（`http://localhost:8080/api/v1`）；未就绪时用本目录的 Mock 服务 |

## 2. 快速开始

```powershell
cd src/miniapp
npm install

# 终端 A：启动本地数据源（二选一）
npm run mock                 # Mock 服务，12 条演示数据，端口 8080
# 或直接启动 M02 的 Spring Boot 服务

# 终端 B：编译小程序
npm run dev:mp-weixin
```

微信开发者工具：

1. 「导入项目」→ 目录选择 `src/miniapp/dist/dev/mp-weixin`（HBuilderX 用户为 `unpackage/dist/dev/mp-weixin`）。
2. AppID 可先选「测试号」；`src/manifest.json` 的 `mp-weixin.appid` 留空即可。
3. 「详情 → 本地设置」勾选 **不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书**，否则 `http://localhost:8080` 会被拦截。
4. 修改代码后 uni-app 会自动重新编译，开发者工具自动刷新。

H5 预览（可选，仅用于快速看布局）：`npm run dev:h5`。

## 3. 联调地址配置

默认地址写在 `src/config.ts`：`http://localhost:8080/api/v1`。
需要改地址（真机、局域网）时复制 `.env.local.example` 为 `.env.local`：

```
VITE_API_BASE_URL=http://192.168.1.20:8080/api/v1
```

真机调试必须使用局域网 IP 或 HTTPS 域名，且服务端要监听 `0.0.0.0`。

## 4. 目录结构

```text
src/miniapp/
├── src/
│   ├── api/
│   │   ├── types.ts        # 与 M02 约定的数据结构（唯一字段来源）
│   │   ├── errors.ts       # ApiError 分类与用户可读文案
│   │   ├── client.ts       # 请求封装：baseUrl、超时、响应解包、错误归一
│   │   └── posts.ts        # GET /posts、GET /posts/{postId}
│   ├── composables/
│   │   └── usePostList.ts  # 列表状态机：首次加载、刷新、触底分页、过期响应丢弃
│   ├── utils/
│   │   ├── format.ts       # 时间与评分文案（无评分不显示 0 分）
│   │   ├── feed.ts         # 分页合并、按 id 去重、hasMore 计算
│   │   └── post.ts         # 摘要截断、标签规范化、媒体统计
│   ├── components/
│   │   ├── PostCard.vue        # 广场卡片
│   │   ├── MediaPlaceholder.vue# 图片/视频占位（第一周不做预览与播放）
│   │   ├── TagRow.vue          # 标签
│   │   ├── RatingSummary.vue   # 评分摘要展示
│   │   └── StatePanel.vue      # 加载 / 空 / 失败 / 不存在 统一状态块
│   ├── pages/
│   │   ├── square/index.vue    # 动态广场（入口页）
│   │   └── detail/index.vue    # 动态详情
│   ├── App.vue / main.ts / pages.json / manifest.json
│   └── config.ts
├── mock/mock-server.mjs    # 零依赖 Mock 服务（M02 就绪后可删）
├── tests/                  # 零依赖单元测试 + 联调冒烟
└── docs/
    ├── M07-week1.md       # 第一周设计说明、验收记录、工作记录
    └── 契约对照.md         # 代码 ↔ specs 逐条对照表（验收用）
```

> 说明：`src/` 内相对导入统一带 `.ts` 后缀，这样同一份模块既能被 Vite/uni-app 打包，也能被 `node` 直接执行并跑单元测试（无需先安装依赖）。

## 5. 页面与状态

### 动态广场 `pages/square/index`

| 状态 | 触发 | 界面 |
| --- | --- | --- |
| 首次加载 | 进入页面、`items` 为空 | 3 张骨架屏 + “正在加载动态…” |
| 加载失败 | 网络错误 / 5xx / 非契约响应 | 失败图标、具体文案、**重新加载**按钮 |
| 空结果 | `total = 0` | “还没有动态” + 刷新按钮 |
| 有数据 | 正常返回 | 卡片列表 + “共 N 条动态” |
| 触底加载 | `onReachBottom` 且有下一页 | 底部“正在加载更多…” |
| 触底失败 | 分页请求失败 | “加载失败，点击重试”，不影响已加载内容 |
| 没有更多 | `hasMore = false` | “没有更多了” |

下拉刷新走 `onPullDownRefresh`，结束后调用 `uni.stopPullDownRefresh()`。
刷新会自增请求令牌，**旧请求返回不会覆盖新数据**。

### 动态详情 `pages/detail/index`

| 状态 | 触发 | 界面 |
| --- | --- | --- |
| 加载中 | 进入页面 | 加载状态块 |
| 正常 | 详情返回 | 作者、发布时间、正文、标签、媒体占位、评分摘要 |
| 已删除 / 不存在 | 接口 404 或路由参数非法 | “动态不存在或已删除” + 返回广场 |
| 加载失败 | 其他错误 | 文案 + 重新加载 |

评分与评论按钮本阶段不提供，仅展示传入的评分摘要，并用“后续阶段开放”标注，避免误导演示。

## 6. 字段契约（严格对齐 specs/contracts/api.md）

列表 `GET /posts?page&pageSize` 返回 `{ items, page, pageSize, total }`，详情 `GET /posts/{postId}` 返回单个对象，两者字段一致：

| 字段 | 类型 | 页面用途 |
| --- | --- | --- |
| `id` | number | 卡片 key、跳转详情 |
| `author.id` / `author.nickname` | number / string | 头像首字、作者名 |
| `text` | string | 卡片摘要（70 字截断）、详情全文 |
| `media[].id / type / url` | number / `IMAGE`\|`VIDEO` / string | 媒体占位、视频角标 |
| `tags[]` | string[] | 标签（最多展示 5 个） |
| `createdAt` | ISO 8601 带时区 | 卡片相对时间、详情绝对时间 |
| `rating.average` | number \| null | 评分摘要，`null` 显示“暂无评分” |
| `rating.count` | number | 评分人数 |

统一响应包装 `{ code, data, message }`，`code !== "OK"` 一律按失败处理。
前端未新增任何自定义字段；需要新字段时先改 `specs/contracts/` 再同步此处。

## 7. 测试

```powershell
npm test                    # 26 项单元测试（Node 22.6+ 直接运行 .ts）
node mock/mock-server.mjs   # 终端 A
npm run test:integration    # 终端 B，4 项真实 HTTP 联调冒烟
```

| 测试文件 | 覆盖 |
| --- | --- |
| `tests/format.test.ts` | 时间格式化、相对时间、无评分文案、星级取整 |
| `tests/feed.test.ts` | 分页合并、去重、空结果、空页不死循环、异常响应 |
| `tests/post.test.ts` | 摘要截断（含 emoji）、标签规范化、媒体统计 |
| `tests/client.test.ts` | URL 拼装、成功解包、业务错误、404、5xx、非契约响应、超时/网络 |
| `tests/integration.ts` | 真实 HTTP：分页顺序、详情字段、404、空结果、500 |

若本机 Node 低于 22.6，可先用 `npx tsx tests/index.ts` 运行，或安装依赖后接入 Vitest。

## 8. 手工验收（微信开发者工具）

1. 启动 Mock（`npm run mock`）→ 编译 → 打开广场：应看到 12 条卡片、下拉刷新、触底加载第二页。
2. 点任一卡片进入详情：作者、时间、正文、标签、媒体占位、评分摘要齐全。
3. 访问 `/pages/detail/index?id=999`（可在开发者工具「编译模式」里填启动参数）：显示“动态不存在或已删除”。
4. 停掉 Mock 服务后下拉刷新：显示网络失败提示与重试按钮，页面不崩溃；重启服务点重试恢复。
5. 终端设置 `$env:MOCK_SCENARIO="empty"` 后重启 Mock：广场显示空状态。
6. 设置 `$env:MOCK_SCENARIO="error"`：广场显示接口失败与重试。
7. 制造慢请求（`$env:MOCK_SCENARIO="slow"`）：确认骨架屏与加载提示出现，非白屏。

截图建议放到项目根 `tests/evidence/M07/`，命名为 `S2-广场-正常列表.png` 这种“阶段-页面-场景”格式。

## 9. 已知限制 / 下一周

- 图片预览、多图滑动、MP4 播放：本阶段只有占位（M07-05，第三周）。
- 日期/标签筛选、清空条件：API 已预留 `from`/`to`/`tag` 参数，界面未做（M07-02）。
- 评分提交、评论列表与输入：未实现（M07-06/07）。
- 登录引导、作者编辑入口：未实现，需等 M05 的登录态（M07-08）。
- 媒体 `url` 是相对路径，直接给 `<image>` 用需要拼接 `API_BASE_URL` 的域名部分；本阶段不加载真实图片，接入预览时统一在 `config.ts` 导出 `MEDIA_ORIGIN`。
- `package.json` 中 `@dcloudio/*` 用 `latest` 便于首次安装，装好后请把版本固定下来（提交 lock 文件），避免组员之间版本漂移。