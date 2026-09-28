# XiyuanMarry 2.5.0 审查记录

目标：Java 21、Paper/Folia 1.21.11。本轮范围为 /marry claim 列表查询及入口参数校验；保留 GPL-3.0、作者、主类、权限、指令名称和存储结构。

## 修改前调用链与问题分类

| 分类 | 入口与调用链 | 上下文与所有者 | 跨边界数据、任务终点与风险 |
|---|---|---|---|
| must_fix，已解决 | /marry claim → MarriageSubcommands → RewardService.list / GiftService.list → MarriageService.submit | 指令在玩家 Entity；事务在 IoDispatcher Async；输出回到 Entity | 直接连续提交会争 busy；延迟串联也不能证明前一个 Async 已退出 finally。玩家回调提前执行的测试实际出现 reward-pending、reward-id、busy，礼物列表缺失。 |
| must_fix，已解决 | 列表提交 → 数据库查询 → 延迟玩家回调 | SQL 由 Async 执行；消息输出属于玩家 Entity | 数据库/配置切换可使两次查询来自不同代次，也可让旧结果延迟显示；新链路一次事务读取两类数据，并在查询及输出前验证代次。 |
| must_fix，已解决 | /marry claim UUID extra → 奖励/礼物领取 | Entity 入口 | 多余参数此前仍执行领取；现于采集玩家快照和提交任务之前拒绝。 |

新链路：MarryCommand → ClaimSubcommand → ClaimInboxService → MarriageService.submit → 一个 Repository 事务内调用两个 inboxMessages → 不可变消息键/占位符列表 → UnifiedScheduler.player → 校验代次、身份 → MessageService.send。

跨边界只传递 PlayerSnapshot、UUID、字符串、数字、布尔值和不可变列表；没有 Player、World 或 Inventory 进入数据库工作闭包。本轮没有新增网络、文件或跨 Region 阻塞。

## 第一遍：功能与范围

- 同一请求只提交一次查询，玩家快照只采集一次；真正重叠的用户请求仍受到 busy 防护。
- 独立 ClaimSubcommand；帮助内容留在 messages.yml，原 GUI gifts 动作继续调用同一 claim 指令。
- 空箱、仅奖励、仅礼物和领取中记录均测试；私有收件箱不显示其他玩家记录，待核对状态不自动重开。
- 对照 2.4.0 源码归档检查变更，只涉及本链路、测试、语言帮助、版本和发布文档；未改求婚、婚礼仪式或奖励发放状态机。
- Mockito 5.17.0 仅为 test 依赖；公共运行库仍由 plugin.yml libraries 声明，没有进入成品 JAR。

## 第二遍：Folia 与性能

- 数据库读取仍在既有异步有界串行队列内；两个列表共用同一 Repository 事务，不通过等待跨线程结果来串联。
- 玩家输出通过 UnifiedScheduler.player，沿用至少一 tick 的官方实体调度；没有调用同步 teleport 或传统 BukkitScheduler。
- 新增列表均为方法局部集合，跨线程发布前 List.copyOf；新服务仅有 volatile closed 生命周期标志，无新增共享可变 Map/Set 或缓存。
- get() 候选已核对为原子类、ThreadLocal、Supplier 读取；反射仅命中 FoliaSupport 的一次检测。
- 全项目指定 Folia 不可用事件扫描无命中，无需列出新增事件替代方案。
- suggested_fix：收件箱仍沿用已有 bucket 全量扫描，超大数据集后续可增加按接收人索引的 Repository 查询。

## 第三遍：生命周期与资源

- ClaimInboxService.close 使已排队结果失效且拒绝新请求；没有额外线程、执行器、连接池或重复任务。
- reload 不产生新服务实例；旧配置代次被拒绝，新查询读取新代次。数据库切换也检查实际数据库代次。
- 断线由实体任务退休/在线检查跳过；持久身份变化由缓存快照校验跳过。服务不保存玩家实时对象。
- 插件关闭继续调用 inbox.close、marriages.shutdown、directory.close、io.close、database.close、scheduler.close；数据库旧连接租约机制未改变。

## 验证证据

- 红灯：outputs/claim-flow-red-final.log，3 项测试中 2 项断言失败，分别是仍提交两次查询、提前实体回调导致 busy。
- 绿灯：新增 17 项真实 SQLite/受控调度流程测试全部通过。
- 最终 Maven -o clean verify：142 项，失败 0、错误 0、跳过 0，共 40 类；结束于 2026-09-27 20:39:18 +08:00。
- 覆盖现有全部 YAML、插件元数据、Help/Tab 可见性、GUI lore/非斜体、身份配置、SQLite 和数据库热切换测试。
- 发布检查：JAR 200 条目、major 65、外部依赖类 0；详细扫描候选与空命中结果见 outputs/verification-2.5.0.json。
- 本地 Mockito 自附加 Java agent、测试环境缺少 SLF4J binder，以及旧监听器使用弃用 API 产生警告；不作为真实服务端行为验证。

## defer / uncertain

- defer：真实 Paper/Folia 1.21.11、Vault、MySQL 集成与压测。
- defer：物品礼物发送/领取仍是旧状态机，尚未采用奖励领取的代次/token；切库和异常恢复需要单独加固，本轮不宣称物品交付全链路安全。
- defer：启动/关闭的既有同步配置与数据库边界、等级奖励、自定义物品奖励、任务额外奖励、DH/ItemsAdder。
- uncertain：第三方 Vault 经济实现的线程契约、奖励命令产生的外部副作用、跨进程数据库并发。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
