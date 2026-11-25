# 管理员 Web 后台

M08（叶天豪）第一周实现：Vue 3、TypeScript、Vite、Element Plus 工程；管理员登录、会话恢复、路由权限；用户关键词查询；动态关键词、作者 ID、日期与单标签查询；分页、加载、空结果、错误与重试。用户禁用、动态删除、媒体预览和改密页面属于后续任务。

## 启动与检查

要求 Node.js 22.12+。依赖版本已固定在 `package.json` 和 `package-lock.json`；切换 Node 后执行：

```sh
cd src/admin-web
npm ci
npm run dev:demo
```

浏览器打开 `http://127.0.0.1:5173`。演示模式仅用于检查前端；所有数据均为虚构，顶部持续标明“样例演示”。管理员账号为 `demo_admin`，普通用户为 `demo_user`，密码均为 `demo1234`。输入不存在的关键词查看空结果，输入 `请求失败` 查看失败状态。演示模式会话使用独立存储键，生产构建不装配演示请求。

```sh
npm run typecheck
npm run lint
npm run format:check
npm test
npm run build
npm run preview
```

`preview` 在 `http://127.0.0.1:4173` 查看构建产物，不提供 API 代理，也不作为最终部署服务。实际服务联调与最终部署见下文。

## 接入同一套服务端

复制 `.env.example` 为 `.env.local`，并执行 `npm run dev`。默认前端请求 `/api/v1`，Vite 将 `/api` 转发到 `http://127.0.0.1:8080`，不改变请求路径。小程序和管理员网站共用这一套 Spring Boot，数据库与实际权限校验由服务端负责。

```dotenv
VITE_ADMIN_API_BASE_URL=/api/v1
VITE_ADMIN_DEV_PORT=5173
ADMIN_API_PROXY_TARGET=http://127.0.0.1:8080
```

也可将 `VITE_ADMIN_API_BASE_URL` 配置为完整服务地址，此时需要服务端允许浏览器来源的 CORS；配置改变后重启 Vite。`ADMIN_API_PROXY_TARGET` 只在开发服务器使用，不注入页面。前端环境变量不能存放 JWT 签名密钥或数据库密码。

| 接口                      | 本前端采用的字段                                                                    | 状态                                              |
| ------------------------- | ----------------------------------------------------------------------------------- | ------------------------------------------------- |
| `POST /api/v1/auth/login` | 输入 `account`、`password`；输出 `accessToken`、ISO 时间 `expiresAt`、`user`        | 路径和顶层字段已约定，M01 完整样例待确认          |
| `GET /api/v1/users/me`    | `id`、`account`、`nickname`、`role`；可选 `status`、`createdAt`                     | M01 需允许管理员访问并确认字段                    |
| `GET /api/v1/admin/users` | `keyword`、`page`、`pageSize`；每项含上述基本资料及必填 `status`、`createdAt`       | 参数名、匹配账号或昵称的规则和完整样例待 M01 确认 |
| `GET /api/v1/admin/posts` | `keyword`、`authorId`、`from`、`to`、`tag`、`page`、`pageSize`；返回 M02 `PostView` | 已对齐 M02 现有代码，生产认证与摘要适配待联调     |

所有接口使用 `{ code, data, message }`；列表 `data` 为 `{ items, page, pageSize, total }`，页码从 1 开始、每页 10/20/50 条。网络响应在数据入口校验，字段不一致会显示错误，不会回退成成功或样例。日期按北京时间自然日发送，界面时间按 Asia/Shanghai 展示。

前端保存登录令牌，公开登录不附加旧令牌；刷新后先向 `/users/me` 复核管理员身份，再挂载管理页面。401 撤销当前会话并回登录；403 清空本页管理数据并提示无权限。退出只清当前标签页会话，当前契约没有服务端退出端点。服务端须自行验证 JWT、角色、账号状态与令牌版本，前端角色判断仅控制页面访问。

当前仓库没有 M01 主工程，`npm run dev` 已具备真实 API 请求入口，但真实登录、用户列表和服务端越权测试尚未执行。前端演示与自动化测试不构成服务端验收结论。

## 文件与代码规范

```text
src/
├── app/                 应用装配、路由与后台布局
├── modules/
│   ├── auth/logic/      会话与身份复核
│   ├── auth/pages/      登录表单
│   ├── users/pages/     用户查询页面
│   └── posts/pages/     动态查询页面
├── common/data/         API、响应解析与共用模型
├── common/logic/        查询条件、时间展示与可取消分页状态
├── common/components/   共用列表反馈、分页与状态页
├── demo/                仅开发演示模式使用的虚构请求
└── styles/              全局布局与主题
tests/                   认证、请求、查询和页面集成测试
```

参考 aireader 的代码规范：页面管理展示和交互；外部请求和数据解析集中在 data；认证和异步规则集中在 logic；目录与函数使用简单英文；具名函数和类具有中文“行为/实现”或“职责/概览”头部注释。沿用 TypeScript、Vue 工具约定，2 空格缩进，单引号，无分号，由 ESLint 和 Prettier 检查。共用查询入口负责取消旧请求、隔离旧会话和避免晚到响应覆盖新结果。

## 最终部署

执行 `npm run build` 后将 `dist/` 放入 Nginx 静态目录。前端以 `/api/v1` 请求同域接口，由 Nginx 转发到同一套 Spring Boot。下面是部署在域名根路径的配置示例，路径由运行环境负责人替换：

```nginx
server {
    listen 80;
    server_name _;
    root /srv/moments/admin-web;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

`proxy_pass` 没有附加尾部 `/`，保留 `/api/v1` 路径。history 路由通过 `index.html` 回退，支持刷新 `/users` 与 `/posts`。真机或公开演示的 HTTPS 由运行环境负责人配置。此配置仅为交接示例，本次未部署服务器。
