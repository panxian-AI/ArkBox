package ai.panxian.arkbox;

import android.content.Context;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * ArkBox 本机链路（一台手机自运行，零注入）：
 *
 * <pre>
 * LinuxQQ 官方客户端(Xvfb 无头) → ArkLink(:6900 UI 通道) → SnowLink(OneBot v11 :3000/:3001) → AstrBot(:6185)
 * </pre>
 *
 * <p>安装全部由 termux/install.sh 的 --qq-only 段完成：apt 装 QQ arm64 deb + Xvfb/xdotool/noVNC，
 * pip 装 ArkLink + SnowLink。我们只负责下发命令和读状态。
 * 扫码登录走 noVNC（6081），AstrBot 面板连 SnowLink 的 OneBot WS（3001）。
 */
final class Box {

    /** SnowLink WebUI 端口（状态/调试/配置编辑）。 */
    static final int WEBUI_PORT = 5099;

    /** ArkLink api（HTTP + SSE）端口，官方客户端通道的底座。 */
    static final int ARKLINK_PORT = 6900;

    /** SnowLink 的 OneBot v11 正向 WS——AstrBot 的 aiocqhttp 连的是它。 */
    static final int ONEBOT_PORT = 3001;

    /** noVNC 端口：浏览器开这个页面看 QQ 画面、扫码登录。 */
    static final int NOVNC_PORT = 6081;

    static final String WEBUI_URL = "http://127.0.0.1:" + WEBUI_PORT;
    static final String NOVNC_URL = "http://127.0.0.1:" + NOVNC_PORT + "/vnc.html";
    static final String ONEBOT_URL = "ws://127.0.0.1:" + ONEBOT_PORT;

    /** 最近一次探测结果。 */
    static volatile boolean webuiUp;
    static volatile boolean arklinkUp;
    static volatile boolean onebotUp;
    static volatile boolean novncUp;
    static volatile String lastError = "";
    static volatile long probedAt;

    private Box() {
    }

    /**
     * 装/补本机 QQ 链路：跑 install.sh --qq-only。
     */
    static boolean install(Context ctx) {
        BridgeService.serve(ctx); // 让 Termux 能把日志 POST 回来
        LogBus.append("装本机链路：QQ deb + Xvfb/xdotool + ArkLink/SnowLink（install.sh --qq-only）");
        return Termux.send(ctx,
                "if [ ! -s \"$HOME/.arkbox/install.sh\" ]; then"
                        + " echo \"✗ install.sh 不在（先在 AstrBot 卡点一次「安装 / 更新」）\""
                        + "; else for c in astr qq box; do ln -sf \"$HOME/.arkbox/$c\" \"$PREFIX/bin/$c\" 2>/dev/null; done"
                        + "; nohup bash \"$HOME/.arkbox/install.sh\" --qq-only </dev/null >/dev/null 2>&1 &"
                        + " echo \"✓ 本机链路安装已后台启动（pid $!），输出实时流回这里\"; fi",
                60);
    }

    /** 启动本机链路（ArkLink + SnowLink + QQ 无头）。 */
    static boolean start(Context ctx) {
        BridgeService.serve(ctx);
        return Termux.send(ctx,
                "~/.arkbox/box start; ~/.arkbox/qq start",
                300);
    }

    /** 探测 SnowLink WebUI(5099)、OneBot WS(3001)、ArkLink(6900)、noVNC(6081)。要在后台线程调。 */
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
            // 401 也算在线——SnowLink 的 OneBot/WebUI 要 token，会先回 401/426
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
            return "SnowLink 在线（WebUI " + WEBUI_PORT + "），OneBot " + ONEBOT_PORT + " 未确认（box log 看）";
        }
        if (arklinkUp) {
            return "ArkLink 在线（" + ARKLINK_PORT + "），SnowLink 未启动（box start）";
        }
        return "没响应（还没装，或者没启动）";
    }

    /**
     * 确保 SnowLink 配置目录存在（config/onebot_&lt;uin&gt;.json 由 snowlink init 生成，
     * 这里只做兜底建目录，不猜内容）。
     */
    static void seedOneBotConfig(Context ctx) {
        // proot-run.sh 是安装器 2/9 步（解完 rootfs 后）才生成的。没装完就调会
        // 报 "No such file or directory"，日志里一片假错——先判存在再调。
        Termux.send(ctx, "if [ -x \"$HOME/.arkbox/proot-run.sh\" ]; then "
                + "~/.arkbox/proot-run.sh /bin/bash -c 'mkdir -p /root/.snowlink/config'; fi", 30);
    }
}
