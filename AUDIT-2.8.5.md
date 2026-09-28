# XiyuanMarry 2.8.5 审查

静态检查覆盖统一调度、传统 BukkitScheduler、同步传送、Folia 不可用事件、阻塞等待、共享集合和资源关闭。已修复玩家目录启动补扫、退出竞态、GUI 请求与停服清理；数据库代次继续保持旧请求完成后再关闭旧池。

SQLite 默认配置为 data/marriages.db。MySQL 8.0 兼容性基于 Connector/J 9.2 和参数化 SQL；本轮未连接真实 MySQL 8.0 服务端，属于 uncertain，仍需部署环境做集成验证。

静态检查、单元测试和 Maven 构建成功，不等于真实 Folia 服务端运行测试。
