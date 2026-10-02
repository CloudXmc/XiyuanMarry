# 2.10.51 文本审查记录

## 本轮修改

- 正式结婚公屏提示改为：`❤️ 祝福 {player1} 与 {player2} 喜结连理！ ❤️`。
- 保留 `player1`、`player2` 占位符，继续使用粉金渐变 MiniMessage。
- 版本号更新为 `2.10.51`。
- 新增语言模板回归测试，检查爱心、成语、占位符和渐变标签。

## 验证

- Maven `clean verify`：成功。
- 测试结果：831 个测试，0 失败，0 错误，0 跳过。
- YAML 与 MiniMessage 文本测试：通过。
- 重点测试：`BrandTextTest`、`PluginMetadataTest`、`WeddingFlowTest` 全部通过。
- JAR：`outputs/XiyuanMarry-2.10.51.jar`，422,900 bytes，SHA-256 `A64FA741FDEA5F41C08668C9035A2011F474C703C3044CFA1A83156CA7FE0C4F`。
- 源码 ZIP：`outputs/XiyuanMarry-source-2.10.51.zip`；大小与 SHA-256 见 `outputs/XiyuanMarry-2.10.51-manifest.json`。

## 限制

- 本轮未执行真实 Paper/Folia 服务端联调。
