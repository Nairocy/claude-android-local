# 贡献指南

## 项目定位

这是一个**像素级复刻 Claude 安卓客户端**的开源项目，但只保留「本地 API」这一条路线：

- 单 Activity + Jetpack Compose，不引入第二套 UI 框架
- 零账号 / 零云端 / 零遥测：任何依赖服务端的功能都不进本仓库
- 只对接 OpenAI 兼容协议，不为单一厂商加私有参数
- UI 改动必须能用 Paparazzi 在纯 JVM 出图验证（不需要模拟器/真机）

## 红线（PR 会被直接关闭）

1. 提交任何 API Key、token、`keystore.properties`、`local.properties`、签名文件
2. 引入收集用户数据的 SDK：统计、崩溃上报、广告、推送、社交登录
3. 把模型请求发往用户配置的 `baseUrl` 之外的任何地址
4. 提交 `build/`、`app/src/test/snapshots/images/` 等构建产物
5. 修改 `LICENSE` 或删除「非官方、与 Anthropic 无关」的声明

## 本地开发

```bash
# 需要 JDK 17 + Android SDK 36
./gradlew assembleDebug          # 编译
./gradlew recordPaparazziDebug   # 生成截图 + 跑全部单测（必须 0 失败）
```

代码风格：

- Kotlin 官方风格，4 空格缩进，Compose 函数 PascalCase
- 注释写「为什么」，不写「是什么」；踩过的坑要留在代码旁边
- 新增页面沿用现有配色（`ClaudeTheme.colors`）与设置页的卡片口径（15dp 圆角 / 56dp 行高 / 2dp 分隔）

提交信息：`模块: 做了什么`，例如 `settings: 新增 API 设置页`。

## 有意不做

以下方向已经明确排除，不必再提 issue：

- Anthropic 原生 `/v1/messages` 协议（只做 OpenAI 兼容）
- 内置联网搜索 / 网页抓取
- 账号、登录、订阅、支付、额度
- 云同步 / 云备份 / 多设备
- 语音输入、TTS、语音通话
- 换肤系统（只跟随系统深浅色 + 字体样式）

## 报告问题

请附上：

- 设备型号与系统版本
- App 版本（设置 → API 设置 → 底部版本号）
- 上游接口类型（DeepSeek / OpenRouter / Ollama / 自建…）与模型名
- 复现步骤与 `adb logcat` 中相关片段（**不要贴完整 API Key**）
