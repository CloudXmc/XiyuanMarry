# 2.1.0 审查记录

- Vault API 使用 `provided` 依赖，`VaultEconomyGateway` 仅在 Vault 已启用且 Economy 服务已注册时创建。
- 金币入账发生在全球调度上下文；到账失败保留奖励票据并进入 `REVIEW`，不会回退为已领取。
- 未安装 Vault 时插件仍可加载，经验和命令奖励继续可用。
- 75 项测试全部通过；未执行真实 Paper/Folia/Vault 服务端验证。
