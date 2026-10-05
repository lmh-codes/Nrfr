# Nrfr

免 Root 的 SIM 卡运营商配置覆盖工具，面向 Android 16/17，单 APK 安装。

## 主要特性

- **单 APK**：下载安装即可使用
- **Android 16/17 适配**：通过 Shizuku 直连 `ICarrierConfigLoader`，受限时回退自指向 instrumentation
- **SIM 配置总览**：同时展示 SIM1 / SIM2 当前覆盖配置，并标记「已覆盖」状态
- **双卡独立配置**：可分别设置国家码与运营商名称
- **保存/还原同步**：操作完成后等待系统配置生效，界面立即刷新

## 安装方式

1. 手机安装并启用 [Shizuku](https://github.com/lmh-codes/shizuku)
2. 从 [Releases](https://github.com/lmh-codes/Nrfr/releases) 下载最新 `Nrfr-*.apk` 并安装
3. 在 Shizuku 中授予 Nrfr 权限后打开应用，选择 SIM 卡、国家码、运营商后点「保存生效」

> 无需单独编译，直接下载 Release 中的 APK 即可使用。

> Android 17 的实际行为仍可能受设备厂商 Telephony 实现影响；本仓库当前以 Android 16（API 36）编译，Android 17 真机验证需在目标设备上单独确认。

## v1.5.7 更新说明

- 双卡草稿独立：切换 SIM1 / SIM2 时互不影响，各自保存与还原
- 重启后按已保存配置重新写入覆盖；打开应用不再自动跳转 Shizuku
- 在线更新弹窗居中显示，下载文案为「正在下载安装包」，配色与界面暖灰雾蓝一致
- 保存配置时「当前状态」标题栏不再被加载指示撑开
- 检查更新按当前安装版本比对仓库最新 Release

## v1.5.6 更新说明

- 界面视觉刷新：暖灰结构色、雾蓝强调、大圆角组件
- 夜间模式修复：卡片与文字随系统深色主题切换
- 关于页在线更新：检查与下载均走本仓库 GitHub Releases 官方通道
- 已安装 Shizuku 时优先打开本机应用或请求授权，未安装再引导下载
- 更新安装缺少「安装未知应用」权限时，授权后可继续安装，无需重复下载
- 保存/还原结果提示样式优化

## v1.5.5 更新说明

- 新增在线更新按钮：从本仓库 GitHub Releases 检查并在 App 内下载 APK。
- 新增 FileProvider 安全安装流程，下载完成后交给系统安装确认。
- 打开 Nrfr 时，检测到 Shizuku 已运行但尚未授权时自动弹出一次授权提醒。
- 修复 SIM1 / SIM2 还原后系统状态栏仍保留运营商角标名称的问题。
- 还原操作增加 Telephony、OPPO 扩展和 ISubExt 多路径清理与重试。

## 从源码构建（可选）

```bash
cd Nrfr
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

## 许可证

本项目基于 **[Ackites/Nrfr](https://github.com/Ackites/Nrfr)**（原作者 [@actkites](https://x.com/actkites)）开发，遵循 **Apache-2.0** 许可证。上游许可证见 [Ackites/Nrfr LICENSE](https://github.com/Ackites/Nrfr/blob/master/LICENSE)。

## 免责声明

本工具仅供学习研究。修改运营商配置可能影响网络、漫游与区域识别，请自行承担风险。
