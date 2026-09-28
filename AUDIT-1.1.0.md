# 1.1.0 修改前审查（2026-09-26）

- must_fix：CoupleTaskListener → CoupleTaskService.apply → MarriageService.addBond。每次事件重新创建 target=1 的任务，可重复奖励；改为不可变事件 → 有界 IO 队列 → 同事务保存进度和羁绊。
- must_fix：EntityDeathEvent 位于死者区域，不能直接 capture(killer)；仅取得路由引用并调度到击杀者所有者线程，跨边界携带类型和坐标快照。
- must_fix：AsyncChatEvent 使用过期快照且截获所有玩家的誓言；只截获仪式参与者，调度到玩家线程重新采集。退出/死亡取消仪式。
- must_fix：CoupleTaskState.add 的 progress+amount 可溢出；先比较剩余量。
- must_fix：任务配置 COMMON_* 与事件类型不一致，任务命令为占位；验证配置并用持久进度构建 GUI。
- suggested_fix：新增传送和私聊复用统一调度器，快照跨线程，无同步等待；teleportAsync 的回调重新进入玩家上下文。
- defer：外部物品/经济奖励发放、18 种事件完整实现、跨服一致性、真实 MySQL 与 Paper/Folia 运行验证；不能以配置存在宣称完成。
- uncertain：启动同步配置/数据库初始化、数据库切换后缓存刷新失败的恢复，需继续检查。

目标 Java 21 / Paper、Folia 1.21.11。静态检查不能替代实际服务器测试。
