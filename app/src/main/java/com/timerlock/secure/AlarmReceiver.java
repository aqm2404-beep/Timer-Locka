package com.timerlock.secure;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class AlarmReceiver extends BroadcastReceiver {
    static final String CHANNEL_EXPIRED = "timerlock_expired";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : String.valueOf(intent.getAction());
        TimerStore.recordAlarmReceived(context, "action=" + action);

        if (TimerStore.state(context) != TimerState.ACTIVE) {
            TimerStore.log(context, "ALARM_IGNORED", "Timer state is not ACTIVE");
            return;
        }

        long remaining = TimerStore.remainingMs(context);
        if (remaining == Long.MIN_VALUE) {
            TimerStore.setRecoveryRequired(context, "Clock integrity failure at alarm");
            return;
        }

        if (remaining > 1000L) {
            TimerStore.log(context, "ALARM_EARLY", "remaining=" + remaining + "ms; rescheduling exact alarm");
            if (!TimerStore.schedule(context)) {
                TimerStore.log(context, "EXACT_ALARM_SCHEDULE_FAILED", "Unable to reschedule early alarm");
            }
            return;
        }

        TimerStore.log(context, "EXPIRY_CONFIRMED", "Scheduled exact alarm reached protected expiry");
        TimerStore.markExpired(context, "Scheduled exact expiry reached");

        // Lock is the primary action. Non-essential notification work must never block it.
        boolean locked = DeviceLockHelper.lockScreen(context);
        if (!locked) {
            TimerStore.log(context, "LOCK_FAILED", "AlarmReceiver lock request was not accepted");
        }

        try {
            notifyExpired(context);
        } catch (Exception ex) {
            TimerStore.log(context, "EXPIRY_NOTIFICATION_FAILED", ex.getClass().getSimpleName());
        }
    }

    public static void notifyExpired(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL_EXPIRED, "Timer expired",
                    NotificationManager.IMPORTANCE_HIGH);
            c.setDescription("Urgent TimerLock expiry alerts");
            c.enableVibration(true);
            c.setVibrationPattern(new long[]{0, 700, 250, 700, 250, 1000});
            c.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
            nm.createNotificationChannel(c);
        }
        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, 7001, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification n = new android.app.Notification.Builder(context, CHANNEL_EXPIRED)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("TimerLock — TIME EXPIRED")
                .setContentText("Administrator authentication is required.")
                .setContentIntent(content)
                .setAutoCancel(false)
                .setOngoing(true)
                .build();
        nm.notify(7001, n);

        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 250, 500, 250, 900}, -1));
            } else {
                v.vibrate(1200);
            }
        }
    }
}
