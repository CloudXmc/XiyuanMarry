# XiyuanMarry 2.10.45 全菜单返回入口审查

日期：2026-09-30。目标：Java 21，Paper/Folia 1.21.11。许可证沿用 GPL-3.0。

## 范围与结果

- 9 个默认 GUI 均有可见的渐变“返回主菜单”按钮；主菜单自身也包含该入口。
- main_menu 使用 X，避免覆盖原 R 排行榜入口；其他页面使用 R。新增按钮位于第 5 行第 9 格；rank 保留原第 4 行第 9 格。
- 复用内置 back 动作，不执行 return-command 中的外部命令。保持全部原有功能入口、列表容量和任务日期卡位置。
- 精确识别已发布的旧默认布局并补齐返回入口；保留服主自定义标题、分隔板和布局。冲突的自定义动作不会因升级而被激活。重复 reload 幂等。
- 本轮不改变任务日序、数据库、奖励或结婚状态机。

## 调用链与边界

1. GUI 点击：GuiListener.click → 取消原点击 → 校验持有人、点击类型、槽位、防抖 → UnifiedScheduler.runEntity → 复查当前 Inventory holder → GuiFactory.activate → back → open(main_menu) → openFiltered → render。
2. 点击事件及重新调度后的绘制位于玩家实体所有者上下文。沿用至少 1 tick 的统一实体调度；本轮未新增异步实时对象访问、数据库操作或同步等待。
3. 返回主菜单取消该玩家旧 GUI 查询令牌；异步任务/收件箱查询原有代次和令牌校验继续生效。关闭、退出、死亡会 cancel，reload/close 会清空请求。未新增集合、后台任务或资源。
4. 配置：ConfigurationManager.prepare → ConfigurationDefaults.merge → upgradeAlignedMenus。新增判断仅操作当前加载的 YAML 和局部不可变列表；保存及发布仍使用项目既有流程。启动 config.load 的同步文件读取属于既有边界，本轮没有扩展。

## 三遍审查

- 功能范围：插件名、主类、公开命令、权限与业务状态保持；只增加 GUI 返回配置和默认布局迁移规则，更新版本及测试。
- Folia/性能：返回动作沿用所有者调度；传统 BukkitScheduler、不可用事件、同步传送和阻塞 join 扫描均无命中；调度器访问均位于 UnifiedScheduler。共享集合和反射候选存于 verification 报告，修改点未新增共享状态或反射。
- 生命周期：复查 GuiFactory.close/reload/cancel、GuiListener 的 close/quit/death/open 以及主类关闭入口；本轮没有新增需关闭的资源。

## 实际验证

- TDD：新增导航/迁移测试首先运行 51 项，失败 9、错误 1，均对应缺少主菜单按钮或迁移缺口；实现后目标回归 79 项全部通过。
- 完整构建：mvn clean verify -DskipTests=false，BUILD SUCCESS。
- 96 个测试套件，817 项测试，失败 0，错误 0，跳过 0。本轮新增 45 项参数化用例。
- 覆盖 YAML、字符布局、图标映射、教学 lore、MiniMessage 渐变和递归非斜体、九页返回路由、旧配置升级/幂等、自定义布局及动作保护；完整套件同时运行既有 Help、Tab、身份、数据库热切换、任务与奖励等回归。
- scripts/verify-release.ps1 -Version 2.10.45 通过：JAR 218 个条目；Java class major 65；外部依赖 class 数量 0；POM、plugin.yml、依赖作用域和许可证检查通过。
- CRLF 项目文件使用 git -c core.whitespace=cr-at-eol diff --check，结果通过。

## 未验证与保留事项

- uncertain：本轮未在真实 Paper/Folia 1.21.11 上加载或点击验证；未执行真实 MySQL/Vault 联调。
- defer：自定义布局或与新增符号冲突的配置不强制覆盖，服主需手动将内置 back 动作放入空闲槽位。
- defer：既有同步启动配置 IO、停服调度拒绝及外部前置联动边界不属于本次菜单修复范围。
- 未替换或重启用户测试服；目录中现有成品仍为 XiyuanMarry-2.10.43.jar。
- 未推送 GitHub、未创建 Release。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
