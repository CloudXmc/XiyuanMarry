# 1.7.0 审查记录

- PlaceholderAPI 2.11.6 以 Maven `provided` 依赖编译；`plugin.yml` 保持软依赖，Expansion 只在 PlaceholderAPI 已启用时注册，并于关闭时注销。
- 占位符解析只接收 UUID、名称、不可变 `MarriageService.View`、不可变羁绊等级表和时间戳；没有调用 `OfflinePlayer#getPlayer()`、数据库或在线实体 API。
- 榜单排序复用 `RankingService`；可解析 bond、duration、online、total 四榜的字段及个人排名。
- 单元测试覆盖婚姻变量、离线名称身份、榜单字段、个人名次、无榜记录和未知变量。
- JAR 内容扫描确认包含 `MarriageExpansion` 与 `PlaceholderResolver`，未发现 `me/clip` 类被打包。
- 全量测试 71 项：失败 0、错误 0、跳过 0。Maven `clean package` 成功。
- Folia 运行测试未执行。全项目扫描发现 `WeddingService` 在玩家调度回调中调用 `Bukkit.getWorld`；此既有路径未由本轮改动，所有权安全仍需在目标 Folia 核验，列为 `uncertain`。
- Vault、DecentHolograms、ItemsAdder、戒指 Buff、奖励 outbox、纪念日发奖、周榜自动奖励及跨服协调仍未完成。
