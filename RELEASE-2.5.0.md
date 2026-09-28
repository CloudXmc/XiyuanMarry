# XiyuanMarry 2.5.0

目标环境：Java 21、Paper API 1.21.11-R0.1-SNAPSHOT、Folia 1.21.11。作者 xiaota，GPL-3.0。

## 修改

- /marry claim 无参数时，在同一次数据库事务中读取奖励与礼物，并在一次玩家实体回调中输出。消除同一命令的两次提交竞争 busy 锁的问题。
- 查询结果绑定数据库与配置代次；切库、配置重载、关闭、断线或持久身份变化后，旧排队结果不再显示。
- ClaimSubcommand 独立负责入口参数校验；只接受零个参数或一个 UUID，多余参数不会进入领取流程。
- 礼物按创建时间、编号稳定排序；只有处理中礼物时，不再同时提示礼物箱为空。
- 帮助文案明确区分查看与领取，采用暖金到粉色渐变。既有用户语言配置继续保留，不自动覆盖。
- 保留奖励预占、token、领取发放逻辑与礼物回退入口；没有新增数据库表或执行数据迁移。

## 验证

2026-09-27 20:39:18 +08:00，Maven -o clean verify 成功。142 项测试，失败 0、错误 0、跳过 0，共 40 个测试类。

新增 17 项流程回归使用真实 SQLite、MarriageService 与 IoDispatcher，服务器调度、玩家和消息发送由测试对象控制；覆盖单次事务、提前执行的实体回调、参数校验、空箱、单类记录、待核对记录、权限过滤、切库前后、重载、关闭、断线、身份变化和重复请求。

先复现再修复的证据：outputs/claim-flow-red-final.log 中两项断言失败；修复后 outputs/claim-flow-green-final.log 与 outputs/claim-flow-boundaries.log 通过。最终全量输出为 outputs/build-2.5.0.log。

scripts/verify-release.ps1 -Version 2.5.0：JAR 200 条目、Java class major 65、外部依赖类 0；传统 BukkitScheduler、指定不可用事件、同步传送和阻塞调用扫描无命中。

## 边界与后续

- 未进行真实 Paper/Folia 1.21.11 服务端测试，未进行真实 Vault、MySQL 联调。
- 物品礼物发送/领取的旧状态机尚未统一到奖励领取的代次/token 机制，本轮只修复列表查询；这部分仍需专项审查，不提供跨实例或切库期间物品交付的完整安全保证。
- 等级升级奖励、自定义物品奖励、情侣任务的额外金币/经验/物品、DecentHolograms 和 ItemsAdder 仍未完整实现。
- 没有跨服多实例分布式协调；旧版本缺少 token 的 CLAIMING 票据仍需人工核对。

升级请保留插件目录和数据库备份，替换 JAR 后重启；已有配置不会被本轮默认消息覆盖。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
