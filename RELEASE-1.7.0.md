# XiyuanMarry 1.7.0

本版本新增 PlaceholderAPI 适配。服务器安装 PlaceholderAPI 后，插件会注册 `mythicmarry` expansion；没有安装时插件仍可正常启动。适配只读取婚姻服务发布的不可变快照，并在插件关闭时注销扩展。

支持状态、伴侣名称、羁绊等级/称号/数值、结婚天数、四类榜单字段和玩家所在情侣名次。详见 README 中的变量表。

验证：Java 21 下执行 Maven `clean package` 成功；71 项测试通过，0 失败、0 错误、0 跳过。JAR 包含本插件 PlaceholderAPI 适配类，不包含 PlaceholderAPI 本体。

本版本没有执行真实 Paper 1.21.11 或 Folia 服务端测试。静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
