package com.timerlock.secure;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        TimerStore.log(context, "SYSTEM_EVENT", action == null ? "" : action);

        if (Intent.ACTION_TIME_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            TimerStore.heartbeat(context);
            return;
        }

        if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_USER_UNLOCKED.equals(action)) {
            TimerStore.recoverAfterBoot(context, true);
            if (TimerStore.state(context) == TimerState.EXPIRED) {
                AlarmReceiver.notifyExpired(context);
            }
        }
    }
}
