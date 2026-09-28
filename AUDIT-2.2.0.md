# 2.2.0 审查记录

- 周榜结算窗口使用配置时区；结算日前或结算小时之前沿用上一个周期，结算时刻及之后使用当前周期。
- 周榜幂等键包含周期、名次、关系 ID 和领取人，重复扫描不会重复生成票据。
- `Ticket.rank` 为新增字段，Gson 读取 2.1.0 及更早票据时缺失字段自动取 0；纪念日票据仍按 `anniversaryDays` 展示。
- 周榜奖励与纪念日奖励共享领取和 Vault 经济边界，金币失败不会删除票据。
- 静态扫描：未发现业务代码直接调用传统 BukkitScheduler、同步 `teleport`、`Future.get()` 或阻塞式 `join()`；Folia 调度集中在 `UnifiedScheduler`。
- 78 项测试全部通过；未执行真实 Paper/Folia/Vault 服务端验证。

## defer / uncertain

- `defer`：真实 Paper 1.21.11、Folia、Vault Economy、DecentHolograms 服务端联调尚未执行。
- `uncertain`：不同经济插件对负数余额、异常返回值和跨服数据库延迟的实际契约需要目标服务器验证。
