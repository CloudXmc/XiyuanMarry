# 结婚系统 XiyuanMarry 2.8.3

目标：Java 21、Paper API 1.21.11-R0.1-SNAPSHOT / Folia 1.21.11。作者 xiaota，GPL-3.0。

## 本轮变更

- 源码、资源和项目文档的旧中文名称统一改为结婚系统。
- 聊天前缀、玩家帮助、管理帮助、戒指说明、主菜单标题使用 MiniMessage 渐变；粉色到淡紫为主色，戒指与管理帮助沿用暖金配色。
- 插件名 XiyuanMarry、包名、主类、/marry、/marryadmin、权限与数据目录保持原值。
- 旧版完全未修改的默认前缀、帮助标题、戒指说明与主菜单标题在启动或 reload 时更新；自定义配置不覆盖，数据库和玩家物品不自动修改。
- 保留既有固定 GUI 分隔板格式。

## 验证

新增 5 项回归：资源旧名称扫描、插件标识、实际渐变颜色、旧默认文案保存及重复加载、旧主菜单标题升级、布局与分隔板保护、自定义文本保护。先运行时有 2 项升级断言失败；修复后 5 项通过，失败 0、错误 0、跳过 0。

全量构建结果以 outputs/build-2.8.3.log 和 outputs/verification-2.8.3.json 为准。成品路径、大小和 SHA-256 见 outputs/XiyuanMarry-2.8.3-manifest.json。

## 升级与边界

停服后替换插件 JAR 并重启；无需删除配置或数据库。已自定义的消息如仍包含旧中文名，请只替换相应文字并执行 /marryadmin reload。历史玩家持有物品保留原始 ItemMeta，不批量改写。

- defer：此前未完成的高级奖励、DecentHolograms/ItemsAdder、跨服协调及既有启动/关闭同步配置与数据库边界，本轮未扩展。
- uncertain：本轮未执行真实 Paper/Folia 1.21.11、Vault、PlaceholderAPI 或 MySQL 联调；已保存的自定义配置和玩家物品未作真实服务器扫描。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。

2026-09-28 本轮实际验证：Maven -o clean verify 成功；200 项测试、46 个测试类，失败 0、错误 0、跳过 0。verify-release.ps1 通过；JAR 205 条目、Java 21 字节码版本 65、外部依赖类 0。传统调度、绕过统一调度、同步传送、指定不可用事件和阻塞调用扫描均无违规命中。
