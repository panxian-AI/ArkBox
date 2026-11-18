# ArkBox v0.1.7

一台安卓手机自运行 **QQ → SnowLuma（OneBot v11）→ AstrBot** 全链路。一个 APK，装完点几下就能用。

## 这版改了什么

- **修复：6081 QQ 窗口一直打不开的真正原因**。`ensure_vnc` 用 `pgrep -f "websockif[y]"`
  判断 websockify 是否在跑——但那段脚本自己的命令行里就含有 websockify 这个词，
  **永远会匹配到自己**，于是 websockify 从来没被拉起过，6081 一直拒绝连接。
  改用 bash 内建 `/dev/tcp` 探端口，谁都不骗；另加 `qq vnc` 子命令单独修 VNC 链并打印日志。
- **hook 载入器自动自愈**。旧安装器装的机器 rootfs 里没有 `/opt/qqstack/libslhook.so`，
  现在启动 QQ 时自动把 App 缓存（`~/.arkbox/hook/`）里的载入器补进 rootfs，
  没有缓存就在线拉，不用再重跑安装器。
- **不再谎报「已带 hook 注入」**：启动后以握手 socket 为准分三档如实报告
  （已握手 / 载入器已挂等 SnowLuma / 缺载入器没注入）。
- **修脚本执行 Permission denied（退出码 126）**：脚本下载循环里逐个 chmod +x，
  不再依赖循环后的统一 chmod（它可能没跑完你就敲命令了）。
- **`qq start` 会自己发现「在跑但没注入」并自动重启**：注入成功的标志是握手目录里出现
  `mojo.<pid>.control.sock`（SnowLuma 就靠扫这个发现 QQ）。之前起了旧进程（v0.1.4 及以前）
  的机器不用再手动 `qq restart`，App 一开就自动换成带注入的方式重启，登录态不丢。
- **`box` 的「已经在跑」不再骗人**：原来只看 3001 有没有监听（没接上 QQ 也照样应答），
  现在以 `config/onebot_<QQ号>.json` 为真判据；SnowLuma 是旧配置起的会用标记文件
  （`~/.arkbox/.hookdir`）迁移一次握手目录，不会反复重启。
- **脚本改为线上优先**：`qq`/`box`/`astr` 等纯 shell 脚本每次开 App 先从仓库 main 拉
  最新版（三通道，失败退回 APK 内置），以后改这类脚本不用再发 APK。`install.sh` 仍用内置版
  （与 APK 强绑定）。
- **修复：QQ 收消息链路（最重要）**。查明根因：SnowLuma 官方靠往 QQ 进程写内存注入，
  在 proot 里这条路必然失效，所以之前「SnowLuma 在线但 OneBot 3001 不通」。
  现在改走 AstralNext 同款的 LD_PRELOAD 方案：QQ 启动时挂 `libslhook.so`（hook 载入器）→
  加载 `libsnowluma_linux_resident_dispatch.so` → 接上 lite 包自带的
  `snowluma-linux-arm64.so`，通过 `/tmp/snowluma-arkbox` 与 SnowLuma 握手。
  两个载入器（共 112KB）已进仓库 assets，装机自动就位。
  ⚠️ 注入必须在登录之前：如果之前扫码登录过，跑 `qq restart` 换成带注入的方式重启（不用重新扫码）。
- **修复：6081 QQ 窗口打不开**。原来 `qq start` 发现已跑就直接返回，Xvfb/noVNC 挂了不会补起。
  现在 VNC 链独立成 `ensure_vnc`，每次 start（以及 QQ 已在跑时）都会先补齐；
  另加 `qq restart` 子命令。
- **AstrBot 源码进入独立备份位**：装机用的 AstrBot 4.30.0-alpha.1（含预构建 WebUI）已托管到本仓库
  release `astrbot-4.30.0-alpha.1`，`install.sh` 与线上 `manifest.json` 都改用它作首选源，
  ArkLink 的 `dist-v1` 降为同内容镜像，上游 `codeload` 只作最后兜底。该 release 附 `SOURCE.md`，
  写了版本、sha256 和从头重新打包的完整命令——上游哪天删仓库也能自己重建。
- **新增「打开 QQ 窗口」按钮**：QQ 跑在 proot 的 Xvfb 无头环境里，画面走 noVNC（6081）。
  现在在 App 里一键打开（自动连接、自适应缩放），扫码登录、验证、确认登录都在里面点完；
  没起来时会直接给「启动 QQ」一键路径。
- **应用名 / 图标**：正式改名 ArkBox，换成「以下克上」手势图标（康神同款：两手一上一下、两指并拢、掌心相对）。
- **SnowLuma WebUI 入口密码**：
  - 「WebUI 密码」按钮：一键读取登录密码（账号 `admin`）；
  - 「重置 WebUI 密码」按钮：留空自动生成强密码，写好后自动重启 SnowLuma；
  - 命令行等价：`box token` 查看 / `box setpass 新密码` 重置。
- **覆盖升级**：CI 改用固定签名库（存于仓库 secrets），从这版起新旧包可以直接覆盖安装。
  ⚠️ 唯一例外：从旧的 debug 签名包升级到本版需要**先卸载旧版**（旧包是 CI 一次性 debug 签名，签名不同装不上）。

## 装上之后怎么用

1. 安装 APK，打开 App，下载卡点「一键下载全部」（约 32 MB + SnowLuma 8 MB）。
2. 点「安装 / 更新」跑 9 步安装器（QQ deb + Xvfb/noVNC + Node24 + SnowLuma）。
3. 「启动」→ 浏览器开 `http://127.0.0.1:6081/vnc.html` 扫码登录 QQ。
4. SnowLuma 自动注入并生成 OneBot 配置（`box log` 看进度）。
5. AstrBot 面板（6185）→ 中继地址保持 `ws://127.0.0.1:3001`，token 用 `box start` 打印的那个。
6. SnowLuma WebUI（5099）登录密码：点「WebUI 密码」读取，或「重置 WebUI 密码」自己定一个。

## 命令速查

```bash
qq start|stop|status|log          # QQ（Xvfb 无头）+ noVNC 6081
box start|stop|status|log         # SnowLuma：注入 QQ，OneBot 3000/3001，WebUI 5099
box token                         # WebUI 登录密码（网页里改过则看不回明文）
box setpass 新密码                # 重置 WebUI 密码（≥8 位，自动重启）
astr start|stop|status|log        # AstrBot 6185
```

## 端口一览

| 端口 | 服务 |
|---|---|
| 6185 | AstrBot 面板 |
| 3000 / 3001 | SnowLuma OneBot v11 HTTP / WS |
| 5099 | SnowLuma WebUI |
| 6081 | noVNC（首次扫码用） |

## 已知限制

- proot 内 hook 注入的稳定性待真机长测；`box log` 看不到注入成功就先确认 QQ 进程活着。
- 内存建议 6 GB+（QQ Electron + AstrBot 同机跑）。
- 仅 arm64-v8a / x86_64。
