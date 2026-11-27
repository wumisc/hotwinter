package io.github.wumisc.hotwinter.ui;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.wumisc.hotwinter.R;
import io.github.wumisc.hotwinter.cloud.CloudPanel;
import io.github.wumisc.hotwinter.cloud.CloudPrefs;
import io.github.wumisc.hotwinter.cloud.CloudService;
import io.github.wumisc.hotwinter.cloud.CloudSync;
import io.github.wumisc.hotwinter.core.HeatRuntime;
import io.github.wumisc.hotwinter.core.HeatSample;
import io.github.wumisc.hotwinter.core.HeatStatus;
import io.github.wumisc.hotwinter.core.Preset;
import io.github.wumisc.hotwinter.core.ThermalMonitor;
import io.github.wumisc.hotwinter.couple.CoupleLink;
import io.github.wumisc.hotwinter.couple.CoupleService;
import io.github.wumisc.hotwinter.data.DeviceInspector;
import io.github.wumisc.hotwinter.data.LocalArtwork;
import io.github.wumisc.hotwinter.data.Logbook;
import io.github.wumisc.hotwinter.data.RomCompat;
import io.github.wumisc.hotwinter.HeatApp;
import io.github.wumisc.hotwinter.danmaku.DanmakuOverlayService;
import io.github.wumisc.hotwinter.danmaku.DanmakuSender;
import io.github.wumisc.hotwinter.danmaku.DmBus;
import io.github.wumisc.hotwinter.danmaku.DmMessage;
import io.github.wumisc.hotwinter.danmaku.DmSettings;
import io.github.wumisc.hotwinter.danmaku.UdpMesh;
import io.github.wumisc.hotwinter.data.SettingsStore;
import io.github.wumisc.hotwinter.net.LocalChannel;
import io.github.wumisc.hotwinter.service.HeatingService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_NOTIF = 102;
    private static final int REQ_STORAGE = 103;
    private static final int REQ_BT = 104;
    private static final int REQ_LOCAL_NETWORK = 106;
    private static final int REQ_QR_CAMERA = 107;
    private static final int REQ_CAMERA = 108;
    private static final long TICK_MS = 500L;

    private SettingsStore store;
    private HeroTempView heroTemp;
    private TextView tvHeroStatus;
    private TextView tvSubtitle;
    private TextView btnMain;
    private TextView tvPresetHint;
    private TextView tvTargetValue;
    private TextView tvAutoOffValue;
    private TextView tvFloorValue;
    private TextView tvLoad;
    private TextView tvThreads;
    private TextView tvTemp;
    private TextView tvThermal;
    private TextView tvElapsed;
    private TextView chipGentle;
    private TextView chipBalanced;
    private TextView chipStrong;
    private TextView chipExtreme;
    private TextView chipCheck;
    private TextView tvCheckResult;
    private TextView tvCrash;
    private TextView chipCopyCrash;
    private String crashText = "";
    private SwitchCompat switchLog;
    private SwitchCompat switchKeepAlive;
    private SwitchCompat switchReceive;
    private TextView tvLogPath;
    private TextView tvCoupleStatus;
    private TextView chipPick;
    private TextView chipHeatHer;
    private TextView chipStopHer;
    private SceneryView scenery;
    private CoupleView couple;
    private SparkView sparkTemp;
    private TextView tvSparkCaption;
    private TextView tvRom;
    private TextView tvRomNote;
    private TextView chipAutostart;
    private TextView chipBattery;
    private TextView chipAppInfo;
    private SwitchCompat switchCouple;
    private RomCompat rom;
    private boolean lastWarmState;
    private long lastStartedAt;
    private long lastSparkAt;
    private float runStartTemp = -1f;
    private TextView tabHome;
    private TextView tabFeature;
    private TextView tabDanmaku;
    private TextView tabMine;
    private View pageHome;
    private View pageFeature;
    private View pageDanmaku;
    private View pageMine;
    private CloudPanel cloudPanel;
    private View navBar;
    private int currentTab;
    private SeekBar seekTarget;
    private SeekBar seekAutoOff;
    private SeekBar seekFloor;
    private SwitchCompat switchEco;

    private DmSettings dmSettings;
    private SwitchCompat switchOverlay;
    private SwitchCompat switchUdp;
    private SwitchCompat switchDmNick;
    private EditText etNick;
    private EditText etRoom;
    private EditText etPort;
    private EditText etPeerIp;
    private EditText etDmInput;
    private TextView tvDmStatus;
    private TextView tvDmFeed;
    private TextView tvDmFontValue;
    private TextView tvDmSpeedValue;
    private TextView tvDmAlphaValue;
    private TextView tvDmLanesValue;
    private TextView chipDmSend;
    private TextView chipDmBt;
    private TextView chipDmChannelCloud;
    private TextView chipDmChannelUdp;
    private TextView chipDmChannelBt;
    private TextView tvDmChannelHint;
    private CloudSync cloudSync;
    private final CloudSync.Listener cloudListener = this::onCloudStateChanged;
    private SeekBar seekDmFont;
    private SeekBar seekDmSpeed;
    private SeekBar seekDmAlpha;
    private SeekBar seekDmLanes;
    private boolean pendingOverlay;
    private Runnable pendingLocalNetworkAction;
    private String dmFeedText = "";
    private DmBus.Listener dmListener;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private ExecutorService worker;
    private HeatSample idleSample = HeatSample.empty();
    private boolean ticking;
    private int idleTick;
    private long prevCpuMs = -1;
    private long prevCpuAt;
    private float measuredPct = -1f;
    private Runnable pendingBluetoothAction;

    private LocalChannel channel;
    private LocalChannel.Listener channelListener;
    private TextView tvChannelStatus;
    private TextView tvChannelCode;
    private TextView tvChannelCodeTip;
    private TextView tvChannelMembers;
    private TextView tvPeersEmpty;
    private LinearLayout listChannelPeers;
    private TextView chipCreateChannel;
    private TextView chipJoinChannel;
    private TextView chipChannelQr;
    private TextView chipChannelScan;
    private TextView chipStopChannel;
    private SwitchCompat switchChannelAuto;
    private AlertDialog autoAcceptDialog;
    private Runnable autoAcceptTask;
    private boolean resumed;
    private boolean qrScanPending;
    private boolean channelPermissionAsked;
    private boolean fatalShown;
    private boolean uiReady;
    private int renderFailures;
    private Uri qrOutputUri;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!ticking || fatalShown || !uiReady) return;
            try {
                render();
                renderFailures = 0;
            } catch (Throwable error) {
                renderFailures++;
                Logbook.log("app", "刷新界面失败：" + android.util.Log.getStackTraceString(error));
                // 单次异常继续刷新；连续失败就停下来，别把主线程拖成 ANR。
                if (renderFailures >= 5) {
                    ticking = false;
                    ui.removeCallbacks(this);
                    return;
                }
            }
            if (ticking) ui.postDelayed(this, TICK_MS);
        }
    };

    private final CoupleLink.Listener coupleListener = (text, connected) -> {
        if (tvCoupleStatus != null) {
            tvCoupleStatus.setText(text);
            tvCoupleStatus.setTextColor(ContextCompat.getColor(this,
                    connected ? R.color.accent : R.color.text_secondary));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            initUi();
        } catch (Throwable error) {
            showFatal(error);
        }
    }

    private void initUi() {
        store = SettingsStore.get(this);
        Logbook.init(this);
        // 自己处理状态栏/导航栏内边距，避免内容顶到状态栏
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "hotwinter-ui-task");
            thread.setDaemon(true);
            return thread;
        });
        try {
            idleSample = ThermalMonitor.read(this);
        } catch (Throwable error) {
            idleSample = HeatSample.empty();
            Logbook.log("app", "读取温度失败：" + error);
        }

        bindViews();
        try {
            initChannel();
        } catch (Throwable error) {
            channel = null;
            Logbook.log("app", "频道初始化失败：" + error);
        }
        bindViewport();
        bindActions();
        syncControls();
        applyStatusBarIcons();

        requestNotificationPermission();
        runDetection();
        bindRom();
        try {
            refreshCrashLog();
        } catch (Throwable ignored) {
            // 崩溃记录读不到不影响启动
        }
        dmSettings = new DmSettings(this);
        try {
            setupDanmaku();
        } catch (Throwable error) {
            Logbook.log("app", "弹幕界面初始化失败：" + error);
        }
        alignCouple();
        uiReady = true;
        Logbook.log("app", "界面启动");

        if (store.keepAlive()) {
            HeatingService.keepAlive(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (fatalShown) return;
        try {
        if (!ticking) {
            ticking = true;
            ui.removeCallbacks(ticker);
            ui.post(ticker);
        }
        CoupleLink.addListener(coupleListener);
        if (tvCoupleStatus != null) {
            tvCoupleStatus.setText(CoupleLink.status());
        }

        try {
            initDanmakuRuntime();
        } catch (Throwable error) {
            Logbook.log("app", "弹幕运行时初始化失败：" + error);
        }
        updateDmStatus();
        } catch (Throwable error) {
            showFatal(error);
        }
    }

    private void initDanmakuRuntime() {
        DmBus.addListener(dmListener);
        if (cloudSync != null) cloudSync.addListener(cloudListener);
        if (pendingOverlay && Settings.canDrawOverlays(this)) {
            pendingOverlay = false;
            if (switchOverlay != null) switchOverlay.setChecked(true);
        } else if (pendingOverlay && switchOverlay != null) {
            switchOverlay.setChecked(false);
        }
        if (dmSettings != null && dmSettings.overlayEnabled()
                && !DanmakuOverlayService.RUNNING && Settings.canDrawOverlays(this)) {
            DanmakuOverlayService.start(this);
        }
        if (dmSettings != null && dmSettings.udpEnabled()) {
            if (UdpMesh.hasPermission(this)) {
                if (!UdpMesh.active()) {
                    UdpMesh.start(this, dmSettings.port(), dmSettings.room(), dmSettings.peerIp());
                }
            } else {
                dmSettings.setUdpEnabled(false);
                if (switchUdp != null) switchUdp.setChecked(false);
            }
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        cancelAutoAccept(false);
        ticking = false;
        ui.removeCallbacks(ticker);
        CoupleLink.removeListener(coupleListener);
        DmBus.removeListener(dmListener);
        if (cloudSync != null) cloudSync.removeListener(cloudListener);
        try {
            commitDmFields();
        } catch (Throwable error) {
            Logbook.log("app", "保存弹幕设置失败：" + error);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        ticking = false;
        uiReady = false;
        ui.removeCallbacks(ticker);
        ui.removeCallbacksAndMessages(null);
        cancelAutoAccept(true);
        if (channel != null) {
            channel.clearListener(channelListener);
            if (isFinishing()) channel.stopAll();
        }
        if (cloudSync != null) cloudSync.removeListener(cloudListener);
        if (cloudPanel != null) {
            cloudPanel.destroy();
            cloudPanel = null;
        }
        if (worker != null) {
            worker.shutdownNow();
            worker = null;
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQ_NOTIF) {
            if (!granted) {
                toast(getString(R.string.perm_denied));
            }
        } else if (requestCode == REQ_STORAGE) {
            if (granted) {
                LocalArtwork.clearCache();
            } else {
                toast(getString(R.string.log_need_permission));
            }
        } else if (requestCode == REQ_LOCAL_NETWORK) {
            Runnable action = pendingLocalNetworkAction;
            pendingLocalNetworkAction = null;
            if (granted && action != null) {
                action.run();
            } else if (!granted) {
                if (dmSettings != null) dmSettings.setUdpChannel(false);
                renderDmChannels();
                toast(getString(R.string.dm_need_local_permission));
            }
        } else if (requestCode == REQ_CAMERA) {
            if (granted && qrScanPending) {
                qrScanPending = false;
                launchQrCamera();
            } else {
                qrScanPending = false;
                toast(getString(R.string.channel_camera_denied));
            }
        } else if (requestCode == REQ_BT) {
            Runnable action = pendingBluetoothAction;
            pendingBluetoothAction = null;
            if (granted && action != null) {
                action.run();
            } else if (!granted) {
                toast(getString(R.string.bt_need_permission));
                if (switchReceive != null) switchReceive.setChecked(false);
            }
        }
    }

    // --- 绑定 ---

    private void bindViews() {
        heroTemp = findViewById(R.id.heroTemp);
        tvHeroStatus = findViewById(R.id.tvHeroStatus);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        btnMain = findViewById(R.id.btnMain);
        tvPresetHint = findViewById(R.id.tvPresetHint);
        tvTargetValue = findViewById(R.id.tvTargetValue);
        tvAutoOffValue = findViewById(R.id.tvAutoOffValue);
        tvFloorValue = findViewById(R.id.tvFloorValue);
        tvLoad = findViewById(R.id.tvLoad);
        tvThreads = findViewById(R.id.tvThreads);
        tvTemp = findViewById(R.id.tvTemp);
        tvThermal = findViewById(R.id.tvThermal);
        tvElapsed = findViewById(R.id.tvElapsed);
        chipGentle = findViewById(R.id.chipGentle);
        chipBalanced = findViewById(R.id.chipBalanced);
        chipStrong = findViewById(R.id.chipStrong);
        chipExtreme = findViewById(R.id.chipExtreme);
        chipCheck = findViewById(R.id.chipCheck);
        tvCheckResult = findViewById(R.id.tvCheckResult);
        tvCrash = findViewById(R.id.tvCrash);
        chipCopyCrash = findViewById(R.id.chipCopyCrash);
        seekTarget = findViewById(R.id.seekTarget);
        seekAutoOff = findViewById(R.id.seekAutoOff);
        seekFloor = findViewById(R.id.seekFloor);
        switchEco = findViewById(R.id.switchEco);
        switchCouple = findViewById(R.id.switchCouple);
        switchLog = findViewById(R.id.switchLog);
        switchKeepAlive = findViewById(R.id.switchKeepAlive);
        switchReceive = findViewById(R.id.switchReceive);
        tvLogPath = findViewById(R.id.tvLogPath);
        tvCoupleStatus = findViewById(R.id.tvCoupleStatus);
        chipPick = findViewById(R.id.chipPick);
        chipHeatHer = findViewById(R.id.chipHeatHer);
        chipStopHer = findViewById(R.id.chipStopHer);
        scenery = findViewById(R.id.scenery);
        couple = findViewById(R.id.couple);
        sparkTemp = findViewById(R.id.sparkTemp);
        tvSparkCaption = findViewById(R.id.tvSparkCaption);
        tvRom = findViewById(R.id.tvRom);
        tvRomNote = findViewById(R.id.tvRomNote);
        chipAutostart = findViewById(R.id.chipAutostart);
        chipBattery = findViewById(R.id.chipBattery);
        chipAppInfo = findViewById(R.id.chipAppInfo);

        tabHome = findViewById(R.id.tabHome);
        tabFeature = findViewById(R.id.tabFeature);
        tabDanmaku = findViewById(R.id.tabDanmaku);
        tabMine = findViewById(R.id.tabMine);
        pageHome = findViewById(R.id.pageHome);
        pageFeature = findViewById(R.id.pageFeature);
        pageDanmaku = findViewById(R.id.pageDanmaku);
        pageMine = findViewById(R.id.pageMine);
        navBar = findViewById(R.id.navBar);

        switchOverlay = findViewById(R.id.switchOverlay);
        switchUdp = findViewById(R.id.switchUdp);
        switchDmNick = findViewById(R.id.switchDmNick);
        etNick = findViewById(R.id.etNick);
        etRoom = findViewById(R.id.etRoom);
        etPort = findViewById(R.id.etPort);
        etPeerIp = findViewById(R.id.etPeerIp);
        etDmInput = findViewById(R.id.etDmInput);
        tvDmStatus = findViewById(R.id.tvDmStatus);
        tvDmFeed = findViewById(R.id.tvDmFeed);
        tvDmFontValue = findViewById(R.id.tvDmFontValue);
        tvDmSpeedValue = findViewById(R.id.tvDmSpeedValue);
        tvDmAlphaValue = findViewById(R.id.tvDmAlphaValue);
        tvDmLanesValue = findViewById(R.id.tvDmLanesValue);
        chipDmSend = findViewById(R.id.chipDmSend);
        chipDmBt = findViewById(R.id.chipDmBt);
        chipDmChannelCloud = findViewById(R.id.chipDmChannelCloud);
        chipDmChannelUdp = findViewById(R.id.chipDmChannelUdp);
        chipDmChannelBt = findViewById(R.id.chipDmChannelBt);
        tvDmChannelHint = findViewById(R.id.tvDmChannelHint);
        seekDmFont = findViewById(R.id.seekDmFont);
        seekDmSpeed = findViewById(R.id.seekDmSpeed);
        seekDmAlpha = findViewById(R.id.seekDmAlpha);
        seekDmLanes = findViewById(R.id.seekDmLanes);

        tvChannelStatus = findViewById(R.id.tvChannelStatus);
        tvChannelCode = findViewById(R.id.tvChannelCode);
        tvChannelCodeTip = findViewById(R.id.tvChannelCodeTip);
        tvChannelMembers = findViewById(R.id.tvChannelMembers);
        tvPeersEmpty = findViewById(R.id.tvPeersEmpty);
        listChannelPeers = findViewById(R.id.listChannelPeers);
        chipCreateChannel = findViewById(R.id.chipCreateChannel);
        chipJoinChannel = findViewById(R.id.chipJoinChannel);
        chipChannelQr = findViewById(R.id.chipChannelQr);
        chipChannelScan = findViewById(R.id.chipChannelScan);
        chipStopChannel = findViewById(R.id.chipStopChannel);
        switchChannelAuto = findViewById(R.id.switchChannelAuto);
    }

    /** 状态栏避让 + 底部悬浮导航栏的导航栏避让，并把三页导航接上。 */
    private void bindViewport() {
        View root = findViewById(R.id.root);
        if (root == null || navBar == null) {
            Logbook.log("app", "窗口根布局缺失，跳过状态栏适配");
            return;
        }
        final int baseNavMargin = dp(16);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, 0);
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) navBar.getLayoutParams();
            params.bottomMargin = bars.bottom + baseNavMargin;
            navBar.setLayoutParams(params);
            alignCouple();
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        if (tabHome == null || tabFeature == null || tabDanmaku == null || tabMine == null) {
            Logbook.log("app", "底部导航缺失，跳过页面切换绑定");
            return;
        }
        tabHome.setOnClickListener(v -> selectTab(0));
        tabFeature.setOnClickListener(v -> selectTab(1));
        tabDanmaku.setOnClickListener(v -> selectTab(2));
        tabMine.setOnClickListener(v -> selectTab(3));
        selectTab(0);
    }

    private void selectTab(int index) {
        if (pageHome == null || pageFeature == null || pageDanmaku == null || pageMine == null
                || scenery == null || couple == null) {
            return;
        }
        if (index == 3 && cloudPanel == null) {
            cloudPanel = new CloudPanel(this, pageMine, true);
        }
        currentTab = index;
        pageHome.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        pageFeature.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        pageDanmaku.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        pageMine.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
        scenery.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        couple.setVisibility(index == 0 && store.coupleEnabled() ? View.VISIBLE : View.GONE);
        if (index == 0) {
            scenery.setWarm(HeatRuntime.get().running, false);
            lastWarmState = HeatRuntime.get().running;
        }
        tabHome.setSelected(index == 0);
        tabFeature.setSelected(index == 1);
        tabDanmaku.setSelected(index == 2);
        tabMine.setSelected(index == 3);
        if (index == 2 && dmSettings != null && dmSettings.cloudChannel()) {
            ensureCloudStarted();
        }
        if (channel != null) {
            if (index == 2) {
                if (!LocalChannel.hasPermission(this) && !channelPermissionAsked) {
                    channelPermissionAsked = true;
                    withLocalNetwork(() -> channel.startScan());
                } else {
                    channel.startScan();
                }
            } else if (!channel.hosting()) {
                channel.stopScan();
            }
        }
        applyStatusBarIcons();
        if (index == 0) {
            render();
        }
    }

    /** 初始化阶段兜底：把异常显示在屏幕上，避免一打开就直接消失。 */
    private void showFatal(Throwable error) {
        fatalShown = true;
        try {
            Logbook.log("app", "启动初始化失败：" + android.util.Log.getStackTraceString(error));
        } catch (Throwable ignored) {
            // 日志失败不影响兜底界面
        }
        try {
            Logbook.log("app", "启动失败：" + error);
            String detail = android.util.Log.getStackTraceString(error);
            android.widget.ScrollView scroll = new android.widget.ScrollView(this);
            android.widget.LinearLayout box = new android.widget.LinearLayout(this);
            box.setOrientation(android.widget.LinearLayout.VERTICAL);
            int pad = dp(18);
            box.setPadding(pad, pad, pad, pad);
            android.widget.TextView title = new android.widget.TextView(this);
            title.setText("启动时崩了，把下面这段发给我");
            title.setTextSize(18);
            title.setTextColor(0xFFF07A5A);
            box.addView(title);
            android.widget.TextView body = new android.widget.TextView(this);
            body.setText(detail);
            body.setTextSize(11);
            body.setTextIsSelectable(true);
            body.setPadding(0, dp(12), 0, dp(12));
            box.addView(body);
            android.widget.TextView copy = new android.widget.TextView(this);
            copy.setText("复制崩溃信息");
            copy.setTextSize(14);
            copy.setPadding(dp(14), dp(12), dp(14), dp(12));
            copy.setBackgroundColor(0xFFF07A5A);
            copy.setTextColor(0xFFFFFFFF);
            copy.setOnClickListener(v -> {
                android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("fatal", detail));
                toast("已复制");
            });
            box.addView(copy);
            scroll.addView(box);
            setContentView(scroll);
        } catch (Throwable ignored) {
            // 兜底界面也不能再抛
        }
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }

    private void bindActions() {
        btnMain.setOnClickListener(v -> {
            if (HeatRuntime.isActive()) {
                HeatingService.stop(this);
                Logbook.log("app", "手动停止加热");
            } else {
                HeatingService.start(this);
                Logbook.log("app", "手动开始加热");
            }
            ui.postDelayed(this::render, 500);
        });

        chipGentle.setOnClickListener(v -> selectPreset(Preset.GENTLE));
        chipBalanced.setOnClickListener(v -> selectPreset(Preset.BALANCED));
        chipStrong.setOnClickListener(v -> selectPreset(Preset.STRONG));
        chipExtreme.setOnClickListener(v -> selectPreset(Preset.EXTREME));

        seekTarget.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float target = SettingsStore.MIN_TARGET_C + progress;
                tvTargetValue.setText(Math.round(target) + " °C");
                if (fromUser) store.setTargetC(target);
            }
        });

        seekAutoOff.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int minutes = SettingsStore.MIN_AUTO_OFF_MIN + progress * 5;
                tvAutoOffValue.setText(minutes + " 分钟");
                if (fromUser) store.setAutoOffMinutes(minutes);
            }
        });

        seekFloor.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int floor = SettingsStore.MIN_FLOOR + progress * 5;
                tvFloorValue.setText(floor + " %");
                if (fromUser) store.setBatteryFloor(floor);
            }
        });

        switchEco.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked != store.ecoMode()) {
                store.setEcoMode(isChecked);
            }
        });

        switchKeepAlive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked == store.keepAlive()) return;
            store.setKeepAlive(isChecked);
            if (isChecked) {
                HeatingService.keepAlive(this);
                toast("已开启通知保后台，状态栏会常驻「爱情加热中」");
            } else {
                HeatingService.release(this);
            }
            Logbook.log("app", "通知保后台：" + isChecked);
        });

        switchLog.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked == store.logEnabled()) return;
            if (isChecked && !ensureStorageAccess()) {
                toast(getString(R.string.log_need_permission));
                switchLog.setChecked(false);
                return;
            }
            store.setLogEnabled(isChecked);
            Logbook.setEnabled(isChecked);
            updateLogHint();
            toast(isChecked ? getString(R.string.log_on) : getString(R.string.log_off));
        });

        switchReceive.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked == store.coupleReceive()) return;
            if (isChecked) {
                ensureBluetooth(() -> {
                    store.setCoupleReceive(true);
                    CoupleService.startServer(this);
                    Logbook.log("couple", "开启被远程加热");
                });
            } else {
                store.setCoupleReceive(false);
                CoupleService.release(this);
            }
        });

        chipPick.setOnClickListener(v -> ensureBluetooth(this::showPeerPicker));
        chipHeatHer.setOnClickListener(v -> heatHer(true));
        chipStopHer.setOnClickListener(v -> heatHer(false));

        chipCheck.setOnClickListener(v -> runDetection());
        chipCopyCrash.setOnClickListener(v -> {
            if (crashText.isEmpty()) {
                toast(getString(R.string.crash_empty));
                return;
            }
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("crash", crashText));
            toast(getString(R.string.crash_copied));
        });
        switchCouple.setOnCheckedChangeListener((buttonView, isChecked) -> {
            store.setCoupleEnabled(isChecked);
            couple.setVisibility(isChecked && currentTab == 0 ? View.VISIBLE : View.GONE);
        });
        chipAutostart.setOnClickListener(v -> openRomIntent(rom == null ? null : rom.autostart(),
                "这个 UI 没有公开的自启动入口，请到系统设置里找「自启动 / 后台管理」。"));
        chipBattery.setOnClickListener(v -> openRomIntent(rom == null ? null : rom.battery(),
                "无法打开电池优化页，请到系统设置里手动找。"));
        chipAppInfo.setOnClickListener(v -> openRomIntent(rom == null ? null : rom.appDetails(),
                "无法打开应用设置页。"));

        try {
            bindDanmakuActions();
        } catch (Throwable error) {
            Logbook.log("app", "弹幕控件绑定失败：" + error);
        }
    }

    // --- 弹幕 ---

    private void setupDanmaku() {
        cloudSync = CloudSync.get(this);
        dmListener = new DmBus.Listener() {
            @Override
            public void onDanmaku(DmMessage message) {
                if (tvDmFeed == null) return;
                String line = (message.nick.isEmpty() ? "" : message.nick + "：") + message.text;
                dmFeedText = dmFeedText.isEmpty() ? line : line + "\n" + dmFeedText;
                String[] lines = dmFeedText.split("\n");
                if (lines.length > 3) {
                    dmFeedText = lines[0] + "\n" + lines[1] + "\n" + lines[2];
                }
                tvDmFeed.setText(dmFeedText);
            }

            @Override
            public void onStatus(String status) {
                if (tvDmStatus != null) tvDmStatus.setText(status);
            }
        };

        etNick.setText(dmSettings.nick());
        etRoom.setText(dmSettings.room());
        etPort.setText(String.valueOf(dmSettings.port()));
        etPeerIp.setText(dmSettings.peerIp());
        seekDmFont.setProgress(dmSettings.fontSizeSp() - 10);
        seekDmSpeed.setProgress(Math.round((dmSettings.speed() - 0.5f) * 10f));
        seekDmAlpha.setProgress(Math.round((dmSettings.alpha() - 0.35f) * 100f));
        seekDmLanes.setProgress(dmSettings.lanes() - 2);
        switchDmNick.setChecked(dmSettings.showNick());
        switchOverlay.setChecked(dmSettings.overlayEnabled() && Settings.canDrawOverlays(this));
        switchUdp.setChecked(dmSettings.udpEnabled());
        setupDmChannels();
        renderDmValues();
        updateDmStatus();
    }

    private void onCloudStateChanged() {
        if (uiReady) ui.post(this::updateDmStatus);
    }

    private void setupDmChannels() {
        if (chipDmChannelCloud == null || chipDmChannelUdp == null || chipDmChannelBt == null) {
            return;
        }
        chipDmChannelCloud.setOnClickListener(v -> toggleCloudChannel());
        chipDmChannelUdp.setOnClickListener(v -> toggleUdpChannel());
        chipDmChannelBt.setOnClickListener(v -> toggleBtChannel());
        renderDmChannels();
    }

    private void renderDmChannels() {
        if (dmSettings == null || chipDmChannelCloud == null) return;
        boolean hasToken = CloudPrefs.get(this).hasToken();
        if (!hasToken && dmSettings.cloudChannel()) {
            dmSettings.setCloudChannel(false);
        }
        applyChannelState(chipDmChannelCloud, dmSettings.cloudChannel());
        applyChannelState(chipDmChannelUdp, dmSettings.udpChannel());
        applyChannelState(chipDmChannelBt, dmSettings.btChannel());
        if (tvDmChannelHint != null) {
            tvDmChannelHint.setText(hasToken
                    ? getString(R.string.dm_channels_hint)
                    : "云端未登录；局域网和蓝牙仍可发送。");
        }
    }

    private void applyChannelState(TextView chip, boolean selected) {
        chip.setSelected(selected);
        chip.setTextColor(ContextCompat.getColor(this,
                selected ? R.color.on_accent : R.color.text_primary));
    }

    private void toggleCloudChannel() {
        boolean next = !dmSettings.cloudChannel();
        if (next && !CloudPrefs.get(this).hasToken()) {
            toast(getString(R.string.dm_cloud_need_login));
            return;
        }
        dmSettings.setCloudChannel(next);
        if (next) ensureCloudStarted();
        renderDmChannels();
        updateDmStatus();
    }

    private void toggleUdpChannel() {
        boolean next = !dmSettings.udpChannel();
        dmSettings.setUdpChannel(next);
        if (next && !UdpMesh.active()) {
            if (UdpMesh.hasPermission(this)) {
                pendingLocalNetworkAction = null;
                startLanDanmaku();
            } else {
                pendingLocalNetworkAction = this::startLanDanmaku;
                toast(getString(R.string.dm_need_local_permission));
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_LOCAL_NETWORK}, REQ_LOCAL_NETWORK);
            }
        }
        renderDmChannels();
        updateDmStatus();
    }

    private void toggleBtChannel() {
        boolean next = !dmSettings.btChannel();
        dmSettings.setBtChannel(next);
        if (next && !CoupleLink.isConnected()) {
            toast("蓝牙还没连上，先到情侣模式配对");
        }
        renderDmChannels();
        updateDmStatus();
    }

    private void ensureCloudStarted() {
        if (dmSettings != null && dmSettings.cloudChannel()
                && CloudPrefs.get(this).hasToken()) {
            CloudService.start(this);
        }
    }

    private void renderDmValues() {
        tvDmFontValue.setText(dmSettings.fontSizeSp() + " sp");
        tvDmSpeedValue.setText(String.format(java.util.Locale.US, "%.1fx", dmSettings.speed()));
        tvDmAlphaValue.setText(Math.round(dmSettings.alpha() * 100) + " %");
        tvDmLanesValue.setText(dmSettings.lanes() + " 条");
    }

    private void bindDanmakuActions() {
        seekDmFont.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) dmSettings.setFontSizeSp(progress + 10);
                renderDmValues();
            }
        });
        seekDmSpeed.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) dmSettings.setSpeed(0.5f + progress * 0.1f);
                renderDmValues();
            }
        });
        seekDmAlpha.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) dmSettings.setAlpha(0.35f + progress * 0.01f);
                renderDmValues();
            }
        });
        seekDmLanes.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) dmSettings.setLanes(progress + 2);
                renderDmValues();
            }
        });

        etNick.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                dmSettings.setNick(etNick.getText().toString());
                etNick.setText(dmSettings.nick());
            }
        });
        etRoom.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                dmSettings.setRoom(etRoom.getText().toString());
                etRoom.setText(dmSettings.room());
                if (UdpMesh.active()) {
                    UdpMesh.start(this, dmSettings.port(), dmSettings.room(), dmSettings.peerIp());
                }
            }
        });
        etPort.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) return;
            try {
                dmSettings.setPort(Integer.parseInt(etPort.getText().toString().trim()));
            } catch (NumberFormatException ignored) {
                // 输入不是数字就回到当前端口
            }
            etPort.setText(String.valueOf(dmSettings.port()));
        etPeerIp.setText(dmSettings.peerIp());
            if (UdpMesh.active()) {
                UdpMesh.start(this, dmSettings.port(), dmSettings.room(), dmSettings.peerIp());
            }
        });

        switchDmNick.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked != dmSettings.showNick()) dmSettings.setShowNick(isChecked);
        });

        switchOverlay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked == dmSettings.overlayEnabled()) return;
            if (isChecked) {
                if (!Settings.canDrawOverlays(this)) {
                    pendingOverlay = true;
                    toast(getString(R.string.dm_permission));
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                    } catch (RuntimeException ignored) {
                        // 个别 ROM 没有这个入口
                    }
                    switchOverlay.setChecked(false);
                    return;
                }
                commitDmFields();
                dmSettings.setOverlayEnabled(true);
                DanmakuOverlayService.start(this);
                toast("悬浮弹幕已打开");
            } else {
                dmSettings.setOverlayEnabled(false);
                DanmakuOverlayService.stop(this);
            }
            updateDmStatus();
        });

        switchUdp.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked == dmSettings.udpEnabled()) return;
            commitDmFields();
            if (!isChecked) {
                dmSettings.setUdpEnabled(false);
                UdpMesh.stop();
                updateDmStatus();
                return;
            }
            // 先把开关压回去，拿到权限后再真正打开，避免界面显示已开却发不出去
            switchUdp.setChecked(false);
            pendingLocalNetworkAction = this::startLanDanmaku;
            if (UdpMesh.hasPermission(this)) {
                pendingLocalNetworkAction = null;
                startLanDanmaku();
            } else {
                toast(getString(R.string.dm_need_local_permission));
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_LOCAL_NETWORK}, REQ_LOCAL_NETWORK);
            }
        });

        chipDmSend.setOnClickListener(v -> {
            String text = etDmInput.getText().toString().trim();
            if (text.isEmpty()) {
                toast(getString(R.string.dm_empty));
                return;
            }
            commitDmFields();
            if (dmSettings.cloudChannel()) ensureCloudStarted();
            int channels = DanmakuSender.send(this, text);
            if (channels == 0) {
                toast(getString(R.string.dm_sent_local));
            } else {
                toast(getString(R.string.dm_sent_channels, DanmakuSender.channelNames(channels)));
            }
            etDmInput.setText("");
            updateDmStatus();
        });
        chipDmBt.setOnClickListener(v -> selectTab(1));
        bindChannelActions();
    }

    // ---- 本地频道 ----

    private void initChannel() {
        channel = LocalChannel.get(this);
        channelListener = new LocalChannel.Listener() {
            @Override
            public void onPeers(List<LocalChannel.Peer> peers) {
                renderChannelPeers(peers);
            }

            @Override
            public void onJoinRequest(LocalChannel.JoinRequest request) {
                handleChannelJoinRequest(request);
            }

            @Override
            public void onMembers(List<String> members) {
                if (tvChannelMembers == null) return;
                if (members.isEmpty()) {
                    tvChannelMembers.setText(R.string.channel_members_empty);
                } else {
                    tvChannelMembers.setText(android.text.TextUtils.join("、", members));
                }
            }

            @Override
            public void onStatus(String text) {
                if (tvChannelStatus != null) tvChannelStatus.setText(text);
                refreshChannelUi();
            }

            @Override
            public void onJoined(String channelName) {
                if (tvChannelStatus != null) tvChannelStatus.setText("已加入 · " + channelName);
                refreshChannelUi();
            }
        };
        channel.setListener(channelListener);
        if (switchChannelAuto != null) {
            switchChannelAuto.setChecked(store.channelAutoAccept());
            switchChannelAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
                store.setChannelAutoAccept(isChecked);
                if (isChecked) {
                    toast("自动同意只在屏幕亮着、停留在连接页时生效");
                }
            });
        }
        refreshChannelUi();
        if (tvChannelStatus != null) tvChannelStatus.setText(channel.statusText());
    }

    private void bindChannelActions() {
        if (chipCreateChannel != null) {
            chipCreateChannel.setOnClickListener(v -> showCreateChannelDialog());
        }
        if (chipJoinChannel != null) {
            chipJoinChannel.setOnClickListener(v -> showJoinCodeDialog());
        }
        if (chipChannelQr != null) {
            chipChannelQr.setOnClickListener(v -> showChannelQr());
        }
        if (chipChannelScan != null) {
            chipChannelScan.setOnClickListener(v -> scanChannelQr());
        }
        if (chipStopChannel != null) {
            chipStopChannel.setOnClickListener(v -> {
                if (channel == null) return;
                channel.stopAll();
                refreshChannelUi();
                toast("频道已停止");
            });
        }
    }

    private void refreshChannelUi() {
        if (channel == null) return;
        boolean host = channel.hosting();
        if (chipStopChannel != null) chipStopChannel.setVisibility(host ? View.VISIBLE : View.GONE);
        if (tvChannelCode != null) {
            tvChannelCode.setVisibility(host ? View.VISIBLE : View.GONE);
            if (host) tvChannelCode.setText(getString(R.string.channel_code_prefix) + channel.channelCode());
        }
        if (tvChannelCodeTip != null) {
            tvChannelCodeTip.setVisibility(host ? View.VISIBLE : View.GONE);
        }
        if (chipCreateChannel != null) {
            chipCreateChannel.setText(host ? "重新创建" : getString(R.string.channel_create));
        }
    }

    private boolean requireChannel() {
        if (channel != null) return true;
        toast("本地频道暂时不可用，请重启应用后再试");
        return false;
    }

    private void showCreateChannelDialog() {
        if (!requireChannel()) return;
        EditText input = new EditText(this);
        input.setHint("我们的冬天");
        input.setText("我们的冬天");
        input.setSingleLine(true);
        int pad = dp(20);
        input.setPadding(pad, dp(8), pad, dp(8));
        new AlertDialog.Builder(this)
                .setTitle("创建频道")
                .setMessage("频道建在本机，不需要外网；别人加入时都会先问你。")
                .setView(input)
                .setPositiveButton("创建", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = "我们的冬天";
                    String finalName = name;
                    withLocalNetwork(() -> {
                        if (channel == null) return;
                        channel.host(MainActivity.this, finalName,
                                LocalChannel.randomCode(), channelNick());
                        refreshChannelUi();
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showJoinCodeDialog() {
        if (!requireChannel()) return;
        EditText input = new EditText(this);
        input.setHint(R.string.channel_join_hint);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(4)
        });
        input.setSingleLine(true);
        int pad = dp(20);
        input.setPadding(pad, dp(8), pad, dp(8));
        new AlertDialog.Builder(this)
                .setTitle(R.string.channel_join_title)
                .setView(input)
                .setPositiveButton(R.string.channel_join_ok, (dialog, which) -> {
                    String code = input.getText().toString().trim();
                    if (!code.matches("\\d{4}")) {
                        toast("口令是 4 位数字");
                        return;
                    }
                    withLocalNetwork(() -> {
                        if (channel != null) channel.joinByCode(code);
                    });
                })
                .setNeutralButton(R.string.channel_scan, (dialog, which) -> scanChannelQr())
                .setNegativeButton("取消", null)
                .show();
    }

    private String channelNick() {
        String nick = etNick != null ? etNick.getText().toString().trim() : "";
        if (nick.isEmpty()) return store.channelNick();
        store.setChannelNick(nick);
        return nick;
    }

    private void joinPeer(LocalChannel.Peer peer) {
        if (!requireChannel()) return;
        withLocalNetwork(() -> {
            if (channel != null) channel.join(peer, "", "nearby");
        });
    }

    private void withLocalNetwork(Runnable action) {
        if (LocalChannel.hasPermission(this)) {
            action.run();
            return;
        }
        pendingLocalNetworkAction = action;
        toast(getString(R.string.dm_need_local_permission));
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_LOCAL_NETWORK}, REQ_LOCAL_NETWORK);
    }

    private boolean autoAcceptEffective() {
        if (!store.channelAutoAccept() || !resumed || currentTab != 2) return false;
        if (channel == null || !channel.hosting()) return false;
        try {
            PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return power != null && power.isInteractive();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void handleChannelJoinRequest(LocalChannel.JoinRequest request) {
        if (isFinishing()) {
            channel.approve(request.id, false);
            return;
        }
        if (channel == null) return;
        if (autoAcceptEffective()) {
            showAutoAcceptDialog(request);
            return;
        }
        String via = "nearby".equals(request.via) ? "附近的人" : "频道口令";
        new AlertDialog.Builder(this)
                .setTitle("加入请求")
                .setMessage(request.name + " 想加入「" + channel.channelName() + "」\n方式：" + via)
                .setPositiveButton("同意", (dialog, which) -> channel.approve(request.id, true))
                .setNegativeButton("拒绝", (dialog, which) -> channel.approve(request.id, false))
                .setOnCancelListener(dialog -> channel.approve(request.id, false))
                .show();
    }

    private void showAutoAcceptDialog(LocalChannel.JoinRequest request) {
        cancelAutoAccept(true);
        final boolean[] handled = {false};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("加入请求")
                .setMessage(request.name + " 想加入「" + channel.channelName() + "」\n5 秒后自动同意")
                .setNegativeButton("取消", (d, which) -> {
                    handled[0] = true;
                    channel.approve(request.id, false);
                })
                .create();
        dialog.setOnDismissListener(d -> {
            if (!handled[0]) {
                handled[0] = true;
                if (autoAcceptTask != null) ui.removeCallbacks(autoAcceptTask);
                channel.approve(request.id, false);
            }
        });
        autoAcceptDialog = dialog;
        autoAcceptTask = () -> {
            if (handled[0]) return;
            handled[0] = true;
            if (dialog.isShowing()) dialog.dismiss();
            channel.approve(request.id, true);
        };
        dialog.show();
        ui.postDelayed(autoAcceptTask, 5000);
    }

    private void cancelAutoAccept(boolean reject) {
        if (autoAcceptTask != null) {
            ui.removeCallbacks(autoAcceptTask);
            autoAcceptTask = null;
        }
        if (autoAcceptDialog != null) {
            autoAcceptDialog.dismiss();
            autoAcceptDialog = null;
        }
    }

    private void renderChannelPeers(List<LocalChannel.Peer> peers) {
        if (listChannelPeers == null) return;
        listChannelPeers.removeAllViews();
        if (tvPeersEmpty != null) {
            tvPeersEmpty.setVisibility(peers.isEmpty() ? View.VISIBLE : View.GONE);
        }
        for (LocalChannel.Peer peer : peers) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));

            TextView info = new TextView(this);
            LinearLayout.LayoutParams infoParams =
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            info.setLayoutParams(infoParams);
            info.setText(peer.hostName + " · " + peer.channelName
                    + "\n" + peer.memberCount + "/" + peer.maxMembers + " 人 · 同一个 Wi-Fi");
            info.setTextSize(13);
            info.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            row.addView(info);

            TextView join = new TextView(this);
            join.setText(R.string.channel_peer_join);
            join.setTextSize(13);
            join.setGravity(android.view.Gravity.CENTER);
            join.setPadding(dp(14), dp(8), dp(14), dp(8));
            join.setBackgroundResource(R.drawable.bg_chip);
            join.setTextColor(ContextCompat.getColor(this, R.color.accent));
            join.setOnClickListener(v -> joinPeer(peer));
            row.addView(join);
            listChannelPeers.addView(row);
        }
    }

    private void showChannelQr() {
        if (channel == null || !channel.hosting() || channel.channelCode().isEmpty()) {
            toast("先创建频道，再展示二维码");
            return;
        }
        String content = "HWJOIN:" + channel.channelCode();
        Bitmap bitmap;
        try {
            bitmap = makeQr(content);
        } catch (Throwable error) {
            toast("二维码生成失败");
            return;
        }
        ImageView image = new ImageView(this);
        image.setImageBitmap(bitmap);
        image.setAdjustViewBounds(true);
        int pad = dp(20);
        image.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle(R.string.channel_qr)
                .setMessage(R.string.channel_code_tip)
                .setView(image)
                .setPositiveButton("关闭", null)
                .show();
    }

    private Bitmap makeQr(String content) throws Exception {
        int size = 480;
        com.google.zxing.common.BitMatrix matrix = new QRCodeWriter().encode(
                content, BarcodeFormat.QR_CODE, size, size);
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels[y * size + x] = matrix.get(x, y) ? 0xFF15110F : 0xFFF7F2EC;
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
    }

    private void scanChannelQr() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            qrScanPending = true;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        launchQrCamera();
    }

    private void launchQrCamera() {
        try {
            File dir = new File(getCacheDir(), "qr");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("缓存目录不可用");
            File file = new File(dir, "scan.jpg");
            qrOutputUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, qrOutputUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQ_QR_CAMERA);
        } catch (Exception e) {
            toast("打不开相机：" + e.getMessage());
        }
    }

    private void handleQrResult(Uri uri) {
        Bitmap bitmap = null;
        try {
            InputStream stream = getContentResolver().openInputStream(uri);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            bitmap = BitmapFactory.decodeStream(stream, null, options);
            if (stream != null) stream.close();
        } catch (Exception e) {
            // 下面统一提示
        }
        if (bitmap == null) {
            toast(R.string.channel_qr_bad);
            return;
        }
        String text = null;
        try {
            text = decodeQr(bitmap);
        } catch (Exception ignored) {
            // 下面统一提示
        } finally {
            bitmap.recycle();
        }
        if (text == null) {
            toast(R.string.channel_qr_bad);
            return;
        }
        String code = text.startsWith("HWJOIN:") ? text.substring(7) : text.trim();
        if (!code.matches("\\d{4}")) {
            toast(R.string.channel_qr_bad);
            return;
        }
        String finalCode = code;
        if (!requireChannel()) return;
        withLocalNetwork(() -> {
            if (channel != null) channel.joinByCode(finalCode);
        });
    }

    private String decodeQr(Bitmap bitmap) throws Exception {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        RGBLuminanceSource source = new RGBLuminanceSource(width, height, pixels);
        BinaryBitmap binary = new BinaryBitmap(new HybridBinarizer(source));
        Result result = new MultiFormatReader().decode(binary);
        return result.getText();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_QR_CAMERA && resultCode == RESULT_OK) {
            Uri uri = data != null && data.getData() != null ? data.getData() : qrOutputUri;
            if (uri != null) handleQrResult(uri);
        }
    }

    /** 输入框还在焦点里时直接点发送，也要先把昵称/房间/端口/对方 IP 落盘。 */
    private boolean commitDmFields() {
        if (dmSettings == null || etNick == null) return false;
        boolean changed = false;
        String nick = etNick.getText().toString().trim();
        if (!nick.isEmpty() && !nick.equals(dmSettings.nick())) {
            dmSettings.setNick(nick);
            changed = true;
        }
        String room = etRoom.getText().toString().trim();
        if (!room.isEmpty() && !room.equals(dmSettings.room())) {
            dmSettings.setRoom(room);
            changed = true;
        }
        try {
            int port = Integer.parseInt(etPort.getText().toString().trim());
            if (port >= 1024 && port <= 65535 && port != dmSettings.port()) {
                dmSettings.setPort(port);
                changed = true;
            }
        } catch (NumberFormatException ignored) {
            etPort.setText(String.valueOf(dmSettings.port()));
        }
        String peer = etPeerIp.getText().toString().trim();
        if (!peer.equals(dmSettings.peerIp())) {
            dmSettings.setPeerIp(peer);
            changed = true;
        }
        if (changed && UdpMesh.active()) {
            UdpMesh.start(this, dmSettings.port(), dmSettings.room(), dmSettings.peerIp());
        }
        return changed;
    }

    private void startLanDanmaku() {
        dmSettings.setUdpEnabled(true);
        UdpMesh.start(this, dmSettings.port(), dmSettings.room(), dmSettings.peerIp());
        if (!UdpMesh.active()) {
            dmSettings.setUdpEnabled(false);
            dmSettings.setUdpChannel(false);
            switchUdp.setChecked(false);
            renderDmChannels();
            toast("局域网没起来：换个 Wi-Fi 或稍后再试");
        } else {
            switchUdp.setChecked(true);
            toast("局域网发送已打开");
        }
        updateDmStatus();
    }

    private void refreshCrashLog() {
        if (tvCrash == null) return;
        String log = HeatApp.lastCrash(this);
        if (log == null || log.trim().isEmpty()) {
            crashText = "";
            tvCrash.setText(R.string.crash_empty);
            return;
        }
        crashText = log.length() > 4000 ? log.substring(log.length() - 4000) : log;
        tvCrash.setText(crashText);
    }

    private void updateDmStatus() {
        if (tvDmStatus == null || dmSettings == null) return;
        String udp;
        if (UdpMesh.active()) {
            udp = "局域网：已连接";
        } else if (!UdpMesh.hasPermission(this)) {
            udp = "局域网：需要「附近连接」权限";
        } else {
            udp = "局域网：未连接";
        }
        String cloud;
        if (!CloudPrefs.get(this).hasToken()) {
            cloud = "云端：未登录";
        } else if (cloudSync == null) {
            cloud = "云端：未初始化";
        } else {
            cloud = "云端：" + cloudSync.snapshot().connectionText;
        }
        String overlay = DanmakuOverlayService.RUNNING ? "悬浮弹幕运行中" : "悬浮弹幕未开启";
        tvDmStatus.setText(udp + "\n蓝牙：" + CoupleLink.status() + "\n" + cloud + "\n" + overlay);
    }

    // --- 蓝牙情侣 ---

    private void ensureBluetooth(Runnable action) {
        if (CoupleLink.hasPermission(this)) {
            action.run();
            return;
        }
        pendingBluetoothAction = action;
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQ_BT);
    }

    private void showPeerPicker() {
        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) {
            toast(getString(R.string.bt_no_adapter));
            return;
        }
        if (!adapter.isEnabled()) {
            toast(getString(R.string.bt_off));
            return;
        }

        final List<BluetoothDevice> devices = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            for (BluetoothDevice device : bonded) {
                devices.add(device);
                String name = device.getName();
                labels.add((name == null || name.isEmpty() ? "未命名设备" : name)
                        + "\n" + device.getAddress());
            }
        } catch (SecurityException e) {
            toast(getString(R.string.bt_need_permission));
            return;
        }

        if (devices.isEmpty()) {
            toast(getString(R.string.bt_no_bonded));
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.bt_pick_title)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    BluetoothDevice device = devices.get(which);
                    String name = labels.get(which).split("\n")[0];
                    store.setPeer(device.getAddress(), name);
                    updateCoupleHint();
                    CoupleService.connect(this, device.getAddress(), name, null);
                    Logbook.log("couple", "选择设备 " + name + " " + device.getAddress());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void heatHer(boolean start) {
        String address = store.peerAddress();
        if (address.isEmpty()) {
            toast(getString(R.string.bt_need_peer));
            return;
        }
        ensureBluetooth(() -> {
            String command = start ? CoupleLink.CMD_HEAT_ON : CoupleLink.CMD_HEAT_OFF;
            if (CoupleLink.isConnected()) {
                CoupleService.send(this, command);
            } else {
                CoupleService.connect(this, address, store.peerName(), command);
            }
            toast(start ? R.string.bt_sent_start : R.string.bt_sent_stop);
            Logbook.log("couple", (start ? "请求她开始加热" : "请求她停止加热"));
        });
    }

    private void updateCoupleHint() {
        if (tvCoupleStatus == null) return;
        String name = store.peerName();
        if (name == null || name.isEmpty()) {
            tvCoupleStatus.setText(CoupleLink.status());
        } else if (!CoupleLink.isConnected()) {
            tvCoupleStatus.setText("她的手机：" + name + " · " + CoupleLink.status());
        }
    }

    // --- 渲染 ---

    private void selectPreset(Preset preset) {
        store.setPreset(preset);
        store.setTargetC(preset.defaultTargetC);
        syncControls();
        render();
    }

    private void syncControls() {
        seekTarget.setProgress((int) (store.targetC() - SettingsStore.MIN_TARGET_C));
        tvTargetValue.setText(Math.round(store.targetC()) + " °C");

        seekAutoOff.setProgress((store.autoOffMinutes() - SettingsStore.MIN_AUTO_OFF_MIN) / 5);
        tvAutoOffValue.setText(store.autoOffMinutes() + " 分钟");

        seekFloor.setProgress((store.batteryFloor() - SettingsStore.MIN_FLOOR) / 5);
        tvFloorValue.setText(store.batteryFloor() + " %");

        switchEco.setChecked(store.ecoMode());
        switchCouple.setChecked(store.coupleEnabled());
        switchLog.setChecked(store.logEnabled());
        switchKeepAlive.setChecked(store.keepAlive());
        switchReceive.setChecked(store.coupleReceive());
        updateLogHint();
        updateCoupleHint();

        Preset preset = store.preset();
        chipGentle.setSelected(preset == Preset.GENTLE);
        chipBalanced.setSelected(preset == Preset.BALANCED);
        chipStrong.setSelected(preset == Preset.STRONG);
        chipExtreme.setSelected(preset == Preset.EXTREME);
        tvPresetHint.setText(preset.label + "：" + preset.summary);
    }

    private void updateLogHint() {
        if (tvLogPath == null) return;
        tvLogPath.setText(store.logEnabled()
                ? "写入中：" + Logbook.path()
                : "关闭时不写任何文件 · 开关打开后写入 " + Logbook.path());
    }

    private void render() {
        if (!uiReady || heroTemp == null || btnMain == null || tvHeroStatus == null
                || tvSubtitle == null || tvLoad == null || tvThreads == null || tvTemp == null
                || tvThermal == null || tvElapsed == null) {
            return;
        }
        HeatStatus status = HeatRuntime.get();
        if (!status.running && ++idleTick >= 2) {
            idleTick = 0;
            idleSample = ThermalMonitor.read(this);
        }

        float temp = status.batteryTempC > 0f ? status.batteryTempC : idleSample.batteryTempC;
        long elapsed = status.elapsedMs(System.currentTimeMillis());

        String heroStatus;
        if (status.running) {
            heroStatus = status.preset.label + " · " + Math.round(status.duty * 100) + "%";
        } else if (!status.note.isEmpty()) {
            heroStatus = status.note;
        } else {
            heroStatus = "待机";
        }
        if (currentTab == 0 && scenery != null && couple != null) {
            heroTemp.setTemperature(temp, status.running);
            tvHeroStatus.setText(heroStatus);
            if (lastWarmState != status.running) {
                lastWarmState = status.running;
                scenery.setWarm(status.running, true);
            }
            scenery.setGlow(status.running ? 0.55f + status.duty * 0.45f : 0.3f);
            couple.setWarm(status.running, status.running ? status.duty : 0f);
        }

        btnMain.setText(status.running ? R.string.stop : R.string.start);
        btnMain.setTextColor(ContextCompat.getColor(this, R.color.on_accent));

        if (status.running) {
            tvSubtitle.setText(String.format(Locale.CHINA, "火山模式 · 目标 %d°C · %d 线程 · 已运行 %s",
                    Math.round(store.targetC()), status.threadCount, formatDuration(elapsed)));
        } else {
            tvSubtitle.setText("雪山模式 · 静静待机，点下面开始发热");
        }

        if (status.running) {
            updateMeasuredLoad();
            tvLoad.setText(measuredPct < 0f ? "测量中"
                    : String.format(Locale.CHINA, "%.1f %%", measuredPct));
        } else {
            prevCpuMs = -1;
            measuredPct = -1f;
            tvLoad.setText("—");
        }
        tvThreads.setText(status.running ? status.threadCount + " 个" : "—");
        tvTemp.setText(temp > 0f ? String.format(Locale.CHINA, "%.1f °C", temp) : "读取中");

        if (status.running) {
            if (status.startedAt != lastStartedAt) {
                lastStartedAt = status.startedAt;
                runStartTemp = temp > 0f ? temp : -1f;
                sparkTemp.reset();
                lastSparkAt = 0L;
            }
            long wallNow = SystemClock.elapsedRealtime();
            if (wallNow - lastSparkAt >= 1000L && temp > 0f) {
                lastSparkAt = wallNow;
                sparkTemp.push(temp);
            }
            if (runStartTemp > 0f && temp > 0f) {
                tvSparkCaption.setText(String.format(Locale.CHINA, "过去 60 秒 · 较开始 %+.1f°C", temp - runStartTemp));
            }
        } else {
            lastStartedAt = 0L;
            runStartTemp = -1f;
        }

        tvThermal.setText(thermalName(status.running ? status.thermalStatus : idleSample.thermalStatus));
        tvElapsed.setText(status.running ? formatDuration(elapsed) : "—");
    }

    /** 用进程累计 CPU 时间算真实占用，避免「设定 70% 实际几乎没跑」这种假象。 */
    private void updateMeasuredLoad() {
        long now = SystemClock.elapsedRealtime();
        long cpuMs = Process.getElapsedCpuTime();
        if (prevCpuMs >= 0 && now > prevCpuAt) {
            long deltaCpu = Math.max(0L, cpuMs - prevCpuMs);
            long deltaWall = Math.max(1L, now - prevCpuAt);
            int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
            measuredPct = deltaCpu * 100f / deltaWall / cores;
        }
        prevCpuMs = cpuMs;
        prevCpuAt = now;
    }

    private String thermalName(int status) {
        switch (status) {
            case HeatSample.THERMAL_LIGHT:
                return "轻微";
            case HeatSample.THERMAL_MODERATE:
                return "中等 · 已降档";
            case HeatSample.THERMAL_SEVERE:
                return "严重 · 自动停止";
            case HeatSample.THERMAL_CRITICAL:
                return "危急 · 自动停止";
            default:
                return "正常";
        }
    }

    private String formatDuration(long ms) {
        long totalSec = Math.max(0L, ms / 1000L);
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return String.format(Locale.CHINA, "%02d:%02d", min, sec);
    }

    // --- 系统适配 ---

    private void bindRom() {
        rom = RomCompat.detect(this);
        if (tvRom != null) {
            tvRom.setText("当前系统：" + rom.name);
            tvRomNote.setText(rom.note());
        }
    }

    private void openRomIntent(Intent intent, String fallbackMessage) {
        if (intent == null) {
            toast(fallbackMessage);
            return;
        }
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (RuntimeException e) {
            toast(fallbackMessage);
        }
    }

    /** 让两个人正好坐在导航栏的顶边上。 */
    private void alignCouple() {
        View root = findViewById(R.id.root);
        navBar.post(() -> {
            if (couple == null || navBar.getHeight() == 0) return;
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) couple.getLayoutParams();
            int navTop = navBar.getTop();
            int rootHeight = root.getHeight();
            if (rootHeight <= 0 || navTop <= 0) return;
            int target = Math.max(0, rootHeight - navTop - dp(74));
            if (params.bottomMargin == target) return;
            params.bottomMargin = target;
            couple.setLayoutParams(params);
        });
    }

    /** 写日志需要能落到 /Android/NBB/：Android 11+ 走「所有文件访问」，更早版本走读存储权限。 */
    private boolean ensureStorageAccess() {
        if (LocalArtwork.hasAccess(this)) return true;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
            }
        } catch (RuntimeException e) {
            toast(getString(R.string.log_need_permission));
        }
        return false;
    }

    private void toggleDonate() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(18);
        panel.setPadding(pad, dp(4), pad, dp(8));

        TextView hint = new TextView(this);
        hint.setText(R.string.donate_dialog_hint);
        hint.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        hint.setTextSize(13);
        hint.setLineSpacing(dp(4), 1f);
        panel.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        addDonateQr(panel, R.string.donate_alipay, R.drawable.donate_alipay);
        addDonateQr(panel, R.string.donate_wechat, R.drawable.donate_wechat);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(panel);
        new AlertDialog.Builder(this)
                .setTitle(R.string.donate)
                .setView(scroll)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void addDonateQr(LinearLayout parent, int labelRes, int drawableRes) {
        TextView label = new TextView(this);
        label.setText(labelRes);
        label.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        label.setTextSize(14);
        label.getPaint().setFakeBoldText(true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(14);
        parent.addView(label, labelParams);

        ImageView image = new ImageView(this);
        image.setImageResource(drawableRes);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setContentDescription(getString(labelRes));
        image.setBackgroundResource(R.drawable.bg_logo);
        image.setClipToOutline(true);
        image.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        imageParams.topMargin = dp(8);
        parent.addView(image, imageParams);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException e) {
            toast("没有可用的浏览器，链接：" + url);
        }
    }

    // --- 设备检测 ---

    private void runDetection() {
        if (tvCheckResult == null) return;
        tvCheckResult.setText(R.string.check_running);
        runInWorker(() -> {
            final String text = DeviceInspector.describe(this, DeviceInspector.inspect(this));
            ui.post(() -> {
                if (!isFinishing() && tvCheckResult != null) {
                    tvCheckResult.setText(text);
                }
            });
        });
    }

    private void runInWorker(Runnable task) {
        ExecutorService executor = worker;
        if (executor != null && !executor.isShutdown()) {
            executor.execute(task);
        }
    }

    // --- 杂项 ---

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
    }

    private void applyStatusBarIcons() {
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        // 首页是深色实景背景，即使系统是浅色模式也要用浅色状态栏图标。
        boolean lightIcons = currentTab == 0 || !night;
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(lightIcons);
        controller.setAppearanceLightNavigationBars(lightIcons);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void toast(int resId) {
        toast(getString(resId));
    }

    /** SeekBar 监听只需要进度回调，避免重复实现两个空方法。 */
    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
