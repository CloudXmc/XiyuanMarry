# XiyuanMarry 2.10.46 审查修复版

2026-09-30 · Java 21 · Paper/Folia 1.21.11 目标 API

## 本次变化

- 修复戒指开关偏好丢失：原 `RingService.tick()` 每 2 秒按**当前在线玩家集合**清理开关缓存，玩家退出即被移除，重新登录会静默恢复默认开启（`/marry ring` 的关闭状态丢失）。
- 开关偏好现按持久身份存入数据库 `ring-prefs`，登录后异步回填；退出、重登、reload 与切库后均保持。
- 回填使用 `putIfAbsent`，玩家在加载完成前的操作不会被库中旧值覆盖。
- 移除 `plugin.yml` 中无效的 `marry.admin.setexp` 权限：该节点从未被任何代码检查，授予它不会有任何效果（`setexp` 与 `setlevel` 实际都由 `marry.admin.set` 控制，与该节点描述一致）。
- 新增回归测试 `RingPreferenceTest`：验证偏好写入持久身份键，以及退出后偏好不被清除。

## 更新方式

1. 备份现有插件 JAR 和配置目录，正常停止服务器。
2. 用 `outputs/XiyuanMarry-2.10.46.jar` 替换旧插件 JAR，避免 plugins 下保留两个版本。
3. 启动即可，无需删除配置或数据库。
4. 已删除的 `marry.admin.setexp` 权限节点不会影响既有权限组；若曾显式授予，`setexp` 仍由 `marry.admin.set` 控制。

## 验证与交付

- 源码审查：本轮通读约 150 个源文件；子代理报告中 7 条高危结论经逐条核对源码后推翻（含被列为 CRITICAL 的“嵌套事务不回滚”——`JdbcTransaction` 已 `setAutoCommit(false)`，内层写入确在事务内）。
- 未执行真实 Paper/Folia/Vault/MySQL 联调。
- 未执行完整 Maven 构建验证（本机构建受权限策略限制），改动仅涉及 `RingService`、`plugin.yml`、版本号与新增测试。

## 未验证与保留事项

- uncertain：未在真实 Paper/Folia 上运行验证；未执行真实 MySQL/Vault 联调。
- uncertain：戒指偏好回填依赖玩家登录资料 (`profiles`) 已存在；首次启用且资料尚未写入时，该玩家首次开关仍按默认开启，写入偏好后即持久化。
- defer：右键在除 `invitation` 外的界面被静默忽略、`/marry partner` 与 `/marry info` 行为相同等既有低危项，本轮未改动。
- 未推送 GitHub（本机无 git 及仓库凭据），未创建 Release。

静态检查与单元测试通过，不等于真实 Folia 服务端运行测试。
