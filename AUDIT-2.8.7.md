# XiyuanMarry 2.8.7 功能完整性审查

日期：2026-09-28。工作区：F:/Codex/XiyuanMarry。原仓库：CloudXmc/XiyuanMarry。

## 结论

**功能尚未齐全。** 核心婚姻、任务、礼物、奖励、身份和数据库流程已有实现及部分自动化回归证据；存在未接入界面、无法选择多张请帖、列表容量限制、无效配置项及未完成的奖励模块。234 项测试通过仅证明现有测试覆盖的行为，不能覆盖这些缺失功能。

本次没有新增上一页、下一页、返回按钮；9 份默认 GUI 都是 45 格，最底下一行保留完整分隔板。没有进行数据库或身份数据迁移，也没有替换原 GPL-3.0 许可证及原作者信息。

## 项目边界

| 项目 | 核对结果 |
|---|---|
| 名称 / 中文名 | XiyuanMarry / 结婚系统 |
| 主指令 | /marry、/marryadmin；原别名 xmarry 保留 |
| 包名 / 主类 | cn.mcxyd.xiyuanmarry / XiyuanMarryPlugin |
| 本次版本 | 2.8.7，Bug 修复补丁版 |
| 编译目标 | Java 21、Paper API 1.21.11-R0.1-SNAPSHOT；Folia 目标 1.21.11 |
| 存储 / 身份 | 默认 SQLite、OFFLINE_NAME；MySQL 分支及 ONLINE_UUID 有实现 |
| 前置适配 | Vault、PlaceholderAPI 有代码；DecentHolograms 仅声明软依赖，ItemsAdder 无完整适配 |
| 跨服 | 不提供多实例实时同步、分布式锁或跨服业务保证 |
| 许可证 | 现有 GPL-3.0，原文件保留并纳入 JAR |

## 尚未完成的功能

以下按影响排序，均为源码核对所得；标注 defer 表示本次审查记录并保留，不能视为已解决。

| 优先级 / 分类 | 缺项与触发条件 | 代码依据及影响 |
|---|---|---|
| P1 / defer | 同时收到两张以上有效请帖时无法指定一张接受或拒绝 | WeddingService.respond 要求 candidates.size()==1；AcceptSubcommand / DenySubcommand 的参数只选择 proposal 或 invitation，不能选择新人或婚礼 ID。兼容旧指令也调用同一方法；只能等待歧义消失。 |
| P1 / defer | 伴侣信息、收件请帖、领取箱三个 GUI 没有接入 | GuiFactory.activate 中 info 调用文字指令，gifts 调用 claim，invitations 打开发送请帖；partner_info、invitation、gift 均无打开调用，MenuContentProvider.entries 对其返回空列表。YAML 文件存在不代表功能已经可用。 |
| P2 / defer | 在线列表超过 30 人无法在 GUI 中查看全量 | 默认 propose、send_invite 共 30 个 D 槽，GuiFactory.render 固定 number=0；没有筛选/搜索入口。可用 /marry normal 或 wedding ＜玩家＞、invite ＜玩家＞直接操作，不代表 GUI 已完整。 |
| P2 / defer | 排行榜允许配置 1000 条，但默认 GUI 只展示前 30 条 | rank.yml.top-size 允许 1–1000；render 与在线列表共用截断逻辑。切换排行榜类型不能显示该榜的第 31 条以后内容。 |
| P2 / defer | 多项公开配置未进入业务流程 | bond.elite-kill、bond.build-ten-blocks、bond.wedding-gift-target 没有业务读取；privileges.ring-offhand-required 未被 RingService 使用，当前始终检查副手。不能承诺修改这些设置会生效。 |
| P2 / defer | weddings.yml 场地配置未接入 | locations 和 default-location-id 仅随文件加载；WeddingService 使用数据库 weddings 桶与 /marry hunliset、setweddingloc。配置预设场地不是当前可用功能。 |
| P2 / defer | 奖励体系未全部实现 | rewards.yml.level-up 仅为预留目录；原生自定义物品奖励、任务额外金币/经验/物品奖励尚未完整接入。纪念日/周榜票据目前支持金币、经验和配置命令；任务完成奖励是羁绊。 |
| P2 / defer | 参数补全覆盖不完整 | 管理 Simple.complete 一律返回空；玩家 block、unblock、invite 等仍缺少参数候选。主指令名称补全和权限过滤存在，不等于全部参数已有补全。 |
| P2 / defer | REVIEW 人工核对流程缺少插件内管理入口 | 礼物和奖励会保守保留处理中/待核对票据，但尚无专用管理指令安全查询、裁定及恢复；不得通过直接重发绕过一次性保护。 |

### GUI 逐页核对

| 文件 | 默认容量 | 默认动态槽 | 入口 / 状态 |
|---|---:|---:|---|
| main_menu.yml | 45 | 0 | /marry 或 /marry menu，可达 |
| propose.yml | 45 | 30 | 普通/婚礼求婚按钮及相关指令，可达 |
| wedding_plan.yml | 45 | 0 | 主菜单婚礼筹备，可达；具体角色/座位仍需指令配置 |
| send_invite.yml | 45 | 30 | 婚礼筹备中的发送请帖，可达 |
| task.yml | 45 | 30 | /marry task，可达；展示 30 天任务 |
| rank.yml | 45 | 30 | /marry rank，可达；四类榜单 |
| partner_info.yml | 45 | 30 | 未接入；目前使用文字 info/partner |
| invitation.yml | 45 | 30 | 未接入；目前使用 accept/deny invitation |
| gift.yml | 45 | 30 | 未接入；目前使用 /marry claim [编号] |

所有上述默认文件的底行都是 #########，没有 P/F/N 图标定义。可达性通过 GuiFactory 和指令注册调用链核对；functional-evidence-2.8.7.json 同时记录资源容量、底栏和音效键。用户自定义布局不被视为默认布局。

## 已有实现与证据范围

| 功能 | 实现 / 验证范围 |
|---|---|
| 普通结婚 / 婚礼订婚 | propose、accept、deny、到期维护、婚礼誓词状态机；仓储与 WeddingCeremonyTest 有测试，完整游戏流程未实服验证 |
| 离婚 / 撤回 / 冷却 | MarriageService 与仓储状态转换；SQLite 事务测试 |
| 屏蔽求婚 | block/unblock 持久化并在资格检查读取；没有专门游戏端用例 |
| 在线玩家 | PlayerDirectory 采集不可变快照、登录/退出和启动补扫；GUI 列表存在上限 |
| 身份 | OFFLINE_NAME 默认、大小写选项、ONLINE_UUID 保留真实 UUID；DeliveryContractTest 覆盖解析，切换不迁移历史 |
| 情侣任务 / 共同在线 | 任务类型监听、30 天循环、一次性羁绊、共同在线账本；任务与账本测试覆盖局部流程 |
| 私聊 / 伴侣传送 | 私聊校验、传送冷却令牌、异步传送、失败处理；真实跨区域传送未测试 |
| 礼物 / 贺礼 / 领取 | 持久化快照、令牌、背包不足恢复、待核对状态；GiftSendFlowTest / ClaimFlowTest 覆盖模拟事务与回调 |
| 纪念日 / 周榜奖励 | 奖励目录、周期结算、票据和领取；AnniversaryRewardFlowTest / WeeklyRewardLedgerTest 等覆盖 SQLite 流程 |
| 戒指 | 管理发放、副手判定、开关、范围 Buff；本次补充回调线程/关闭测试，副手配置开关缺项见上表 |
| 管理 / 帮助 | 权限过滤、危险指令 confirm、消息和主入口补全；CommandHelpCoverageTest 核对所有已注册指令帮助键 |
| SQLite | 实际临时文件数据库测试；默认配置、事务、切换保留旧数据及租约关闭有覆盖 |
| MySQL | JDBC 驱动和独立 SQL 分支存在；没有执行 MySQL 8.0 连接、DDL、认证、事务或热切换集成测试 |
| Vault / PlaceholderAPI | 代码与依赖声明存在；本轮未运行真实服务端及前置联调 |

## 本次确认并修复

| 分类 | 问题与修复 | 测试证据 |
|---|---|---|
| must_fix 已修复 | 新数据库能建表但业务快照解析失败时，原先先切库再回滚会改变代次，令旧请求失效。现在在候选池内读取完整业务快照，成功后再发布；失败只关闭候选池。 | ReloadFlowTest、DatabaseSwitchTest |
| must_fix 已修复 | 停服后排队的 reload 仍可能发布配置、恢复已清空缓存。现在任务入口和内存发布检查关闭状态，缓存发布与关闭使用短生命周期锁。 | ReloadFlowTest.queuedReloadCannotPublishAfterShutdown |
| must_fix 已修复 | 管理员参数错误、异步业务失败无控制台反馈；玩家 clear/divorce 丢失操作者身份。现在保留 UUID，直接参数错误回原发送者，异步错误按玩家/控制台调度反馈。 | AdminSubcommandsTest、ReloadFlowTest |
| must_fix 已修复 | 所有默认 GUI 开关音效引用不存在于目标 API 的 ui.chest.*。改为 block.chest.open/close，兼容迁移已知错误默认键，保留其他自定义音效；NaN 音量/音调在加载时拒绝。 | GuiSoundDefaultsTest；目标 Paper API Sound 字节码检查 |
| must_fix 已修复 | 五行自定义主菜单只因底行为 #AAAAAAA# 就被自动替换。迁移现在核对完整的已知主菜单/筹备布局。 | ConfigurationDefaultsTest.customizedMainMenuWithEmptyFooterIsNotOverwritten |
| must_fix 已修复 | 戒指发放回调跨目标玩家上下文读取原发送者 Player，退休回调直接发消息；关闭后仍可能访问背包。现在先捕获发送者 UUID，再经对应实体/全局调度反馈，回调检查 closed。 | RingCallbackTest，3 个针对性用例先失败后通过 |
| suggested_fix 已修复 | /marry menu 被硬编码分支排除在帮助和补全注册表外。新增独立 MenuSubcommand，保留无参数 /marry 入口。 | CommandHelpCoverageTest |
| suggested_fix 已修复 | 排行榜文案仍提示已经删除的返回按钮。更新默认文案，仅迁移相同旧默认文本。 | 资源检查及全量构建 |
| must_fix 已验证 | 上一轮尚未完整验证的 GUI 底栏迁移、配置校验失败前延后保存、酿造区域回调读取实时库存、串行数据库切换，本次一起纳入完整构建。 | ConfigurationPrepareTest、ConfigurationDefaultsTest、完整测试及源码检查 |

候选发布后若运行状态刷新回调发生异常，插件明确提示“配置和数据库已生效，但部分运行状态刷新失败”，并记录完整控制台异常；不会重新打开旧数据库冒充未切换。此状态需要管理员查看日志并重启，不能等同于跨所有组件的原子回滚。

## Folia 修改点与生命周期

| 调用链 | 线程 / 所有者与边界 | 结束 / 清理 | 规则 |
|---|---|---|---|
| admin reload → IoDispatcher → config.prepare / DatabaseManager.switchTo → 内存发布 → directory.refresh | 命令在实体或控制台上下文；文件/数据库在 Async 串行队列；候选快照仅包含值对象；发布锁内无 IO | 候选失败关闭池；旧活动租约释放后关闭旧池；关闭时拒绝后续内存发布 | 6、9、11、21、22 |
| admin set/clear/divorce → MarriageService.admin → submit → 反馈 | 只跨边界传递 UUID、操作名、数值；数据库在 Async；玩家反馈回 Entity、控制台反馈回 Global | busy 在 finally 清理；关闭后不发送管理回调 | 5、6、11 |
| givering → RingService.give → recipient Entity → sender Entity / Global | 发起者上下文捕获 UUID；背包只在接收者实体调度回调访问；不可用回调不直接操作原 Player | queued callback 检查 closed；定时器由 close 取消，enabled 清理 | 6、7、11 |
| GUI open/click/close → GuiFactory | Inventory 在玩家实体上下文；音效修改只更正配置，不引入 Async Inventory 访问 | 关闭、退出、死亡和禁用清理请求令牌 | 6、11、19 |

### 资源与内存结论

- DatabaseSwitchTest 实测：旧活动租约可完成；被拒绝候选池关闭；不切换时校验失败不关闭原池；验证中关闭管理器时拒绝并关闭候选池。
- IoDispatcher 有 1024 项容量，关闭清队列并取消工作任务；不在 Region 等待 IO。
- 在线目录保存 PlayerSnapshot，退出/禁用取消更新任务；GUI 请求令牌清理，工厂不建立全局 Inventory 缓存。
- 婚礼、传送、任务、奖励和礼物管理器有清理入口；主类关闭顺序和 HandlerList.unregisterAll 已核对。
- **suggested_fix 未解决**：MarriageService.View 全量保存历史 profiles 并在事务后全量刷新；RingService.enabled 只在 reload/close 清理，reload 又保留历史 profiles 中的身份。规模和长期运行内存增长仍需容量策略/压力测试，不能宣称“无内存泄漏”。
- **defer**：onEnable 配置读取、数据库连接/迁移和 initialize 仍同步执行；关闭池也可能耗时。这不满足全部 IO 异步化要求，尚未改动完整启动状态机。
- **uncertain**：无堆转储、长时间压力测试、真实退出/重载交错测试，不能证明无泄漏、无死锁或所有跨区域操作安全。

## 与通用规范仍有差距

- 部分业务类仍直接读取配置树，多数子指令使用集中匿名实现，GUI 动作仍由 switch 分发；尚未完全满足每子指令独立类、配置 DTO、独立动作注册器的结构要求。
- YAML 大多只有总注释/通用补全注释，没有每个字段完整的范围、默认值和影响说明；部分婚礼/戒指设置未严格校验，未使用的 icons 也没有启动警告。
- 数据库迁移仍集中在 JdbcMarriageRepository.migrate，只有 schema 版本标记，不是独立的逐版本 Schema Migrator。
- plugin.yml 保留原 author: xiaota，尚未按新通用模板写成 xWtree，且没有 website；本次未擅自替换原作者信息。
- 这些是审查记录，不声称本轮已完成规范级全面重构。

## 实际验证

- 先运行重载/管理回归：12 项中 10 项行为断言失败、0 错误；修复后相关 15 项通过。
- 音效/布局/帮助回归：16 项中 6 项行为断言失败、0 错误；修复后纳入全量通过。
- 戒指回归在修正 Paper Plugin.namespace 测试夹具后：3 项行为断言失败、0 错误；修复后纳入全量通过。
- 最终执行 Maven -o -B -ntp clean verify，构建成功；**234 项测试、53 个测试类，失败 0、错误 0、跳过 0**。这些包含实际 SQLite 文件数据库测试与模拟 Bukkit 回调测试，不包含真实 Minecraft 服务器集成测试。
- 严格 YAML 解析、plugin.yml、帮助键、补全、权限、默认 GUI 底栏、lore/非斜体、身份及数据库切换相关现有用例均随全量执行。
- verify-release.ps1：JAR 208 条目，Java class major 65（Java 21），外部依赖 class 数量 0；公共依赖作用域和 libraries 声明核对，GPL 许可证进入 JAR。
- 传统 BukkitScheduler、绕开 UnifiedScheduler、同步 teleport、指定 Folia 不可用事件、阻塞 join/Future.get 扫描无命中。单个 get() 与共享集合候选另行核对：主要为 ThreadLocal、原子变量、局部集合、并发集合或短锁保护队列；扫描不证明运行线程所有权。
- 真实 Paper 1.21.11：未执行；真实 Folia 1.21.11：未执行；MySQL 8.0、Vault、PlaceholderAPI 实服联调：未执行。
- 最终 SHA-256 和文件大小见 XiyuanMarry-2.8.7-manifest.json / SHA256SUMS-2.8.7.txt；源码 ZIP 逐条与工作树核对。

**静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。**
