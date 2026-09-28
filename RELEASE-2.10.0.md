# XiyuanMarry 2.10.0

本版补齐收到请帖和领取箱 GUI，并修复失效请帖处理、戒指任务积压、重复 timer 和死亡后的属性重算路径。保留 45 格独立菜单、完整底部分隔板，未恢复分页或返回按钮。

## 使用

- /marry menu invitation：左键接受、右键拒绝选中的婚礼请帖。
- /marry menu gift：点击领取对应奖励/礼物；待核对条目不可点击。
- /marry menu normal 玩家名 或 /marry menu propose 玩家名：分别以普通/婚礼模式筛选玩家。
- /marry menu send_invite 玩家名：筛选发送对象。
- /marry menu gift 关键词、/marry menu invitation 关键词：按名称或编号筛选超过 30 条的列表。
- /marry acceptinvitation [婚礼UUID]、/marry denyinvitation [婚礼UUID]：精确处理请帖。

默认布局、教学说明及新消息只对已知旧默认值迁移，自定义值保留；没有数据库或玩家身份迁移。升级应完整重启服务端，不依赖在线卸载插件清理属性。

Java 21 / Paper API 1.21.11-R0.1-SNAPSHOT 编译，Folia 目标为 1.21.11；真实 Paper/Folia/MySQL 8.0 联调尚未执行。启动同步数据库 IO 和停服属性清理仍是已知未解决项，详见 AUDIT-2.10.0.md。

构建、测试数量、JAR/源码 ZIP 文件大小及 SHA-256 见 XiyuanMarry-2.10.0-manifest.json 和 SHA256SUMS-2.10.0.txt。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
