#!/bin/bash
# ArkBox 启动链：Xvfb → QQ → ArkLink → SnowLink → AstrBot（全部幂等）
set -u
export DISPLAY=${DISPLAY:-:1}
export TZ=Asia/Shanghai
LOG=/opt/arkbox/logs
MARK=/root/.arkbox
mkdir -p "$LOG" "$MARK"

up_port() { curl -s -m 2 -o /dev/null "http://127.0.0.1:$1" && return 0 || return 1; }

# 1 虚拟屏 + VNC（扫码用）
pgrep -x Xvfb >/dev/null || { Xvfb :1 -screen 0 1280x720x16 >"$LOG/xvfb.log" 2>&1 </dev/null & sleep 2; }
pgrep -x fluxbox >/dev/null || { fluxbox >"$LOG/fluxbox.log" 2>&1 </dev/null & }
pgrep -x x11vnc >/dev/null || { x11vnc -display :1 -noshm -nopw -forever -shared -rfbport 5900 -localhost >"$LOG/x11vnc.log" 2>&1 </dev/null & }
pgrep -f "websockif[y]" >/dev/null || { websockify --web /usr/share/novnc 127.0.0.1:6081 127.0.0.1:5900 >"$LOG/novnc.log" 2>&1 </dev/null & }

# 2 ArkLink api（QQ 官方客户端通道的底座）
up_port 6900 || { (arklink serve api >"$LOG/arklink.log" 2>&1 </dev/null &) ; sleep 4; }

# 3 SnowLink（OneBot v11：3000/3001/5099，配置与 SnowLuma 同构）
pgrep -f "snowlink ru[n]" >/dev/null || { (cd /root/.snowlink 2>/dev/null || mkdir -p /root/.snowlink && cd /root/.snowlink; python3 -m snowlink run >"$LOG/snowlink.log" 2>&1 </dev/null &) ; sleep 3; }

# 4 QQ 官方客户端（Xvfb 里，xdotool 由 ArkLink 调用；登录态在 HOME 里，二次启动免扫码）
pgrep -x qq >/dev/null || { (cd /root && HOME=/root DISPLAY=:1 \
    /opt/QQ/qq --no-sandbox --disable-gpu --disable-software-rasterizer \
    --disable-gpu-compositing --disable-dev-shm-usage >"$LOG/qq.log" 2>&1 </dev/null &) ; }

# 5 AstrBot（面板 6185；OneBot 适配器连 SnowLink 3001）
pgrep -f "python main\.p[y]" >/dev/null || { (cd /root/AstrBot && \
    setsid nohup .venv/bin/python main.py >"$LOG/astrbot.log" 2>&1 </dev/null &) ; }

sleep 3
echo "== ArkBox 链路状态 =="
for p in 6185 3000 3001 5099 6900 6081; do
    printf "%-6s " "$p"; up_port "$p" && echo OK || echo "-"
done
echo "扫码：http://127.0.0.1:6081/vnc.html"
