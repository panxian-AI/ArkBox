# ArkBox v0.1.3

一台安卓手机自运行 **QQ → SnowLuma（OneBot v11）→ AstrBot** 全链路。一个 APK，装完点几下就能用。

## 这版改了什么

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
