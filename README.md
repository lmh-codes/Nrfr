# Nrfr

免 Root 的 SIM 卡运营商配置覆盖工具，面向 Android 16/17，单 APK 安装。

## 主要特性

- **单 APK**：下载安装即可使用
- **Android 16/17 适配**：通过 Shizuku 直连 `ICarrierConfigLoader`，受限时回退自指向 instrumentation
- **SIM 配置总览**：同时展示 SIM1 / SIM2 当前覆盖配置，并标记「已覆盖」状态
- **双卡独立配置**：可分别设置国家码与运营商名称
- **保存/还原同步**：操作完成后等待系统配置生效，界面立即刷新
- **在线更新**：关于页检查更新，使用本仓库 GitHub Releases（官方 API + 官方 APK）

## 安装方式

1. 手机安装并启用 [Shizuku](https://github.com/lmh-codes/shizuku)（从该仓库 [Releases](https://github.com/lmh-codes/shizuku/releases) 下载安装）
2. 从 [Releases](https://github.com/lmh-codes/Nrfr/releases) 下载最新 `Nrfr-*.apk` 并安装
3. 在 Shizuku 中授予 Nrfr 权限后打开应用，选择 SIM 卡、国家码、运营商后点「保存生效」

> 无需单独编译，直接下载 Release 中的 APK 即可使用。

## v1.5.6 更新说明

- 界面视觉刷新：暖灰结构色、雾蓝强调、大圆角组件
- 夜间模式修复：卡片与文字随系统深色主题切换
- 关于页在线更新：检查与下载均走本仓库 GitHub Releases 官方通道（Releases API + 官方 APK）
- 已安装 Shizuku 时优先打开本机应用或请求授权，未安装再引导下载
- 更新安装缺少「安装未知应用」权限时，授权后可继续安装，无需重复下载
- 保存/还原结果提示样式优化

## v1.5.5 更新说明

- 新增在线更新：从本仓库 GitHub Releases 检查并在 App 内下载 APK
- 新增 FileProvider 安全安装流程
- 检测到 Shizuku 已运行但未授权时自动弹出授权提醒
- 修复 SIM1 / SIM2 还原后状态栏仍保留运营商角标名称的问题
- 还原操作增加多路径清理与重试

## 从源码构建（可选）

```bash
cd Nrfr
./gradlew :app:assembleRelease
```

## 许可证

本项目基于 **[Ackites/Nrfr](https://github.com/Ackites/Nrfr)**（原作者 [@actkites](https://x.com/actkites)）开发，遵循 **Apache-2.0** 许可证。上游许可证见 [Ackites/Nrfr LICENSE](https://github.com/Ackites/Nrfr/blob/master/LICENSE)。

## 免责声明

本工具仅供学习研究。修改运营商配置可能影响网络、漫游与区域识别，请自行承担风险。
