# 结婚系统 XiyuanMarry 1.3.0 验证报告

- 构建：Maven clean package 成功。
- 测试：53 项；失败 0；错误 0；跳过 0；测试套件 22。
- JAR：230560 bytes；SHA-256 D1759698B37A1DEAE7F937BC23F3F5A95B57A27BC3FEF0D1D4E754B44B0B8E7F。
- 源码 ZIP：119942 bytes；SHA-256 602D16A6EBA55EE12CE9373CE66DD9A466FE53E777C505B6BEBA67C4965270AA。
- JAR 条目：148；包含 META-INF/LICENSE：True；包含外部依赖类：False。
- 版本元数据：plugin.yml 1.3.0，api-version 1.21.11，author xiaota，folia-supported true，4 项 libraries。
- 静态扫描：未发现传统 BukkitScheduler、同步 teleport、目标 Folia 不可用事件、Future.get 或 CompletableFuture.join；唯一 Class.forName 为缓存 Folia 启动检测。
- 资源检查：9 个 GUI 布局通过解析；固定分隔板字段通过测试；消息 MiniMessage/传统颜色解析和递归非斜体测试通过；任务目录30日、12种已接入类型通过测试。
- 真实服务端：未启动 Paper 1.21.11/Folia 1.21.11；未进行客户端 GUI、跨区传送、多人压测或真实 MySQL 测试。
- defer：六类任务事件、在线累计、奖励领取/outbox、礼物/贺礼、戒指与 Buff、纪念日/周榜发放、依赖联动、完整生命周期异步化。
- uncertain：整套 reload 的数据库/缓存原子性、已在线玩家动态加载、婚礼并发退出恢复、跨服协调。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
