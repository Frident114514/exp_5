# M01 诊断开关与连接保持重复实验

2026-11-25（北京时间）。本次修改压测代码以支持诊断关闭、连接保持和重复轮次；业务准入仍为 64、数据库池仍为 20。每组独立启动隔离 MySQL 与 Java，每轮以 1000 QPS 预热 20 s，预建 10000 条连接后统一放行。诊断开关组各完成 3 轮及恢复，连接保持组完成 1/3 轮后在建连阶段中断，未完成恢复。服务端连接超时 60 s 贯穿整轮，请求超时 15 s，成功门禁仍为 >99% 且成功 P95 <1 s。

| 配置 | 完成轮次 | 成功数中位数及范围 | 成功 P95 中位数及范围 ms | 容量门禁 |
| --- | --- | --- | --- | --- |
| 诊断关 立即关闭 | 3/3 | 2645（2610–3483） | 235.46（226.71–501.91） | 未通过 |
| 诊断开 立即关闭 | 3/3 | 3145（3030–3411） | 147.17（104.81–445.11） | 未通过 |
| 诊断开 保持连接 | 1/3，中断 | 单轮 4490 | 单轮 499.13 | 本轮未通过，重复对照未完成 |

## 原始轮次

| 配置及证据目录 | 轮次 | 成功数 | 成功 P95 ms | 写完跨度 ms | 客户端观测在途峰值 |
| --- | --- | ---: | ---: | ---: | ---: |
| [诊断关 close](../performance-2026-11-25-223012/preconnected-10000-1.json) | preconnected-10000-1 | 3483 | 501.91 | 1133.76 | 3761 |
| [诊断关 close](../performance-2026-11-25-223012/preconnected-10000-2.json) | preconnected-10000-2 | 2610 | 235.46 | 1021.73 | 2001 |
| [诊断关 close](../performance-2026-11-25-223012/preconnected-10000-3.json) | preconnected-10000-3 | 2645 | 226.71 | 799.85 | 3162 |
| [诊断开 close](../performance-2026-11-25-223330/preconnected-10000-1.json) | preconnected-10000-1 | 3411 | 445.11 | 621.39 | 5627 |
| [诊断开 close](../performance-2026-11-25-223330/preconnected-10000-2.json) | preconnected-10000-2 | 3145 | 104.81 | 741.22 | 1241 |
| [诊断开 close](../performance-2026-11-25-223330/preconnected-10000-3.json) | preconnected-10000-3 | 3030 | 147.17 | 708.13 | 2577 |
| [诊断开 keep-alive, one request, cleanup excluded](../performance-2026-11-25-223905/preconnected-10000-1.json) | preconnected-10000-1 | 4490 | 499.13 | 549.56 | 6368 |

## 判定与边界

以上是同机、按配置分组顺序执行的探索结果，诊断开关各完成 3 轮，连接保持仅完成 1/3 轮，组间重启 JVM，组内轮次不是独立进程复现；未随机交错配置，JIT、机器状态和实际发出跨度仍为混淆项。因此只报告观测范围，不将组间差值全部归因于探针或连接策略。已完成业务轮次均未满足容量门禁，不改变生产准入与数据库预算。连接保持组第二轮预建连接遭遇 Windows AddressAlreadyInUse，未发出该轮业务请求，该组仅完成 1/3 轮；不构成重复收益证据，亦未完成该组恢复检查。failure.json 保留中断原因，不能把未执行轮次记为业务失败或通过。

连接保持实验仍是一连接一个请求，业务响应立即读取；所有请求读取并解析完成即停止计时，再统一关闭连接，cleanup 不计入请求阶段。它测试将连接关闭移出业务计时区间，不是多请求连接复用，也不能证明真实复用收益。128 个校准请求在服务端保持连接不关闭的情况下完整读取成功，说明客户端无需等待 EOF。校准不算业务容量。

诊断开启组保留编号与 503 对账；诊断关闭组不读取诊断接口，不伪造服务端数据。对照增加独立命令参数，不永久开启探针。新计时将整批 cleanup 排除，不能直接与旧脚本整批吞吐比较。后续先拆数据库连接获取、SQL 与映射时间，再决定是否调整处理名额；此次不盲目扩池或排队。

## 复现

```powershell
./tests/performance/run_local_m01.ps1 -MysqlBin '<MySQL84 bin>' -BurstOnly -MeWaiters 0 -MeWaitMs 0 -Repetitions 3 -DiagnosticsOff
./tests/performance/run_local_m01.ps1 -MysqlBin '<MySQL84 bin>' -BurstOnly -MeWaiters 0 -MeWaitMs 0 -Repetitions 3
./tests/performance/run_local_m01.ps1 -MysqlBin '<MySQL84 bin>' -BurstOnly -MeWaiters 0 -MeWaitMs 0 -Repetitions 3 -KeepAlive
```

聚合由 tests/performance/compare_m01.ps1 生成 comparison.json；每组目录保留逐轮 JSON、client-traces、阶段采样、配置和日志；诊断开启时另保存 server-traces 与 reconciliation.json。
