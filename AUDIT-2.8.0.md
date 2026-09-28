# XiyuanMarry 2.8.0 审查记录

本轮范围：发送礼物时主手物品移除与数据库预占之间的关闭窗口。

## must_fix

- 原流程先移除主手物品，再提交 RESERVED 票据。插件在 IO 任务开始前关闭时，既没有票据也没有返还路径，可能丢失物品。
- 失败处理若同时盲目返还而票据已经持久化，可能形成物品复制。

## 已完成

- GiftService 为每个发送尝试保存 UUID、发送者 live UUID、不可变物品字节和 `persisted` 阶段标志；不保存 Player 或 Inventory。
- 只有 RESERVED 事务提交回调成功后才将阶段标志设为 true；事务回滚或提交失败仍按未持久化处理并返还物品。关闭或失败时仅对未持久化尝试自动返还；已持久化尝试保留票据并进入 REVIEW 处理路径。
- 生产 onDisable 在 IoDispatcher 关闭前调用 GiftService.close；返还仍通过 UnifiedScheduler.player 进入玩家实体上下文。
- 发送和婚礼贺礼两条路径共用相同关闭保护；礼物领取的 token/generation 确认逻辑保持不变。

## Folia、复制与资源

- 物品读写继续只发生在玩家实体上下文；数据库操作在 IoDispatcher 异步队列执行。
- 跨线程只传递 UUID、字节数组副本、状态标志和不可变坐标/快照；没有实时 Bukkit 对象进入异步闭包。
- 不新增线程、连接池、传统 BukkitScheduler、同步 teleport、Future.get/join 或不可用事件。
- close 不等待数据库或其他 Region，不在关闭阶段同步阻塞。

## 验证证据

- 定向礼物/领取测试：21 项通过。
- 全量 Maven `-o clean verify`：146 项，失败 0、错误 0、跳过 0，共 40 类。
- `verify-release.ps1 -Version 2.8.0`：JAR 201 条目、major 65、外部依赖类 0；静态调度扫描无命中。

## defer / uncertain

- defer：真实 Paper/Folia/Vault/MySQL 联调、跨服多实例协调，以及服务器在数据库持久化后立即崩溃时的人工 REVIEW 处理。
- defer：等级升级奖励、自定义物品奖励、任务额外奖励、DecentHolograms、ItemsAdder。
- uncertain：离线关闭时无法进入玩家实体上下文的未持久化物品返还，需要管理员根据服务器停服前状态核对。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。


