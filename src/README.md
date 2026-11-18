# 实现目录

- `miniapp/`：用户小程序。
- `backend/`：服务端 API、业务逻辑与数据库访问。
- `admin-web/`：管理员 Web 后台。

目前 M02 的 [动态、标签和检索模块](backend/modules/posts/README.md) 已建立独立 Maven 工程；三端主工程和其他成员模块仍待建立或合入。技术栈以 `../specs/design.md` 为准。模块测试和完整系统启动的条件分别写在各子目录说明中。
