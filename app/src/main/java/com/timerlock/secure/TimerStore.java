package com.timerlock.secure;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public final class TimerStore {
    private static final String PREF = "timerlock_state";
    private static final String LOG_PREF = "timerlock_log";
    private static final String K_STATE = "state";
    private static final String K_ID = "id";
    private static final String K_DURATION = "duration";
    private static final String K_START_WALL = "start_wall";
    private static final String K_EXPIRY_WALL = "expiry_wall";
    private static final String K_START_ELAPSED = "start_elapsed";
    private static final String K_EXPIRY_ELAPSED = "expiry_elapsed";
    private static final String K_BOOT_COUNT = "boot_count";
    private static final String K_LAST_WALL = "last_wall";
    private static final String K_CLOCK_ANOMALY = "clock_anomaly";
    private static final String K_LAST_ALARM_SCHEDULED_WALL = "last_alarm_scheduled_wall";
    private static final String K_LAST_ALARM_RECEIVED_WALL = "last_alarm_received_wall";
    private static final String K_LAST_LOCK_REQUEST_WALL = "last_lock_request_wall";
    private static final String K_LAST_LOCK_RESULT = "last_lock_result";
    private static final long CLOCK_BACKWARD_TOLERANCE = 5 * 60_000L;
    private static final int LOG_LIMIT = 500;

    private TimerStore() {}

    private static Context dp(Context context) {
        return context.createDeviceProtectedStorageContext();
    }

    private static SharedPreferences prefs(Context context) {
        return dp(context).getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized TimerState state(Context context) {
        String raw = prefs(context).getString(K_STATE, TimerState.IDLE.name());
        try { return TimerState.valueOf(raw); }
        catch (Exception e) { return TimerState.RECOVERY_REQUIRED; }
    }

    public static boolean hasExactAlarmAccess(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        } catch (Exception e) {
            return false;
        }
    }

    public static synchronized void start(Context context, long durationMs) {
        if (durationMs <= 0) throw new IllegalArgumentException("Duration must be > 0");
        if (state(context) != TimerState.IDLE) throw new IllegalStateException("Timer already exists");
        if (!hasExactAlarmAccess(context)) {
            log(context, "EXACT_ALARM_PERMISSION_MISSING", "Protected timer start blocked");
            throw new IllegalStateException("Exact alarm access unavailable");
        }

        long wall = System.currentTimeMillis();
        long elapsed = SystemClock.elapsedRealtime();
        long expiryElapsed = safeAdd(elapsed, durationMs);
        long expiryWall = safeAdd(wall, durationMs);
        prefs(context).edit()
                .putString(K_STATE, TimerState.ACTIVE.name())
                .putString(K_ID, UUID.randomUUID().toString())
                .putLong(K_DURATION, durationMs)
                .putLong(K_START_WALL, wall)
                .putLong(K_EXPIRY_WALL, expiryWall)
                .putLong(K_START_ELAPSED, elapsed)
                .putLong(K_EXPIRY_ELAPSED, expiryElapsed)
                .putInt(K_BOOT_COUNT, bootCount(context))
                .putLong(K_LAST_WALL, wall)
                .putBoolean(K_CLOCK_ANOMALY, false)
                .commit();
        log(context, "TIMER_STARTED", "Duration=" + durationMs + "ms; expiryWall=" + expiryWall);

        if (!schedule(context)) {
            log(context, "START_ABORTED", "Exact alarm could not be scheduled");
            prefs(context).edit().clear().putString(K_STATE, TimerState.IDLE.name()).commit();
            throw new IllegalStateException("Exact alarm scheduling failed");
        }
    }

    public static synchronized long remainingMs(Context context) {
        if (state(context) != TimerState.ACTIVE) return 0L;
        SharedPreferences p = prefs(context);
        int storedBoot = p.getInt(K_BOOT_COUNT, Integer.MIN_VALUE);
        int currentBoot = bootCount(context);
        if (storedBoot != Integer.MIN_VALUE && storedBoot == currentBoot) {
            return Math.max(0L, p.getLong(K_EXPIRY_ELAPSED, 0L) - SystemClock.elapsedRealtime());
        }
        long now = System.currentTimeMillis();
        long last = p.getLong(K_LAST_WALL, 0L);
        if (last > 0 && now + CLOCK_BACKWARD_TOLERANCE < last) return Long.MIN_VALUE;
        return Math.max(0L, p.getLong(K_EXPIRY_WALL, 0L) - now);
    }

    public static synchronized void heartbeat(Context context) {
        if (state(context) != TimerState.ACTIVE) return;
        SharedPreferences p = prefs(context);
        int storedBoot = p.getInt(K_BOOT_COUNT, Integer.MIN_VALUE);
        int currentBoot = bootCount(context);
        if (storedBoot == currentBoot) {
            long expectedWall = p.getLong(K_START_WALL, 0L)
                    + (SystemClock.elapsedRealtime() - p.getLong(K_START_ELAPSED, 0L));
            long now = System.currentTimeMillis();
            boolean anomaly = Math.abs(now - expectedWall) > 2 * 60_000L;
            if (anomaly) {
                log(context, "CLOCK_ANOMALY", "Wall/monotonic delta=" + (now - expectedWall) + "ms");
                p.edit().putBoolean(K_CLOCK_ANOMALY, true).apply();
            } else {
                p.edit().putLong(K_LAST_WALL, now).apply();
            }
        }
    }

    public static synchronized void recoverAfterBoot(Context context, boolean forcePostBootRecovery) {
        if (state(context) != TimerState.ACTIVE) return;
        SharedPreferences p = prefs(context);
        int storedBoot = p.getInt(K_BOOT_COUNT, Integer.MIN_VALUE);
        int currentBoot = bootCount(context);

        if (!forcePostBootRecovery
                && storedBoot != Integer.MIN_VALUE
                && storedBoot == currentBoot) {
            if (!schedule(context)) {
                log(context, "EXACT_ALARM_PERMISSION_MISSING", "Active timer could not be rescheduled after process recreation");
            }
            return;
        }

        if (p.getBoolean(K_CLOCK_ANOMALY, false)) {
            setRecoveryRequired(context, "CLOCK_ANOMALY_BEFORE_REBOOT");
            return;
        }

        long nowWall = System.currentTimeMillis();
        long lastWall = p.getLong(K_LAST_WALL, 0L);
        long expiryWall = p.getLong(K_EXPIRY_WALL, 0L);
        if (expiryWall <= 0L || (lastWall > 0 && nowWall + CLOCK_BACKWARD_TOLERANCE < lastWall)) {
            setRecoveryRequired(context, "POST_REBOOT_TIME_INTEGRITY_FAILED");
            return;
        }
        if (nowWall >= expiryWall) {
            log(context, "EXPIRY_CONFIRMED", "Expiry passed while device was unavailable");
            markExpired(context, "Expired while device was unavailable");
            return;
        }

        long remaining = expiryWall - nowWall;
        long nowElapsed = SystemClock.elapsedRealtime();
        p.edit()
                .putInt(K_BOOT_COUNT, currentBoot)
                .putLong(K_START_ELAPSED, nowElapsed)
                .putLong(K_EXPIRY_ELAPSED, safeAdd(nowElapsed, remaining))
                .putLong(K_DURATION, remaining)
                .putLong(K_START_WALL, nowWall)
                .putLong(K_LAST_WALL, nowWall)
                .putBoolean(K_CLOCK_ANOMALY, false)
                .commit();
        log(context, "TIMER_RECOVERED", "Remaining=" + remaining + "ms");
        if (!schedule(context)) {
            log(context, "EXACT_ALARM_PERMISSION_MISSING", "Recovered timer could not be scheduled exactly");
        }
    }

    public static synchronized void markExpired(Context context, String reason) {
        TimerState s = state(context);
        if (s == TimerState.EXPIRED) return;
        if (s != TimerState.ACTIVE) return;
        prefs(context).edit().putString(K_STATE, TimerState.EXPIRED.name()).commit();
        cancelAlarm(context);
        log(context, "TIMER_EXPIRED", reason == null ? "" : reason);
    }

    public static synchronized void setRecoveryRequired(Context context, String reason) {
        prefs(context).edit().putString(K_STATE, TimerState.RECOVERY_REQUIRED.name()).commit();
        cancelAlarm(context);
        log(context, "RECOVERY_REQUIRED", reason == null ? "" : reason);
    }

    public static synchronized void reset(Context context) {
        cancelAlarm(context);
        prefs(context).edit().clear().putString(K_STATE, TimerState.IDLE.name()).commit();
        log(context, "ADMIN_RESET", "Timer state cleared by authenticated administrator");
    }

    public static synchronized long targetWallClock(Context context) {
        return prefs(context).getLong(K_EXPIRY_WALL, 0L);
    }

    public static synchronized String sessionId(Context context) {
        return prefs(context).getString(K_ID, "-");
    }

    public static synchronized long lastAlarmScheduledWall(Context context) {
        return prefs(context).getLong(K_LAST_ALARM_SCHEDULED_WALL, 0L);
    }

    public static synchronized long lastAlarmReceivedWall(Context context) {
        return prefs(context).getLong(K_LAST_ALARM_RECEIVED_WALL, 0L);
    }

    public static synchronized long lastLockRequestWall(Context context) {
        return prefs(context).getLong(K_LAST_LOCK_REQUEST_WALL, 0L);
    }

    public static synchronized String lastLockResult(Context context) {
        return prefs(context).getString(K_LAST_LOCK_RESULT, "-");
    }

    public static synchronized void recordAlarmReceived(Context context, String detail) {
        prefs(context).edit().putLong(K_LAST_ALARM_RECEIVED_WALL, System.currentTimeMillis()).apply();
        log(context, "ALARM_RECEIVED", detail == null ? "" : detail);
    }

    public static synchronized void recordLockRequest(Context context) {
        prefs(context).edit().putLong(K_LAST_LOCK_REQUEST_WALL, System.currentTimeMillis()).apply();
    }

    public static synchronized void recordLockResult(Context context, String result) {
        prefs(context).edit().putString(K_LAST_LOCK_RESULT, result == null ? "-" : result).apply();
    }

    public static synchronized boolean schedule(Context context) {
        if (state(context) != TimerState.ACTIVE) return false;
        long remaining = remainingMs(context);
        if (remaining == Long.MIN_VALUE) {
            setRecoveryRequired(context, "CLOCK_MOVED_BACKWARD");
            return false;
        }
        if (remaining <= 0) {
            log(context, "EXPIRY_CONFIRMED", "Expiry detected while scheduling");
            markExpired(context, "Expiry detected while scheduling");
            return false;
        }
        if (!hasExactAlarmAccess(context)) {
            log(context, "EXACT_ALARM_PERMISSION_MISSING", "No inexact fallback allowed for protected expiry");
            return false;
        }

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            log(context, "EXACT_ALARM_SCHEDULE_FAILED", "AlarmManager unavailable");
            return false;
        }

        PendingIntent pi = alarmIntent(context);
        long triggerElapsed = safeAdd(SystemClock.elapsedRealtime(), remaining);
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsed, pi);
            prefs(context).edit().putLong(K_LAST_ALARM_SCHEDULED_WALL, System.currentTimeMillis()).apply();
            log(context, "EXACT_ALARM_SCHEDULED",
                    "remaining=" + remaining + "ms; triggerElapsed=" + triggerElapsed + "; expiryWall=" + targetWallClock(context));
            return true;
        } catch (SecurityException ex) {
            log(context, "EXACT_ALARM_PERMISSION_MISSING", "SecurityException while scheduling exact alarm");
            return false;
        } catch (Exception ex) {
            log(context, "EXACT_ALARM_SCHEDULE_FAILED", ex.getClass().getSimpleName());
            return false;
        }
    }

    public static synchronized void cancelAlarm(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(alarmIntent(context));
        } catch (Exception ignored) {}
    }

    private static PendingIntent alarmIntent(Context context) {
        Intent i = new Intent(context, AlarmReceiver.class).setAction("com.timerlock.secure.EXPIRE");
        return PendingIntent.getBroadcast(context, 6011, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static synchronized void log(Context context, String type, String detail) {
        SharedPreferences lp = dp(context).getSharedPreferences(LOG_PREF, Context.MODE_PRIVATE);
        int count = lp.getInt("count", 0);
        int next = count % LOG_LIMIT;
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        String version = "?";
        try {
            version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        String metadata = "session=" + prefs(context).getString(K_ID, "-")
                + "; state=" + state(context)
                + "; device=" + Build.MANUFACTURER + "/" + Build.MODEL
                + "; sdk=" + Build.VERSION.SDK_INT
                + "; app=" + version;
        lp.edit().putString("e_" + next,
                        stamp + " | " + type + " | " + metadata + " | " + (detail == null ? "" : detail))
                .putInt("count", count + 1).apply();
    }

    public static synchronized String readLog(Context context) {
        SharedPreferences lp = dp(context).getSharedPreferences(LOG_PREF, Context.MODE_PRIVATE);
        int count = lp.getInt("count", 0);
        int size = Math.min(count, LOG_LIMIT);
        int start = Math.max(0, count - size);
        StringBuilder b = new StringBuilder();
        for (int absolute = start; absolute < count; absolute++) {
            String line = lp.getString("e_" + (absolute % LOG_LIMIT), null);
            if (line != null) b.append(line).append('\n');
        }
        return b.length() == 0 ? "No events recorded." : b.toString();
    }

    public static int bootCount(Context context) {
        try {
            return Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT);
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    private static long safeAdd(long a, long b) {
        if (b > 0 && a > Long.MAX_VALUE - b) return Long.MAX_VALUE;
        return a + b;
    }
}
