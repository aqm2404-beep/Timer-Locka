package com.timerlock.secure;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

/**
 * Ensures TimerLock has the Android capabilities required for reliable expiry.
 * Xiaomi / Redmi / POCO devices additionally require the lock-only Accessibility
 * fallback because some OEM builds can accept DevicePolicyManager.lockNow()
 * without reliably presenting the lock screen from a background receiver.
 */
public class SetupGateActivity extends Activity {
    private boolean dialogVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkAccessAndContinue();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!dialogVisible) checkAccessAndContinue();
    }

    private void checkAccessAndContinue() {
        if (!hasExactAlarmAccess()) {
            showExactAlarmDialog();
            return;
        }

        if (DeviceLockHelper.requiresAccessibilityFallback()
                && !TimerAccessibilityService.isEnabled(this)) {
            showAccessibilityDialog();
            return;
        }

        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(open);
        finish();
    }

    private void showExactAlarmDialog() {
        if (dialogVisible) return;
        dialogVisible = true;
        new AlertDialog.Builder(this)
                .setTitle("Precise Timer Access Required")
                .setMessage("TimerLock must be allowed to schedule exact alarms so the protected expiry can fire while another app is open or the phone is idle.\n\nTap Continue, then enable Alarms & reminders for TimerLock.")
                .setCancelable(false)
                .setNegativeButton("Exit", (d, w) -> {
                    dialogVisible = false;
                    finish();
                })
                .setPositiveButton("Continue", (d, w) -> {
                    dialogVisible = false;
                    requestExactAlarmAccess();
                })
                .setOnDismissListener(d -> dialogVisible = false)
                .show();
    }

    private void showAccessibilityDialog() {
        if (dialogVisible) return;
        dialogVisible = true;
        new AlertDialog.Builder(this)
                .setTitle("Xiaomi Lock Fallback Required")
                .setMessage("This Xiaomi / Redmi / POCO device needs TimerLock's second lock path for reliable background expiry.\n\nIn Accessibility settings, enable ‘TimerLock Screen Lock’. TimerLock uses this service only for Android's global Lock Screen action. It does not read or control content in other apps.")
                .setCancelable(false)
                .setNegativeButton("Exit", (d, w) -> {
                    dialogVisible = false;
                    finish();
                })
                .setPositiveButton("Open Accessibility", (d, w) -> {
                    dialogVisible = false;
                    requestAccessibilityAccess();
                })
                .setOnDismissListener(d -> dialogVisible = false)
                .show();
    }

    private boolean hasExactAlarmAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    private void requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            checkAccessAndContinue();
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(fallback);
        }
    }

    private void requestAccessibilityAccess() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(fallback);
        }
    }
}
