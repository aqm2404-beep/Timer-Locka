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
            if (TimerStore.state(context) == TimerState.ACTIVE) TimerStore.schedule(context);
            return;
        }

        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            TimerStore.log(context, "PROCESS_RECREATED", "Package replaced; restoring active expiry alarm if needed");
            TimerStore.recoverAfterBoot(context, false);
            return;
        }

        if (Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_USER_UNLOCKED.equals(action)) {
            TimerStore.log(context, "BOOT_RECOVERY", "action=" + action);
            TimerStore.recoverAfterBoot(context, true);
            if (TimerStore.state(context) == TimerState.EXPIRED) {
                TimerStore.log(context, "EXPIRY_CONFIRMED", "Expired state restored during boot recovery");
                DeviceLockHelper.lockScreen(context);
                try { AlarmReceiver.notifyExpired(context); } catch (Exception ignored) {}
            }
        }
    }
}
