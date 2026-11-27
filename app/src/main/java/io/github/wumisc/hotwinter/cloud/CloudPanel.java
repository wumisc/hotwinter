package io.github.wumisc.hotwinter.cloud;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.wumisc.hotwinter.BuildConfig;
import io.github.wumisc.hotwinter.R;

/**
 * 云端账号、配对和状态的界面控制器。
 * 既可以被独立的 CloudActivity 使用，也可以嵌进一级「我的」页。
 * 所有网络请求都在单线程池里跑，页面只读 CloudSync 的快照。
 */
public final class CloudPanel implements CloudSync.Listener {

    private final AppCompatActivity host;
    private final View root;
    private CloudPrefs prefs;
    private CloudSync sync;
    private ExecutorService io;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private EditText etServer;
    private EditText etEmail;
    private EditText etPassword;
    private EditText etName;
    private EditText etPairCode;
    private EditText etDm;
    private ScrollView scroll;
    private LinearLayout cardLogin;
    private TextView tvAvatarInitial;
    private TextView tvProfileName;
    private TextView tvProfileMeta;
    private TextView tvProfileId;
    private TextView tvProfileStatus;
    private TextView chipProfileAction;
    private TextView tvAccountMenuHint;
    private TextView tvPairStatus;
    private TextView tvTogetherDays;
    private TextView tvPairCode;
    private TextView tvPairCodeHint;
    private TextView tvPairError;
    private TextView tvPairState;
    private TextView tvPartnerAvatar;
    private TextView tvPartnerName;
    private TextView tvPartnerOnline;
    private TextView tvPartnerId;
    private View viewPartnerDot;
    private TextView tvPartnerBattery;
    private TextView tvPartnerCharging;
    private TextView tvPartnerNetwork;
    private TextView tvPartnerHeating;
    private TextView tvPartnerAt;
    private TextView tvCloudShareState;
    private TextView tvCloudFeed;
    private TextView chipCreateCode;
    private TextView chipJoin;
    private TextView chipUnpair;
    private TextView chipRecover;
    private TextView chipDmSend;
    private TextView chipNudgeSleep;
    private TextView chipNudgeWater;
    private TextView chipAffectionMiss;
    private TextView chipAffectionHug;
    private TextView chipAffectionKiss;
    private TextView chipAffectionNight;
    private SwitchCompat switchShare;

    private JSONObject couple;
    private long lastAffectionAt;
    private String pairCode;
    private boolean rendering;
    private String feed = "";

    public CloudPanel(AppCompatActivity host, View root, boolean firstLevel) {
        this.host = host;
        this.root = root;
        prefs = CloudPrefs.get(host);
        sync = CloudSync.get(host);
        io = Executors.newSingleThreadExecutor();

        bind();
        setupActions();
        String remembered = prefs.lastEmail();
        if (!remembered.isEmpty() && text(etEmail).isEmpty()) etEmail.setText(remembered);
        if (firstLevel) {
            View back = root.findViewById(R.id.tvCloudBack);
            if (back != null) back.setVisibility(View.GONE);
            if (scroll != null) {
                scroll.setPadding(scroll.getPaddingLeft(), scroll.getPaddingTop(),
                        scroll.getPaddingRight(), dp(108));
                scroll.setClipToPadding(false);
            }
        }

        sync.addListener(this);
        if (prefs.sharing() && prefs.hasToken()) {
            CloudService.start(host);
        }
        renderAll();
        if (prefs.hasToken()) loadMe();
    }

    private void bind() {
        scroll = root.findViewById(R.id.scrollCloud);
        etServer = root.findViewById(R.id.etCloudServer);
        etEmail = root.findViewById(R.id.etCloudEmail);
        etPassword = root.findViewById(R.id.etCloudPassword);
        etName = root.findViewById(R.id.etCloudName);
        etPairCode = root.findViewById(R.id.etPairCode);
        etDm = root.findViewById(R.id.etCloudDm);
        cardLogin = root.findViewById(R.id.cardLogin);
        tvAvatarInitial = root.findViewById(R.id.tvAvatarInitial);
        tvProfileName = root.findViewById(R.id.tvProfileName);
        tvProfileMeta = root.findViewById(R.id.tvProfileMeta);
        tvProfileId = root.findViewById(R.id.tvProfileId);
        tvProfileStatus = root.findViewById(R.id.tvProfileStatus);
        chipProfileAction = root.findViewById(R.id.chipProfileAction);
        tvAccountMenuHint = root.findViewById(R.id.tvAccountMenuHint);
        tvPairStatus = root.findViewById(R.id.tvPairStatus);
        tvTogetherDays = root.findViewById(R.id.tvTogetherDays);
        tvPairCode = root.findViewById(R.id.tvPairCode);
        tvPairCodeHint = root.findViewById(R.id.tvPairCodeHint);
        tvPairError = root.findViewById(R.id.tvPairError);
        tvPairState = root.findViewById(R.id.tvPairState);
        tvPartnerAvatar = root.findViewById(R.id.tvPartnerAvatar);
        tvPartnerName = root.findViewById(R.id.tvPartnerName);
        tvPartnerOnline = root.findViewById(R.id.tvPartnerOnline);
        tvPartnerId = root.findViewById(R.id.tvPartnerId);
        viewPartnerDot = root.findViewById(R.id.viewPartnerDot);
        tvPartnerBattery = root.findViewById(R.id.tvPartnerBattery);
        tvPartnerCharging = root.findViewById(R.id.tvPartnerCharging);
        tvPartnerNetwork = root.findViewById(R.id.tvPartnerNetwork);
        tvPartnerHeating = root.findViewById(R.id.tvPartnerHeating);
        tvPartnerAt = root.findViewById(R.id.tvPartnerAt);
        tvCloudShareState = root.findViewById(R.id.tvCloudShareState);
        tvCloudFeed = root.findViewById(R.id.tvCloudFeed);
        chipCreateCode = root.findViewById(R.id.chipCloudCreateCode);
        chipJoin = root.findViewById(R.id.chipCloudJoin);
        chipUnpair = root.findViewById(R.id.chipCloudUnpair);
        chipRecover = root.findViewById(R.id.chipCloudRecover);
        chipDmSend = root.findViewById(R.id.chipCloudDmSend);
        chipNudgeSleep = root.findViewById(R.id.chipCloudNudgeSleep);
        chipNudgeWater = root.findViewById(R.id.chipCloudNudgeWater);
        chipAffectionMiss = root.findViewById(R.id.chipCloudMiss);
        chipAffectionHug = root.findViewById(R.id.chipCloudHug);
        chipAffectionKiss = root.findViewById(R.id.chipCloudKiss);
        chipAffectionNight = root.findViewById(R.id.chipCloudNight);
        switchShare = root.findViewById(R.id.switchCloudShare);

        etServer.setText(prefs.baseUrl());
    }

    private void setupActions() {
        root.findViewById(R.id.tvCloudBack).setOnClickListener(v -> host.finish());
        chipProfileAction.setOnClickListener(v -> {
            if (prefs.hasToken()) showAccountDialog();
            else scrollTo(cardLogin);
        });
        root.findViewById(R.id.rowAccount).setOnClickListener(v -> {
            if (prefs.hasToken()) showAccountDialog();
            else {
                toast("先登录云端账号");
                scrollTo(cardLogin);
            }
        });
        root.findViewById(R.id.rowCare).setOnClickListener(v -> showCareDialog());
        root.findViewById(R.id.rowAbout).setOnClickListener(v -> showAboutDialog());
        root.findViewById(R.id.chipCloudLogin).setOnClickListener(v -> login());
        root.findViewById(R.id.chipCloudRegister).setOnClickListener(v -> register());
        chipRecover.setOnClickListener(v -> showRecoverDialog());
        chipCreateCode.setOnClickListener(v -> createPairCode());
        chipJoin.setOnClickListener(v -> joinPair());
        chipUnpair.setOnClickListener(v -> confirmUnpair());
        chipDmSend.setOnClickListener(v -> sendDm());
        chipNudgeSleep.setOnClickListener(v -> nudge("sleep"));
        chipNudgeWater.setOnClickListener(v -> nudge("water"));
        chipAffectionMiss.setOnClickListener(v -> sendAffection(v, "想你了"));
        chipAffectionHug.setOnClickListener(v -> sendAffection(v, "抱抱"));
        chipAffectionKiss.setOnClickListener(v -> sendAffection(v, "亲亲"));
        chipAffectionNight.setOnClickListener(v -> sendAffection(v, "晚安"));
        switchShare.setOnCheckedChangeListener((button, checked) -> {
            if (rendering) return;
            setSharing(checked);
        });
    }

    public void destroy() {
        sync.removeListener(this);
        io.shutdownNow();
    }

    @Override
    public void onCloudState() {
        ui.post(this::renderSnapshot);
    }

    @Override
    public void onCloudDanmaku(String name, String text) {
        ui.post(() -> appendFeed((name.isEmpty() ? "TA" : name) + "：" + text));
    }

    // --- 渲染 ---

    private void renderAll() {
        renderAccount();
        renderPair();
        renderSnapshot();
        renderShare();
    }

    private void renderAccount() {
        boolean logged = prefs.hasToken();
        boolean paired = couple != null;
        cardLogin.setVisibility(logged ? View.GONE : View.VISIBLE);
        chipRecover.setVisibility(logged ? View.GONE : View.VISIBLE);
        etServer.setEnabled(!logged);

        String name = prefs.displayName().trim();
        if (name.isEmpty()) name = logged ? "我" : host.getString(R.string.cloud_profile_guest);
        tvProfileName.setText(name);
        tvAvatarInitial.setText(initial(name));
        tvProfileMeta.setText(logged ? emailText() : host.getString(R.string.cloud_profile_guest_meta));
        tvProfileId.setText(logged
                ? host.getString(R.string.cloud_profile_id_format, shortId(prefs.userId()))
                : host.getString(R.string.cloud_profile_id_empty));
        tvProfileStatus.setText(logged
                ? host.getString(paired ? R.string.cloud_status_paired : R.string.cloud_status_logged_in)
                : host.getString(R.string.cloud_status_local));
        chipProfileAction.setText(logged
                ? R.string.cloud_profile_action_account
                : R.string.cloud_profile_action_login);
        tvAccountMenuHint.setText(logged ? emailText() : host.getString(R.string.cloud_menu_account_desc));
    }

    private void renderPair() {
        boolean logged = prefs.hasToken();
        boolean paired = couple != null;
        chipCreateCode.setVisibility(logged && !paired ? View.VISIBLE : View.GONE);
        etPairCode.setVisibility(logged && !paired ? View.VISIBLE : View.GONE);
        chipJoin.setVisibility(logged && !paired ? View.VISIBLE : View.GONE);
        chipUnpair.setVisibility(paired ? View.VISIBLE : View.GONE);
        tvPairCode.setVisibility(pairCode != null && !paired ? View.VISIBLE : View.GONE);
        tvPairCodeHint.setVisibility(pairCode != null && !paired ? View.VISIBLE : View.GONE);
        tvPairState.setText(paired
                ? R.string.cloud_pair_state_paired
                : R.string.cloud_pair_state_empty);

        String partnerName = "TA";
        String partnerId = "";
        if (paired) {
            JSONObject partner = couple.optJSONObject("partner");
            if (partner != null) {
                partnerName = partner.optString("displayName", "").trim();
                partnerId = partner.optString("id", "");
                if (partnerName.isEmpty()) partnerName = "TA";
            }
            tvPairStatus.setText(host.getString(R.string.cloud_pair_bound_format, partnerName));
            int days = togetherDays();
            tvTogetherDays.setVisibility(days > 0 ? View.VISIBLE : View.GONE);
            if (days > 0) {
                tvTogetherDays.setText(host.getString(R.string.cloud_together_days_format, days));
            }
        } else {
            tvPairStatus.setText(logged
                    ? R.string.cloud_pair_empty
                    : R.string.cloud_pair_need_login);
            tvTogetherDays.setVisibility(View.GONE);
        }

        tvPartnerAvatar.setText(initial(partnerName));
        tvPartnerName.setText(paired ? partnerName : host.getString(R.string.cloud_partner_empty));
        tvPartnerId.setText(paired && !partnerId.isEmpty()
                ? host.getString(R.string.cloud_partner_id_format, shortId(partnerId))
                : host.getString(R.string.cloud_partner_id_empty));

        switchShare.setEnabled(logged && paired);
        if (!logged || !paired) {
            rendering = true;
            switchShare.setChecked(false);
            rendering = false;
        }
    }

    private void renderShare() {
        rendering = true;
        if (prefs.hasToken() && couple != null && prefs.sharing()) {
            switchShare.setChecked(true);
            tvCloudShareState.setText(R.string.cloud_share_on);
        } else {
            switchShare.setChecked(false);
            tvCloudShareState.setText(R.string.cloud_share_off);
        }
        rendering = false;
    }

    private void renderSnapshot() {
        CloudSync.Snapshot snapshot = sync.snapshot();
        String name = snapshot.partner.name;
        if (name == null || name.isEmpty()) {
            JSONObject partner = couple == null ? null : couple.optJSONObject("partner");
            name = partner == null ? "" : partner.optString("displayName", "").trim();
        }
        if (name.isEmpty()) {
            name = "TA";
        }
        boolean paired = couple != null;
        if (!paired) {
            tvPartnerAvatar.setText(R.string.cloud_partner_avatar);
            tvPartnerName.setText(R.string.cloud_partner_empty);
            tvPartnerOnline.setText(R.string.cloud_partner_offline);
            tvPartnerId.setText(R.string.cloud_partner_id_empty);
            viewPartnerDot.setVisibility(View.GONE);
            tvPartnerBattery.setText(R.string.cloud_unknown);
            tvPartnerCharging.setText(R.string.cloud_unknown);
            tvPartnerNetwork.setText(R.string.cloud_unknown);
            tvPartnerHeating.setText(R.string.cloud_unknown);
            tvPartnerAt.setText(R.string.cloud_status_empty);
        } else {
            tvPartnerAvatar.setText(initial(name));
            tvPartnerName.setText(name);
            boolean online = snapshot.partnerOnline();
            viewPartnerDot.setVisibility(online ? View.VISIBLE : View.GONE);
            if (online) {
                tvPartnerOnline.setText(snapshot.connected ? R.string.cloud_online : R.string.cloud_online_local);
            } else {
                tvPartnerOnline.setText(lastSeen(snapshot.partner.presenceAt));
            }
            CloudSync.Partner partner = snapshot.partner;
            tvPartnerBattery.setText(partner.hasStatus && partner.battery >= 0
                    ? partner.battery + "%" : host.getString(R.string.cloud_unknown));
            tvPartnerCharging.setText(partner.hasStatus
                    ? host.getString(partner.charging ? R.string.cloud_yes : R.string.cloud_no)
                    : host.getString(R.string.cloud_unknown));
            tvPartnerNetwork.setText(partner.hasStatus
                    ? networkText(partner.network) : host.getString(R.string.cloud_unknown));
            tvPartnerHeating.setText(partner.hasStatus
                    ? host.getString(partner.heating
                    ? R.string.cloud_heating_on : R.string.cloud_heating_off)
                    : host.getString(R.string.cloud_unknown));
            tvPartnerAt.setText(partner.statusAt > 0
                    ? "状态更新：" + clock(partner.statusAt) : host.getString(R.string.cloud_status_empty));
        }
        if (snapshot.connected) {
            tvCloudShareState.setText(prefs.sharing()
                    ? R.string.cloud_share_on : R.string.cloud_connected_not_shared);
        } else if (!snapshot.lastError.isEmpty() && prefs.hasToken()) {
            tvCloudShareState.setText("云端：" + snapshot.lastError);
        }
    }

    private void showAccountDialog() {
        String name = prefs.displayName().trim();
        if (name.isEmpty()) name = "我";
        String email = emailText();
        String id = shortId(prefs.userId());
        String fullId = prefs.userId().trim().isEmpty() ? id : prefs.userId().trim();
        String token = prefs.encrypted()
                ? "登录令牌已用系统 Keystore 加密保存在本机。"
                : "当前 ROM 不支持 Keystore，令牌只保存在应用私有目录。";
        new AlertDialog.Builder(host)
                .setTitle(R.string.cloud_menu_account)
                .setMessage("昵称：" + name + "\n邮箱：" + email + "\nID：" + id
                        + "\n服务器：" + prefs.baseUrl()
                        + "\n\n" + token
                        + "\n\n退出后，本机将停止同步；再次使用需要重新登录。")
                .setNeutralButton("复制 ID", (d, w) -> copyValue(fullId, "ID 已复制"))
                .setNegativeButton("关闭", null)
                .setPositiveButton(R.string.cloud_logout, (d, w) -> logout())
                .show();
    }

    private void showCareDialog() {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        CheckBox battery = new CheckBox(host);
        battery.setText(R.string.cloud_care_battery);
        battery.setChecked(prefs.careBattery());
        CheckBox heat = new CheckBox(host);
        heat.setText(R.string.cloud_care_heat);
        heat.setChecked(prefs.careHeat());
        box.addView(battery);
        box.addView(heat);
        new AlertDialog.Builder(host)
                .setTitle(R.string.cloud_care)
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    prefs.setCareBattery(battery.isChecked());
                    prefs.setCareHeat(heat.isChecked());
                    toast(host.getString(R.string.cloud_care_saved));
                })
                .show();
    }

    private void showAboutDialog() {
        String version = BuildConfig.VERSION_NAME;
        String message = "情侣加热 · 我的\n"
                + "版本 " + version + " · 作者 wumisc\n\n"
                + "把两台手机的电量、充电、网络、是否加热，以及弹幕和轻提醒同步到云端。"
                + "只共享这五项，不读取对方用了什么 App，也不提供远程锁机。\n\n"
                + "感谢名单：AndroidX / AppCompat、JUnit、弹幕人生、AGPL-3.0 社区，还有家里那位。\n\n"
                + "支持与反馈：github.com/wumisc/hotwinter/issues\n"
                + "开源地址：github.com/wumisc/hotwinter\n"
                + "开源协议：AGPL-3.0";
        new AlertDialog.Builder(host)
                .setTitle(R.string.cloud_about)
                .setIcon(R.mipmap.ic_launcher)
                .setMessage(message)
                .setNeutralButton("功能建议", (d, w) -> openUrl("https://github.com/wumisc/hotwinter/issues"))
                .setPositiveButton("知道了", null)
                .show();
    }

    private void openUrl(String url) {
        try {
            host.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable error) {
            toast("没有可打开链接的应用");
        }
    }

    private void scrollTo(View target) {
        if (target == null) return;
        scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, target.getTop() - dp(12))));
    }

    private String emailText() {
        String email = prefs.email().trim();
        return email.isEmpty() ? "未填写邮箱" : email;
    }

    private String initial(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) return "我";
        return value.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private String shortId(String id) {
        String value = id == null ? "" : id.trim();
        if (value.isEmpty()) return "—";
        return value.length() <= 12 ? value : value.substring(0, 12);
    }

    private void appendFeed(String line) {
        feed = feed.isEmpty() ? line : line + "\n" + feed;
        String[] lines = feed.split("\n");
        if (lines.length > 3) feed = lines[0] + "\n" + lines[1] + "\n" + lines[2];
        tvCloudFeed.setText(feed);
    }

    // --- 账号 ---

    private void register() {
        String base = serverOrToast();
        if (base == null) return;
        String email = text(etEmail);
        String password = text(etPassword);
        String name = text(etName);
        if (email.isEmpty() || password.length() < 8) {
            toast("邮箱要填，密码至少 8 位");
            return;
        }
        setBusy(true);
        JSONObject body = new JSONObject();
        put(body, "email", email);
        put(body, "password", password);
        put(body, "displayName", name);
        call("POST", "/api/register", null, body, new Callback() {
            @Override
            public void ok(JSONObject json) {
                prefs.setBaseUrl(base);
                JSONObject user = json.optJSONObject("user");
                prefs.saveSession(json.optString("token", ""),
                        user == null ? email : user.optString("email", email),
                        user == null ? name : user.optString("displayName", name),
                        user == null ? "" : user.optString("id", ""));
                String recovery = json.optString("recoveryCode", "");
                setBusy(false);
                renderAll();
                if (!recovery.isEmpty()) showRecovery(recovery, false, email);
                loadMe();
            }

            @Override
            public void fail(String message) {
                setBusy(false);
                toast(message);
            }
        });
    }

    private void login() {
        String base = serverOrToast();
        if (base == null) return;
        String email = text(etEmail);
        String password = text(etPassword);
        if (email.isEmpty() || password.isEmpty()) {
            toast("邮箱和密码都要填");
            return;
        }
        setBusy(true);
        JSONObject body = new JSONObject();
        put(body, "email", email);
        put(body, "password", password);
        call("POST", "/api/login", null, body, new Callback() {
            @Override
            public void ok(JSONObject json) {
                prefs.setBaseUrl(base);
                JSONObject user = json.optJSONObject("user");
                prefs.saveSession(json.optString("token", ""),
                        user == null ? email : user.optString("email", email),
                        user == null ? "" : user.optString("displayName", ""),
                        user == null ? "" : user.optString("id", ""));
                setBusy(false);
                renderAll();
                loadMe();
            }

            @Override
            public void fail(String message) {
                setBusy(false);
                toast(withEmailHint(message, email));
            }
        });
    }

    private void loadMe() {
        if (!prefs.hasToken()) return;
        call("GET", "/api/me", prefs.token(), null, new Callback() {
            @Override
            public void ok(JSONObject json) {
                JSONObject me = json.optJSONObject("user");
                if (me != null) {
                    prefs.saveSession(prefs.token(), me.optString("email", prefs.email()),
                            me.optString("displayName", prefs.displayName()),
                            me.optString("id", prefs.userId()));
                }
                couple = json.optJSONObject("couple");
                prefs.saveCouple(couple == null ? "" : couple.optString("id", ""));
                if (couple == null) prefs.setSharing(false);
                renderAll();
                renderSnapshot();
                if (couple != null && prefs.sharing()) {
                    CloudService.start(host);
                    sync.start();
                }
            }

            @Override
            public void fail(String message) {
                toast(message);
            }
        });
    }

    private void logout() {
        String token = prefs.token();
        if (!token.isEmpty()) {
            call("POST", "/api/logout", token, new JSONObject(), new Callback() {
                @Override
                public void ok(JSONObject json) {
                }

                @Override
                public void fail(String message) {
                }
            });
        }
        CloudService.stop(host);
        sync.stop();
        prefs.clearSession();
        couple = null;
        pairCode = null;
        feed = "";
        tvCloudFeed.setText(R.string.cloud_feed_empty);
        renderAll();
        toast("已退出云端账号");
    }

    private void showRecoverDialog() {
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        EditText email = new EditText(host);
        email.setHint("账号邮箱");
        email.setSingleLine(true);
        email.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        String remembered = prefs.lastEmail();
        email.setText(remembered.isEmpty() ? text(etEmail) : remembered);
        EditText code = new EditText(host);
        code.setHint("10 位恢复码");
        code.setSingleLine(true);
        EditText password = new EditText(host);
        password.setHint("新密码（至少 8 位）");
        password.setSingleLine(true);
        password.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(email);
        box.addView(code);
        box.addView(password);
        AlertDialog dialog = new AlertDialog.Builder(host)
                .setTitle("用恢复码重置密码")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("重置", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String base = serverOrToast();
                    if (base == null) return;
                    String account = text(email);
                    String c = code.getText().toString().trim().toUpperCase(Locale.ROOT);
                    String p = password.getText().toString();
                    if (account.isEmpty()) {
                        toast("账号邮箱要填");
                        return;
                    }
                    if (c.isEmpty() || p.length() < 8) {
                        toast("恢复码和新密码都要填，密码至少 8 位");
                        return;
                    }
                    JSONObject body = new JSONObject();
                    put(body, "email", account);
                    put(body, "recoveryCode", c);
                    put(body, "newPassword", p);
                    call("POST", "/api/recover", null, body, new Callback() {
                        @Override
                        public void ok(JSONObject json) {
                            dialog.dismiss();
                            String newCode = json.optString("recoveryCode", "");
                            if (!newCode.isEmpty()) showRecovery(newCode, true, account);
                            toast("密码已重置，请重新登录");
                        }

                        @Override
                        public void fail(String message) {
                            toast(withEmailHint(message, account));
                        }
                    });
                }));
        dialog.show();
    }

    private void showRecovery(String code, boolean rotated, String email) {
        AlertDialog dialog = new AlertDialog.Builder(host)
                .setTitle(rotated ? "新的恢复码" : "保存你的恢复码")
                .setMessage((email == null || email.trim().isEmpty()
                        ? "" : "账号：" + email.trim() + "\n\n")
                        + "恢复码只显示这一次：\n\n" + code
                        + "\n\n忘记密码时，用它换新密码。不要发给别人。")
                .setPositiveButton("复制", (d, w) -> copy(code))
                .setNegativeButton("我已保存", null)
                .create();
        dialog.show();
    }

    // --- 配对 ---

    private void createPairCode() {
        if (!prefs.hasToken()) {
            toast("先登录云端账号");
            return;
        }
        call("POST", "/api/pair/create", prefs.token(), new JSONObject(), new Callback() {
            @Override
            public void ok(JSONObject json) {
                pairCode = json.optString("code", "");
                tvPairCode.setText(pairCode);
                tvPairCodeHint.setText("10 分钟内有效，让对方在「输入配对码」里填这 6 位。");
                renderPair();
            }

            @Override
            public void fail(String message) {
                pairError(message);
            }
        });
    }

    private void joinPair() {
        String code = text(etPairCode);
        if (!code.matches("\\d{6}")) {
            pairError("配对码是 6 位数字");
            return;
        }
        JSONObject body = new JSONObject();
        put(body, "code", code);
        call("POST", "/api/pair/join", prefs.token(), body, new Callback() {
            @Override
            public void ok(JSONObject json) {
                couple = json.optJSONObject("couple");
                prefs.saveCouple(couple == null ? "" : couple.optString("id", ""));
                pairCode = null;
                pairError("");
                renderAll();
                loadMe();
                toast("配对成功");
            }

            @Override
            public void fail(String message) {
                pairError(message);
            }
        });
    }

    private void confirmUnpair() {
        new AlertDialog.Builder(host)
                .setTitle("解除绑定？")
                .setMessage("解绑后双方都看不到对方的云端状态，已有的弹幕记录也不再同步。")
                .setNegativeButton("取消", null)
                .setPositiveButton("解绑", (d, w) -> unpair())
                .show();
    }

    private void unpair() {
        call("POST", "/api/pair/unpair", prefs.token(), new JSONObject(), new Callback() {
            @Override
            public void ok(JSONObject json) {
                couple = null;
                pairCode = null;
                prefs.saveCouple("");
                prefs.setSharing(false);
                CloudService.stop(host);
                sync.stop();
                renderAll();
                renderSnapshot();
                toast("已解除绑定");
            }

            @Override
            public void fail(String message) {
                toast(message);
            }
        });
    }

    // --- 共享 / 提醒 / 弹幕 ---

    private void setSharing(boolean checked) {
        if (checked && (!prefs.hasToken() || couple == null)) {
            rendering = true;
            switchShare.setChecked(false);
            rendering = false;
            toast("登录并绑定伴侣后才能共享状态");
            return;
        }
        prefs.setSharing(checked);
        if (checked) {
            CloudService.start(host);
            sync.start();
            sync.sendStatus();
            tvCloudShareState.setText(R.string.cloud_share_on);
        } else {
            CloudService.stop(host);
            sync.stop();
            tvCloudShareState.setText(R.string.cloud_share_off);
        }
    }

    private void nudge(String kind) {
        if (couple == null) {
            toast("先绑定伴侣");
            return;
        }
        if (!sync.sendNudge(kind)) {
            toast("云端还没连上，稍等一下再试");
            return;
        }
        toast("提醒已发到云端");
    }

    private int togetherDays() {
        if (couple == null) return 0;
        return CloudCouple.togetherDays(couple.optLong("joinedAt", 0L), System.currentTimeMillis());
    }

    private void sendAffection(View source, String text) {
        if (source != null) source.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (!prefs.hasToken() || couple == null) {
            toast("先登录并绑定伴侣");
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (!CloudCouple.canSendAffection(lastAffectionAt, now)) {
            toast("慢一点，TA 刚收到上一句");
            return;
        }
        String id = sync.sendDanmaku(text);
        if (id.isEmpty()) {
            toast("云端还没连上，稍等一下再试");
            return;
        }
        lastAffectionAt = now;
        appendFeed("我：" + text);
        toast("已把「" + text + "」发给 TA");
    }

    private void sendDm() {
        String text = text(etDm);
        if (text.isEmpty()) return;
        if (couple == null) {
            toast("先绑定伴侣");
            return;
        }
        String id = sync.sendDanmaku(text);
        if (id.isEmpty()) {
            toast("云端还没连上，稍等一下再试");
            return;
        }
        appendFeed("我：" + text);
        etDm.setText("");
    }

    // --- 网络 ---

    private String serverOrToast() {
        String base = CloudPrefs.normalize(text(etServer));
        if (base.isEmpty()) {
            toast("先填云端 Worker 地址");
            return null;
        }
        prefs.setBaseUrl(base);
        return base;
    }

    private void call(String method, String path, String token, JSONObject body, Callback callback) {
        String base = prefs.baseUrl();
        io.execute(() -> {
            try {
                JSONObject json = "GET".equals(method)
                        ? CloudApi.get(base, path, token)
                        : CloudApi.post(base, path, token, body == null ? new JSONObject() : body);
                ui.post(() -> callback.ok(json));
            } catch (CloudApi.CloudException error) {
                ui.post(() -> {
                    if (CloudApi.isSessionExpired(error, token)) {
                        handleAuthLost(token);
                    } else {
                        callback.fail(error.getMessage());
                    }
                });
            } catch (Throwable error) {
                ui.post(() -> callback.fail("网络不可用：" + error.getClass().getSimpleName()));
            }
        });
    }

    private void handleAuthLost(String failedToken) {
        if (failedToken == null || !failedToken.equals(prefs.token())) return;
        CloudService.stop(host);
        sync.stop();
        prefs.clearSession();
        couple = null;
        pairCode = null;
        renderAll();
        toast("登录已过期，请重新登录");
    }

    private void setBusy(boolean busy) {
        root.findViewById(R.id.chipCloudLogin).setEnabled(!busy);
        root.findViewById(R.id.chipCloudRegister).setEnabled(!busy);
        root.findViewById(R.id.chipCloudLogin).setAlpha(busy ? 0.5f : 1f);
        root.findViewById(R.id.chipCloudRegister).setAlpha(busy ? 0.5f : 1f);
    }

    private String withEmailHint(String message, String enteredEmail) {
        String last = prefs.lastEmail();
        if (last.isEmpty() || last.equalsIgnoreCase(enteredEmail == null ? "" : enteredEmail.trim())) {
            return message;
        }
        if (message != null && (message.contains("邮箱或密码") || message.contains("邮箱或恢复码"))) {
            return message + "；上次邮箱是 " + last;
        }
        return message;
    }

    private void pairError(String message) {
        tvPairError.setText(message);
        tvPairError.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private String lastSeen(long at) {
        if (at <= 0) return "还没有收到过在线状态";
        long minutes = Math.max(0, (System.currentTimeMillis() - at) / 60_000L);
        if (minutes < 1) return "最后在线：刚刚";
        if (minutes < 60) return "最后在线：" + minutes + " 分钟前";
        return "最后在线：" + (minutes / 60) + " 小时前";
    }

    private String clock(long at) {
        if (at <= 0) return "刚刚";
        long minutes = Math.max(0, (System.currentTimeMillis() - at) / 60_000L);
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + " 分钟前";
        return (minutes / 60) + " 小时前";
    }

    private String networkText(String network) {
        if ("wifi".equals(network)) return host.getString(R.string.cloud_network_wifi);
        if ("mobile".equals(network)) return host.getString(R.string.cloud_network_mobile);
        return host.getString(R.string.cloud_network_none);
    }

    private String text(EditText view) {
        return view.getText() == null ? "" : view.getText().toString().trim();
    }

    private void put(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (Throwable ignored) {
        }
    }

    private void copy(String value) {
        copyValue(value, "恢复码已复制");
    }

    private void copyValue(String value, String hint) {
        ClipboardManager manager = (ClipboardManager) host.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) manager.setPrimaryClip(ClipData.newPlainText("情侣加热", value));
        toast(hint);
    }

    private void toast(String message) {
        Toast.makeText(host, message, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * host.getResources().getDisplayMetrics().density);
    }

    private interface Callback {
        void ok(JSONObject json);

        void fail(String message);
    }
}
