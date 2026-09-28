# XiyuanMarry 2.10.0 审查与交付记录

审查日期：2026-09-28。源码工作区：F:/Codex/XiyuanMarry。
目标：Java 21、Paper API 1.21.11-R0.1-SNAPSHOT、Folia 1.21.11。
本轮在已有 2.9.0 未提交工作上继续修复；未删除用户数据，未切换或迁移玩家身份、数据库。保留 GPL-3.0、原作者 xiaota、插件标识、基础包名及公开指令。

## 修改前问题与调用链

| 分类 | 入口及调用链 | 上下文与跨界数据 | 问题与处理 |
|---|---|---|---|
| must_fix，已修复 | 主菜单 gifts → GuiFactory.activate | 玩家 Entity | 原先仅执行 claim 文字列表；现在打开 gift.yml，读取复用 ClaimInboxService |
| must_fix，已修复 | 新人邀请 → WeddingService.respond → 仓储 | Entity 采集身份，IoDispatcher Async 内执行事务；传递 UUID/不可变快照 | 已完成婚礼或过期订婚的请帖仍可被接受；新 WeddingInvitationService 同时校验关系、有效期、收件人及仪式状态 |
| must_fix，已修复 | 多张请帖 → respond | 同上 | 无法指定婚礼，失效请帖也参与歧义检查；GUI 左键接受、右键拒绝指定婚礼，命令可附婚礼 UUID |
| must_fix，已修复 | GUI open → Async 查询 → Entity 渲染 | 仅身份、代次、令牌和条目 DTO 跨线程 | 新页面检查配置/数据库代次、身份、权限、死亡和请求令牌；关闭、退出、死亡、reload/disable 清理请求 |
| must_fix，已修复 | RingService.tick → scheduler.player | Async 遍历不可变在线快照，Entity 读库存和写药水 | 原先每次扫描均排队；卡顿区域可无限积压。现在每玩家最多一个等待回调，执行/退休/调度失败释放令牌 |
| must_fix，已修复 | RingService.start / BondAttributeService.start | 生命周期入口 | 多次 start 遗失旧任务句柄；start 幂等，closed 后不能重启，close 取消对应 timer |
| must_fix，已修复 | PlayerDeathEvent → attributes.refresh → sweep | 死亡事件及属性修改在玩家 Entity；轮询 Async 仅计算期望等级 | 死亡对象过早登记 applied 导致复活后同等级漏重算；死亡使缓存失效，死亡期间不登记属性，存活后的轮询重算 |
| suggested_fix，已处理 | 属性 reload → schedule | Async 发布配置后安排 Entity | 不再清空已有 pending 标记，同一等待回调使用当前配置，避免 reload 重复排队 |
| suggested_fix，已处理 | 在线列表 → 渲染 | Entity 读取目录不可变快照 | 增加名称/编号过滤和溢出提示，能查到默认 30 个槽位以外的玩家；普通/婚礼模式保持分别路由 |

此前尝试将“退出取消求婚”改为保留请求，但没有足够证据认定原行为为 Bug；本轮已恢复原有退出规则。戒指药水没有插件独占来源标记，未采用直接删除 SPEED/RESISTANCE 的方案，以免误删其他插件或原版药水。

## GUI 与指令

- 9 份独立 GUI YAML 均有入口；保持 45 格、底部完整 ######### 分隔板，无上一页、下一页、返回按钮。
- 主菜单新增“收到的请帖”，婚礼筹备的“发送请帖”保留原用途。
- /marry menu invitation [名称或编号]：筛选收到的有效请帖；左键接受，右键拒绝。
- /marry menu gift [名称或编号]：同时列出纪念日奖励、周榜奖励、伴侣礼物和已可领取的婚礼贺礼。
- /marry menu normal 玩家名：普通结婚模式筛选；/marry menu propose 玩家名：婚礼模式筛选。
- /marry menu send_invite 玩家名：筛选发送请帖的在线目标，支持 Tab 玩家名补全。
- /marry acceptinvitation [婚礼UUID]、/marry denyinvitation [婚礼UUID]：指定请帖；无参数兼容旧行为。
- 领取点击仍经过原 ClaimSubcommand、奖励/礼物事务和一次性令牌，没有新增直接发物品路径。待核对记录只显示，不注册可领取动作。
- 默认配置升级仅匹配已知旧布局、文案及 lore；自定义布局、Q 图标、lore、分隔板保留。

## 生命周期及内存审查

- GuiRequestTracker 仅存 UUID 与时间戳，上限 1024，10 秒过期；使用 synchronized 保护复合读改写，事件和 reload/close 显式清理。
- GUI ItemStack/Inventory 只在玩家所有者回调构造，没有加入全局缓存。异步查询返回不可变列表，回调不捕获发起者 Player。
- RingService 的开关按在线身份定期裁剪，退出后不长期积累历史玩家；回调令牌存 ConcurrentHashMap，退休/异常/close 清理。戒指开关是内存会话设置，不新增持久字段。
- BondAttributeService 使用可取消 timer、并发缓存和去重集合；属性不改基础值，只操作插件命名空间临时修饰符。
- DatabaseManager 按代次租约延迟退役旧池；配置切换和原有物品恢复流程未改写。该结论由现有 SQLite 集成回归验证，不代表 MySQL/Folia 实服已验证。
- 新请帖业务不保存实时实体、库存或 World；没有新增执行器、网络客户端或跨 Region 等待。

## 三遍审查结果

1. 功能与范围：已核对 45 格布局、9 个独立配置、菜单动作、中文文案、领取入口、Tab/Help；等级属性、SQLite 默认、身份模式、许可证及已有持久记录保持现有契约。版本因新增兼容菜单入口提升为 2.10.0。
2. Folia 与性能：新增 IO 均走既有异步事务队列，实体任务走 UnifiedScheduler；没有新增同步传送、直接 BukkitScheduler、反射调度、Future.get 或 join。共享集合扫描需结合短锁/不可变快照审阅，零关键词命中不等于跨 Region 安全证明。
3. 资源：检查重复 start、pending 释放、closed 回调防护、菜单失效令牌、数据库池租约与注销顺序；停服属性清理仍受下述生命周期限制。

## 实际验证

Maven -o -B -ntp clean verify 成功；280 项测试、60 个测试类，失败 0、错误 0、跳过 0。verify-release.ps1 通过；JAR 211 条目、Java 字节码 65、外部依赖类 0。

- TDD 失败证据：gui-routing-red.log 2 项预期失败；invitation-red.log 3 项预期失败；gui-migration-red.log 2 项预期失败；attribute-death-red.log 1 项预期失败。生命周期首轮另含 4 项失败及 1 项测试夹具异常，夹具已隔离，后续通过。
- 定向验证：菜单/请帖/配置/Help 39 项通过；属性/戒指相关首轮 16 项通过。最终完整测试数量以 verification-2.10.0.json 为准，包含后续新增边界用例。
- 构建脚本检查所有 YAML、plugin.yml、依赖 scope、Java 21 字节码、JAR 无外部库类；Folia 指定不可用事件、传统调度、绕过统一调度、同步传送和阻塞调用扫描见 verification JSON。
- 用户指定的五类 Folia 不可用事件在生产 Java 的扫描结果以 verification JSON 为准；没有通过新增事件绕开死亡恢复检查。

## 未解决项与验证边界

- must_fix，仍未解决：onEnable → new DatabaseManager 以及 MarriageService.initialize 仍同步初始化数据库/读取快照。这个启动架构问题需要独立的异步初始化状态机和失败/关闭回归；本版不能声称全面符合所有异步 IO 规范。
- must_fix，仍未解决：onDisable 提交的属性清理任务随后可能被 UnifiedScheduler.close 取消；Folia 禁用阶段也可能拒绝新实体任务。不能保证在线热卸载立即清除属性，不应将单元测试的“已安排清理”视为实服已清理。
- uncertain：真实 Paper 1.21.11、Folia 1.21.11、MySQL 8.0、Vault、PlaceholderAPI 联调和多人分区压力/堆分析均未执行。
- suggested_fix：戒指药水在失效条件、reload 或关闭后自然过期，默认最长 120 tick；不主动删除无法归属的同类型药水效果。
- defer：排行榜超量仍不分页，新增搜索主要覆盖在线玩家、请帖、收件箱；任务关键词不会改变 30 天周期显示。
- defer：等级升级奖励、自定义物品奖励、任务额外奖励、场地预设、部分旧配置键、ItemsAdder/DecentHolograms、管理参数补全尚未全部接入，不能宣称全部功能齐全。
- defer：多服分布式协调、强制断电及外部经济/命令副作用后的自动补偿仍不提供；REVIEW 数据保留人工核对。
- 审查结论是局部修复和静态检查，没有进行真实内存泄漏压力测试，也不作“无内存泄漏”保证。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
