# Nrfr

免 Root 的 SIM 卡运营商配置覆盖工具，支持 Android 16+，单 APK 安装。

## 主要特性

- **单 APK**：下载安装即可使用
- **Android 16+ 适配**：通过 Shizuku 直连 `ICarrierConfigLoader`，受限时回退自指向 instrumentation
- **SIM 配置总览**：同时展示 SIM1 / SIM2 当前覆盖配置，并标记「已覆盖」状态
- **双卡独立配置**：可分别设置国家码与运营商名称
- **保存/还原同步**：操作完成后等待系统配置生效，界面立即刷新

## 安装方式

1. 手机安装并启用 [Shizuku](https://shizuku.rikka.app/)
2. 从 [Releases](https://github.com/lmh-codes/Nrfr/releases) 下载最新 `Nrfr-*.apk` 并安装
3. 在 Shizuku 中授予 Nrfr 权限后打开应用，选择 SIM 卡、国家码、运营商后点「保存生效」

> 无需单独编译，直接下载 Release 中的 APK 即可使用。

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
