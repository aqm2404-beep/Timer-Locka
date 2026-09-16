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
 * One-time capability gate. Overlay permission belongs to TimerLock itself.
 * TimerLock never requests overlay permission for WhatsApp, Chrome, YouTube,
 * or any other installed application.
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
        if (!hasOverlayAccess()) {
            showOverlayAccessDialog();
            return;
        }
        if (!hasExactAlarmAccess()) {
            showExactAlarmDialog();
            return;
        }
        openMain();
    }

    private boolean hasOverlayAccess() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void showOverlayAccessDialog() {
        if (dialogVisible) return;
        dialogVisible = true;
        new AlertDialog.Builder(this)
                .setTitle("Floating Timer Permission")
                .setMessage("TimerLock needs one Android permission to display its countdown bubble above other apps.\n\nOnly TimerLock needs this permission. Do not enable overlay permission for WhatsApp, Chrome, YouTube, or any other app.\n\nTap Continue, enable ‘Allow display over other apps’ for TimerLock, then return.")
                .setCancelable(false)
                .setNegativeButton("Exit", (d, w) -> {
                    dialogVisible = false;
                    finish();
                })
                .setPositiveButton("Continue", (d, w) -> {
                    dialogVisible = false;
                    requestOverlayAccess();
                })
                .setOnDismissListener(d -> dialogVisible = false)
                .show();
    }

    private void requestOverlayAccess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            checkAccessAndContinue();
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (Exception ignored) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        }
    }

    private void showExactAlarmDialog() {
        if (dialogVisible) return;
        dialogVisible = true;
        new AlertDialog.Builder(this)
                .setTitle("Precise Timer Access Required")
                .setMessage("TimerLock needs Alarms & reminders access so expiry can be checked at the selected time while the phone is idle or another app is open.")
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

    private void openMain() {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(open);
        finish();
    }
}
