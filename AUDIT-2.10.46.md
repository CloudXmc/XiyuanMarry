# XiyuanMarry 2.10.46 全量审查与戒指开关修复

日期：2026-09-30。目标：Java 21，Paper/Folia 1.21.11。许可证沿用 GPL-3.0。

## 审查范围

通读全部约 150 个源文件：服务层（婚姻、礼物、婚礼、奖励、任务、传送、戒指、羁绊属性、IO 队列、玩家目录）、仓库层（JDBC/SQLite/事务/连接池代次）、调度层、命令层、GUI 层、监听器、PlaceholderAPI 适配、消息渲染，以及全部资源文件与测试。

## 发现与修复

### RING-01：戒指开关偏好随退出丢失

- 分类：must_fix，已修复。
- `RingService.tick()` 每 2 秒用当前**在线玩家身份集合**对开关缓存执行 `retainAll`。玩家退出后其身份不在集合中，偏好被清除；重新登录时 `getOrDefault(id, true)` 回落到默认开启，`/marry ring` 的关闭选择静默失效，`RingPolicy.active` 重新给予速度与抗性加成。
- 修复：`tick()` 改为按已知资料 (`marriages.view().profiles()`) 清理，保留离线玩家偏好；开关值按持久身份写入 `ring-prefs` 元数据桶，首次激活及 reload/切库后异步回填。
- 回填使用 `putIfAbsent`：加载是异步的，玩家在加载完成前的操作不会被库中旧值覆盖。
- 新增 `RingPreferenceTest`：断言偏好写在持久身份键下，且退出后不被 `tick` 清除。

### PERM-01：`marry.admin.setexp` 声明但从未检查

- 分类：must_fix，已修复。
- `plugin.yml` 声明 `marry.admin.setexp`，但 `AdminSubcommands` 中 `setexp` 与 `setlevel` 实际都使用 `marry.admin.set`。全仓库对该节点零引用，管理员授予它不会有任何效果。
- 该节点描述为“设置羁绊值”，而 `marry.admin.set` 的描述是“设置羁绊等级和数值”，两者语义一致，故移除冗余声明而不拆分节点（拆分属功能变更，不在本轮范围）。

## 复核并否决的既有结论

以下结论来自本轮并行审查，经逐条核对源码后**不成立**，未据以修改代码：

- “嵌套事务不参与回滚，`DailyTaskLedger` / `SharedOnlineLedger` 可重复发放羁绊”（曾被列为 CRITICAL）：`JdbcTransaction.execute` 在事务开始即执行 `setAutoCommit(false)`，`JdbcMarriageRepository.sql()` 复用 `ThreadLocal` 连接，内层 `addBond` / `addOnline` 确实处于同一事务并随之回滚。
- “`state-*` 消息键永远取不到，`/marry info` 状态恒为空”：`MarriageState.MARRIED.name().toLowerCase(Locale.ROOT)` 结果为 `married`，与 `messages.yml` 的 `state-married` 完全一致；`DIVORCE_PENDING` 同理。
- “`gui/task.yml` 的周期卡 H 被 30 条任务覆盖，且任务菜单无分页”：该布局的 `dynamicSlots()` 为 0–34 共 35 个连续槽位，`H` 位于槽 35，不重叠；`TaskPeriodCardTest` 已断言该布局。
- “奖励票据超时竞态可重复发放”：`RewardClaimLedger` 以 `CLAIMING` 状态加一次性 token 校验，恢复/确认均要求 token 匹配，重复发放路径不成立。
- “酿造任务凭证可被客户端刷新伪造”：`BrewCreditRegistry.produce` 仅由服务端 `BrewEvent` 经区域调度触发，玩家无法直接提交凭证。
- “任务事件数值未钳制，单次事件即可完成任务”：`CoupleTaskListener` 传入的 `amount` 全部源自服务端侧统计或事件计数，取值很小且未受玩家控制。

## 调用链与线程边界

修复只改动 `RingService` 的缓存清理依据、新增一次异步偏好读取与一次异步偏好写入，均经由既有 `MarriageService.submit` → `IoDispatcher` 串行队列，不新增调度器、不新增同步 IO、不在实体线程执行数据库操作。戒指物品与药水效果仍只在玩家实体所有者上下文访问。

## 验证

- 完整构建：`mvn -o -B clean package -DskipTests=false`，BUILD SUCCESS。
- 819 项测试，失败 0、错误 0、跳过 0。本轮新增 2 项 `RingPreferenceTest` 用例，并修正 `RingSchedulingTest` 中因 `tick` 新增只读调用而失效的 `verifyNoInteractions` 断言（该用例本意是约束回调自身不触碰服务，故改为在 `tick` 后清理调用记录再断言）。
- JAR：218 个条目，Java class major 65，外部依赖 class 0，含 `plugin.yml` / `rewards.yml` / `messages.yml` / `META-INF/LICENSE`。
- `scripts/verify-release.ps1` 静态扫描通过：无传统 BukkitScheduler、无同步传送、无阻塞 join、无非调度层调度器访问。

## 未验证与保留事项

- uncertain：本轮未在真实 Paper/Folia 1.21.11 上加载或运行验证；未执行真实 MySQL/Vault 联调。
- uncertain：偏好回填依赖玩家登录资料已存在；首次启用且资料尚未写入时，该玩家首次开关仍按默认开启，写入偏好后即持久化。
- defer：右键在除 `invitation` 外的界面被静默忽略、`/marry partner` 与 `/marry info` 行为相同、管理员命令接受并忽略多余参数等既有低危项，本轮未改动。
- defer：`config.yml` 中 `bond.elite-kill`、`bond.build-ten-blocks`、`bond.wedding-gift-target`、`privileges.ring-offhand-required` 及 `weddings.yml` 尚未被业务读取（README 已声明）。
- 未推送 GitHub、未创建 Release（本机无 git 与仓库凭据）。

静态检查、单元测试和 Maven 构建成功，不等于真实 Folia 服务端运行测试。
