# XiyuanMarry 1.9.0

本轮优化统一了跨世界传送边界。婚礼与伴侣传送服务现在只传递不可变世界 UUID、坐标和朝向，由 `UnifiedScheduler` 创建目标位置并调用异步传送，减少业务层直接接触 Paper/Folia 世界对象的机会。

本轮未引入奖励半成品；纪念日、周榜和 Vault 奖励仍保留在后续迭代。

验证：Java 21 下 Maven `clean package` 成功；73 项测试通过，0 失败、0 错误、0 跳过。未执行真实 Paper/Folia 服务端测试。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
