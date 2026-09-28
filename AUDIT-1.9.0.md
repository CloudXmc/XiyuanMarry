# 1.9.0 审查记录

- `WeddingService` 和 `PartnerTeleportService` 不再直接调用 `Bukkit.getWorld` 或创建目标 `Location`。
- `UnifiedScheduler.teleportAsync(Entity, UUID, coordinates...)` 接收不可变传送 DTO 字段，并将世界解析与异步传送集中在调度边界。
- 传送仍使用 `Entity#teleportAsync`，没有同步等待或阻塞式 `join/get`。
- 73 项测试全部通过；真实 Paper/Folia 服务端仍未运行验证，因此目标核心中的世界解析线程契约列为 `uncertain`，不能视为已完成运行兼容。
