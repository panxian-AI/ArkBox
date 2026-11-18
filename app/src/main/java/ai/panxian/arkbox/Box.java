package ai.panxian.arkbox;

import android.content.Context;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * ArkBox 本机链路（一台手机自运行，零注入）：
 *
 * <pre>
 * LinuxQQ 官方客户端(Xvfb 无头) ← SnowLuma 注入 hook(OneBot v11 :3000/:3001) → AstrBot(:6185)
 * </pre>
 *
 * <p>安装全部由 termux/install.sh 的 --qq-only 段完成：apt 装 QQ arm64 deb + Xvfb/xdotool/noVNC，
 * Node 24 + SnowLuma（hook 注入收信）+ 可选 ArkLink（x11 发信兜底）。我们只负责下发命令和读状态。
 * 扫码登录走 noVNC（6081），AstrBot 面板连 SnowLuma 的 OneBot WS（3001）。
 */
final class Box {

    /** SnowLuma WebUI 端口（状态/调试/配置编辑）。 */
    static final int WEBUI_PORT = 5099;

    /** ArkLink api（HTTP + SSE）端口，官方客户端通道的底座。 */
    static final int ARKLINK_PORT = 6900;

    /** SnowLuma 的 OneBot v11 正向 WS——AstrBot 的 aiocqhttp 连的是它。 */
    static final int ONEBOT_PORT = 3001;

    /** noVNC 端口：浏览器开这个页面看 QQ 画面、扫码登录。 */
    static final int NOVNC_PORT = 6081;

    static final String WEBUI_URL = "http://127.0.0.1:" + WEBUI_PORT;
    static final String NOVNC_URL = "http://127.0.0.1:" + NOVNC_PORT + "/vnc.html";
    /** 内置浏览器用的 noVNC 地址：自动连接 + 自适应缩放，省得手点。 */
    static final String NOVNC_VIEW = NOVNC_URL + "?autoconnect=true&resize=scale&reconnect=true&reconnect_delay=2000";
    static final String ONEBOT_URL = "ws://127.0.0.1:" + ONEBOT_PORT;

    /** 最近一次探测结果。 */
    static volatile boolean webuiUp;
    static volatile boolean arklinkUp;
    static volatile boolean onebotUp;
    static volatile boolean novncUp;
    static volatile String lastError = "";
    static volatile long probedAt;

    /** SnowLuma WebUI 登录密码（box token --raw 打回来的 PASS= 行）。空 = 没读到明文。 */
    static volatile String webuiPass = "";

    /**
     * 吃 Termux 那边 POST 回来的 {@code box token --raw} 输出，抽出 PASS= 行。
     * PASS= 空（或没有）表示密码已在网页里改过、看不回明文，只能走重置。
     */
    static void absorbWebuiPass(String body) {
        if (body == null) {
            return;
        }
        for (String raw : body.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("PASS=")) {
                webuiPass = line.substring(5).trim();
            }
        }
    }

    private Box() {
    }

    /**
     * 装/补本机 QQ 链路：跑 install.sh --qq-only。
     */
    static boolean install(Context ctx) {
        BridgeService.serve(ctx); // 让 Termux 能把日志 POST 回来
        LogBus.append("装本机链路：QQ deb + Xvfb/xdotool + Node24/SnowLuma/ArkLink（install.sh --qq-only）");
        return Termux.send(ctx,
                "if [ ! -s \"$HOME/.arkbox/install.sh\" ]; then"
                        + " echo \"✗ install.sh 不在（先在 AstrBot 卡点一次「安装 / 更新」）\""
                        + "; else for c in astr qq box; do ln -sf \"$HOME/.arkbox/$c\" \"$PREFIX/bin/$c\" 2>/dev/null; done"
                        + "; nohup bash \"$HOME/.arkbox/install.sh\" --qq-only </dev/null >/dev/null 2>&1 &"
                        + " echo \"✓ 本机链路安装已后台启动（pid $!），输出实时流回这里\"; fi",
                60);
    }

    /**
     * 启动本机链路。顺序不能反：SnowLuma 要「发现 QQ 进程」才注得进去，
     * 所以先把 QQ（Xvfb 无头）拉起来，再起 SnowLuma。
     */
    static boolean start(Context ctx) {
        BridgeService.serve(ctx);
        return Termux.send(ctx,
                "~/.arkbox/qq start; ~/.arkbox/box start",
                400);
    }

    /** 探测 SnowLuma WebUI(5099)、OneBot WS(3001)、ArkLink(6900，可选)、noVNC(6081)。要在后台线程调。 */
    static void probe() {
        webuiUp = alive(WEBUI_PORT);
        onebotUp = alive(ONEBOT_PORT);
        arklinkUp = alive(ARKLINK_PORT);
        novncUp = alive(NOVNC_PORT);
        probedAt = System.currentTimeMillis();
    }

    private static boolean alive(int port) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/").openConnection();
            c.setConnectTimeout(700);
            c.setReadTimeout(900);
            c.setRequestMethod("GET");
            int code = c.getResponseCode();
            // 401/426 也算在线——OneBot/WebUI 要 token，会先回 401
            return code > 0;
        } catch (IOException e) {
            lastError = e.getClass().getSimpleName();
            return false;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    static String summary() {
        if (onebotUp) {
            return "链路在线 · OneBot 3001（AstrBot 连它） · WebUI " + WEBUI_PORT
                    + (novncUp ? " · 扫码页 6081 就绪" : "");
        }
        if (webuiUp) {
            return "SnowLuma 在线（WebUI " + WEBUI_PORT + "），OneBot " + ONEBOT_PORT
                    + " 还没起来——多半是 QQ 没登录，登录后会自动生成配置";
        }
        if (novncUp) {
            return "QQ 在跑（扫码页 6081），SnowLuma 没起（box start）";
        }
        if (arklinkUp) {
            return "ArkLink 在线（" + ARKLINK_PORT + "），SnowLuma 未启动";
        }
        return "没响应（还没装，或者没启动）";
    }

    /**
     * 兜底建 SnowLuma 的配置目录。真正的 onebot_&lt;uin&gt;.json 要等 QQ 登录后
     * 由 SnowLuma 自己解密 login.db 才能生成，这里不猜内容。
     */
    static void seedOneBotConfig(Context ctx) {
        // proot-run.sh 是安装器 2/9 步（解完 rootfs 后）才生成的。没装完就调会
        // 报 "No such file or directory"，日志里一片假错——先判存在再调。
        Termux.send(ctx, "if [ -x \"$HOME/.arkbox/proot-run.sh\" ]; then "
                + "~/.arkbox/proot-run.sh /bin/bash -c 'mkdir -p /opt/snowluma/config'; fi", 30);
    }
}
