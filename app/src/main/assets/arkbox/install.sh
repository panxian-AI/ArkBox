#!/bin/bash
# ArkBox 安装器：在 proot Ubuntu 24.04 (arm64) 内执行
# 流程：apt 依赖 → LinuxQQ arm64 → ArkLink + SnowLink → AstrBot
set -u
MARK=/root/.arkbox
mkdir -p "$MARK" /opt/arkbox/logs
exec > >(tee -a "$MARK/install.log") 2>&1

step() { echo ""; echo "==== [ArkBox] $1 ===="; }

fetch() { # fetch <url> <out> <sha256>  带断点续传 + 校验
    local url="$1" out="$2" sum="$3" tries=0
    if [ -f "$out" ] && echo "$sum  $out" | sha256sum -c --status 2>/dev/null; then
        echo "已有校验通过的缓存：$out"; return 0
    fi
    while [ $tries -lt 3 ]; do
        tries=$((tries+1))
        curl -fL -C - --retry 2 -o "$out" "$url" && break
        rc=$?
        echo "下载失败(exit=$rc，第 $tries 次)：$url"
        [ -f "$out" ] && { echo "$sum  $out" | sha256sum -c --status 2>/dev/null && break; }
        sleep 3
    done
    echo "$sum  $out" | sha256sum -c - || { rm -f "$out"; echo "校验失败：$out"; return 1; }
}

step "1/6 apt 基础依赖（Electron 全套 + 虚拟屏 + xdotool）"
export DEBIAN_FRONTEND=noninteractive
dpkg --configure -a >/dev/null 2>&1 || true
apt-get update -qq
apt-get install -y -qq --no-install-recommends \
    python3 python3-venv python3-pip curl ca-certificates tzdata \
    xvfb fluxbox x11vnc websockify novnc xdotool x11-utils \
    libnss3 libgbm1 libasound2t64 libcups2t64 \
    libgtk-3-0t64 libatk1.0-0t64 libatk-bridge2.0-0t64 libatspi2.0-0t64 \
    libdrm2 libxkbcommon0 libxcomposite1 libxdamage1 libxrandr2 \
    libpango-1.0-0 libcairo2 libxfixes3 libxrender1 libxi6 libxtst6 \
    fonts-noto-cjk dbus-x11 || exit 1
ldd /opt/QQ/qq 2>/dev/null | grep -q "not found" && {
    echo "[警告] QQ 仍有缺库，请看上方 apt 输出"; }

step "2/6 时区"
ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
echo Asia/Shanghai > /etc/timezone

step "3/6 LinuxQQ (arm64 官方 deb)"
QQ_URL="https://qqdl.gtimg.cn/qqfile/QQNT/9.9.36/beta/9ee04bef/linuxqq_3.2.34-53644_arm64.deb"
QQ_SUM="181b895cb573548e396e54bb0548cca92ffdc1b1a4b018fa4551bb062385fdca"
fetch "$QQ_URL" "$MARK/linuxqq.deb" "$QQ_SUM" || exit 1
dpkg -i "$MARK/linuxqq.deb" || apt-get install -y -f -qq || exit 1
test -x /opt/QQ/qq || { echo "QQ 主程序缺失"; exit 1; }
chmod 4755 /opt/QQ/chrome-sandbox 2>/dev/null || true

step "4/6 ArkLink + SnowLink（官方客户端通道 + OneBot v11）"
python3 -m pip install -q --break-system-packages \
    "arklink @ git+https://github.com/panxian-AI/ArkLink" \
    "snowlink @ git+https://github.com/panxian-AI/SnowLink" || exit 1
arklink version && python3 -m snowlink check || true

step "5/6 AstrBot（含面板 dist）"
if [ ! -x /root/AstrBot/.venv/bin/python ]; then
    AB_URL="https://github.com/panxian-AI/ArkDroid/releases/download/dist-v1/astrbot.tar.gz"
    AB_SUM="26a0a941806e2690e0a34e3371f0467a25dc22a97a3ded0a2a90f299052f4fc4"
    fetch "$AB_URL" "$MARK/astrbot.tar.gz" "$AB_SUM" || exit 1
    rm -rf /root/AstrBot && mkdir -p /root/AstrBot
    tar -xzf "$MARK/astrbot.tar.gz" -C /root/AstrBot --strip-components=1 || exit 1
    cd /root/AstrBot
    python3 -m pip install -q --break-system-packages uv || exit 1
    uv venv .venv && uv pip install -r requirements.txt --python .venv/bin/python || exit 1
fi
# AstrBot OneBot 适配器指向 SnowLink 的 WS（3001），token 启动时由 start.sh 注入
mkdir -p /root/AstrBot/data
cat > /root/AstrBot/data/arkbox-readme.txt <<'EOF'
AstrBot 首次启动后在面板里添加「AI聊天平台 → aiocqhttp(OneBot v11)」：
  反向 WS 不用；选「正向 WS」地址 ws://127.0.0.1:3001/
  AccessToken 与 SnowLink config/onebot_<QQ号>.json 的 wsServers[0].accessToken 一致
EOF

step "6/6 完成"
echo "安装完成。扫码：浏览器开 http://127.0.0.1:6081/vnc.html"
echo "启动链路：arkbox start（App 会自动调）"
