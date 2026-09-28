# 结婚系统 XiyuanMarry 1.5.0

本轮实现礼物领取箱的持久化链路：

- `/marry gift` 读取主手物品并保存不可变 Paper 物品序列化快照，主手物品在持久化前移出。
- 礼物状态使用 `RESERVED`、`COMMITTED`、`CLAIMING`，只有 `COMMITTED` 礼物可见和可领取。
- `/marry claim` 列出待领取编号，`/marry claim <编号>` 领取指定物品。
- 领取前在玩家实体上下文保存背包快照；背包不足或最终删除事务失败时恢复背包和礼物状态，避免重复领取。
- `CLAIMING` 超过 5 分钟会在下一次查询时恢复为可领取状态。
- 主菜单的领取箱按钮现在会调用领取箱流程；所有礼物文本使用渐变 MiniMessage 消息。

仍未完成：婚礼贺礼独立账本、戒指物品与 Buff、Vault/PlaceholderAPI/DecentHolograms/ItemsAdder 适配、奖励 outbox、纪念日和周榜自动发放，以及真实 Paper/Folia/MySQL 运行测试。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
