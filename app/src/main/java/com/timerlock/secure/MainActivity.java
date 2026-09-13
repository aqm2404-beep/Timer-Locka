package com.timerlock.secure;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 9001;
    private static final int BG = Color.rgb(9, 11, 14);
    private static final int PANEL = Color.rgb(22, 25, 31);
    private static final int TEXT = Color.rgb(238, 241, 245);
    private static final int MUTED = Color.rgb(150, 158, 170);
    private static final int ACCENT = Color.rgb(255, 70, 70);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView countdown;
    private long lastRenderedSecond = Long.MIN_VALUE;
    private ToneGenerator tone;
    private MediaPlayer expiryPlayer;

    private final Runnable uiTick = new Runnable() {
        @Override public void run() {
            if (TimerStore.state(MainActivity.this) == TimerState.ACTIVE) {
                long remaining = TimerStore.remainingMs(MainActivity.this);
                if (remaining == Long.MIN_VALUE) {
                    TimerStore.setRecoveryRequired(MainActivity.this, "UI detected clock integrity failure");
                    render();
                    return;
                }
                if (remaining <= 0) {
                    TimerStore.markExpired(MainActivity.this, "UI reached zero");
                    AlarmReceiver.notifyExpired(MainActivity.this);
                    DeviceLockHelper.lockScreen(MainActivity.this);
                    render();
                    return;
                }
                long sec = remaining / 1000L;
                if (countdown != null) countdown.setText(format(remaining));
                if (sec != lastRenderedSecond) {
                    lastRenderedSecond = sec;
                    if (sec <= 10) {
                        if (tone == null) tone = new ToneGenerator(AudioManager.STREAM_ALARM, 45);
                        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 80);
                        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                        if (v != null && v.hasVibrator()) {
                            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(80, 110));
                            else v.vibrate(80);
                        }
                    }
                }
                handler.postDelayed(this, 250L);
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotificationPermission();
        immersive();
        if (TimerStore.state(this) == TimerState.ACTIVE) TimerStore.recoverAfterBoot(this, false);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        immersive();
        TimerState s = TimerStore.state(this);
        if (s == TimerState.ACTIVE || s == TimerState.EXPIRED) enterKioskIfDeviceOwner();
        render();
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(uiTick);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacks(uiTick);
        if (tone != null) { tone.release(); tone = null; }
        stopExpiryAlarm();
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        TimerState s = TimerStore.state(this);
        if (s == TimerState.ACTIVE || s == TimerState.EXPIRED || s == TimerState.RECOVERY_REQUIRED) {
            Toast.makeText(this, "TimerLock is locked. Administrator access required.", Toast.LENGTH_SHORT).show();
            return;
        }
        super.onBackPressed();
    }

    private void render() {
        handler.removeCallbacks(uiTick);
        lastRenderedSecond = Long.MIN_VALUE;
        stopExpiryAlarm();

        if (!PasswordManager.hasPassword(this)) {
            renderFirstLaunch();
            return;
        }

        TimerState state = TimerStore.state(this);
        switch (state) {
            case IDLE: renderIdle(); break;
            case ACTIVE: renderActive(); break;
            case EXPIRED: renderExpired(); break;
            case RECOVERY_REQUIRED: renderRecovery(); break;
        }
    }

    private LinearLayout base() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        root.setBackgroundColor(BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
        return root;
    }

    private TextView title(LinearLayout root, String text) {
        TextView t = text(text, 22, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(.08f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, dp(18), 0, dp(28));
        root.addView(t, lp);
        return t;
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackgroundColor(PANEL);
        b.setPadding(dp(18), dp(14), dp(18), dp(14));
        return b;
    }

    private EditText input(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(TEXT);
        e.setTextSize(18);
        e.setGravity(Gravity.CENTER);
        e.setInputType(type);
        e.setBackgroundColor(PANEL);
        e.setPadding(dp(12), dp(12), dp(12), dp(12));
        return e;
    }

    private void renderFirstLaunch() {
        LinearLayout root = base();
        title(root, "TIMERLOCK SETUP");
        TextView note = text("Create the administrator password.\nMinimum 6 characters.", 15, MUTED);
        root.addView(note, matchWrap(0, dp(18)));
        EditText p1 = input("New password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText p2 = input("Confirm password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(p1, matchWrap(0, dp(10)));
        root.addView(p2, matchWrap(0, dp(22)));
        Button save = button("Save & Continue");
        root.addView(save, matchWrap(0, 0));
        save.setOnClickListener(v -> {
            String a = p1.getText().toString();
            String b = p2.getText().toString();
            if (a.length() < 6) { toast("Use at least 6 characters."); return; }
            if (!a.equals(b)) { toast("Passwords do not match."); return; }
            if (PasswordManager.setPassword(this, a.toCharArray())) {
                TimerStore.log(this, "ADMIN_PASSWORD_CREATED", "Initial administrator credential configured");
                render();
            } else toast("Unable to save password.");
        });
    }

    private void renderIdle() {
        LinearLayout root = base();
        title(root, "TIMERLOCK");
        TextView ready = text("SECURE COUNTDOWN READY", 14, MUTED);
        ready.setLetterSpacing(.12f);
        root.addView(ready, matchWrap(0, dp(26)));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        EditText hh = numericBox("HH");
        EditText mm = numericBox("MM");
        EditText ss = numericBox("SS");
        row.addView(hh, boxParams());
        row.addView(text(":", 28, TEXT), new LinearLayout.LayoutParams(dp(24), dp(62)));
        row.addView(mm, boxParams());
        row.addView(text(":", 28, TEXT), new LinearLayout.LayoutParams(dp(24), dp(62)));
        row.addView(ss, boxParams());
        root.addView(row, matchWrap(0, dp(28)));

        Button start = button("START TIMER");
        start.setBackgroundColor(Color.rgb(150, 35, 35));
        root.addView(start, matchWrap(0, dp(18)));
        Button admin = button("Admin");
        root.addView(admin, matchWrap(0, 0));

        start.setOnClickListener(v -> {
            long h = parse(hh); long m = parse(mm); long s = parse(ss);
            if (h > 9999) { toast("Hours must be 0–9999."); return; }
            if (m > 59 || s > 59) { toast("Minutes and seconds must be 0–59."); return; }
            long totalSec = h * 3600L + m * 60L + s;
            if (totalSec <= 0) { toast("Set a duration greater than zero."); return; }
            long duration;
            try { duration = Math.multiplyExact(totalSec, 1000L); }
            catch (ArithmeticException ex) { toast("Duration is too large."); return; }
            if (!DeviceLockHelper.isAdminActive(this)) {
                new AlertDialog.Builder(this)
                        .setTitle("System Lock Access Required")
                        .setMessage("TimerLock needs Android Device Admin access before a locked countdown can start. Activate it, then start the timer again.")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Activate", (d, w) -> requestDeviceAdmin())
                        .show();
                return;
            }
            if (!DeviceLockHelper.isDeviceSecure(this)) {
                new AlertDialog.Builder(this)
                        .setTitle("Android Screen Lock Required")
                        .setMessage("Set an Android PIN, pattern, or password first. Without a secure Android lock screen, lockNow() can only put the screen to sleep instead of requiring unlock authentication.")
                        .setPositiveButton("OK", null)
                        .show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Start & Lock Timer?")
                    .setMessage("Once started, the timer cannot be paused, changed, stopped, or reset without administrator authentication.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Start & Lock", (d, w) -> startTimer(duration))
                    .show();
        });
        admin.setOnClickListener(v -> authenticate(this::showAdminPanel));
    }

    private void startTimer(long duration) {
        try {
            TimerStore.start(this, duration);
            enterKioskIfDeviceOwner();
            Intent svc = new Intent(this, TimerService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
            render();
        } catch (Exception e) {
            TimerStore.log(this, "START_FAILED", e.getClass().getSimpleName());
            toast("Timer could not be started safely.");
        }
    }

    private void renderActive() {
        LinearLayout root = base();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("TIMERLOCK", 18, TEXT);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button admin = button("🔒 Admin");
        top.addView(admin, new LinearLayout.LayoutParams(dp(110), dp(52)));
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        countdown = text(format(TimerStore.remainingMs(this)), 54, TEXT);
        countdown.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        countdown.setLetterSpacing(.03f);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, dp(70), 0, dp(20));
        root.addView(countdown, cp);

        TextView active = text("ACTIVE", 18, ACCENT);
        active.setTypeface(Typeface.DEFAULT_BOLD);
        active.setLetterSpacing(.18f);
        root.addView(active, matchWrap(0, dp(12)));
        root.addView(text("●  SECURE TIMER RUNNING", 14, MUTED), matchWrap(0, dp(24)));

        long target = TimerStore.targetWallClock(this);
        String targetText = target > 0 ? new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(target)) : "—";
        root.addView(text("Target  " + targetText, 16, MUTED), matchWrap(0, 0));

        admin.setOnClickListener(v -> authenticate(this::showAdminPanel));
        handler.post(uiTick);
    }

    private void renderExpired() {
        LinearLayout root = base();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("TIMERLOCK", 18, TEXT);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button admin = button("🔒 Admin");
        top.addView(admin, new LinearLayout.LayoutParams(dp(110), dp(52)));
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        TextView expired = text("TIME EXPIRED", 34, ACCENT);
        expired.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2);
        ep.setMargins(0, dp(85), 0, dp(22));
        root.addView(expired, ep);
        TextView zero = text("00 : 00 : 00", 52, TEXT);
        zero.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        root.addView(zero, matchWrap(0, dp(28)));
        root.addView(text("● TIMER LOCKED", 15, MUTED), matchWrap(0, 0));
        admin.setOnClickListener(v -> authenticate(this::showAdminPanel));
        startExpiryAlarm();
    }

    private void renderRecovery() {
        LinearLayout root = base();
        title(root, "TIMERLOCK");
        TextView warn = text("SYSTEM RECOVERY REQUIRED", 27, ACCENT);
        warn.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(warn, matchWrap(0, dp(20)));
        root.addView(text("Timer integrity could not be safely verified.\nAdministrator authentication is required.", 16, MUTED), matchWrap(0, dp(28)));
        Button admin = button("🔒 ADMIN ACCESS");
        root.addView(admin, matchWrap(0, 0));
        admin.setOnClickListener(v -> authenticate(this::showAdminPanel));
    }

    private void showAdminPanel() {
        String[] items = {
                "Stop & Reset Timer",
                "View System Log",
                "System Lock Access",
                "Change Admin Password",
                "Exit TimerLock"
        };
        new AlertDialog.Builder(this)
                .setTitle("Admin Control")
                .setItems(items, (d, which) -> {
                    if (which == 0) confirmReset();
                    else if (which == 1) showLog();
                    else if (which == 2) showSystemLockAccess();
                    else if (which == 3) changePassword();
                    else if (which == 4) confirmExit();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showSystemLockAccess() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, AdminReceiver.class);
        boolean active = dpm != null && dpm.isAdminActive(adminComponent);
        boolean owner = dpm != null && dpm.isDeviceOwnerApp(getPackageName());
        boolean secure = DeviceLockHelper.isDeviceSecure(this);
        if (active) {
            String message = "Device Admin: ACTIVE\nMode: " + (owner ? "DEVICE OWNER" : "DEVICE ADMIN")
                    + "\nAndroid secure lock: " + (secure ? "READY" : "NOT CONFIGURED")
                    + "\n\nUse Test Screen Lock to verify the phone really locks before starting a timer.";
            AlertDialog.Builder b = new AlertDialog.Builder(this)
                    .setTitle("System Lock Access")
                    .setMessage(message)
                    .setNegativeButton("Close", null);
            if (secure) {
                b.setPositiveButton("Test Screen Lock", (d, w) -> {
                    TimerStore.log(this, "SYSTEM_LOCK_TEST", "Administrator requested lock test");
                    boolean ok = DeviceLockHelper.lockScreen(this);
                    if (!ok) toast("System lock request failed. Check Device Admin access.");
                });
            }
            b.show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Enable System Lock Access?")
                .setMessage("Android will show its Device Admin confirmation screen. TimerLock uses this permission only to request a screen lock when the countdown expires.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (d, w) -> requestDeviceAdmin())
                .show();
    }

    private void requestDeviceAdmin() {
        ComponentName adminComponent = new ComponentName(this, AdminReceiver.class);
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Allow TimerLock to lock the Android screen when the countdown expires.");
        startActivity(intent);
    }

    private void confirmReset() {
        new AlertDialog.Builder(this)
                .setTitle("Stop & Reset Timer?")
                .setMessage("This will permanently end the current countdown.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm Reset", (d, w) -> {
                    stopExpiryAlarm();
                    stopService(new Intent(this, TimerService.class));
                    ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(7001);
                    TimerStore.reset(this);
                    render();
                }).show();
    }

    private void showLog() {
        TextView log = new TextView(this);
        log.setText(TimerStore.readLog(this));
        log.setTextColor(TEXT);
        log.setTextSize(12);
        log.setTypeface(Typeface.MONOSPACE);
        log.setPadding(dp(16), dp(16), dp(16), dp(16));
        log.setBackgroundColor(BG);
        ScrollView s = new ScrollView(this);
        s.addView(log);
        new AlertDialog.Builder(this).setTitle("System Log").setView(s).setPositiveButton("Close", null).show();
    }

    private void changePassword() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        EditText p1 = input("New password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText p2 = input("Confirm password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(p1, matchWrap(0, dp(10)));
        box.addView(p2, matchWrap(0, 0));
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Change Admin Password")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();
        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String a = p1.getText().toString(); String b = p2.getText().toString();
            if (a.length() < 6) { toast("Use at least 6 characters."); return; }
            if (!a.equals(b)) { toast("Passwords do not match."); return; }
            if (PasswordManager.setPassword(this, a.toCharArray())) {
                TimerStore.log(this, "ADMIN_PASSWORD_CHANGED", "Credential updated");
                dlg.dismiss();
                toast("Password updated.");
            } else toast("Unable to update password.");
        }));
        dlg.show();
    }

    private void confirmExit() {
        new AlertDialog.Builder(this)
                .setTitle("Exit TimerLock?")
                .setMessage("This exits kiosk mode where Android policy allows it. Active timer state is not reset.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Exit", (d, w) -> {
                    try { stopLockTask(); } catch (Exception ignored) {}
                    TimerStore.log(this, "ADMIN_EXIT", "Administrator exited TimerLock UI");
                    finishAndRemoveTask();
                }).show();
    }

    private void authenticate(Runnable success) {
        long remain = PasswordManager.lockoutRemainingMs(this);
        if (remain > 0) { toast("Admin locked. Try again in " + ((remain + 999) / 1000) + "s."); return; }
        EditText pw = input("Admin password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pw.setSingleLine(true);
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Admin Access")
                .setView(pw)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Unlock", null)
                .create();
        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            long left = PasswordManager.lockoutRemainingMs(this);
            if (left > 0) { toast("Locked for " + ((left + 999) / 1000) + "s."); return; }
            if (PasswordManager.verify(this, pw.getText().toString().toCharArray())) {
                TimerStore.log(this, "ADMIN_AUTH_SUCCESS", "Administrator authenticated");
                dlg.dismiss();
                success.run();
            } else {
                TimerStore.log(this, "ADMIN_AUTH_FAILED", "Incorrect administrator credential");
                long lock = PasswordManager.lockoutRemainingMs(this);
                toast(lock > 0 ? "Too many attempts. Locked for " + ((lock + 999) / 1000) + "s." : "Incorrect password.");
                pw.setText("");
            }
        }));
        dlg.show();
    }

    private void enterKioskIfDeviceOwner() {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(this, AdminReceiver.class);
            if (dpm.isDeviceOwnerApp(getPackageName())) {
                dpm.setLockTaskPackages(admin, new String[]{getPackageName()});
                if (Build.VERSION.SDK_INT >= 28) dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
                startLockTask();
            }
        } catch (Exception e) {
            TimerStore.log(this, "KIOSK_WARNING", e.getClass().getSimpleName());
        }
    }

    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private void startExpiryAlarm() {
        if (expiryPlayer != null) return;
        try {
            expiryPlayer = MediaPlayer.create(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
            if (expiryPlayer != null) {
                expiryPlayer.setLooping(true);
                expiryPlayer.start();
            }
        } catch (Exception ignored) {}
    }

    private void stopExpiryAlarm() {
        if (expiryPlayer != null) {
            try { expiryPlayer.stop(); } catch (Exception ignored) {}
            expiryPlayer.release();
            expiryPlayer = null;
        }
    }

    private EditText numericBox(String hint) {
        EditText e = input(hint, InputType.TYPE_CLASS_NUMBER);
        e.setSingleLine(true);
        return e;
    }

    private LinearLayout.LayoutParams boxParams() { return new LinearLayout.LayoutParams(dp(82), dp(62)); }

    private LinearLayout.LayoutParams matchWrap(int top, int bottom) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, top, 0, bottom);
        return lp;
    }

    private long parse(EditText e) {
        String s = e.getText().toString().trim();
        if (s.isEmpty()) return 0L;
        try { return Long.parseLong(s); } catch (Exception ex) { return 0L; }
    }

    private static String format(long ms) {
        long total = Math.max(0L, ms / 1000L);
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return String.format(Locale.US, "%02d : %02d : %02d", h, m, s);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
}
