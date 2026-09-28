# XiyuanMarry 2.8.3 审查记录

目标：Java 21、Paper API 1.21.11 / Folia 1.21.11。本轮范围：中文显示名称、渐变文案和原版默认文案升级。

## 修改前记录

- must_fix：默认资源替换后，ConfigurationManager.prepare → ConfigurationDefaults.merge 只补缺失键，旧版已存在的默认消息和主菜单标题仍保持旧名称。新增测试先验证出 2 项失败。
- 生命周期入口：插件启动调用 config.load → prepare；运行中 /marryadmin reload → MarriageService.reload → IoDispatcher → config.prepare。加载器已有文件读写边界不变；本轮只在局部 YamlConfiguration 上比较字符串并更新完全匹配的旧默认值。
- 跨边界数据：资源名、字符串、局部配置对象；不读取 Player、Inventory、World，不新建任务、缓存、执行器、连接池或外部客户端。方法返回即结束，没有新的取消或关闭资源。
- defer：启动时已有同步配置/数据库工作，本轮不重构。reload 沿用既有 Async IO 队列，不增加 Region 阻塞。

## 本轮验证

- outputs/brand-red-2.8.3.log：5 项，2 项预期业务断言失败，错误 0、跳过 0。
- outputs/brand-green-2.8.3.log：5 项全部通过，失败 0、错误 0、跳过 0。
- 新测试实际解析 MiniMessage 并检查结婚系统四字颜色均非空且不同，避免仅有标签而未显示渐变。
- 全量测试与构建输出保存在 outputs/build-2.8.3.log；最终静态扫描、字节码、JAR 条目和测试总数由 outputs/verification-2.8.3.json 记录。
- 打包时逐个核对源码 ZIP 条目与工作文件 SHA-256，并核对 JAR 副本；最终 SHA-256 见 outputs/XiyuanMarry-2.8.3-manifest.json。

## 三遍审查

1. 功能与范围：保留插件标识、命令、权限、存储契约。默认前缀、帮助、戒指说明和主菜单为渐变新名称；仅完全匹配旧默认值的 5 个展示字段升级，自定义文本、布局和固定分隔板保留。
2. Folia/性能：新增逻辑只处理字符串及既有配置对象，无新的实时对象访问、反射、数据库 IO、调度、同步传送或跨区域等待。局部列表只读，不引入共享集合。
3. 生命周期：沿用既有配置加载保存发布流程，不新增长期引用或资源。重复合并无进一步变化，保存后重新读取维持新文案。

## defer / uncertain

- defer：旧版文档列出的高级奖励、DH/ItemsAdder、跨服协调和启动/关闭边界，本轮范围不变。
- uncertain：真实 Paper/Folia 1.21.11 及软依赖联调未执行；服务器已有自定义配置及物品未扫描。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。

2026-09-28 本轮实际验证：Maven -o clean verify 成功；200 项测试、46 个测试类，失败 0、错误 0、跳过 0。verify-release.ps1 通过；JAR 205 条目、Java 21 字节码版本 65、外部依赖类 0。传统调度、绕过统一调度、同步传送、指定不可用事件和阻塞调用扫描均无违规命中。
