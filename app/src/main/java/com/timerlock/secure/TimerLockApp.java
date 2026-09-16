package com.timerlock.secure;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/**
 * Application entry point. Tracks whether TimerLock's own UI is visible so the
 * floating bubble can stay out of the way while MainActivity/SetupGate is open.
 */
public class TimerLockApp extends Application {
    private static volatile int startedActivities = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {}
            @Override public void onActivityStarted(Activity activity) { startedActivities++; }
            @Override public void onActivityResumed(Activity activity) {}
            @Override public void onActivityPaused(Activity activity) {}
            @Override public void onActivityStopped(Activity activity) {
                startedActivities = Math.max(0, startedActivities - 1);
            }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
            @Override public void onActivityDestroyed(Activity activity) {}
        });
        TimerStore.log(this, "PROCESS_RECREATED",
                "Application process created; exactAlarm=" + TimerStore.hasExactAlarmAccess(this));
    }

    public static boolean isUiForeground() {
        return startedActivities > 0;
    }
}
