# ArkBox

手机上的 AstrBot 一体机：**全链路 Ark 系**，不注入 QQ、不改协议。

```
proot Ubuntu 24.04 (arm64, 首启下载)
├── LinuxQQ 官方客户端 (Xvfb 虚拟屏, 扫码走 noVNC :6081)
│     ↑ xdotool UI 通道
├── ArkLink (:6900)          ← 官方客户端通道，界面怎么发它就怎么发
│     ↑ HTTP + SSE
├── SnowLink (:3000/:3001)   ← OneBot v11（同 SnowLuma 端口/配置，可无缝替换）
│     ↑ 正向 WS
└── AstrBot (:6185 面板)     ← aiocqhttp / OneBot v11 适配器
```

## 和其他方案的区别

| | ArkDroid | SnowLuma | **ArkBox** |
|---|---|---|---|
| QQ 端 | 安卓 QQ + NapCat 注入 | hook 注入 NTQQ | **官方 LinuxQQ 客户端，零注入** |
| 协议端 | NapCat（闭源链路） | 已停更/仓库消失 | **SnowLink（Ark 系，纯 Python）** |
| 中继 | ArkBridge | — | **ArkLink serve api** |
| 上层 | AstrBot (Termux) | — | **AstrBot (proot Ubuntu)** |

QQ 风控面更小：登录的是官方客户端本体，消息由界面通道产生；协议更新不影响我们。

## 安装

1. 装 APK（arm64），打开后自动下载 bootstrap + Ubuntu rootfs（断点续传）；
2. 安装脚本自动完成：Electron 依赖 → LinuxQQ arm64 → ArkLink → SnowLink → AstrBot；
3. 首次登录：App 里点「扫码」，浏览器开 `http://127.0.0.1:6081/vnc.html` 扫码；
4. 之后全自动登录，AstrBot 面板在 `:6185`，SnowLink WebUI 在 `:5099`。

## 端口一览

| 端口 | 服务 |
|---|---|
| 6185 | AstrBot 面板 |
| 3000 / 3001 | SnowLink OneBot v11 HTTP / WS |
| 5099 | SnowLink WebUI |
| 6900 | ArkLink api（HTTP + SSE） |
| 6081 | noVNC（看 QQ 画面，仅 127.0.0.1） |

## 免责

QQ 机器人有封号风险，请专用小号；本仓库仅供个人学习研究。
