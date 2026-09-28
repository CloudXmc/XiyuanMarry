# XiyuanMarry 2.9.0 功能审查

## 本次新增
- 按现有 1-10 级羁绊计算情侣属性加成：生命上限、攻击伤害、移动速度。
- 默认每级（从 1 级以上开始）增加生命上限 1.0、攻击伤害 0.25、移动速度 0.005。
- 配置位于 config.yml 的 bond.attributes，支持 /marryadmin reload，不迁移已有数据。
- 属性使用插件命名空间的临时 AttributeModifier，按等级更新，不修改基础值、不重复叠加。
- 结婚生效时应用，降级、离婚冷静期、退出和重载时清理或重算。
- /marry info 显示当前属性加成。

## 验证
- Maven clean verify：237 tests，0 failures，0 errors，0 skipped。
- Java 21 / Paper API 1.21.11-R0.1-SNAPSHOT 编译。
- 真实 Paper、Folia、MySQL 8.0 运行联调未执行。
- 静态检查、单元测试和 Maven/Gradle 构建成功，不等于真实 Folia 服务端运行测试。

## 仍需关注
- 邀请、礼物两个独立 GUI 仍未接入实际页面；伙伴信息 GUI 已接入并显示属性加成。
- 列表 GUI 超过 30 个条目仍无搜索入口。
- 真实服务端线程、属性与其他插件属性冲突需要联调。