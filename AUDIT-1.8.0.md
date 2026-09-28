# 1.8.0 审查记录

- `RingPolicy` 仅使用不可变状态判断 Buff 资格，覆盖开关、戒指类型、婚姻状态、世界和距离边界。
- `RingService` 只通过 `UnifiedScheduler` 访问在线玩家的 Inventory、Location 和 PotionEffect；异步扫描只传递 `PlayerSnapshot`。
- 戒指物品使用 NamespacedKey 持久标记，不能靠普通同材质物品伪造；名称和 Lore 使用 Adventure/MiniMessage 解析。
- `/marry ring` 已从开发中提示改为真实开关；管理员 `givering` 已接入并保留 `marry.admin.give` 权限。
- 仍未执行真实 Paper 1.21.11/Folia 服务端测试；WeddingService 的 `Bukkit.getWorld` 所有权问题仍列为 `uncertain`。
