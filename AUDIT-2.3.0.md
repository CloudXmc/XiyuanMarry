# XiyuanMarry 2.3.0 审查记录

目标：Java 21、Paper 1.21.11 API 与同版本 Folia 调度接口。保留 GPL-3.0、插件名称、主类、公开指令和权限。本轮没有部署、发布或修改真实玩家数据库。

## 修改前记录

- must_fix（已修复）：scan → scanWeekly → issueWeekly 每 30 秒按实时排名逐人入库，键含名次，换位与新入榜会重复发奖；奖励和广播属于独立事务。
- must_fix（已修复）：首次启用直接选上一周期，以当前榜单错误补发历史奖励。
- must_fix（已修复）：使用奖励项数量截榜，只有第 3 名配置时无法发奖。
- must_fix（已修复）：逐叶补全会重新添加服主删除的名次、纪念日。
- must_fix（已修复）：多个字段及 clear/putAll 在重载时可能混合榜单、时间、目录，现改为完整不可变快照和代次检查。

线程链：官方 AsyncScheduler → RewardService.scan → MarriageService.submit → 有界串行 IoDispatcher → DatabaseManager.use → Repository.transaction → WeeklyRewardLedger。跨边界仅不可变配置/关系记录、UUID、字符串、数值；无实时 Bukkit 对象及 Region 等待。提交后通过 broadcast/notifyIdentity → UnifiedScheduler.player → 玩家实体任务发送消息。

## 第一遍：功能与范围

- 分离周时钟、结算事务、票据模型、奖励目录和配置补全职责；不更改其他婚姻业务或公共命令。
- 名单、双方票据和游标同事务提交，异常回滚全部；串行 Repository 和游标只承诺单实例防重。
- 首次启用不补历史，恢复只补最近一期并明示使用当前指标；过去榜单没有快照时不伪造历史排名。
- 保留旧票据及旧发奖记录，遇到旧期记录保守封账，需管理员核对旧版部分/重复发奖。
- 消息来自语言文件；rewards.yml 补充中文用途、范围、默认、reload、数据影响；已有奖励目录和用户值保持不变。
- 既有 GUI 分隔板、非斜体文本、MiniMessage/传统色、身份、权限帮助策略、Tab 策略和数据库热切换测试继续通过；没有真实游戏内命令交互测试。

## 第二遍：Folia 与性能

- 新路径只有异步数据库事务和实体线程消息，无新实时对象跨 Region 访问、反射、同步传送或阻塞等待。
- 传统 BukkitScheduler 扫描 0；官方调度调用集中在 UnifiedScheduler；实体/区域保留 Math.max(1,ticks) 或固定 1 tick。
- 同步实体 teleport 扫描 0；唯一 .teleport 命中为 TeleportSubcommand 调用业务服务，内部转入 teleportAsync。
- join/Future.get 扫描无阻塞命中；无参数 get 候选人工确认为 Supplier、ThreadLocal、Atomic 等。
- PlayerRespawnEvent、PlayerTeleportEvent、PlayerChangedWorldEvent、WorldLoadEvent、WorldUnloadEvent 在 src/main/java 均未命中。
- 反射仅命中 FoliaSupport 启动缓存检测的 Class.forName。
- 新集合均为局部构建并复制为不可变记录；共享目录由 volatile 完整快照发布。获奖名单按名次分行，避免把大榜单写进单个 MySQL TEXT；真实 MySQL 未测试。

## 第三遍：生命周期与资源

- RewardService.close 设置 closed、取消 timer、释放目录和网关引用；新结算在 IO 执行与提交回调前检查代次和关闭状态。
- Ledger 不持有 Player/Entity/World/Inventory，也不创建额外线程池、连接池或监听器。
- 使用现有 DatabaseManager 代次连接，旧连接池等待活动引用释放；新路径不增加资源关闭责任。
- 获奖审计保留约 120 天；四个榜单各留一个防重游标，持久待领票据不按审计期限删除。
- 公告不是数据库的原子外部副作用：提交后崩溃可能漏播，不自动重新发奖或承诺恰好一次广播。

## 证据

- 旧算法的结算前误发、名次变化重复结算：2 项断言失败；旧默认补全覆盖删掉名次：1 项断言失败。均在修复前实际运行。
- 最终 Maven clean verify：2026-09-27T18:29:44+08:00，BUILD SUCCESS；110 项 / 0 失败 / 0 错误 / 0 跳过 / 36 类。
- 覆盖重启、防重、稀疏榜、空榜、并发、回滚、旧票据/标记、时钟回退、跨期恢复、清理、夏令时、首次扫描延迟及配置补全。
- JAR：190 条目，major 65；所有 class 在插件包内，无 Vault/PAPI/Paper/Gson/HikariCP 等外部类。POM 使用 provided/test，无 systemPath。
- 验证记录：outputs/build-2.3.0.log、outputs/verification-2.3.0.json。

## defer / uncertain

- defer：真实 Paper 1.21.11/Folia 1.21.11、Vault 和 MySQL 联调未执行；folia-supported 不作为实服通过证据。
- defer：多实例共用数据库没有分布式锁，不能保证跨服只结算一次。
- defer：既有启动/关闭同步配置与数据库边界、旧领取流程在数据库热切换时的恢复策略仍需专项审查；本轮不等于全插件 Folia 安全认证。
- defer：等级升级、自定义物品和任务额外奖励、DecentHolograms/ItemsAdder 适配未完成。
- uncertain：目标经济服务的线程契约、离线身份账户对应、控制台奖励命令 Folia 兼容性需实服确认。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。