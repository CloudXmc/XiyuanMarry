# XiyuanMarry 2.10.47 戒指偏好竞态修复审查

日期：2026-09-30。目标：Java 21，Paper/Folia 1.21.11。

## 修复范围

- `RingService.toggle()` 捕获当前数据库代次，`persistPreference()` 使用 `submitAtGeneration` 写入，数据库切换后迟到的旧请求会被拒绝。
- 不再要求 `profiles` 快照已存在才写入 `ring-prefs`。玩家刚登录时资料注册与交互可能并行，偏好先按持久身份落库，随后资料注册完成不影响该偏好。
- `loadPreferences()` 也绑定读取时的数据库代次；旧库异步回调不能覆盖新库的内存开关。
- 同库 reload 保留当前内存状态，切库时清空旧代次状态后读取新库。

## 验证

- TDD：新增“首次资料提交前保存偏好”和“写入绑定数据库代次”测试；修复前首次测试失败，修复后通过。
- `mvn clean verify -DskipTests=false`：821 项测试，失败 0、错误 0、跳过 0。
- 目标回归 `RingPreferenceTest`、`RingSchedulingTest` 通过。
- 运行时对象仍只在玩家实体上下文访问；偏好读写经既有 IO 队列，未新增同步数据库 IO。
- 版本、plugin.yml、Java 21 字节码、YAML、依赖和 Folia 静态扫描将在成品校验中复核。

## 限制

- 本轮未执行真实 Paper/Folia 1.21.11、Vault 或 MySQL 联调。
- 未修改正式工作区，待验证完成后再提交并推送 GitHub。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
