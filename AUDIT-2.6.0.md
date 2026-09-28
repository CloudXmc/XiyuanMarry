# XiyuanMarry 2.6.0 审查记录

本轮范围：实物礼物领取状态机的数据库代次、一次性 token 与异常冻结。

## must_fix

- 原 GiftService 只以 `CLAIMING` 状态识别领取。切库后旧回调可能在新数据库处理同编号记录；重复回调也缺少一次性尝试标识。
- 原物品插入背包后最终确认只校验状态，无法区分旧回调和后续领取尝试。

## 已完成

- `Gift` 票据新增 `claimToken`、`claimGeneration`。预占事务随机生成 token，并绑定当前 DatabaseManager generation。
- 所有释放、冻结和删除操作必须匹配票据 UUID、token、generation 和状态；旧 generation 通过 `DatabaseManager.use(expected, ...)` 拒绝。
- 玩家实体上下文重新采集持久身份；身份变化、离线或物品解析失败时不写入新库，票据进入 REVIEW 或保守保留状态。
- 背包不足在实体上下文恢复原内容并按 token 恢复 COMMITTED；已开始写入后不自动重试。
- 列表将 REVIEW 与 CLAIMING 作为待核对提示，避免玩家误以为领取箱为空。

## Folia 与生命周期

- SQL 仍仅在 IoDispatcher 异步队列运行；Inventory、Player 和物品写入只在 UnifiedScheduler.player 的实体上下文执行。
- 代次校验只传递 UUID、字符串、数字和不可变快照；没有跨线程保存 Player、Inventory 或 World。
- 没有新增传统 BukkitScheduler、同步 teleport、Future.get/join 或指定 Folia 不可用事件。
- GiftService 不创建线程、连接池或定时任务；插件关闭由既有 `inbox → rewards → ... → io → database → scheduler` 顺序清理。

## 测试证据

- 定向礼物/领取测试：19 项通过。
- 全量 Maven `-o clean verify`：144 项，失败 0、错误 0、跳过 0，共 40 类。
- `verify-release.ps1 -Version 2.6.0`：JAR 200 条目、major 65、外部类 0；调度、阻塞、同步传送和不可用事件扫描无命中。

## defer / uncertain

- defer：真实 Paper/Folia/Vault/MySQL 联调、跨服多实例协调、物品进入背包后进程崩溃的人工核对流程。
- defer：等级升级奖励、自定义物品奖励、任务额外奖励、DecentHolograms、ItemsAdder。
- uncertain：服务器重启后旧版本缺少 token 的 CLAIMING 礼物需要人工核对；不自动解冻。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
