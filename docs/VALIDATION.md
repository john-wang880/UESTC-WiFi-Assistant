# v5.0.0 验证记录

验证日期：2026-10-12（Asia/Shanghai）。

## 真实构建与 APK

使用 Microsoft OpenJDK 17.0.20.1、Google Android SDK 35 / Build Tools 35.0.0、Android Gradle Plugin 8.9.2 和 Gradle Wrapper 8.11.1。

标准任务 `assembleRelease`、`testDebugUnitTest`、`lintRelease`、`assembleDebugAndroidTest` 已在 Windows 本机成功执行。使用标准 Manifest 合并、aapt2、D8、打包和签名流程。Gradle 分发包与官方 SHA-256 一致，Wrapper 校验固定于配置中。

| 项目 | 结果 |
| --- | --- |
| 应用 ID | cn.edu.uestc.wifiassistant |
| 版本 | 5.0.0 / 50000 |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |
| 启动 Activity | MainActivity，MAIN/LAUNCHER，exported=true |
| 设置页 | SettingsActivity，exported=false |
| 权限 | INTERNET、ACCESS_NETWORK_STATE、ACCESS_WIFI_STATE、ACCESS_COARSE_LOCATION、ACCESS_FINE_LOCATION |
| 签名与对齐 | APK v2 验证通过，zipalign 验证通过 |
| 正式 APK 大小 | 47,216 字节 |
| Lint | 0 errors；1 个 MonochromeLauncherIcon 提示，API 33 已提供 monochrome 变体 |

最终交付 APK 已再次安装，从 OPPO 拉回的 `base.apk` 与交付文件 SHA-256 一致。其 `classes.dex` 与通过以下 11 项设备测试的版本一致；最终打包包含第三方 MIT 声明。

APK SHA-256：

```text
82ef280e0a585d2d779238902085ea6a7200b7cf9d3f7dff89c54f57ce4892b0
```

签名证书 SHA-256：

```text
ab4c4f3ea1b32adb772a884db15dc59c5f5d2053538dad70bfb16417f99d3dcc
```

纯 Java / Android 系统 UI，无原生 .so、广告或统计 SDK，因此正式 APK 体积较小。

## 协议与单元测试

在 `Xiaomi_181A` 读取当前学校页面与 `Portal.js`，核对当前页面 `/srun_portal_pc?ac_id=3&theme=pro`、校园网侧 IP 解析、3 个 CGI 端点和 SRun 算法参数。没有注销宿舍网络或使用测试账号提交真实登录。

18 项 JUnit 测试全部通过，0 failure / 0 error，覆盖：

- 5 组官方 JavaScript 生成的合成凭据向量，包括中文、emoji、空格、引号、反斜线。
- CONFIG / 隐藏字段解析、运营商后缀、JSONP 按数据解析与附加脚本拒绝。
- 错误分类、隐私过滤、密码数组清除。
- 已联网不加载凭据、不取令牌、不提交登录；校园网在线但外网不可达时避免重登。
- 无授权、未知状态、只读模式、无效令牌、取消与密钥读取失败的处理。
- 登录仅一次、使用校园网侧 IP、获取令牌后室友恢复网络时停止登录。
- 密码错误无自动重试、30 秒频率保护、未确认外网时不误报成功。

合成测试数据不是有效校园网凭据。

## OPPO K10 真机检查

设备：OPPO K10 5G（PGJM10），Android 14 / API 34，arm64-v8a。

11 项独立设备检查全部通过：

1. 未确认账号持有人授权时拒绝保存。
2. 真实 Keystore AES-GCM 保存读取、随机 IV、无明文持久化、普通 API 不可导出密钥。
3. 篡改密文后拒绝解密。
4. 清除密文与 Keystore 条目，已撤销授权不能再次读取。
5. HTTP 仅对校园网认证 IP 放行。
6. 首页启动及 Activity 重建正常。
7. 设置页密码不保存状态、授权不预选、防截屏。
8. 用户授权后正确检测目标 SSID。
9. Android 官方 Wi-Fi 面板打开并返回，仍连接目标网络。
10. 当前校园网在线的只读检测零凭据读取、零登录。
11. 测试未改动真实用户的授权存储。

测试包使用独立 alias / preferences；测试结束已卸载，正式 App 保留。最终首页显示「Xiaomi_181A」「网络已连接」「未提交登录请求」。观察期间无本 App 的 AndroidRuntime FATAL EXCEPTION 或崩溃。

## 设备检查的执行

在你明确授权的测试手机上构建并安装同一签名的 App 和测试包：

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w cn.edu.uestc.wifiassistant.test/cn.edu.uestc.wifiassistant.DeviceChecks
adb uninstall cn.edu.uestc.wifiassistant.test
```

设备需连接目标 Wi-Fi，并由手机使用者按系统提示允许 Wi-Fi 名称读取所需的精确位置权限。已有正式版与自行构建的 debug 签名通常不同，请使用单独测试手机；卸载正式版会删除本机授权。

## 尚未真实验收

- 测试时宿舍已认证，未实际用账号持有人的凭据从失效状态提交登录。模拟响应与协议向量通过不等于完成真实登录验收。
- 等待网络自然失效后，由账号持有人在手机设置页自行输入并授权，再验收认证结果；不要为测试主动注销整个宿舍网络。
- 红魔及其他 Android 版本、机型尚未各自真机验收。
- 本记录的构建结果指本机标准构建；GitHub Actions 的运行结果以仓库 Actions 页面为准。
