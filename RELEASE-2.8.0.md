# XiyuanMarry 2.8.0

目标环境：Java 21、Paper API 1.21.11-R0.1-SNAPSHOT、Folia 1.21.11。作者 xiaota，GPL-3.0。

## 修改

- 发送礼物建立内存中的不可变物品快照和持久化阶段标记。
- 插件关闭前，尚未写入数据库的发送尝试会通过玩家实体调度返还物品；已写入数据库的尝试不会盲目返还，避免礼物票据和背包物品同时存在。
- 持久化确认失败时保留 RESERVED 票据并标记 REVIEW，等待人工核对，不自动删除或重复发放。
- 插件生命周期增加 GiftService.close，执行顺序早于 IoDispatcher 和数据库关闭。

## 验证

2026-09-28，Maven `-o clean verify` 成功。146 项测试，失败 0、错误 0、跳过 0，共 40 个测试类。

新增关闭路径回归：未持久化发送会排队返还，已持久化发送不会返还；礼物 token/代次、切库保护和领取列表测试继续通过。

`scripts/verify-release.ps1 -Version 2.8.0`：JAR 201 条目、Java class major 65、外部依赖类 0；调度、同步传送、阻塞调用和指定 Folia 不可用事件扫描无命中。

## 边界

- 未执行真实 Paper/Folia 1.21.11、Vault、MySQL 服务端联调，也没有跨服分布式锁。
- 若数据库已持久化但服务器在最终状态确认前崩溃，票据会保留待核对；系统不自动返还以避免复制。
- 等级升级奖励、自定义物品奖励、任务额外奖励、DecentHolograms 和 ItemsAdder 仍未完整实现。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。


