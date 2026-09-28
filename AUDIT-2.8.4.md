# XiyuanMarry 2.8.4 审查记录

目标 Paper/Folia 1.21.11、Java 21。范围仅为用户要求的 45 格 GUI、移除上一页/下一页/返回及保留 GUI 独立 YAML。

## 修改前 Folia 清单

- 入口：`GuiFactory.open` -> `render` -> `Bukkit.createInventory`、`Inventory#setItem`、`Player#openInventory`。点击入口：`GuiListener.click` -> `UnifiedScheduler.runEntity` -> `GuiFactory.activate`。这些调用继续处在玩家实体所有者上下文。
- GUI 文件由 `ConfigurationManager.prepare` 在配置 IO 工作队列读取为独立 `gui/<name>.yml`；校验后整体发布。新布局迁移只处理局部 YAML 数据，不读写玩家、Inventory、World 或其他实时对象。
- must_fix：旧 GUI 预设仍为六行并引用 `P`、`F`、`N`，与用户要求冲突。单页容量扩至 45 格，七种列表菜单最多呈现 30 条任务或排行条目，无可用页面切换按钮，因此清理原分页动作并限定第一页。
- 生命周期：不新增任务、共享集合、缓存、数据库访问或资源；reload 使用既有配置加载及 GUI 请求取消流程。

## 三遍复查

1. 功能与范围：9 个独立 GUI YAML 均为 5 行、每行 9 格。列表页提供最多 37 个动态槽位；任务模型的 30 天记录全部可见。主菜单和婚礼筹备功能按钮、固定分隔板格式均保留。旧默认版式只匹配六行且末行为 `P###F###N` 的配置执行迁移；其他用户图标和文字保留。
2. Folia 与性能：GUI 仍在既有玩家实体调度上下文创建和填充；无新增传统 Bukkit 调度器调用、跨 Region 访问、同步传送、阻塞 IO 或同步等待。
3. 生命周期与资源：无新任务、监听器、外部客户端或连接池。菜单 holder 和现有关闭/退出清理流程不变。

## 验证与限制

- `mvn -o clean verify` 成功：201 项测试、46 个测试类，失败 0、错误 0、跳过 0。
- `verify-release.ps1 -Version 2.8.4` 检查成功；JAR 205 个条目、Java 21 class major 65、插件外类 0。
- 已检查全部 9 个 GUI YAML 解析及布局；扫描传统调度调用、指定 Folia 不可用事件、同步传送与阻塞等待均无命中。
- 未执行真实 Paper 或 Folia 1.21.11 服务端测试。
- defer：无关的既有插件运行保证与功能限制沿用 README 已列说明。本轮没有发现需要新增 uncertain 项。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
