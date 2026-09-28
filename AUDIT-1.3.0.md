# 1.3.0 修改前检查（2026-09-26）

- must_fix：任务目录声称支持12种事件，CoupleTaskListener实际只有放置、破坏和死亡监听。须补齐目录中事件并测试注册覆盖。监听器只取事件值，数据库通过有界队列异步处理。
- must_fix：EntityDeathEvent属于死者区域，直接capture(killer)跨所有者；通过统一实体调度器重新采集击杀者，只有不可变类型、数量跨边界。
- must_fix：传送失败releaseTeleportCooldown按玩家无条件删除；过期旧回调会删除新请求的冷却。改为持久化请求令牌，比较令牌后释放。离线、重载、超时均有清理路径。
- must_fix：/marry task和rank为占位，主菜单无法看到任务持久进度。连接异步读取与实体线程GUI；关闭、死亡、退出、reload后不得重开旧界面。
- must_fix：榜单读取boards.*而配置提供types.*，排序忽略按天、等级次排序；提取纯排序服务并回归。
- suggested_fix：将新增/改动指令与Tab补全拆分，未知参数友好返回；已有其他匿名指令重构列defer。
- defer：真实Folia/Paper1.21.11、MySQL、跨服、经济/物品交付、周榜自动奖励和剩余任务事件不在本轮保证范围。
- uncertain：旧启动同步IO、热切换与缓存读取的原子性需继续审查，不把构建成功当成运行验证。

没有额外源项目业务内容或许可证变更。PlayerMenu只作实现审查参考。

## 修改后的调用链与边界

1. 任务事件 → CoupleTaskListener → 玩家实体上下文采集 PlayerSnapshot → DailyTaskService 有界队列 → IoDispatcher 串行异步事务 → DailyTaskLedger 同事务更新进度和羁绊 → 通知分别回玩家实体线程。跨边界为 UUID、字符串、坐标和数量；reload/close 清空待处理队列。提交前事件可能丢失，不能称为所有事件恰好一次。
2. /marry task → GuiFactory.open → DailyTaskService.load 异步读写今日任务 → UnifiedScheduler.player → 校验配置代次、请求令牌、关系 ID → 渲染 Inventory。GuiRequestTracker 最多1024项、10秒有效；关闭/打开其他库存/退出/死亡/reload/disable 取消请求。Inventory 不进入异步数据处理。
3. /marry tp → TeleportSubcommand 在发起者上下文取快照 → PartnerTeleportService → 异步预留冷却令牌 → 对方实体线程取坐标 → 发起者实体线程验证并 teleportAsync → 回调只变更纯状态并异步比较令牌释放冷却。请求30秒超时，提交给核心后结果未知则保留冷却。旧配置回调不会操作新库。
4. 接受/拒绝指令 → RequestTarget 校验 → AcceptSubcommand/DenySubcommand → 对应求婚/请帖业务事务。参数错拼或多余参数不得落入求婚操作。
5. 取消任务 → TaskHandle.cancel → finally 移除注册项；核心取消抛异常也清理登记。UnifiedScheduler 关闭遍历逐项捕获取消异常，绑定晚于关闭的任务再次取消。

## 第一遍：功能与范围

- 两个结婚入口、包名、主类、公开指令及身份配置保留；版本升级1.3.0。
- /task、/rank、/tp、/accept、/deny 为独立实现；帮助根据权限与可用指令筛选，新增请求类型补全。
- 排行榜按配置 types.* 读取；时长按天次级排序，稳定 ID 打破平局。
- 未完成模块不伪造发放结果：gift/ring及贺礼入口返回开发中提示。共同在线榜只展示已有计数。
- 礼物、经济、戒指、六类任务与周结算等功能列为 defer；旧匿名子命令与不完整补全明确未完成。

## 第二遍：Folia 与性能

- 新增事件涉及死者/驯服实体区域时，先提取类型和值，再在玩家实体调度中取玩家位置。
- 所有官方调度器调用集中 UnifiedScheduler；实体/区域延迟至少1 tick。未使用反射调用调度器。
- GUI 和图标操作均在玩家所有者上下文；无运行期 Future.get/CompletableFuture.join 同步等待。扫描中的 String.join、目录join、AtomicReference/ThreadLocal/Supplier.get 不属于阻塞等待。
- 指令注册表启动后只读；GuiRequestTracker/WeddingCeremony 的普通集合由 synchronized 保护；其他共享运行状态为并发集合或不可变快照。局部集合无需替换。
- 已知 must_fix 仍未关闭：主类onEnable→config.load/database构造/MarriageService.initialize 同步IO；onDisable→DatabaseManager.close 可能阻塞。
- 已知 must_fix 仍未关闭：MarriageService.reload 切库/发布配置先于完整缓存校验，后续失败无法整体回退。当前只证明 DatabaseManager 自身失败回退，不证明整套热重载原子性。

## 第三遍：生命周期与资源

- 本轮请求缓存、任务队列与传送超时均有关闭/退出/reload路径；迟到GUI回调按代次与令牌丢弃。
- 数据库代次租约测试证明活动旧连接可完成、最后租约释放后关闭且不复制旧数据。
- 部分旧业务回调、完整启停、已在线玩家动态加载及婚礼并发仍为 uncertain，未做真实服务端验证。
- 实际单元/SQLite集成测试数、YAML与JAR核验明细见发行验证报告；不把静态扫描或纯单元测试当作真实线程调度证明。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
