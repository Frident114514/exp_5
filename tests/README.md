# 测试目录

本目录保存跨模块验收用例、演示数据说明和手工测试证据。服务端单元/接口测试放 `src/backend/` 工程的 `src/test/`，前端测试放各自工程。优先覆盖登录与改密、作者权限、图文视频发布、标签日期检索、评分评论、管理员权限和删除操作。

服务端测试使用 JUnit 5 和 Spring Boot Test；前端逻辑测试使用 Vitest。建立工程后补充运行命令；测试结论应能为实验报告提供依据。

成员 04 维护整体用例和缺陷汇总，每位成员执行自己任务文档中的测试，修复本人模块的问题。证据实施时按 `evidence/M01/` 至 `evidence/M08/` 存放，个人文档记录操作/命令、实际结果和证据链接。未经执行的用例保持未执行状态。

每个阶段都测试本阶段可运行的内容；修改接口或公共配置后，复验受影响的已完成流程。完整协作安排见 [总任务表](../specs/tasks.md)。

## 已有测试：M02 动态、标签与检索

- [测试源代码](../src/backend/modules/posts/src/test/java/edu/szu/moments/poststest/)：输入/日期校验与真实 MySQL 上的 API、持久化、事务和并发测试。
- [测试运行说明](../src/backend/modules/posts/README.md)：环境要求及独立临时 MySQL 实例的建立、执行和清理方式。
- [测试证据与用例映射](evidence/M02/README.md)：2026-11-18 复验 48 项全部通过，0 失败、0 错误、0 跳过；保留首次并发标签缺陷及修复记录。

在项目根目录执行：

```sh
python3 src/backend/modules/posts/scripts/run_mysql_tests.py
```

需事先准备 JDK 21、Maven、MySQL 8.4 和 Python 3；路径配置见模块运行说明。M01 身份认证、M03 媒体、M04 评分及管理员审计使用测试适配器，以上结果不能替代真实服务和前端的联调验收。

## 已有测试：M05 小程序公共层与账号模块

- [测试源代码](../src/miniapp/tests/)：表单校验规则、服务端地址拼接、路由表与 `pages.json` 一致性、接口路径与 `api.md` 一致性、请求层成功/失败/401/网络异常、登录态写入恢复与过期、登录守卫与回跳安全、模拟接口数据形状，共 91 项 Vitest 用例。
- [测试运行说明](../src/miniapp/README.md)：依赖安装、构建与微信开发者工具导入步骤。
- [测试证据与用例映射](evidence/M05/README.md)：2026-11-25 41 项（S1）、2026-11-26 91 项（S2）全部通过，类型检查与 mp-weixin/H5 构建退出码 0。

在 `src/miniapp/` 执行：

```sh
npm install
npm run test
npm run type-check
```

以上覆盖纯逻辑；小程序页面行为需在微信开发者工具中手工验证，账号相关用例（M05-C01～C06）的真实接口部分待 M01 就绪后联调。
