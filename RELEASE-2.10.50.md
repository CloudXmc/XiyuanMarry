# XiyuanMarry 2.10.50

修复婚礼站位、请帖发送和请帖回应的旧异步请求问题。玩家重登、配置重载或数据库切换后，旧操作会被丢弃，避免把旧点击写入新的婚礼状态。

自动化验证已完成：826 个测试全部通过（0 失败、0 错误、0 跳过），静态发布检查通过，成品与源码包已生成。

- JAR：`outputs/XiyuanMarry-2.10.50.jar`，421,460 bytes，SHA-256 `253E3A4E546D25A7BC52A879DA4F3D7193CA3334E3010E76C44131964F8D5A31`
- 源码 ZIP：`outputs/XiyuanMarry-source-2.10.50.zip`；大小与 SHA-256 见 `outputs/XiyuanMarry-2.10.50-manifest.json`，避免把自包含发布说明的哈希写回源码包自身。
- JAR 内容：219 个条目，Java class major 65，外部依赖 class 0 个。

本版本未上传 GitHub，未替换用户测试服 JAR；未执行真实 Paper、Folia、Vault 或 MySQL 联调。
