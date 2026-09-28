# XiyuanMarry 2.8.2 审查记录

目标：Java 21、Paper API 1.21.11 / Folia 1.21.11。范围：纪念日奖励排队、关系校验、入箱事务及 IO 队列故障恢复。

## 修改前记录

- must_fix：RewardService.start → Async 周期扫描 → 缓存 MarriageService.View → issue → MarriageService.submit → IoDispatcher → Repository.transaction。扫描只在入队前校验关系，排队期间离婚、进入冷静期、同玩家再婚或结婚时间变化后仍使用旧数据发奖。事务内应核对当前关系、成员、状态和天数。
- must_fix：同一关系同一纪念日的双方奖励分成两个事务，第二个写入失败时留下单边票据与发奖标记。应在一个事务内生成双方缺失票据；保留既有单边标记，不重发已领取的一方。
- must_fix：奖励扫描未绑定数据库代次，旧快照任务可能在新库写入不存在关系的奖励；读取目录、入队与提交使用同一代次。

上下文：扫描与数据库全部为 Async；跨边界只传 UUID、关系记录及不可变奖励定义，不读取 Player/Inventory/World。不增加 Entity/Region 调度，不在 Region 等待。新增处理在事务返回时结束；只使用局部结果集合；无新缓存、执行器或连接池。服务关闭继续取消周期任务，待执行任务核对 closed 和目录代次。命中规范 6、11、21、22。

- must_fix：命令/服务 → IoDispatcher.submit → start → UnifiedScheduler.runAsync。调度抛异常时 draining 不复位，任务已入队且调用者无法得到 false；MarriageService 的 busy 标记无法清理，后续工作可能长期滞留。单个任务异常也会跳出本次 drain。此问题涉及所有提交入口（Entity/Async/Global），队列只存任务闭包；须保证注册失败不保留任务、队列仍可恢复，真正的 IO 必须在 Async 且不持状态锁。命中规范 9、11。

## 验证

- 纪念日红灯：11 项中 6 项预期失败，错误 0、跳过 0；记录 outputs/anniversary-red-2.8.2.log。
- 队列红灯：30 项中 3 项预期失败，错误 0、跳过 0；记录 outputs/dispatcher-red-2.8.2.log。
- 最终定向：46 项全部通过，失败/错误/跳过均 0；记录 outputs/targeted-green-2.8.2.log。
- 2026-09-28 14:47:33 +08:00，实际执行 Maven -o clean verify 成功：195 项、45 个测试类，失败 0、错误 0、跳过 0。包含真实 SQLite 触发器注入的票据/标记写入故障、回滚重试、旧关系和切库，以及 400 次并发提交、队列容量、关闭和用户 busy 清理。
- verify-release.ps1 -Version 2.8.2：JAR 205 条目、Java 字节码 65、外部依赖类 0；传统调度、业务直接调度、同步传送、指定不可用事件和阻塞调用扫描均无违规命中。反射仅 FoliaSupport 启动检测；无参 get 候选均为原子值、Supplier 或 ThreadLocal。
- 全量包含配置/YAML、GUI 布局和非斜体、Help/Tab/权限、身份契约、SQLite/数据库热切换、领取 Token 和生命周期回归。检查日志与摘要保存在 outputs/build-2.8.2.log、outputs/verification-2.8.2.json。

## 三遍复核

1. 功能与范围：只变更纪念日生成、奖励数据库代次、IO 队列及相应测试；主类仅注入日志与更新版本。现有命令、权限、存储格式和已有奖励标记不变；同一关系当前仍满足资格时才补缺失票据。
2. Folia/性能：新奖励逻辑仅在原 Async IO 队列使用数据库；跨边界为不可变关系/奖励记录和 UUID。IoDispatcher 的 Deque、draining、closed、工作句柄都由同一短锁保护；锁内只登记非阻塞异步任务，不执行数据库、文件、网络操作，不等待其他线程。任务运行和异常日志在锁外。没有新反射、同步传送或实体访问。
3. 生命周期：注册失败撤销本次任务并复位状态；拒绝请求仍由 MarriageService 清除 busy；单任务失败不重跑，已接受任务按 FIFO 继续；close 清空队列并取消句柄，活动 IO 不被同步等待。沿用 DatabaseManager 租约关闭连接池，无新执行器或监听器。新增奖励列表是局部、不可变返回值，不增加长期缓存。

发布脚本在 Windows PowerShell 5.1 实际运行，检查所有源码包条目与工作区文件 SHA-256 一致，并核对成品 JAR 复制。最终文件大小和 SHA-256 见 outputs/XiyuanMarry-2.8.2-manifest.json。

## defer / uncertain

- defer：既有启动/关闭同步配置和数据库边界、跨服协调、高级奖励以及 DecentHolograms/ItemsAdder。
- uncertain：真实 Paper/Folia 1.21.11、Vault、MySQL 联调未执行；数据库与游戏物品/外部经济不具备统一崩溃原子性，已有 REVIEW 仍需人工核对。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
