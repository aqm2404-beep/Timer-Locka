package com.timerlock.secure;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

/**
 * Minimal accessibility fallback used only for the Android global LOCK_SCREEN action.
 * TimerLock does not inspect, read, click, or modify content in other apps.
 */
public class TimerAccessibilityService extends AccessibilityService {
    private static volatile TimerAccessibilityService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        TimerStore.log(this, "ACCESSIBILITY_CONNECTED", "Global lock fallback ready");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Intentionally unused. TimerLock never reads other-app UI content.
    }

    @Override
    public void onInterrupt() {
        TimerStore.log(this, "ACCESSIBILITY_INTERRUPTED", "Accessibility lock fallback interrupted");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        if (instance == this) instance = null;
        TimerStore.log(this, "ACCESSIBILITY_DISCONNECTED", "Global lock fallback unavailable");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }

    public static boolean isEnabled(Context context) {
        try {
            AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (am == null || !am.isEnabled()) return false;
            List<AccessibilityServiceInfo> enabled =
                    am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
            ComponentName expected = new ComponentName(context, TimerAccessibilityService.class);
            for (AccessibilityServiceInfo info : enabled) {
                ResolveInfo ri = info.getResolveInfo();
                ServiceInfo si = ri == null ? null : ri.serviceInfo;
                if (si != null
                        && expected.getPackageName().equals(si.packageName)
                        && expected.getClassName().equals(si.name)) {
                    return true;
                }
                String id = info.getId();
                if (expected.flattenToString().equals(id)
                        || expected.flattenToShortString().equals(id)) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean requestScreenLock(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false;
        TimerAccessibilityService service = instance;
        if (service == null) {
            TimerStore.log(context, "ACCESSIBILITY_LOCK_UNAVAILABLE", "Service not connected");
            return false;
        }
        try {
            boolean accepted = service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
            TimerStore.log(context,
                    accepted ? "ACCESSIBILITY_LOCK_ACCEPTED" : "ACCESSIBILITY_LOCK_REJECTED",
                    "GLOBAL_ACTION_LOCK_SCREEN returned " + accepted);
            return accepted;
        } catch (Exception ex) {
            TimerStore.log(context, "ACCESSIBILITY_LOCK_FAILED", ex.getClass().getSimpleName());
            return false;
        }
    }
}
