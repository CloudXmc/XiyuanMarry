# XiyuanMarry 2.1.0

本轮接入 Vault Economy。纪念日奖励的 `money` 字段会通过 Vault 注册的经济服务入账；未安装 Vault、没有经济服务或存款失败时，奖励票据不会删除，会进入 `REVIEW` 并提示玩家联系管理员。

Vault 仍是软依赖，缺失时插件正常启动，经验和配置指令奖励不受影响。

验证：Java 21 下 Maven `clean package` 成功；75 项测试通过，0 失败、0 错误、0 跳过。未执行真实 Paper/Folia/Vault 服务端测试。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
