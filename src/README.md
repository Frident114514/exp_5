# 实现目录与三端运行说明

> 整体启动顺序、环境变量样例与媒体存储配置由 **成员 03（潘铭伟）** 维护；各端工程的具体启动命令由对应负责人（M01 服务端主工程、M05 小程序、M08 管理后台）合入后共同核对。技术栈与系统边界以 [`../specs/design.md`](../specs/design.md) 为准。

---

## 1. 目录结构与当前模块状态

- `miniapp/`：用户小程序（uni-app + Vue 3 + TypeScript，由 M05/M06/M07 负责）。
- `backend/`：服务端 API、业务逻辑、媒体存储与数据库访问（Java 21 + Spring Boot 3.5.16 + Flyway + MySQL 8.4）。
  - [`backend/modules/posts/`](backend/modules/posts/README.md)：**M02（锡汉唐）动态、标签与检索模块**，已完成独立 Maven 工程与 48 项测试。
  - [`backend/modules/media/`](backend/modules/media/README.md)：**M03（潘铭伟）媒体上传、视频 Range 读取、绑定与清理模块**，已完成独立 Maven 工程、实现 M02 `MediaPort` 并接入 `PostAccess`，通过 26 项单元与跨模块（M02+M03）集成测试。
- [`admin-web/`](admin-web/README.md)：M08 管理员 Web 后台第一周工程已实现，要求 Node.js 22.12+；演示模式、构建及 Vite 真实登录/身份复核已验证，两类管理查询待 M01/M02 实现或接入。详见 [联调记录](../tests/evidence/M08/integration/README.md)。
- [`.env.example`](.env.example)：三端统一环境变量样例（仅含变量名与占位示例值，不含任何真实密钥）。

---

## 2. 环境准备清单

| 组件 | 版本要求 | 检查命令 | 用途说明 |
| --- | --- | --- | --- |
| **JDK** | Java 21 LTS | `java -version` | 服务端及各后端模块编译运行 |
| **Maven** | 3.6.3+（推荐 3.9+） | `mvn -v` | 服务端依赖管理与测试执行 |
| **MySQL** | MySQL 8.4 LTS | `mysqld --version` | 业务数据与媒体元数据持久化（字符集 `utf8mb4`） |
| **Node.js** | Node 20 / 22 LTS | `node -v && npm -v` | 小程序端与管理员 Web 后台构建 |
| **微信开发者工具** | Stable 稳定版 | 图形界面启动 | 导入 `src/miniapp` 进行小程序预览与视频播放调试 |

---

## 3. 环境变量说明

复制 [`src/.env.example`](.env.example) 为本地 `.env`（已被 `.gitignore` 忽略，切勿提交真实密码或私钥）：

| 变量名 | 示例占位值 | 负责/使用方 | 说明 |
| --- | --- | --- | --- |
| `DB_URL` | `jdbc:mysql://127.0.0.1:3306/moments_db?...` | M01–M04 | MySQL 8.4 JDBC 连接串（UTC 时区） |
| `DB_USERNAME` | `moments_app` | M01–M04 | 数据库用户名 |
| `DB_PASSWORD` | `CHANGE_ME_DB_PASSWORD_PLACEHOLDER` | M01–M04 | 数据库密码占位符 |
| `JWT_SECRET` | `CHANGE_ME_AT_LEAST_32_BYTES_...` | M01 | JWT 签名密钥占位符 |
| `ADMIN_INIT_PASSWORD` | `CHANGE_ME_ADMIN_INIT_PASSWORD_...` | M01 | 初始化管理员账号密码占位符 |
| `MEDIA_STORAGE_DIR` | `./data/media-storage` | **M03** | 本地媒体文件物理存储目录（映射 `moments.media.storage-dir`） |
| `MEDIA_MAX_IMAGE_BYTES` | `10485760` | **M03** | 单张图片大小上限（10 MB，支持 JPEG/PNG/WebP） |
| `MEDIA_MAX_VIDEO_BYTES` | `52428800` | **M03** | 单个 MP4 视频大小上限（50 MB，支持 Range 206） |
| `MEDIA_PENDING_EXPIRE_HOURS` | `24` | **M03** | 未绑定或编辑移除解绑后 `PENDING` 媒体的过期清理小时数 |
| `VITE_MINIAPP_API_BASE_URL` | `http://127.0.0.1:8080/api/v1` | M05–M07 | 小程序请求的后端 API 基础路径 |
| `VITE_ADMIN_API_BASE_URL` | `http://127.0.0.1:8080/api/v1` | M08 | Web 管理后台请求的后端 API 基础路径 |

---

## 4. 整体启动与复现顺序

### 第一步：初始化数据库（MySQL 8.4）
1. 启动本地 MySQL 8.4 服务，创建空数据库：
   ```sql
   CREATE DATABASE IF NOT EXISTS moments_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
   ```
2. 服务端启动时由 Flyway 按登记顺序自动执行迁移脚本（`users` → `posts`/`tags`/`post_tags` → `media` → `ratings`/`comments`）。

### 第二步：验证并运行服务端模块（`src/backend`）
1. **验证 M02 动态模块**：
   ```sh
   python3 src/backend/modules/posts/scripts/run_mysql_tests.py
   ```
2. **验证 M03 媒体模块及 M02+M03 跨模块联调**：
   ```sh
   python src/backend/modules/media/scripts/run_media_tests.py
   ```
3. **启动服务端主工程**（M01 首周主工程已提供；环境变量及认证调用示例见 [后端说明](backend/README.md)，与动态、媒体模块的主工程集成仍待联调）：
   ```sh
   cd src/backend
   .\mvnw.cmd spring-boot:run
   ```
   确认服务监听 `http://127.0.0.1:8080`，且本地媒体目录 `MEDIA_STORAGE_DIR` 可读写。

### 第三步：启动管理员 Web 后台（`src/admin-web`）
```sh
cd src/admin-web
npm ci
npm run dev
```
使用 Node.js 22.12+，浏览器打开 `http://127.0.0.1:5173` 进入管理后台登录页。后台默认请求 `/api/v1`，开发时通过 Vite 代理到 `http://127.0.0.1:8080`。真实服务未就绪时执行 `npm run dev:demo` 检查虚构样例；详细配置、演示账号和部署方式见 [后台说明](admin-web/README.md)。

### 第四步：启动用户小程序（`src/miniapp`，待 M05/M06/M07 合入后）
```sh
cd src/miniapp
npm install
npm run dev:mp-weixin
```
使用**微信开发者工具**导入编译产物目录，在“详情 → 本地设置”中勾选“不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书”，即可连接本机 `http://127.0.0.1:8080/api/v1` 进行图文视频上传、动态发布与 MP4 视频拖动播放。
