# AstrBot 源码备份（ArkBox 分发用）

这份 `astrbot.tar.gz` 是 ArkBox 装机时第 5 步解包到 `rootfs/root/AstrBot` 的那一份，
备份在这里是为了**上游仓库万一消失/改名/改分支时仍然能装**。

## 来源

| 项 | 值 |
| --- | --- |
| 上游 | https://github.com/AstrBotDevs/AstrBot |
| 分支 / 版本 | `master`，`pyproject.toml` 里 `version = "4.30.0-alpha.1"` |
| 打包日期 | 2026-11-18 |
| 文件 | `astrbot.tar.gz`（16,433,850 字节，2382 个文件） |
| SHA-256 | `26a0a94127b7222244ea88a46a68229196ed4977e6315914b26a95623102996b` |
| 许可证 | AGPL-3.0-or-later（见包内 `LICENSE`） |

## 和上游 tar 的差别

只有一处：**预置了 WebUI 构建产物** `astrbot/dashboard/dist/`。
上游源码 tar 不含 `dist`，首次启动时要联网拉前端构建包（国内经常拉不动，装机就卡在 WebUI 白屏）。
其余文件与上游 `master`（4.30.0-alpha.1）一致，未做任何代码改动。

## 重新打包

```bash
# 拿上游源码
curl -fL -o astrbot-src.tar.gz \
  https://codeload.github.com/AstrBotDevs/AstrBot/tar.gz/refs/heads/master
mkdir -p src && tar xzf astrbot-src.tar.gz -C src --strip-components=1

# 构建前端（需要 Node 20+/pnpm）
cd src/dashboard && pnpm install --frozen-lockfile && pnpm build && cd ../..
# 产物会落到 astrbot/dashboard/dist

# 打包（保持 AstrBot-master/ 这一层，install.sh 用 --strip-components=1 剥掉）
mkdir -p pkg/AstrBot-master && rsync -a --exclude .git src/ pkg/AstrBot-master/
tar czf astrbot.tar.gz -C pkg AstrBot-master
sha256sum astrbot.tar.gz
```

## 校验

```bash
sha256sum -c astrbot.tar.gz.sha256
```

## 说明

- ArkBox 的 `manifest.json` 与 `install.sh` 里，这份备份是**首选源**，
  ArkLink 的 `dist-v1` 是同内容镜像，上游 `codeload` 只作最后兜底（缺 WebUI）。
- 升级 AstrBot 时：换 tag 重新上传，同时更新 `manifest.json` 的 `size` / `sha256` /
  `mirrors` 和 `install.sh` 里 `astrbot.tar.gz)` 分支的 URL。
