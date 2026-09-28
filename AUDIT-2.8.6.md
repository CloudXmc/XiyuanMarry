# XiyuanMarry 2.8.6 审查

- must_fix 已修复：BrewingStand 延迟区域回调读取旧事件对象中的 Inventory；现改为在目标区域读取实时 Inventory，槽位索引在事件回调时复制。
- must_fix 已修复：并发数据库切换可能替换未退役的连接池；切换流程现串行化。
- must_fix 已修复：2.8.4 已生成的45格 GUI 不迁移固定底栏；只匹配已知默认布局迁移，保留自定义布局与导航图标配置。
- defer：数据库构造/迁移及 MarriageService.initialize() 仍在 onEnable 同步执行，可能阻塞启动线程。转为异步需要调整启动装配和命令可用时序，列为下一轮处理。
- uncertain：MySQL 8.0 与 Folia/Paper 真实服务器未在本环境连接测试。

静态检查、单元测试和 Maven 构建成功，不等于真实 Folia 服务端运行测试。
