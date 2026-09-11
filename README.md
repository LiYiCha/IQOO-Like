# iQOO Community Token Hook & Extraction Module (IQOOLike)

基于现代 `libxposed` (API ≥ 100) 与经典 Xposed (API 82) 双栈架构的 iQOO 社区自动凭证提取伴侣应用。

## 🌟 核心特性
- **主动静默换票**：无需在宿主界面手动点击登录或输入密码，模块直接通过显式广播唤醒宿主底层换票方法（`ba.m.f` / `ba.m.b`）。
- **全量 9 项核心凭证捕获**：
  - `accessToken`（30 天社区 Token）
  - `userId`（社区数字 UID）
  - `expiresIn`（有效期秒数，默认 2592000s）
  - `vivotoken`（Vivo 系统级通行证）
  - `openid`（用户唯一标识）
  - `nickname`（昵称）
  - `mobile`（绑定手机号）
  - `versionCode`（客户端版本）
  - `x-visitor`（网关设备指纹哈希）
- **高可靠双层通信与持久化**：
  - **下行**：`iqoobbs.action.PULL`（显式广播 + Nonce）
  - **上行**：`iqoobbs.action.RESULT`（显式广播 + HMAC-SHA256 签名鉴权）
  - **容灾快照**：宿主自动在 `filesDir/iqoo_token.json` 落盘，伴侣 App 重启仍可读取最后有效快照。
- **现代化 Material 3 界面**：
  - 8dp 栅格体系、动态取色 + 纯正深浅色主题。
  - 主 Token 倒计时进度指示器（绿/橙/红语义色彩区分）。
  - 2 列紧凑字段网格 + 单列长 Token 展开式面板。
  - 历史凭证归档与一键格式化复制（JSON / KEY=VALUE / 单字段）。

## 🛠️ 使用与激活指南
1. **构建与安装**：
   - 使用 Android Studio 打开本项目 (`IQOOLike`)。
   - 连接已 Root/已安装 LSPosed 的 Vivo/iQOO 设备，执行运行或编译 Debug/Release APK。
2. **激活模块**：
   - 打开 **LSPosed Manager**。
   - 在模块列表中找到 **iQOO Token** (`com.yc.iqoolike`) 并启用。
   - **勾选作用域**：将 **iQOO 社区** (`com.iqoo.bbs`) 加入作用域。
   - 强行停止或重启一次 iQOO 社区。
3. **提取凭证**：
   - 保持手机系统已登录 Vivo 账号。
   - 打开 **iQOO Token** 伴侣应用，点击右下角 **「立即获取」**，数秒内即可自动提取完整 Token。
