# ArkBox

手机上的 AstrBot 一体机：**proot Ubuntu + 官方 LinuxQQ + SnowLuma 收信 + AstrBot**，一个 APK 装完，全程 127.0.0.1。

```
proot Ubuntu 24.04 arm64（首启下载 ~30MB）
├── LinuxQQ 官方客户端（Xvfb 无头虚拟屏，首次扫码走 noVNC :6081）
│     ↑ SnowLuma 运行期 hook 注入（读 login.db、解 MsgPush）
├── SnowLuma  OneBot v11   HTTP :3000 / WS :3001 / WebUI :5099
│     ↑ 正向 WS
└── AstrBot  面板 :6185     aiocqhttp / OneBot v11 适配器
```

## 为什么是这套组合

先说清楚一条硬事实：**ArkLink 在无头 Linux 上发得出去、收不回来**（`ntqq-client` 通道
`supports_receive = False`，`ntqq-native` 的推送循环没接进事件流，`ntqq-hook` 收包侧
还在等内核 API）。而 AstrBot 要回话就必须收到消息。

所以 ArkBox 的分工是：

| 环节 | 谁来做 | 为什么 |
|---|---|---|
| 登录 / 收信 / 富消息 | **SnowLuma**（运行期 hook，注入官方 QQ） | 唯一在 Electron 上验证过能解出 MsgPush 的实现 |
| 发信兜底 | ArkLink `x11` 引擎（可选，pip 装了但默认不启动） | SnowLuma 不在时用 xdotool 点界面，只发纯文本 |

SnowLuma 用的是官方 `linux-arm64-lite` 构建（8 MB，不含 ffmpeg addon），由本仓库
镜像到 `panxian-AI/ArkLink` 的 `snowluma-arm64-v2.4.2` release，避免上游再次消失。
运行时需要 Node ^24.22.1，安装器会一起装好。

## 和其他方案的区别

| | ArkDroid | 那个 AstrBot 管理器 | **ArkBox** |
|---|---|---|---|
| QQ 端 | 安卓 QQ + NapCat 注入 | LinuxQQ（proot） | **LinuxQQ 官方客户端（proot）** |
| 收信 | NapCat 协议端 | SnowLuma hook | **SnowLuma hook（同源）** |
| rootfs | 内置 736MB 打进 APK | 内置 736MB | **首启下载 30MB，APK 只有几十 MB** |
| 上层 | AstrBot（Termux） | AstrBot（同一 rootfs） | **AstrBot（同一 rootfs）** |

## 安装

1. 装 APK（arm64）→ 打开，点「一键下载全部」（bootstrap / rootfs / QQ deb / SnowLuma / AstrBot）；
2. 点「安装 / 更新」，安装器后台跑完 9 步（Termux 被杀也不中断）；
3. **首次顺序很重要**：
   ```
   qq start     # 起 QQ，浏览器开 http://127.0.0.1:6081/vnc.html 扫码
   box start    # QQ 登录后起 SnowLuma，它注入 QQ 并生成 onebot_<QQ号>.json
   astr start   # 起 AstrBot，面板 :6185
   ```
   App 打开时会按这个顺序自动拉（装好的才起，幂等）。
4. AstrBot 面板 →「AI 聊天平台」→ 新增 `aiocqhttp`（OneBot v11）→
   正向 WS `ws://127.0.0.1:3001/`，token 用 `box start` 打印的那个
   （或看 `/opt/snowluma/config/onebot_<QQ号>.json` 的 `wsServers[0].accessToken`）。

## 端口一览

| 端口 | 服务 |
|---|---|
| 6185 | AstrBot 面板 |
| 3000 / 3001 | SnowLuma OneBot v11 HTTP / WS |
| 5099 | SnowLuma WebUI |
| 6081 | noVNC（只在首次扫码时要看） |
| 6900 | ArkLink API（可选，发信兜底时才用） |

## 排障

```bash
qq status / qq log      # QQ 进程、四端口、最近日志
box status / box log    # SnowLuma 是否注进去了
astr status / astr log  # AstrBot
```

- `3001 不通` = QQ 没登录 → SnowLuma 生成不出 `onebot_<QQ号>.json`，先扫码；
- `box log` 里出现注入失败 → 看 QQ 进程是否真在跑（`qq status`），proot 内 ptrace 与宿主机不同；
- 登录态在 `/root/.config/QQ`，重启不用重新扫码。

## 许可

SnowLuma 是第三方项目（`TomyJan/SnowLuma`），分发的是其官方 lite 构建，使用需遵守其
EULA / PRIVACY（安装器以环境变量方式接受，不写 consent 文件）。ArkBox 自身代码 MIT。
