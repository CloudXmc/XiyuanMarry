# XiyuanMarry 2.2.0

本轮完成情侣周榜奖励。插件按 `rewards.yml` 中的榜单类型、结算星期和小时，在配置的时区计算当前结算周期，使用现有 `RankingService` 选出配置数量的名次，为情侣双方分别创建幂等奖励票据。

周榜票据包含实际名次，旧版本序列化票据缺少该字段时按 Gson 默认值兼容读取；金币仍通过 Vault Economy 入账，Vault 缺失或入账失败时票据冻结为待核对状态。周榜奖励和纪念日奖励均由 `/marry claim <编号>` 领取。

验证：Java 21 下 Maven `clean package` 成功；78 项测试通过，0 失败、0 错误、0 跳过。静态扫描未发现业务代码直接使用传统 BukkitScheduler、同步传送、`Future.get()` 或阻塞式 `join()`；成品 JAR 未打包 Vault、PlaceholderAPI 等外部 API 类。未执行真实 Paper/Folia/Vault 服务端测试。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
