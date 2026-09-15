package com.timerlock.secure;

import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

import java.util.Locale;

public final class DeviceLockHelper {
    private DeviceLockHelper() {}

    public static boolean isAdminActive(Context context) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(context, AdminReceiver.class);
            return dpm != null && dpm.isAdminActive(admin);
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean isDeviceSecure(Context context) {
        try {
            KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            if (km == null) return false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) return km.isDeviceSecure();
            return km.isKeyguardSecure();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static boolean requiresAccessibilityFallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false;
        String manufacturer = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase(Locale.US);
        String brand = Build.BRAND == null ? "" : Build.BRAND.toLowerCase(Locale.US);
        return manufacturer.contains("xiaomi")
                || brand.contains("xiaomi")
                || brand.contains("redmi")
                || brand.contains("poco");
    }

    /**
     * Requests the lock through two independent Android mechanisms where available.
     * Device Admin remains the standard path. Accessibility GLOBAL_ACTION_LOCK_SCREEN
     * is also fired when enabled so OEM firmware cannot silently accept lockNow()
     * without actually presenting the lock screen.
     */
    public static boolean lockScreen(Context context) {
        TimerStore.recordLockRequest(context);

        boolean dpmAccepted = false;
        boolean accessibilityAccepted = false;
        String dpmResult = "not attempted";

        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(context, AdminReceiver.class);
            if (dpm == null || !dpm.isAdminActive(admin)) {
                dpmResult = "Device Admin inactive";
                TimerStore.log(context, "LOCK_DPM_FAILED", dpmResult);
            } else if (!isDeviceSecure(context)) {
                dpmResult = "secure Android lock missing";
                TimerStore.log(context, "LOCK_DPM_FAILED", dpmResult);
            } else {
                TimerStore.log(context, "LOCK_DPM_REQUESTED", "Calling DevicePolicyManager.lockNow()");
                dpm.lockNow();
                dpmAccepted = true;
                dpmResult = "lockNow returned without exception";
                TimerStore.log(context, "LOCK_DPM_ACCEPTED", dpmResult);
            }
        } catch (SecurityException ex) {
            dpmResult = "SecurityException";
            TimerStore.log(context, "LOCK_DPM_FAILED", dpmResult);
        } catch (Exception ex) {
            dpmResult = ex.getClass().getSimpleName();
            TimerStore.log(context, "LOCK_DPM_FAILED", dpmResult);
        }

        // Fire an independent global system lock when the user enabled the service.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && TimerAccessibilityService.isEnabled(context)) {
            accessibilityAccepted = TimerAccessibilityService.requestScreenLock(context);
        } else if (requiresAccessibilityFallback()) {
            TimerStore.log(context, "ACCESSIBILITY_LOCK_UNAVAILABLE",
                    "OEM fallback required but accessibility service is not enabled");
        }

        boolean accepted = dpmAccepted || accessibilityAccepted;
        String result = "DPM=" + dpmAccepted + " (" + dpmResult + ")"
                + "; Accessibility=" + accessibilityAccepted;
        TimerStore.recordLockResult(context, (accepted ? "SUCCESS: " : "FAILED: ") + result);
        TimerStore.log(context, accepted ? "LOCK_ACCEPTED" : "LOCK_FAILED", result);
        return accepted;
    }
}
