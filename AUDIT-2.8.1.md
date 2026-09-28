# XiyuanMarry 2.8.1 审查记录

目标：Java 21、Paper API 1.21.11 / Folia 1.21.11。范围：礼物发送、领取的事务与关闭边界。

## 修改前记录

- must_fix：/marry gift、weddinggift → GiftService → MarriageService → IoDispatcher → Repository.transaction → 提交回调。物品在玩家 Entity 上下文移除，异步仅持 UUID/字节数组；关闭时 pending=false 不等于未开始事务。close 与失败回调可各返还一次，事务开始后关闭也可能返还。命中规范 9/11；无跨 Region 等待。
- must_fix：发送预占未绑定捕获代次；婚礼确认可能跨切库写入新库。代次必须贯穿预占、确认、错误核对；只在 Async 使用数据库。命中规范 21/22。
- must_fix：Repository.transaction 已提交后 refresh 失败，原提交标记未执行。缓存刷新不得改变数据库提交结果。命中规范 11/21。
- must_fix：超时 RESERVED 自动变 COMMITTED，且确认阶段可覆盖 CLAIMING。未确认记录必须 REVIEW，确认只允许 RESERVED → COMMITTED。命中规范 11。
- must_fix：领取排队后切库/关闭仍可能修改背包；异步确认回调捕获 Player。进入 Entity 前须检查代次和关闭状态，离开 Entity 只传 live UUID。命中规范 6/11。

发送记录以原子状态管理；短锁只保护登记/关闭，不在锁内读写 Inventory 或执行 IO。排队取消、确认回滚只可申请一次返还；提交中或结果不明不自动返还。完成、失败、close 清理映射；旧回调仍持尝试状态以防重复返还。未新增线程或连接池；连接继续由 DatabaseManager 租约管理。

## 验证证据

已保存定向红灯：31 项，9 项预期失败，错误 0、跳过 0；覆盖重复返还、迟到写入、提交中关闭、代次、领取状态覆盖、RESERVED 恢复及提交标记时机。

## defer / uncertain

- defer：真实 Paper/Folia 1.21.11、Vault、MySQL 联调，跨服多实例协调，高级奖励及 DH/ItemsAdder。
- uncertain：断电、玩家离线或核心停服已拒绝实体调度时，物品返还可能无法完成；不能承诺跨游戏库存与数据库的崩溃原子性。结果不明必须人工核对，禁止盲目重发。
- defer：既有启动/关闭同步配置及数据库边界，未在本轮全面重构。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。

## 三遍复核结果

1. 功能范围：仅礼物提交/补偿/领取、共用事务提交边界及发布验证。保留主类、指令、权限、配置键、Schema 与 GPL-3.0。异常类型保留，原奖励领取及传送冷却测试无需改写。
2. Folia/性能：新增逻辑只使用现有 UnifiedScheduler；Inventory 在实体回调读写；SQL 在 IO 队列；跨边界 UUID、不可变票据和复制字节。sendLifecycle 为纯内存短锁，AtomicReference 仲裁复合状态。未增加同步 IO、跨 Region 等待、反射或同步传送。
3. 生命周期：登记与关闭互斥；完成/失败移除发送记录，关闭清空。迟到回调保存终态用于去重，不重新登记。未新增连接池、线程或监听器；沿用数据库租约、任务取消和 ThreadLocal finally 清理。未知结果冻结，不自动解冻或重复发送。

2026-09-28 最终 mvn -o clean verify：172 项，失败 0，错误 0，跳过 0，43 类。包含 100 次并发开始/关闭仲裁，测试不访问真实服务端。
verify-release.ps1 -Version 2.8.1：JAR 204 条目、字节码 65、外部类 0。详细扫描见 outputs/verification-2.8.1.json；源码包逐文件散列核对由 package-release.ps1 实际执行。

注意：全量测试曾捕获事务包装影响 RuleViolation 的 6 项回归；已保留原异常类型并追加回滚确认标记，最终全量复跑通过。