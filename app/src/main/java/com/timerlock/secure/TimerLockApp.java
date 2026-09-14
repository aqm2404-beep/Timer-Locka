package com.timerlock.secure;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/**
 * App-level guard that protects an active TimerLock session when the user
 * leaves the TimerLock UI for Home, Recents, or another application.
 *
 * Background expiry is still handled independently by TimerService and
 * AlarmReceiver. This guard adds immediate screen locking when the protected
 * UI itself leaves the foreground during an active/expired/recovery state.
 */
public class TimerLockApp extends Application implements Application.ActivityLifecycleCallbacks {

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityStopped(Activity activity) {
        if (!(activity instanceof MainActivity)) return;

        // A deliberate, authenticated Admin > Exit calls finishAndRemoveTask().
        // Do not re-lock the device in that explicit administrator path.
        if (activity.isFinishing() || activity.isChangingConfigurations()) return;

        TimerState state = TimerStore.state(activity);
        if (state == TimerState.ACTIVE
                || state == TimerState.EXPIRED
                || state == TimerState.RECOVERY_REQUIRED) {
            TimerStore.log(activity,
                    "PROTECTED_UI_LEFT",
                    "TimerLock left foreground while protected; requesting system lock");
            DeviceLockHelper.lockScreen(activity);
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityResumed(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
