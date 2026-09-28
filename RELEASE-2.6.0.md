# XiyuanMarry 2.6.0

目标环境：Java 21、Paper API 1.21.11-R0.1-SNAPSHOT、Folia 1.21.11。作者 xiaota，GPL-3.0。

## 修改

- 实物礼物票据新增领取 token 与数据库代次，预占、玩家背包写入、最终删除必须匹配同一 token 和代次。
- 背包不足会恢复 COMMITTED；物品无法解析、玩家持久身份变化、玩家离线、数据库切换或最终确认结果不明确会保留 REVIEW/领取中记录，禁止自动重复发放。
- GiftService 的数据库操作通过 `MarriageService.submitAtGeneration` 执行，旧数据库回调不能写入切换后的新数据库。
- 礼物列表同时展示 CLAIMING 和 REVIEW；预占状态不再被误报为空。
- 生产装配使用带 DatabaseManager 的 GiftService 构造；兼容测试构造仍保留。

## 验证

2026-09-27 21:02:19 +08:00，Maven `-o clean verify` 成功。144 项测试，失败 0、错误 0、跳过 0，共 40 个测试类。

新增礼物领取回归覆盖 token/代次写入和旧数据库切换保护；既有领取列表回归继续覆盖重载、关闭、断线、身份变化、重复请求和一次事务读取。

`scripts/verify-release.ps1 -Version 2.6.0`：JAR 200 条目、Java class major 65、外部依赖类 0；传统 BukkitScheduler、指定不可用事件、同步传送和阻塞调用扫描无命中。

## 边界

- 未执行真实 Paper/Folia 1.21.11、Vault、MySQL 服务端联调，也没有跨服分布式锁。
- 物品已经进入背包后进程立即崩溃的最终确认仍需要管理员核对；系统选择冻结记录，避免重复发放。
- 等级升级奖励、自定义物品奖励、情侣任务额外奖励、DecentHolograms 和 ItemsAdder 仍未完整实现。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
