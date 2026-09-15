package com.timerlock.secure;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Reschedules an active protected timer when Android grants exact-alarm access.
 * Android can stop the app and cancel future exact alarms when this access is revoked,
 * so TimerLock never silently falls back to an inexact protected expiry.
 */
public class ExactAlarmPermissionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        TimerStore.log(context, "EXACT_ALARM_PERMISSION_CHECKED",
                "permission-change broadcast; available=" + TimerStore.hasExactAlarmAccess(context));

        if (!TimerStore.hasExactAlarmAccess(context)) {
            TimerStore.log(context, "EXACT_ALARM_PERMISSION_MISSING", "Permission-change broadcast but access unavailable");
            return;
        }

        if (TimerStore.state(context) != TimerState.ACTIVE) return;

        long remaining = TimerStore.remainingMs(context);
        if (remaining == Long.MIN_VALUE) {
            TimerStore.setRecoveryRequired(context, "Clock integrity failure after exact-alarm permission change");
            return;
        }

        if (remaining <= 0L) {
            TimerStore.log(context, "EXPIRY_CONFIRMED", "Timer already expired when exact-alarm access became available");
            TimerStore.markExpired(context, "Expired before exact-alarm permission recovery");
            DeviceLockHelper.lockScreen(context);
            try { AlarmReceiver.notifyExpired(context); } catch (Exception ignored) {}
            return;
        }

        if (!TimerStore.schedule(context)) {
            TimerStore.log(context, "EXACT_ALARM_SCHEDULE_FAILED", "Unable to reschedule after permission change");
        }
    }
}
