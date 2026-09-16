package com.timerlock.secure;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.Locale;

public class TimerService extends Service {
    private static final String CHANNEL = "timerlock_active";
    private static final int NOTIFICATION_ID = 6001;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastNotificationSecond = Long.MIN_VALUE;
    private FloatingBubbleController bubble;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (TimerStore.state(TimerService.this) != TimerState.ACTIVE) {
                hideBubble();
                stopSelf();
                return;
            }

            long remaining = TimerStore.remainingMs(TimerService.this);
            if (remaining == Long.MIN_VALUE) {
                hideBubble();
                TimerStore.setRecoveryRequired(TimerService.this, "Clock integrity failure in service");
                stopSelf();
                return;
            }
            if (remaining <= 0) {
                if (bubble != null) bubble.update(0L);
                TimerStore.log(TimerService.this, "EXPIRY_CONFIRMED", "Foreground watchdog reached zero");
                TimerStore.markExpired(TimerService.this, "Foreground watchdog reached zero");
                DeviceLockHelper.lockScreen(TimerService.this);
                try { AlarmReceiver.notifyExpired(TimerService.this); }
                catch (Exception ex) { TimerStore.log(TimerService.this, "EXPIRY_NOTIFICATION_FAILED", ex.getClass().getSimpleName()); }
                hideBubble();
                stopSelf();
                return;
            }

            syncBubble(remaining);

            long sec = remaining / 1000L;
            if (lastNotificationSecond == Long.MIN_VALUE || sec <= 60 || sec / 60 != lastNotificationSecond / 60) {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(NOTIFICATION_ID, notification(remaining));
                lastNotificationSecond = sec;
            }
            if (sec % 60 == 0) TimerStore.heartbeat(TimerService.this);
            handler.postDelayed(this, 1000L);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        bubble = new FloatingBubbleController(this);
        TimerStore.log(this, "FOREGROUND_SERVICE_CREATED", "TimerService process created");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        TimerStore.log(this, "FOREGROUND_SERVICE_STARTED", "startId=" + startId);
        if (TimerStore.state(this) != TimerState.ACTIVE) {
            hideBubble();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!CriticalAlarmScheduler.schedule(this)) {
            TimerStore.log(this, "ALARM_CLOCK_FALLBACK", "Keeping normal exact alarm + foreground watchdog");
        }

        long remaining = TimerStore.remainingMs(this);
        if (remaining <= 0 || remaining == Long.MIN_VALUE) {
            hideBubble();
            if (remaining == Long.MIN_VALUE) {
                TimerStore.setRecoveryRequired(this, "Service start integrity failure");
            } else {
                TimerStore.log(this, "EXPIRY_CONFIRMED", "Service start found expired timer");
                TimerStore.markExpired(this, "Service start found expired timer");
                DeviceLockHelper.lockScreen(this);
                try { AlarmReceiver.notifyExpired(this); }
                catch (Exception ex) { TimerStore.log(this, "EXPIRY_NOTIFICATION_FAILED", ex.getClass().getSimpleName()); }
            }
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, notification(remaining));
        syncBubble(remaining);
        handler.removeCallbacks(tick);
        handler.post(tick);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(tick);
        if (bubble != null) bubble.destroy();
        TimerStore.log(this, "FOREGROUND_SERVICE_DESTROYED", "TimerService destroyed");
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void syncBubble(long remaining) {
        if (bubble == null) return;
        if (TimerLockApp.isUiForeground()) {
            bubble.hide();
            return;
        }
        if (bubble.hasPermission()) {
            bubble.show(remaining);
        } else {
            bubble.hide();
            TimerStore.log(this, "OVERLAY_PERMISSION_MISSING", "Timer remains active; only TimerLock overlay permission is required");
        }
    }

    private void hideBubble() {
        if (bubble != null) bubble.hide();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "Active timer",
                    NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Persistent TimerLock countdown status");
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(c);
        }
    }

    private Notification notification(long remaining) {
        Intent open = new Intent(this, SetupGateActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 6001, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("TimerLock Active")
                .setContentText("Remaining: " + format(remaining))
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private static String format(long ms) {
        long total = Math.max(0L, ms / 1000L);
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }
}
