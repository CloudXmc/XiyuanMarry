# 1.4.0 修改前审查（2026-09-26）

目标 Java 21 / Paper 1.21.11 / Folia 1.21.11 API；本轮不声称通过真实服务端运行验证。

- must_fix：BIOME 事件 → DailyTaskService → DailyTaskLedger，异步仓库事务内缺少持久去重；双方和重启可能重复计数。去重元数据与奖励同事务更新，按关系/绝对日序隔离。
- must_fix：InventoryClickEvent → inventoryResult 直接计 ANVIL/BREW；预览、失败取物、重放药水都可刷进度。改用玩家上下文的下一 tick 结果证据，跨回调只存不可变签名，退出/死亡/关闭/reload/disable 清理。酿造完成先在所属区域确认输出，再创建有界短期计数凭证。
- must_fix：PlayerTradeEvent → traded 直接计数，缺少实际成交确认。玩家实体上下文下一 tick 核对原版成交统计增量，最多按观察到的事件数量计数。
- must_fix：PlayerMoveEvent 读取起止区域方块，且用位置差计飞行；删除该路径，玩家实体采样实际飞行统计，只在拥有当前方块区域时读群系。跨线程只传 PlayerSnapshot/数值。
- must_fix：共享在线服务未注册退出监听、未接 reload 清理；必须补齐装配，纯快照采样异步提交时间水位、累计秒数与羁绊事务。
- suggested_fix：新增奖励配置移入只读配置对象，验证布尔、时区和奖励上限，避免业务读取 YAML。
- defer：上一版启动/关闭同步 IO、整套 reload 原子性、婚礼代次绑定、奖励 outbox、外部插件适配和旧命令拆分，保留已知问题，不扩大本轮修改调用链。
- uncertain：未执行真实 Paper/Folia 1.21.11、多插件事件顺序和 MySQL 集成测试；延迟确认以保守漏计优于错误发奖为原则，具体边界见发行说明。
