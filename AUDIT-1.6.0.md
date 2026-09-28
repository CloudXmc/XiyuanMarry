# 1.6.0 修改前审查（2026-09-27）

- must_fix：婚礼 GUI 有“贺礼记录”入口，但没有独立婚礼赠送指令/持久化类型；普通伴侣礼物不能表达订婚中的宾客贺礼。沿 weddinggift、accepted invite、metadata ledger、marriage relation ID、spouse claim 实现。
- must_fix：领取状态超时自动回滚可能在物品已进入背包后再次开放领取，造成复制。数据库删除失败/领取中断必须冻结 CLAIMING 记录供人工核对，不自动重新领取；极端中断允许人工恢复。
- suggested_fix：物品序列化保存到 MySQL TEXT，Base64 和 JSON 有额外开销；本轮限制原始字节至 40,000。
- defer：物品库存与数据库无法构成原子事务；领取中断后保持 delivery-review，不承诺零人工恢复。跨服领取缺少分布式锁。
- uncertain：Paper/Folia 实服上 Inventory 和异步 SQLite/MySQL 失败注入未跑；不声称物理物品提交恰好一次。
