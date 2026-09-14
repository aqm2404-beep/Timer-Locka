package com.timerlock.secure;

import android.app.Application;

/**
 * Application entry point.
 *
 * TimerLock protects the countdown state, not foreground app usage. Users may
 * leave TimerLock and use other apps while an active countdown continues in
 * the background. Countdown expiry is handled by the existing timer service
 * and scheduled alarm path.
 */
public class TimerLockApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
    }
}
