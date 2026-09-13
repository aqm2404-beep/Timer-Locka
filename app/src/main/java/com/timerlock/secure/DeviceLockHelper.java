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
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            ComponentName admin = new ComponentName(context, AdminReceiver.class);
            if (dpm == null || !dpm.isAdminActive(admin)) {
                TimerStore.log(context, "SYSTEM_LOCK_UNAVAILABLE", "Device Admin is not active");
                return false;
            }
            if (!isDeviceSecure(context)) {
                TimerStore.log(context, "SYSTEM_LOCK_UNAVAILABLE", "Android secure lock is not configured");
                return false;
            }
            TimerStore.log(context, "SYSTEM_LOCK_REQUESTED", "Timer expired or administrator lock test");
            dpm.lockNow();
            return true;
        } catch (SecurityException ex) {
            TimerStore.log(context, "SYSTEM_LOCK_UNAVAILABLE", "SecurityException");
            return false;
        } catch (Exception ex) {
            TimerStore.log(context, "SYSTEM_LOCK_UNAVAILABLE", ex.getClass().getSimpleName());
            return false;
        }
    }
}
