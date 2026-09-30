# XiyuanMarry 2.10.45 本地修复版

2026-09-30 · Java 21 · Paper/Folia 1.21.11 目标 API

## 本次变化

9 个默认 GUI 全部提供粉紫渐变“返回主菜单”按钮和教学 lore：主菜单、求婚、伴侣信息、婚礼筹备、请帖管理、发送请帖、领取箱、情侣任务、排行榜。左键通过内部 back 动作直接回到主菜单；主菜单自身点击会重新打开主菜单。

新增按钮位于第 5 行第 9 格；排行榜保留第 4 行第 9 格的已有按钮。任务周期卡仍在第 4 行第 9 格，不占用 30 天任务的展示位置。

## 更新方式

1. 备份现有插件 JAR 和配置目录，正常停止服务器。
2. 用 outputs/XiyuanMarry-2.10.45.jar 替换旧插件 JAR，避免 plugins 下保留两个版本。
3. 启动后已发布的默认 GUI 布局自动补齐按钮，无需删除配置或数据库。
4. 自定义布局及冲突的自定义动作不会被覆盖；可在 gui 文件中选择空闲槽映射 action: back。
5. 修改 GUI 配置后执行 /marryadmin reload 并重新打开。安装新版 JAR 本身必须重启，reload 不加载新 Java 代码。

## 验证与交付

- Maven clean verify 成功：817 项测试，96 个套件，失败/错误/跳过均为 0。
- JAR 元数据、YAML、依赖、Java 21 字节码、线程调用扫描通过；外部依赖类未打入 JAR。
- 成品：outputs/XiyuanMarry-2.10.45.jar；源码：outputs/XiyuanMarry-source-2.10.45.zip。
- 大小与 SHA-256 见 outputs/XiyuanMarry-2.10.45-manifest.json。
- 当前仅完成本地构建，本轮未替换用户测试服 JAR，未实机验证 Paper/Folia，未推送 GitHub或创建 Release。
- uncertain/defer 项见 AUDIT-2.10.45.md。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
