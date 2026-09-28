# XiyuanMarry 2.4.0 审查记录

目标：Java 21、Paper/Folia 1.21.11，保留 GPL-3.0、作者 xiaota、公开指令和权限。本轮只修改奖励领取链路及其测试。

## 修改前 must_fix

- `RewardService.claim` 直接通过 `MarriageService.submit` 使用当前数据库；数据库切换后延迟回调可能在新库查找、恢复或删除同编号票据。
- `CLAIMING` 只有状态没有领取 token；旧回调在切库回切或后续重新领取时可能消费新的尝试。
- 经验、经济、命令副作用与票据状态确认分散，关闭、断线和异常路径可能重复尝试或误回到 COMMITTED。
- 领取列表只展示 COMMITTED，CLAIMING/REVIEW 票据可能被用户误认为不存在。

## 第一遍：功能与范围

- 新增 `RewardClaimService`、`RewardClaimLedger`、`RewardClaimAttempt` 和 `RewardInbox`，没有改变求婚、婚礼、任务、排行榜等业务。
- 领取指令仍先尝试奖励，找不到才回退礼物；待核对票据明确展示编号。
- 票据预占、token 写入和状态确认使用事务；旧数据库代次不通过校验时不会写入当前数据库。

## 第二遍：Folia 与性能

- 经验只在玩家实体调度器上下文执行；经济与控制台命令在全局调度器执行；数据库和文件 IO 仍在 IoDispatcher 异步队列。
- 领取操作有界为 128 个进行中请求；每个请求 30 秒超时并可取消；共享 pending 使用 ConcurrentHashMap。
- 没有新增传统 BukkitScheduler、同步 teleport、阻塞 Future.get/join 或跨 Region 实时对象访问。
- 经济适配仍依赖 Vault 服务提供者线程契约，需真实服验证。

## 第三遍：生命周期与资源

- reload 结束进行中的领取并优先恢复/冻结原票据；close 取消超时任务、清空进行中请求并保留无法确认的 CLAIMING 记录。
- 旧数据库代次仍按 DatabaseManager 租约关闭；领取确认带代次与 token，切换后不会触碰新库。
- 外部副作用开始后，异常或结果不明进入 REVIEW，不自动开放重试，避免重复发放。

## 测试证据

- Maven clean verify：125 项 / 0 失败 / 0 错误 / 0 跳过 / 39 类。
- 新增测试覆盖：切换离开与回切、旧代次拒绝、新库同编号保护、token 缺失、重复点击、并发单次副作用、事务回滚、重启冻结、异常列表可见性。
- `scripts/verify-release.ps1 -Version 2.4.0`：JAR 199 条目、major 65、外部类 0；调度、阻塞、不可用事件扫描通过。

## defer / uncertain

- defer：真实 Paper/Folia 1.21.11、Vault、MySQL 联调；多实例共享数据库无分布式协调保证。
- defer：旧版本已有 CLAIMING 记录缺少 token，保守保持冻结，需人工核对。
- uncertain：Vault 经济服务线程安全、OFFLINE_NAME 与经济账户映射、控制台奖励命令的服务器侧副作用。
- defer：启动/关闭的既有同步配置数据库边界、等级升级奖励、自定义物品奖励、任务额外奖励和 DH/ItemsAdder。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
