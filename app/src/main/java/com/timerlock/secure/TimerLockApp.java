package com.timerlock.secure;

import android.app.Application;

/**
 * Application entry point. TimerLock protects the countdown state, not foreground app usage.
 */
public class TimerLockApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        TimerStore.log(this, "PROCESS_RECREATED", "Application process created; exactAlarm=" + TimerStore.hasExactAlarmAccess(this));
    }
}
