package ai.panxian.arkbox;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String PANEL_URL = "http://127.0.0.1:6185";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();

    private Prefs prefs;
    private TextView chipBridge;
    private TextView chipAccount;
    private TextView chipAstr;
    private TextView txtBridgeState;
    private TextView txtAstrState;
    private TextView txtStats;
    private TextView txtLog;
    private NestedScrollView logScroll;
    private SwitchMaterial swAutostart;
    private boolean autoStarted;
    private TextInputEditText inpArklink;
    private TextInputEditText inpAstrbot;
    private TextInputEditText inpSelfId;
    private TextInputEditText inpAstrbotToken;
    private TextInputEditText inpArklinkToken;
    private MaterialButton btnCheck;
    private ProgressBar progressDl;
    private TextView txtDlState;
    private TextView txtCreds;
    private TextView txtTerm;
    private NestedScrollView termScroll;
    private TextInputEditText inpCmd;
    private NestedScrollView scroller;
    private View cardBridge;
    private View cardDl;
    private View cardAstr;
    private View cardTerm;
    private View cardQq;
    private View cardProgress;
    private ProgressBar progressInstall;
    private TextView txtInstallStep;
    private TextView txtInstallHint;
    private android.widget.LinearLayout dlList;
    private final java.util.Map<String, TextView> dlRows = new java.util.HashMap<>();
    private boolean welcomed;
    private boolean credsAuto;
    private boolean dlDoneNotice;

    /**
     * 是否"跟随最新"：用户在底部就往新内容走，往上翻看历史就不打扰。
     * 由滚动回调维护——比每次输出前临时算一次可靠（内容变高前那一刻算不出真实位置）。
     */
    private boolean termFollow = true;
    private boolean logFollow = true;

    private final Runnable termRefresh = new Runnable() {
        @Override
        public void run() {
            if (txtTerm == null) {
                return;
            }
            String text = Term.text();
            if (!text.contentEquals(txtTerm.getText())) {
                // 滚底交给 txtTerm 的 layout 回调：setText 之后要等下一轮排版才知道新内容
                // 的真实高度，在这里直接 fullScroll 滚的还是旧高度（表现为"不往下走"）。
                txtTerm.setText(text);
            }
        }
    };

    /** 用户停在底部附近才算"在看最新"，翻上去看历史就不动他的位置。 */
    private boolean atBottom(NestedScrollView sv) {
        if (sv == null || sv.getChildCount() == 0) {
            return true;
        }
        int bottom = sv.getChildAt(0).getBottom() + sv.getPaddingBottom();
        return bottom - (sv.getHeight() + sv.getScrollY()) < 100;
    }

    /**
     * 内容变高时把内层滚到底。
     *
     * <p>用 scrollTo 而不是 fullScroll：fullScroll 会为了"露出最后一个焦点控件"去请求
     * 祖先一起滚，页面滚到别的卡上时就会被拽回终端卡。直接改内层 scrollY 只动这一块。
     */
    /** 把内层滚到底（只改自己的 scrollY，不惊动外层页面）。 */
    private void pinBottom(NestedScrollView sv) {
        if (sv == null || sv.getChildCount() == 0) {
            return;
        }
        int content = sv.getChildAt(0).getHeight()
                + sv.getPaddingTop() + sv.getPaddingBottom();
        sv.scrollTo(0, Math.max(0, content - sv.getHeight()));
    }

    private void setupFollow(final NestedScrollView sv, final TextView tv) {
        sv.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, x, y, ox, oy) -> {
                    boolean bottom = atBottom(sv);
                    if (sv == termScroll) {
                        termFollow = bottom;
                    } else if (sv == logScroll) {
                        logFollow = bottom;
                    }
                });
        tv.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t <= ob - ot) {
                return; // 没变高，不需要滚
            }
            boolean follow = sv == termScroll ? termFollow : logFollow;
            if (!follow) {
                return;
            }
            sv.post(() -> pinBottom(sv));
        });
    }

    /** 长按整块复制：终端/日志关掉了文本选择（它会把拖动手势吃掉），复制走长按。 */
    private void copyAllLabelled(CharSequence text, String what) {
        android.content.ClipboardManager cm =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("ArkBox", text));
            toast("已复制" + what + "全部内容");
        }
    }

    private final Runnable credsRefresh = new Runnable() {
        @Override
        public void run() {
            if (txtCreds != null) {
                txtCreds.setText(Creds.summary());
            }
        }
    };

    private final Runnable logRefresh = new Runnable() {
        @Override
        public void run() {
            String text = LogBus.text();
            if (!text.contentEquals(txtLog.getText())) {
                txtLog.setText(text);
            }
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            ui.postDelayed(this, 600);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = new Prefs(this);
        bind();
        fill();
        wire();
        showVersion();
        askNotificationPermission();

        bg.execute(() -> {
            try {
                LocalServer.ensure(this);
            } catch (IOException e) {
                LogBus.append("本地服务起不来（安装时会退化成走网络）：" + e.getMessage());
            }
            Catalog.refresh(this);
        });

        if (!welcomed) {
            welcomed = true;
            LogBus.append("ArkBox 已就绪。中继直接跑在本 App 里（Chaquopy + ArkBridge）。");
            Term.note("第一次用：点「一键下载全部」→ 点「安装 / 更新」（Termux 运行时已内置，不用再装别的 App）。");
            if (!Termux.installed(this)) {
                LogBus.append("Termux 运行时还没解压：只想手机当中继可以不管；要跑 AstrBot 就先点「安装 / 更新」。");
            }
        }
        if (prefs.autostart() && !BridgeService.running) {
            BridgeService.start(this);
        }
        autoStartAll();
    }

    /**
     * 傻瓜式：装完之后打开 App 就自动把 AstrBot 和 QQ 链路拉起来，中继地址也自动
     * 迁移到 SnowLuma 直连 3001——用户不用手敲 box start / qq start，也不用去
     * WebUI 配 Milky。全部命令幂等，重复跑无害。
     */
    private void autoStartAll() {
        if (autoStarted || !prefs.autostart() || !Termux.installed(this) || Termux.installing(this)) {
            return;
        }
        autoStarted = true;
        ui.postDelayed(() -> bg.execute(() -> {
            Box.seedOneBotConfig(this);
            // 旧版默认中继指向 ArkLink 6700（要手动配 Milky）；指向 SnowLuma OneBot 3001
            if (prefs.arklink().equals(Prefs.DEF_ARKLINK)) {
                prefs.save(Box.ONEBOT_URL, prefs.astrbot(), prefs.selfId(),
                        prefs.arklinkToken(), prefs.astrbotToken());
                ui.post(() -> inpArklink.setText(Box.ONEBOT_URL));
                BridgeService.restart(this);
                LogBus.append("中继地址已指向 SnowLuma " + Box.ONEBOT_URL + "");
            }
            if (!BridgeService.running) {
                BridgeService.start(this);
            }
            Termux.send(this,
                    // 1) 刷新脚本到 ~/.arkbox。
                    //    纯 shell 脚本（qq/box/astr/creds/setpw/arknet）线上优先——
                    //    改这些不用再发 APK；拉不到就退回 APK 内置（离线也能用）。
                    //    install.sh 是与 APK 版本强绑定的安装流程，始终用内置的。
                    "mkdir -p \"$HOME/.arkbox/hook\""
                            + "; G=\"https://ghfast.top/https://raw.githubusercontent.com/panxian-AI/ArkBox/main/app/src/main/assets/termux\""
                            + "; H=\"https://raw.githubusercontent.com/panxian-AI/ArkBox/main/app/src/main/assets/termux\""
                            + "; for f in arknet.sh astr qq box creds.py setpw.py"
                            + " hook/libslhook.so hook/libsnowluma_linux_resident_dispatch.so; do"
                            + " if curl -fsSL -m 25 -o \"$HOME/.arkbox/$f.new\" \"$G/$f\" 2>/dev/null"
                            + " || curl -fsSL -m 25 -o \"$HOME/.arkbox/$f.new\" \"$H/$f\" 2>/dev/null; then"
                            + " [ -s \"$HOME/.arkbox/$f.new\" ] && { mv -f \"$HOME/.arkbox/$f.new\" \"$HOME/.arkbox/$f\"; chmod +x \"$HOME/.arkbox/$f\" 2>/dev/null; }"
                            + "; rm -f \"$HOME/.arkbox/$f.new\""
                            + "; else curl -fsSL -m 60 -o \"$HOME/.arkbox/$f\" \"$ARKBOX_LOCAL/a/termux/$f\" 2>/dev/null || true; fi; done"
                            + "; curl -fsSL -m 60 -o \"$HOME/.arkbox/install.sh\" \"$ARKBOX_LOCAL/a/termux/install.sh\" 2>/dev/null || true"
                            + "; chmod +x \"$HOME/.arkbox/install.sh\" \"$HOME/.arkbox/astr\""
                            + " \"$HOME/.arkbox/qq\" \"$HOME/.arkbox/box\" 2>/dev/null"
                            + "; for c in astr qq box; do ln -sf \"$HOME/.arkbox/$c\" \"$PREFIX/bin/$c\" 2>/dev/null; done"
                            // 2) 幂等拉起服务：装好的才起。顺序是硬要求——
                            //    QQ 要带 hook 起来，SnowLuma 才扫得到握手 socket
                            + "; if [ -f \"$HOME/.arkbox/rootfs/opt/QQ/qq\" ]; then qq start; fi"
                            + "; if [ -f \"$HOME/.arkbox/rootfs/opt/snowluma/index.mjs\" ]; then box start; fi"
                            + "; if [ -f \"$HOME/.arkbox/rootfs/root/AstrBot/main.py\" ]; then astr start; fi",
                    400);
            LogBus.append("已自动拉起 QQ → SnowLuma → AstrBot（装好的才会起；幂等，重复无害）");
        }), 4000);
    }

    @Override
    protected void onStart() {
        super.onStart();
        LogBus.addListener(logRefresh);
        logRefresh.run();
        Term.addListener(termRefresh);
        termRefresh.run();
        Creds.addListener(credsRefresh);
        credsRefresh.run();
        ui.post(ticker);
    }

    @Override
    protected void onStop() {
        super.onStop();
        LogBus.removeListener(logRefresh);
        Term.removeListener(termRefresh);
        Creds.removeListener(credsRefresh);
        ui.removeCallbacks(ticker);
    }

    // ------------------------------------------------------------------ 初始化

    private void bind() {
        chipBridge = findViewById(R.id.chipBridge);
        chipAccount = findViewById(R.id.chipAccount);
        chipAstr = findViewById(R.id.chipAstr);
        txtBridgeState = findViewById(R.id.txtBridgeState);
        txtAstrState = findViewById(R.id.txtAstrState);
        txtStats = findViewById(R.id.txtStats);
        txtLog = findViewById(R.id.txtLog);
        logScroll = findViewById(R.id.logScroll);
        swAutostart = findViewById(R.id.swAutostart);
        inpArklink = findViewById(R.id.inpArklink);
        inpAstrbot = findViewById(R.id.inpAstrbot);
        inpSelfId = findViewById(R.id.inpSelfId);
        inpAstrbotToken = findViewById(R.id.inpAstrbotToken);
        inpArklinkToken = findViewById(R.id.inpArklinkToken);
        btnCheck = findViewById(R.id.btnCheck);
        progressDl = findViewById(R.id.progressDl);
        txtDlState = findViewById(R.id.txtDlState);
        txtCreds = findViewById(R.id.txtCreds);
        txtTerm = findViewById(R.id.txtTerm);
        termScroll = findViewById(R.id.termScroll);
        inpCmd = findViewById(R.id.inpCmd);

        scroller = findViewById(R.id.scroller);
        cardBridge = findViewById(R.id.cardBridge);
        cardDl = findViewById(R.id.cardDl);
        cardAstr = findViewById(R.id.cardAstr);
        cardTerm = findViewById(R.id.cardTerm);
        cardQq = findViewById(R.id.cardQq);
        cardProgress = findViewById(R.id.cardProgress);
        progressInstall = findViewById(R.id.progressInstall);
        txtInstallStep = findViewById(R.id.txtInstallStep);
        txtInstallHint = findViewById(R.id.txtInstallHint);
        dlList = findViewById(R.id.dlList);

        // 内层是 NestedScrollView：拖动先滚这一块，滚到边界再交给外层页面接力，
        // 不需要再手动 requestDisallowInterceptTouchEvent（那样反而会让手指在终端里
        // 划不动整页）。文本也不再设为可选——可选文本会把拖动手势抢去做选区。
        setupFollow(termScroll, txtTerm);
        setupFollow(logScroll, txtLog);
        txtTerm.setOnLongClickListener(v -> {
            copyAllLabelled(Term.text(), "终端");
            return true;
        });
        txtLog.setOnLongClickListener(v -> {
            copyAllLabelled(LogBus.text(), "日志");
            return true;
        });
    }

    /** 顶部导航：把对应卡片滚到可视区，别让人靠猜「功能在哪」。 */
    private void jumpTo(View card) {
        if (scroller == null || card == null) {
            return;
        }
        scroller.post(() -> scroller.smoothScrollTo(0, Math.max(0, card.getTop() - 6)));
    }

    private void showVersion() {
        TextView tv = findViewById(R.id.txtVersion);
        if (tv == null) {
            return;
        }
        String v = "?";
        try {
            v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        tv.setText("v" + v);
    }

    private void fill() {
        inpArklink.setText(prefs.arklink());
        inpAstrbot.setText(prefs.astrbot());
        inpSelfId.setText(prefs.selfId());
        inpAstrbotToken.setText(prefs.astrbotToken());
        inpArklinkToken.setText(prefs.arklinkToken());
        swAutostart.setChecked(prefs.autostart());
    }

    private void wire() {
        findViewById(R.id.btnStart).setOnClickListener(v -> {
            BridgeService.start(this);
            toast("已下发启动");
        });
        findViewById(R.id.btnStop).setOnClickListener(v -> {
            BridgeService.stop(this);
            toast("已下发停止");
        });
        btnCheck.setOnClickListener(v -> runProbe());
        findViewById(R.id.btnSave).setOnClickListener(v -> saveAndRestart());
        findViewById(R.id.btnBattery).setOnClickListener(v -> requestBatteryWhitelist());
        findViewById(R.id.btnInstall).setOnClickListener(v -> installAstrBot());
        findViewById(R.id.btnAstrStart).setOnClickListener(v -> {
            Term.note("$ astr start");
            sendCommandAsync("astr start");
        });
        findViewById(R.id.btnAstrStop).setOnClickListener(v -> {
            Term.note("$ astr stop");
            sendCommandAsync("astr stop");
        });
        findViewById(R.id.btnPanel).setOnClickListener(v -> openPanel());
        findViewById(R.id.btnCopyLog).setOnClickListener(v -> copyLog());
        findViewById(R.id.btnClearLog).setOnClickListener(v -> LogBus.clear());
        swAutostart.setOnCheckedChangeListener((button, checked) -> prefs.setAutostart(checked));

        findViewById(R.id.btnDlAll).setOnClickListener(v -> startDownload(null));
        buildDlList();

        findViewById(R.id.btnCreds).setOnClickListener(v -> readCreds());
        findViewById(R.id.btnResetPw).setOnClickListener(v -> askResetPassword());
        findViewById(R.id.btnPanelWeb).setOnClickListener(v -> openPanelInApp());

        findViewById(R.id.btnCmdSend).setOnClickListener(v -> sendCommand(value(inpCmd)));
        findViewById(R.id.btnTermStatus).setOnClickListener(v -> {
            Term.note("$ astr status");
            sendCommandAsync("astr status");
        });
        findViewById(R.id.btnTermLog).setOnClickListener(v -> {
            Term.note("$ tail install.log astr.log");
            sendCommand("tail -n 40 ~/.arkbox/install.log ~/.arkbox/astr.log 2>/dev/null");
        });
        findViewById(R.id.btnTermClear).setOnClickListener(v -> Term.clear());

        findViewById(R.id.btnQqInstall).setOnClickListener(v -> {
            if (!readyToSend()) {
                return;
            }
            jumpTo(cardQq);
            Term.note("$ bash install.sh --qq-only   # QQ deb + Xvfb + ArkLink/SnowLuma");
            toast("开始装本机 QQ（约 250 MB，进度在「终端」里看）");
            Box.install(this);
            ui.postDelayed(() -> Box.seedOneBotConfig(this), 5000);
        });
        findViewById(R.id.btnQqStart).setOnClickListener(v -> {
            if (!readyToSend()) {
                return;
            }
            jumpTo(cardQq);
            Term.note("$ qq start");
            Box.start(this);
            toast("QQ（无头）和 ArkLink 正在启动，稍后看状态");
        });
        findViewById(R.id.btnQqWeb).setOnClickListener(v -> openWeb(Box.WEBUI_URL, "SnowLuma WebUI"));
        findViewById(R.id.btnQqVnc).setOnClickListener(v -> openQqWindow());
        findViewById(R.id.btnSlPass).setOnClickListener(v -> readSlPass());
        findViewById(R.id.btnSlReset).setOnClickListener(v -> askSlReset());
        findViewById(R.id.btnQqFill).setOnClickListener(v -> {
            inpArklink.setText(Box.ONEBOT_URL);
            prefs.save(Box.ONEBOT_URL, value(inpAstrbot), value(inpSelfId),
                    value(inpArklinkToken), value(inpAstrbotToken));
            BridgeService.restart(this);
            toast("中继已指向本机 SnowLuma：" + Box.ONEBOT_URL + "");
        });

        findViewById(R.id.navQq).setOnClickListener(v -> jumpTo(cardQq));
        findViewById(R.id.navBridge).setOnClickListener(v -> jumpTo(cardBridge));
        findViewById(R.id.navDl).setOnClickListener(v -> jumpTo(cardDl));
        findViewById(R.id.navAstr).setOnClickListener(v -> jumpTo(cardAstr));
        findViewById(R.id.navTerm).setOnClickListener(v -> jumpTo(cardTerm));
        findViewById(R.id.navPanel).setOnClickListener(v -> openPanelInApp());
    }

    // ------------------------------------------------------------------ 状态刷新

    private void refresh() {
        String json = BridgeService.statusJson;
        boolean running = BridgeService.running;
        boolean astrUp = BridgeService.astrOnline;

        chipBridge.setText(running ? "中继 · 运行中" : "中继 · 已停止");
        chipBridge.setTextColor(getColor(running ? R.color.ok : R.color.muted));

        long selfId = BridgeService.selfId(json);
        chipAccount.setText(selfId > 0 ? "账号 · " + selfId : "账号 · 待学习");
        chipAccount.setTextColor(getColor(selfId > 0 ? R.color.text : R.color.muted));

        chipAstr.setText(astrUp ? "AstrBot · 在线" : "AstrBot · 离线");
        chipAstr.setTextColor(getColor(astrUp ? R.color.ok : R.color.muted));

        int[] c = BridgeService.counters(json);
        txtStats.setText("事件 " + c[0] + " · 转发 " + c[1] + " · API " + c[2]
                + " · 重连 " + c[3] + " · 缓冲 " + c[4]);

        String err = BridgeService.error(json);
        if (running) {
            txtBridgeState.setText(astrUp
                    ? "ArkLink ↔ AstrBot 搬运中，AstrBot 已连上"
                    : "ArkLink ↔ AstrBot 搬运中，等 AstrBot 连上来");
            txtBridgeState.setTextColor(getColor(R.color.muted));
        } else if (!err.isEmpty()) {
            txtBridgeState.setText("未启动：" + err);
            txtBridgeState.setTextColor(getColor(R.color.bad));
        } else {
            txtBridgeState.setText("未启动，点「启动」开始搬运");
            txtBridgeState.setTextColor(getColor(R.color.muted));
        }

        if (!Termux.installed(this)) {
            txtAstrState.setText(Termux.installing(this)
                    ? "Termux 运行时正在解压，几秒后就能用了。"
                    : "运行时没就绪。先在下载卡点「一键下载全部」，再点「安装 / 更新」。");
            txtAstrState.setTextColor(getColor(R.color.warn));
        } else if (astrUp) {
            txtAstrState.setText("面板在线 · " + PANEL_URL);
            txtAstrState.setTextColor(getColor(R.color.ok));
        } else {
            txtAstrState.setText("运行时就绪，面板没响应（还没装完，或者没启动）");
            txtAstrState.setTextColor(getColor(R.color.muted));
        }

        refreshDownload();
        refreshQq();

        // AstrBot 一起来就自己去把面板账号读出来，不用人再点一次「读取账号」
        if (astrUp && !credsAuto) {
            credsAuto = true;
            readCreds();
        }
        // 三个包都齐了，把人带到下一步
        if (!dlDoneNotice && Downloads.allReady(this)) {
            dlDoneNotice = true;
            jumpTo(cardAstr);
            toast("组件都下好了，点「安装 / 更新」继续");
        }

        // 安装进度条（install.sh 实时推的步骤）
        boolean showProgress = InstallProgress.visible();
        cardProgress.setVisibility(showProgress ? View.VISIBLE : View.GONE);
        if (showProgress) {
            progressInstall.setProgress(InstallProgress.percent());
            txtInstallStep.setText(InstallProgress.line());
            txtInstallHint.setText(InstallProgress.hint());
        }
        // 安装进行中把「安装 / 更新」按住：连点会发第二个安装器，锁会让它空转
        View installBtn = findViewById(R.id.btnInstall);
        boolean installing = InstallProgress.busy();
        installBtn.setEnabled(!installing);
        installBtn.setAlpha(installing ? 0.45f : 1f);
    }

    private void refreshDownload() {
        boolean ready = Downloads.allReady(this);
        if (Downloads.busy) {
            progressDl.setProgress(Downloads.percent());
            txtDlState.setText(Downloads.fractionText());
            txtDlState.setTextColor(getColor(R.color.brand));
        } else {
            progressDl.setProgress(ready ? 100 : 0);
            java.util.List<Catalog.Item> items = Catalog.items(this);
            int ok = 0;
            for (Catalog.Item it : items) {
                if (it.ready(this)) {
                    ok++;
                }
            }
            txtDlState.setText(ready
                    ? "全部 " + items.size() + " 个组件都下好了。下一步：装 Termux → 点「安装 / 更新」。"
                    : (Downloads.lastError.isEmpty()
                        ? "已就绪 " + ok + "/" + items.size() + " 个组件。点下面的行补下缺的，或点「一键下载全部」。"
                        : "已就绪 " + ok + "/" + items.size() + " 个。\n" + Downloads.lastError));
            txtDlState.setTextColor(getColor(ready ? R.color.ok : R.color.muted));
        }
        // 每个组件一行：✓ 已就绪 / ○ 待下载 xx MB / ⏳ 下载中
        String current = Downloads.busy ? Downloads.label : "";
        for (java.util.Map.Entry<String, TextView> e : dlRows.entrySet()) {
            Catalog.Item it = Catalog.find(this, e.getKey());
            if (it == null) {
                continue;
            }
            String status;
            if (Downloads.busy && it.title.equals(current)) {
                status = "⏳ " + Math.min(100, Downloads.percent()) + "%";
            } else if (it.ready(this)) {
                status = "✓ 已就绪";
            } else {
                status = "○ 待下载 " + it.sizeLabel();
            }
            e.getValue().setText(it.title + "  ·  " + status);
        }
    }

    /** 下载卡的全组件清单：manifest 里有什么就列什么，点行下单个，一眼看出缺没缺。 */
    private void buildDlList() {
        java.util.List<Catalog.Item> items = Catalog.items(this);
        dlList.removeAllViews();
        dlRows.clear();
        int padV = (int) (10 * getResources().getDisplayMetrics().density);
        for (Catalog.Item it : items) {
            TextView row = new TextView(this);
            row.setPadding(0, padV, 0, padV);
            row.setTextSize(13);
            row.setTextColor(getColor(R.color.text));
            row.setText(it.title);
            row.setOnClickListener(v -> startDownload(java.util.Collections.singletonList(it.id)));
            dlList.addView(row);
            dlRows.put(it.id, row);
        }
    }

    // ------------------------------------------------------------------ 动作

    private void saveAndRestart() {
        String arklink = value(inpArklink);
        String astrbot = value(inpAstrbot);
        if (arklink.isEmpty() || astrbot.isEmpty()) {
            toast("ArkLink 和 AstrBot 地址不能空");
            return;
        }
        prefs.save(arklink, astrbot, value(inpSelfId), value(inpArklinkToken), value(inpAstrbotToken));
        BridgeService.restart(this);
        if (arklink.contains("127.0.0.1") || arklink.contains("localhost")) {
            toast(Box.webuiUp
                    ? "已保存。连的是手机本机，SnowLuma 在线，应该能通"
                    : "已保存。但本机现在没有协议端在跑——127.0.0.1 指手机自己，"
                      + "要么先装本机 QQ，要么填电脑的局域网 IP");
        } else {
            toast("已保存，正在重启中继");
        }
    }

    private void runProbe() {
        final File cfg = BridgeService.configFile(this);
        try {
            BridgeService.writeConfig(this, cfg);
        } catch (IOException e) {
            toast("写配置失败：" + e.getMessage());
            return;
        }
        btnCheck.setEnabled(false);
        toast("自检中…");
        bg.execute(() -> {
            final String result = PyEngine.call(this, "probe", cfg.getAbsolutePath());
            ui.post(() -> {
                LogBus.append("—— 自检 ——\n" + result);
                btnCheck.setEnabled(true);
            });
        });
    }

    private void installAstrBot() {
        Catalog.Item bs = Catalog.find(this, "bootstrap");
        if (!Termux.installed(this)) {
            if (bs == null || !bs.ready(this)) {
                new AlertDialog.Builder(this)
                        .setTitle("先下载运行时")
                        .setMessage("Termux 运行时（bootstrap，约 32 MB）还没下好。"
                                + "去下载卡点「一键下载全部」，或单独点清单里「Termux 运行时」那一行。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("去下载", (d, w) -> {
                            jumpTo(cardDl);
                            if (bs != null) {
                                startDownload(java.util.Collections.singletonList("bootstrap"));
                            } else {
                                startDownload(null);
                            }
                        })
                        .show();
                return;
            }
            // bootstrap 已下好，直接解压 + 装下去
            Term.note("=== 开始安装 AstrBot ===");
            boolean ok = Termux.install(this);
            toast(ok ? "正在解压运行时并开始安装，进度看下面终端" : "安装没启动，看日志");
            return;
        }
        boolean allReady = Downloads.allReady(this);
        String extra = allReady
                ? "要用的包都已经下好，走本机回环递给 Termux，不再走流量。\n\n"
                : "还有包没下：Termux 会自己去镜像下（也能装完，就是慢点，建议先点「一键下载全部」）。\n\n";
        new AlertDialog.Builder(this)
                .setTitle("安装 / 更新 AstrBot")
                .setMessage(extra
                        + "流程：解 Ubuntu 根文件系统到手机（proot，不用 root）→ 装 Python 3.12 + uv → "
                        + "解 AstrBot → 装依赖。总占用约 2.5 GB，挂 WiFi。\n\n"
                        + "装完会注册 astr 命令。进度条在导航条下面，实时输出在「终端」窗里。")
                .setNegativeButton("取消", null)
                .setPositiveButton("开始", (dialog, which) -> {
                    Term.note("=== 开始安装 AstrBot ===");
                    boolean ok = Termux.install(this);
                    toast(ok ? "已开始，进度看下面终端" : "安装没启动，看日志");
                })
                .show();
    }

    // ------------------------------------------------------------------ 内置下载

    private void startDownload(java.util.List<String> ids) {
        if (Downloads.busy) {
            toast("还在下，等这批完");
            return;
        }
        Downloads.start(this, ids);
        toast("开始测速并下载，进度会显示在这张卡上");
    }

    // ------------------------------------------------------------------ 终端 / 账号

    private void sendCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }
        if (!readyToSend()) {
            return;
        }
        inpCmd.setText("");
        // 用户主动发了命令，就是要看输出：重新跟随最新（之前翻历史留下的暂停状态清掉）
        termFollow = true;
        Term.note("$ " + command);
        BridgeService.serve(this);
        if (!Termux.send(this, command, 900)) {
            toastSendFail();
        }
    }

    /** 发命令前统一检查：bootstrap 解压好了没有。 */
    private boolean readyToSend() {
        if (!Termux.installed(this)) {
            showTermuxHelp();
            return false;
        }
        return true;
    }

    private void sendCommandAsync(String command) {
        if (!readyToSend()) {
            return;
        }
        termFollow = true;
        BridgeService.serve(this);
        if (!Termux.astr(this, command.replaceFirst("^astr ", ""))) {
            toastSendFail();
        }
    }

    /** 发命令失败的统一提示。 */
    private void toastSendFail() {
        if (Termux.installing(this)) {
            toast("bootstrap 还在解压，等几秒再试");
        } else {
            toast("命令没能执行，看「运行日志」里的提示");
        }
    }

    private void readCreds() {
        if (!readyToSend()) {
            return;
        }
        Creds.reset();
        txtCreds.setText("正在读 AstrBot 配置…");
        BridgeService.serve(this);
        // 落文件再 POST：管道喂 curl 会先缓冲，这条命令又依赖 $ARKBOX_LOCAL，绕开更稳
        Termux.send(this,
                "mkdir -p ~/.arkbox"
                        + "; astr creds > ~/.arkbox/cred.txt 2>&1"
                        + "; curl -sS -m 60 -X POST --data-binary @\"$HOME/.arkbox/cred.txt\""
                        + " \"$ARKBOX_LOCAL/p/cred\""
                        + "; cat ~/.arkbox/cred.txt",
                120);
    }

    private void askResetPassword() {
        if (!readyToSend()) {
            return;
        }
        final TextInputEditText input = new TextInputEditText(this);
        input.setHint("新密码（大写+小写+数字，≥8 位；留空自动生成）");
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("重置 AstrBot 面板密码")
                .setMessage("直接改 AstrBot 的配置文件（会先停掉 AstrBot，改完自动重启）。\n\n"
                        + "新密码会显示在上面的「账号密码」卡里，照着输就能登录面板。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("重置", (dialog, which) -> {
                    String pw = value(input);
                    if (!pw.isEmpty()) {
                        if (pw.length() < 8) {
                            toast("太短了，至少 8 位");
                            return;
                        }
                        if (!pw.matches(".*[A-Z].*") || !pw.matches(".*[a-z].*")
                                || !pw.matches(".*[0-9].*")) {
                            toast("要同时含大写字母、小写字母和数字");
                            return;
                        }
                    }
                    Term.note("=== 重置面板密码 ===");
                    BridgeService.serve(this);
                    Termux.resetPassword(this, pw);
                    // resetpw 要停进程 + 改配置 + 重启，等它跑完再自动读一次账号
                    ui.postDelayed(this::readCreds, 25_000);
                })
                .show();
    }

    // ---------------------------------------------------------- SnowLuma WebUI 密码

    /** 读 SnowLuma WebUI 登录密码：box token --raw 打回来弹窗显示。 */
    private void readSlPass() {
        if (!readyToSend()) {
            return;
        }
        Box.webuiPass = "";
        Term.note("=== 读 SnowLuma WebUI 密码 ===");
        BridgeService.serve(this);
        // 落文件再 POST：和读 AstrBot 账号一个套路，绕开管道缓冲
        Termux.send(this,
                "mkdir -p ~/.arkbox"
                        + "; box token --raw > ~/.arkbox/slp.txt 2>&1"
                        + "; curl -sS -m 30 -X POST --data-binary @\"$HOME/.arkbox/slp.txt\""
                        + " \"$ARKBOX_LOCAL/p/slpass\""
                        + "; cat ~/.arkbox/slp.txt",
                60);
        // box token 是本地 grep，秒回；留 4 秒余量等 curl 打回来
        ui.postDelayed(this::showSlPassDialog, 4000);
    }

    private void showSlPassDialog() {
        String pw = Box.webuiPass == null ? "" : Box.webuiPass;
        String msg;
        if (pw.isEmpty()) {
            msg = "没读到明文密码。\n\n常见两种情况：\n"
                    + "1. SnowLuma 还没启动过（先点「启动」跑一次 box start）；\n"
                    + "2. 密码已经在 WebUI 里改过了——哈希保存看不回明文。\n\n"
                    + "忘了密码就点「重置密码」，设一个新密码（SnowLuma 会自动重启）。";
        } else {
            msg = "账号 admin\n密码 " + pw + "\n\n"
                    + "登录后 WebUI 会要求改一次密码，改完这个初始密码就作废了。\n"
                    + "忘了随时回来点「重置 WebUI 密码」。";
        }
        new AlertDialog.Builder(this)
                .setTitle("SnowLuma WebUI 登录")
                .setMessage(msg)
                .setNegativeButton("关闭", null)
                .setPositiveButton("重置密码", (d, w) -> askSlReset())
                .show();
    }

    /** 重置 SnowLuma WebUI 密码：box setpass 会写 bootstrap 密码 + 删旧凭据 + 重启。 */
    private void askSlReset() {
        if (!readyToSend()) {
            return;
        }
        final TextInputEditText input = new TextInputEditText(this);
        input.setHint("新密码（≥8 位；留空自动生成）");
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("重置 SnowLuma WebUI 密码")
                .setMessage("会备份并删掉旧凭据、写入新密码，然后自动重启 SnowLuma（约 20 秒）。\n\n"
                        + "新密码会显示在终端里，重启完成后用它登录 WebUI（账号 admin）。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("重置", (dialog, which) -> {
                    String pw = value(input);
                    if (pw.isEmpty()) {
                        pw = genPass();
                        toast("已生成：" + pw);
                    }
                    if (pw.length() < 8) {
                        toast("太短了，至少 8 位");
                        return;
                    }
                    String safe = pw.replace("'", "'\\''");
                    Term.note("=== 重置 SnowLuma WebUI 密码 ===");
                    BridgeService.serve(this);
                    Termux.send(this, "box setpass '" + safe + "'", 300);
                    // setpass 内部有 start 的 12 秒等待，跑完自动再读一次确认
                    ui.postDelayed(this::readSlPass, 20_000);
                })
                .show();
    }

    /** 生成一个肯定满足 ≥8 位、含大小写和数字的随机密码。 */
    private String genPass() {
        String up = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String low = "abcdefghijkmnpqrstuvwxyz";
        String num = "23456789";
        java.security.SecureRandom r = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        sb.append(up.charAt(r.nextInt(up.length())));
        sb.append(low.charAt(r.nextInt(low.length())));
        sb.append(num.charAt(r.nextInt(num.length())));
        String all = up + low + num;
        for (int i = 0; i < 9; i++) {
            sb.append(all.charAt(r.nextInt(all.length())));
        }
        return sb.toString();
    }

    private void sendToTermux(String command) {
        if (!readyToSend()) {
            return;
        }
        boolean ok = Termux.run(this, command, true);
        toast(ok ? "已发给 Termux：" + command : "发送失败，看看 Termux 卡里的帮助说明");
    }

    private void openPanel() {
        openPanelInApp();
    }

    private void refreshQq() {
        TextView tv = findViewById(R.id.txtQqState);
        if (tv == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - Box.probedAt > 3000) {
            Box.probedAt = now;              // 先占位，避免每 600ms 都压一个探测
            bg.execute(Box::probe);
        }
        if (Box.onebotUp && Box.webuiUp) {
            tv.setText("链路在线：SnowLuma OneBot 3001 + WebUI 5099（中继连它，不用再配 Milky）\n"
                    + "扫码页：" + Box.NOVNC_URL);
            tv.setTextColor(getColor(R.color.ok));
        } else if (Box.onebotUp) {
            tv.setText("OneBot 3001 在线（AstrBot 直连它），WebUI 5099 没响应——box log 看日志\n"
                    + "扫码页：" + Box.NOVNC_URL);
            tv.setTextColor(getColor(R.color.warn));
        } else if (Box.webuiUp) {
            tv.setText("SnowLuma 在线（WebUI 5099），OneBot 3001 未确认\n"
                    + "扫码页：" + Box.NOVNC_URL);
            tv.setTextColor(getColor(R.color.warn));
        } else {
            tv.setText("没响应。第一次：点「装 QQ 链路」（一并装 QQ/Xvfb/ArkLink/SnowLuma）→ 点「启动」\n"
                    + "→ 浏览器开 http://127.0.0.1:6081/vnc.html 扫码登录 QQ（ArkLink/SnowLuma 自动接上）\n"
                    + "→ 点「填本机地址」，中继直连 ws://127.0.0.1:3001（不用配 Milky）\n"
                    + "扫码页：" + Box.NOVNC_URL);
            tv.setTextColor(getColor(R.color.muted));
        }
    }

    /**
     * 打开 QQ 的画面（noVNC 6081）——扫码登录、验证、点「确认登录」都在这里操作。
     * 没起来就给出「启动 QQ」这个一键路径，别让人自己去找 URL。
     */
    private void openQqWindow() {
        if (!readyToSend()) {
            return;
        }
        if (System.currentTimeMillis() - Box.probedAt > 4000) {
            bg.execute(Box::probe);
        }
        if (Box.novncUp) {
            openWeb(Box.NOVNC_VIEW, "QQ 窗口 · 扫码登录");
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("QQ 窗口还没起来")
                .setMessage("6081 没响应，说明 Xvfb / QQ / noVNC 还没跑起来。\n\n"
                        + "点「启动 QQ」会执行 qq start（无头 X + QQ + noVNC），大约 15 秒后自动打开窗口。\n\n"
                        + "已经启动过还是这样：`qq status` 看端口，`qq log` 看报错。")
                .setNegativeButton("取消", null)
                .setNeutralButton("仍然打开", (d, w) -> openWeb(Box.NOVNC_VIEW, "QQ 窗口 · 扫码登录"))
                .setPositiveButton("启动 QQ", (d, w) -> {
                    Term.note("$ qq start");
                    BridgeService.serve(this);
                    Termux.send(this, "qq start", 300);
                    ui.postDelayed(() -> openWeb(Box.NOVNC_VIEW, "QQ 窗口 · 扫码登录"), 15_000);
                })
                .show();
    }

    private void openWeb(String url, String title) {
        try {
            Intent intent = new Intent(this, PanelActivity.class);
            intent.putExtra(PanelActivity.EXTRA_URL, url);
            intent.putExtra(PanelActivity.EXTRA_TITLE, title);
            startActivity(intent);
        } catch (Exception e) {
            toast("打不开内置浏览器：" + e.getMessage());
        }
    }

    private void openPanelInApp() {
        try {
            startActivity(new Intent(this, PanelActivity.class));
        } catch (Exception e) {
            toast("内置浏览器打不开：" + e.getMessage());
        }
    }

    private void copyLog() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) {
            return;
        }
        cm.setPrimaryClip(ClipData.newPlainText("ArkBox log", LogBus.text()));
        toast("日志已复制");
    }

    private void requestBatteryWhitelist() {
        try {
            Intent intent = new Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
                toast("这台机器不支持直接跳转，去系统设置里手动放行");
            }
        }
    }

    private void showTermuxHelp() {
        boolean partial = Termux.installing(this);
        String message;
        if (partial) {
            message = "Termux 运行时正在解压（bootstrap 约 32 MB，几秒钟就好）。"
                    + "等一下再点刚才的按钮；解压失败的重新点「安装 / 更新」会自动重解。";
        } else {
            message = "AstrBot 的依赖（faiss / pandas / watchfiles 等）只有 glibc 轮子，"
                    + "Android 原生环境装不上，官方也是 Termux + proot Ubuntu 这条路。\n\n"
                    + "Termux 运行时已经内置在本 App 里（v0.4.0 起不用再装 ArkTerm）："
                    + "先在下载卡点「一键下载全部」（含 bootstrap 约 32 MB），"
                    + "然后点「安装 / 更新」就会自动解压并开始安装。";
        }
        new AlertDialog.Builder(this)
                .setTitle(partial ? "运行时正在解压" : "还没装 AstrBot 运行时")
                .setMessage(message)
                .setNegativeButton("知道了", null)
                .setPositiveButton("去下载卡", (dialog, which) -> jumpTo(cardDl))
                .show();
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
    }

    // ------------------------------------------------------------------ 小工具

    private static String value(TextInputEditText input) {
        CharSequence text = input.getText();
        return text == null ? "" : text.toString().trim();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
