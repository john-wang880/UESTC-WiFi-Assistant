# 成电 Wi-Fi 认证助手

原生 Android 宿舍校园网认证助手。面向电子科技大学清水河校区，当前目标 Wi-Fi 为 `Xiaomi_181A`，认证地址为 `http://10.253.0.235/`，支持 SRun 深澜的移动 `@cmcc` 和电信 `@dx` 账号。

[下载 v5.0.0 APK](https://raw.githubusercontent.com/john-wang880/UESTC-WiFi-Assistant/main/downloads/UESTC-WiFi-Assistant-v5.0.0.apk) · [验证记录](docs/VALIDATION.md) · [MIT 许可证](LICENSE)

**当前验证范围：** v5.0.0 已通过标准 Gradle 构建，并在 OPPO K10 5G / Android 14 上安装、启动和完成 11 项隔离设备检查。宿舍测试时已联网，尚未验收真实账号从掉线到重新认证；红魔和其他设备尚待各自真机验证。

## 功能

- 深色首页显示当前 Wi-Fi、认证状态和大尺寸「连接并认证」按钮；账号和高级设置位于单独页面。
- 打开首页自动检查网络，可关闭自动认证。已联网时不读取密码、不提交登录请求。
- 连接其他 Wi-Fi 时打开 Android 官方 Wi-Fi 选择面板，用户选中目标网络后返回继续。
- 账号持有人首次亲自输入账号、密码、运营商并确认授权；后续使用无需重复输入。
- 使用 Android Keystore + AES-GCM 加密保存账号、密码和运营商，支持清除授权。
- 分别处理认证错误、协议变化与普通网络故障，提供打开学校官方页面的入口。
- 不执行后台定时重连，不需要笔记本长期运行，没有广告或跟踪 SDK。

## 使用

1. 在 Android 8.0 或更新版本安装 APK。首次按系统提示允许精确位置，并开启系统定位；Android 使用该权限控制 Wi-Fi 名称读取，本 App 不获取 GPS 坐标。
2. 点击「连接并认证」。如果出现系统选网窗口，选择 `Xiaomi_181A` 后返回 App。Wi-Fi 密码由系统保存，本 App 不读取它。
3. 宿舍已联网时会直接显示「网络已连接」，无需填写校园网账号。
4. 需要认证时，由账号持有人在设置页自行选择运营商、输入凭据，阅读说明并确认授权。不要把账号密码发到 GitHub Issue 或聊天中。
5. 在设置页「清除授权」可删除本机密文和 Keystore 密钥，同时取消尚未完成的操作；此操作不会注销整个宿舍的校园网。

校园网认证可能使同一路由器下的设备共同恢复联网，请事先得到账号持有人授权并遵守学校及运营商的网络使用规则。本项目不是学校官方应用，不绕过认证、验证码、计费或设备限制。

## 安全与隐私

密钥由 Android Keystore 管理，使用 AES-GCM、随机 IV 和 App 专属 AAD。普通 SharedPreferences 只保存密文；密码不写入日志、网页存储、Activity 保存状态或自动填充。设置页防截屏，关闭应用备份与设备转移。

**Keystore 不能保证手机持有人绝对不能调用已授权凭据。** 将凭据授权给一台手机意味着信任该手机及其使用者；卸载或换机后需要重新授权。

**学校当前认证 API 使用 HTTP。** HMAC-MD5、SHA-1、XEncode 和自定义 Base64 是协议兼容参数，不等于 HTTPS，不能防止同网窃听、篡改。HTTP 仅对学校认证 IP 放行，其他主机禁止明文传输。

凭据请求仅发往 `10.253.0.235`，通过 `Network.openConnection` 绑定已确认的目标 Wi-Fi，不随默认蜂窝网络发送；带凭据请求禁止重定向到其他主机。外网连通检查访问 `https://www.baidu.com/`，不携带账号、密码、学校 Cookie 或 Referer；探测站不可达时也会结合学校在线状态判断，避免盲目重登。

仓库不包含真实凭据、原始在线状态响应、设备标识、本地 SDK 路径或 APK 签名私钥。请勿将这些内容附在公开问题报告中。

## 协议适配

2026-10-12 在目标网络读取学校当前登录页及官方 `Portal.js`，核对了以下流程：

1. 从当前登录页 `CONFIG.ip` / `CONFIG.acid` 获取校园网侧 IP 和接入编号，兼容旧式隐藏输入框；不用手机的路由器局域网 IP 替代。
2. 调用 `/cgi-bin/rad_user_info` 检查在线状态。
3. 调用 `/cgi-bin/get_challenge` 获取新令牌。
4. 按官方协议计算 HMAC-MD5、SHA-1、XEncode 与自定义 Base64，向 `/cgi-bin/srun_portal` 提交一次登录。
5. 获取令牌后再次检查在线状态，避免室友已恢复网络时重复登录；设置 30 秒认证频率保护。
6. 检查登录响应与外网可达性，未知响应或协议异常时停止并提示使用学校页面。

本次官方页面的接入编号为 `3`，参数为 `n=200`、`type=1`、`enc_ver=srun_bx1`、`double_stack=0`。App 在运行时读取当前页面信息，不假定今天的页面参数永久有效。高级设置仅在学校要求时手动指定接入编号。

## 构建

完整 Gradle 工程，可直接在 Android Studio 打开。使用 JDK 17、Android SDK 35、Build Tools 35.0.0、Android Gradle Plugin 8.9.2、Gradle Wrapper 8.11.1。应用 `minSdk=26`、`targetSdk=35`，应用 ID 为 `cn.edu.uestc.wifiassistant`。

Windows：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintRelease
```

Linux / macOS：

```sh
chmod +x gradlew
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintRelease
```

GitHub Actions 使用同样的标准工具链生成 debug APK 和测试、Lint 报告。Actions 产物使用 debug 签名，与仓库中已验证的正式 APK 签名不同；不能直接互相覆盖安装。

发布签名读取环境变量 `UESTC_STORE_FILE`、`UESTC_STORE_PASSWORD`、`UESTC_KEY_PASSWORD`，密钥别名为 `uestc-wifi`。配置你自己的私钥后执行 `:app:assembleRelease`；不要提交私钥或密码。仓库正式 APK 的更新需要维护者保管的原签名。

可选的 `Build.ps1` 支持本地检查及 ADB 安装；设备测试方法和注意事项见[验证记录](docs/VALIDATION.md)。

## 许可证与参考

项目代码使用 [MIT](LICENSE) 许可证。SRun 算法参考 [Akashiarcher/UESTC-](https://github.com/Akashiarcher/UESTC-) 及其来源 coffeehat/BIT-srun-login-script，并对照本次学校官方脚本验证；原始 MIT 声明保留在 [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt) 和 APK assets 中。Gradle Wrapper 保留其原有许可证声明。

Android 官方依据：[网络回调与权限](https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback)、[Wi-Fi 系统面板](https://developer.android.com/reference/android/provider/Settings.Panel)、[Keystore](https://developer.android.com/privacy-and-security/keystore)。
