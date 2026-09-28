# 2.0.0 审查记录

- 纪念日奖励以 `reward-inbox` 和 `reward-issued` 两个 bucket 实现幂等出站；同一关系、里程碑和成员不会重复出票。
- 领取先将票据置为 `CLAIMING`，玩家实体上下文发放经验，全球调度上下文执行配置命令；数据库删除失败时保留核对状态。
- Vault 未安装时带金币票据转为 `REVIEW`，不会伪造到账或删除票据。
- `/marry claim` 保持原有物品礼物兼容；无奖励票据时回退 GiftService。
- 75 项测试全部通过；真实 Paper/Folia 服务端测试仍未执行。
