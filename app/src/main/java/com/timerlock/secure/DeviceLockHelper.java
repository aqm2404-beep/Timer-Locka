package com.timerlock.secure;

import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

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

    public static boolean lockScreen(Context context) {
        TimerStore.recordLockRequest(context);
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(context, AdminReceiver.class);
            if (dpm == null || !dpm.isAdminActive(admin)) {
                TimerStore.recordLockResult(context, "FAILED: Device Admin inactive");
                TimerStore.log(context, "LOCK_FAILED", "Device Admin is not active");
                return false;
            }
            if (!isDeviceSecure(context)) {
                TimerStore.recordLockResult(context, "FAILED: secure Android lock missing");
                TimerStore.log(context, "LOCK_FAILED", "Android secure lock is not configured");
                return false;
            }

            TimerStore.log(context, "LOCK_REQUESTED", "Calling DevicePolicyManager.lockNow()");
            dpm.lockNow();
            TimerStore.recordLockResult(context, "SUCCESS: lockNow returned without exception");
            TimerStore.log(context, "LOCK_SUCCESS", "DevicePolicyManager.lockNow() returned without exception");
            return true;
        } catch (SecurityException ex) {
            TimerStore.recordLockResult(context, "FAILED: SecurityException");
            TimerStore.log(context, "LOCK_FAILED", "SecurityException");
            return false;
        } catch (Exception ex) {
            TimerStore.recordLockResult(context, "FAILED: " + ex.getClass().getSimpleName());
            TimerStore.log(context, "LOCK_FAILED", ex.getClass().getSimpleName());
            return false;
        }
    }
}
