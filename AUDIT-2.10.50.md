# 2.10.50 审查记录

## 本轮修复

- 婚礼站位设置、婚礼请帖发送和请帖回应改用玩家快照提交入口。
- 旧请求现在绑定登录会话、配置代次和数据库代次；重登、reload 或切库后不会继续写入婚礼状态。
- 保留原有订婚有效期、请帖有效期和婚礼进行中校验。
- 新增婚礼站位请求在玩家重登后被拒绝的回归测试。

## 验证

- Maven `clean verify`：成功。
- 测试结果：826 个测试，0 失败，0 错误，0 跳过；97 个测试套件。
- 重点婚礼测试：`WeddingFlowTest`、`WeddingInvitationFlowTest` 全部通过。
- 静态检查：成功，报告见 `outputs/verification-2.10.50.json`。
- JAR 检查：219 个条目，Java class major 65（Java 21），未发现外部 class 混入。
- 发布产物：JAR SHA-256 为 `253E3A4E546D25A7BC52A879DA4F3D7193CA3334E3010E76C44131964F8D5A31`；源码 ZIP 的大小和 SHA-256 记录在 `outputs/XiyuanMarry-2.10.50-manifest.json`。
- 调度扫描：传统 BukkitScheduler 命中 0；同步传送命中 0；Folia 不可用事件命中 0；阻塞等待命中 0。
- 反射扫描：仅发现启动期 Folia 检测 `FoliaSupport`。

## 运行限制

- 本轮未替换 `E:\admin\Desktop\PAPER测试端` 插件。
- 未执行本轮 2.10.50 的真实 Paper/Folia/Vault/MySQL 联调。
