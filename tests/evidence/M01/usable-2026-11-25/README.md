# M01 当前可用高并发版本

本人信息查询新增有界准入等待。当前保留配置为处理名额 64、等待者上限 1024、等待上限 200 ms、数据库连接池 20；high-concurrency 使用虚拟线程，诊断默认关闭。默认 profile 仍即时拒绝。密码认证仍为并行 4、10 请求/秒、突发 20，实时用户状态和令牌版本校验不变。

## 实测结果

各组独立 MySQL 8.4 与 JVM，同机 Windows 异步客户端；10000 个合成账号，不重试，不延迟读取响应。每个规模先建连接再统一放行。门禁为成功率严格 >99%、成功 P95 <1000 ms、无丢失请求；有效响应同时核对账号。

| 等待配置 | 预热秒数 | 轮次 | 成功/请求 | 成功 P95 ms | 门禁 |
| --- | ---: | --- | ---: | ---: | --- |
| 1024/200 ms | 20 | [preconnected-500-1](../performance-2026-11-25-225614/preconnected-500-1.json) | 500/500 | 61.42 | 通过 |
| 1024/200 ms | 20 | [preconnected-1000-1](../performance-2026-11-25-225614/preconnected-1000-1.json) | 1000/1000 | 111.41 | 通过 |
| 1024/200 ms | 20 | [preconnected-2000-1](../performance-2026-11-25-225614/preconnected-2000-1.json) | 1941/2000 | 179.29 | 未通过 |
| 1024/200 ms | 20 | [preconnected-5000-1](../performance-2026-11-25-225614/preconnected-5000-1.json) | 3006/5000 | 361.76 | 未通过 |
| 1024/200 ms | 20 | [preconnected-10000-1](../performance-2026-11-25-225614/preconnected-10000-1.json) | 8785/10000 | 277.76 | 未通过 |
| 1024/200 ms | 20 | [preconnected-500-2](../performance-2026-11-25-225614/preconnected-500-2.json) | 500/500 | 46.78 | 通过 |
| 1024/200 ms | 20 | [preconnected-1000-2](../performance-2026-11-25-225614/preconnected-1000-2.json) | 1000/1000 | 82.11 | 通过 |
| 1024/200 ms | 20 | [preconnected-2000-2](../performance-2026-11-25-225614/preconnected-2000-2.json) | 2000/2000 | 85.61 | 通过 |
| 1024/200 ms | 20 | [preconnected-5000-2](../performance-2026-11-25-225614/preconnected-5000-2.json) | 5000/5000 | 159.82 | 通过 |
| 1024/200 ms | 20 | [preconnected-10000-2](../performance-2026-11-25-225614/preconnected-10000-2.json) | 10000/10000 | 193.67 | 通过 |
| 4096/400 ms | 20 | [preconnected-1000-1](../performance-2026-11-25-225913/preconnected-1000-1.json) | 1000/1000 | 416.90 | 通过 |
| 4096/400 ms | 20 | [preconnected-5000-1](../performance-2026-11-25-225913/preconnected-5000-1.json) | 1839/5000 | 1168.81 | 未通过 |
| 4096/400 ms | 20 | [preconnected-10000-1](../performance-2026-11-25-225913/preconnected-10000-1.json) | 10000/10000 | 147.53 | 通过 |
| 4096/400 ms | 20 | [preconnected-1000-2](../performance-2026-11-25-225913/preconnected-1000-2.json) | 1000/1000 | 62.41 | 通过 |
| 4096/400 ms | 20 | [preconnected-5000-2](../performance-2026-11-25-225913/preconnected-5000-2.json) | 5000/5000 | 222.12 | 通过 |
| 4096/400 ms | 20 | [preconnected-10000-2](../performance-2026-11-25-225913/preconnected-10000-2.json) | 10000/10000 | 133.16 | 通过 |
| 4096/400 ms | 60 | [preconnected-1000-1](../performance-2026-11-25-230218/preconnected-1000-1.json) | 1000/1000 | 115.55 | 通过 |
| 4096/400 ms | 60 | [preconnected-5000-1](../performance-2026-11-25-230218/preconnected-5000-1.json) | 2478/5000 | 1078.15 | 未通过 |
| 4096/400 ms | 60 | [preconnected-10000-1](../performance-2026-11-25-230218/preconnected-10000-1.json) | 5740/10000 | 529.97 | 未通过 |
| 4096/400 ms | 60 | [preconnected-1000-2](../performance-2026-11-25-230218/preconnected-1000-2.json) | 1000/1000 | 51.04 | 通过 |
| 4096/400 ms | 60 | [preconnected-5000-2](../performance-2026-11-25-230218/preconnected-5000-2.json) | 5000/5000 | 137.83 | 通过 |
| 4096/400 ms | 60 | [preconnected-10000-2](../performance-2026-11-25-230218/preconnected-10000-2.json) | 10000/10000 | 236.67 | 通过 |
| 4096/400 ms | 60 | [preconnected-1000-3](../performance-2026-11-25-230218/preconnected-1000-3.json) | 1000/1000 | 60.35 | 通过 |
| 4096/400 ms | 60 | [preconnected-5000-3](../performance-2026-11-25-230218/preconnected-5000-3.json) | 5000/5000 | 193.82 | 通过 |
| 4096/400 ms | 60 | [preconnected-10000-3](../performance-2026-11-25-230218/preconnected-10000-3.json) | 10000/10000 | 159.83 | 通过 |

## 版本选择

首组 1024/200 ms 两轮 1000 请求均全成功，P95 为 111.41/82.11 ms，1000 QPS 预热两轮均 20000/20000 成功，恢复 1000/1000 成功。2000 请求一轮 1941/2000，5000 与 10000 均有失败轮次，不能承诺稳定支持这些档位。4096/400 ms 虽出现万人全成功轮次，5000 请求却有 P95 >1 s 的失败；延长预热仍有失败，故当前选择较小等待预算。

当前已重复通过的最大测试档位是 1000 请求统一放行，不代表数学意义上的系统极限，也不等于服务端恰好同时处理 1000 个请求。请求写出存在跨度，实际在途峰值见原始 JSON。预热后的单接口成绩不覆盖冷启动、登录、全系统混合负载或独立生产环境。单轮万人全成功是优化证据，不能覆盖失败轮次。

## 后续诊断与复现

原有逐请求追踪、管理员诊断接口、JFR、校准、开关对照和连接保持实验全部保留；早期数据库长尾仍待拆分。历史立即拒绝实验须传 -MeWaiters 0 -MeWaitMs 0；各旧目录是当时配置的原始证据。

```powershell
$env:SPRING_PROFILES_ACTIVE='high-concurrency'
# 同时设置 backend/.env.example 所需的数据库与 JWT 环境变量
./src/backend/mvnw.cmd -B -ntp -f ./src/backend/pom.xml package -DskipTests
java -jar ./src/backend/target/moments-backend-0.1.0-SNAPSHOT.jar

./tests/performance/run_local_m01.ps1 -MysqlBin '<MySQL84 bin>' -BurstOnly -DiagnosticsOff -MeWaiters 1024 -MeWaitMs 200 -BurstSizes 500,1000,2000,5000,10000 -Repetitions 2
```

22 项后端单元和真实 MySQL 集成测试全通过，包含等待超时、释放恢复、中断、认证隔离及既有权限与令牌失效语义。原始教师模板保持不变。
