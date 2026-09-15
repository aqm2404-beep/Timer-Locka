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
                .setMessage("TimerLock is a timer app and requests Android exact-alarm capability for protected expiry. This device is not currently exposing that capability.\n\nOpen TimerLock app info and confirm the system has not restricted alarms or background operation.")
                .setCancelable(false)
                .setNegativeButton("Exit", (d, w) -> {
                    dialogVisible = false;
                    finish();
                })
                .setPositiveButton("Open App Info", (d, w) -> {
                    dialogVisible = false;
                    requestAppInfo();
                })
                .setOnDismissListener(d -> dialogVisible = false)
                .show();
    }

    private void showAccessibilityDialog() {
        if (dialogVisible) return;
        dialogVisible = true;
        new AlertDialog.Builder(this)
                .setTitle("Xiaomi Lock Fallback Required")
                .setMessage("For Xiaomi / Redmi / POCO, TimerLock uses a second Android lock path so expiry can still lock the phone while another app is open.\n\n1. Enable ‘TimerLock Screen Lock’ in Accessibility.\n\n2. If Android says ‘Restricted setting’ or the switch is greyed out: open App info → tap the three-dot menu → Allow restricted settings, authenticate, then return to Accessibility.\n\nTimerLock does not read, inspect, click, type, or collect content from other apps.")
                .setCancelable(false)
                .setNegativeButton("Exit", (d, w) -> {
                    dialogVisible = false;
                    finish();
                })
                .setNeutralButton("App Info", (d, w) -> {
                    dialogVisible = false;
                    requestAppInfo();
                })
                .setPositiveButton("Accessibility", (d, w) -> {
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

    private void requestAccessibilityAccess() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception e) {
            requestAppInfo();
        }
    }

    private void requestAppInfo() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {}
    }
}
