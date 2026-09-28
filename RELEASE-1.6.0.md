# 结婚系统 XiyuanMarry 1.6.0

新增婚礼贺礼：宾客使用 `/marry weddinggift <新人>` 将主手物品存入新人领取箱；必须已经接受对应婚礼请帖。贺礼绑定婚姻关系 ID，仪式完成后夫妻任一方可使用 `/marry claim` 查看并领取一次，其他玩家及其他关系不能领取。

领取流程先持久化 `CLAIMING`，再在玩家实体上下文写入背包，最后删除礼物账本。为避免数据库故障或进程中断导致重复领取，结果不确定的 `CLAIMING` 不会自动超时重开，需管理员人工检查。物品原始序列化限制 40,000 字节，避免 Base64/JSON 超过 MySQL `TEXT` 容量。

验证：Maven clean package 成功；67 项测试，失败 0、错误 0、跳过 0。未执行真实 Paper/Folia 1.21.11 或 MySQL 故障注入测试。

仍未完成：戒指 Buff、奖励 outbox、纪念日与周榜自动发放、Vault/PlaceholderAPI/DecentHolograms/ItemsAdder 适配、跨服协调。

静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。
