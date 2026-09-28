# XiyuanMarry 2.0.0

新增纪念日奖励出站箱。系统按 `rewards.yml` 的 `anniversaries` 配置扫描正式婚姻，每个纪念日、每位夫妻成员只生成一次持久化票据。`/marry claim` 会先尝试领取奖励票据，再兼容原有物品礼物；经验和配置指令在领取阶段执行，领取中断会保留票据供核对。

金币字段已保存到票据，但本轮尚未接入 Vault Economy。未安装 Vault 时不会扣除票据，奖励会进入冻结状态并提示管理员安装前置。

验证：Java 21 下 Maven `clean package` 成功；75 项测试通过，0 失败、0 错误、0 跳过。未执行真实 Paper/Folia 服务端测试。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
