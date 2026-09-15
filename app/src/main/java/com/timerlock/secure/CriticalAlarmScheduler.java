package com.timerlock.secure;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public final class CriticalAlarmScheduler {
    private static final int REQUEST_EXPIRE = 6011;
    private static final int REQUEST_SHOW = 6014;
    private static final String ACTION_EXPIRE = "com.timerlock.secure.EXPIRE";

    private CriticalAlarmScheduler() {}

    public static boolean schedule(Context context) {
        if (TimerStore.state(context) != TimerState.ACTIVE) return false;

        long remaining = TimerStore.remainingMs(context);
        long expiryWall = TimerStore.targetWallClock(context);
        if (remaining <= 0L || remaining == Long.MIN_VALUE || expiryWall <= 0L) {
            TimerStore.log(context, "ALARM_CLOCK_SCHEDULE_FAILED", "Invalid active expiry state");
            return false;
        }

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            TimerStore.log(context, "ALARM_CLOCK_SCHEDULE_FAILED", "AlarmManager unavailable");
            return false;
        }

        try {
            PendingIntent fire = expireIntent(context);
            PendingIntent show = PendingIntent.getActivity(
                    context,
                    REQUEST_SHOW,
                    new Intent(context, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Reuses the same PendingIntent identity as TimerStore's exact alarm.
            // AlarmManager replaces the weaker exact alarm with AlarmClock semantics,
            // while TimerStore.cancelAlarm() still cancels this alarm on reset/expiry.
            AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(expiryWall, show);
            am.setAlarmClock(info, fire);
            TimerStore.log(context, "ALARM_CLOCK_SCHEDULED",
                    "remaining=" + remaining + "ms; expiryWall=" + expiryWall);
            return true;
        } catch (SecurityException ex) {
            TimerStore.log(context, "ALARM_CLOCK_SCHEDULE_FAILED", "SecurityException");
            return false;
        } catch (Exception ex) {
            TimerStore.log(context, "ALARM_CLOCK_SCHEDULE_FAILED", ex.getClass().getSimpleName());
            return false;
        }
    }

    public static void cancel(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(expireIntent(context));
        } catch (Exception ignored) {}
    }

    private static PendingIntent expireIntent(Context context) {
        Intent i = new Intent(context, AlarmReceiver.class).setAction(ACTION_EXPIRE);
        return PendingIntent.getBroadcast(context, REQUEST_EXPIRE, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
