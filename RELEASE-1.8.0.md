# XiyuanMarry 1.8.0

本轮将戒指预留功能接入实际流程。`/marryadmin givering <玩家> <engagement|marriage>` 向在线玩家发放带持久标记的戒指；`/marry ring` 切换个人戒指特效。结婚戒指放在副手、双方同世界且距离不超过 `privileges.buff-distance` 时，按配置给予速度和抗性效果。订婚戒指仅作为订婚阶段物品，不触发婚后 Buff。

新增配置：`privileges.ring-materials`、`privileges.ring-effects`。所有物品和药水操作在玩家实体调度器中执行，定时扫描通过统一调度层回到实体上下文。

验证：Java 21 下 Maven `clean package` 成功；73 项测试通过，0 失败、0 错误、0 跳过。未执行真实 Paper/Folia 服务端测试。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
