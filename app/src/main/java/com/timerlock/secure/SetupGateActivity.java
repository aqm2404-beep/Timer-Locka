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
 * Ensures Android exact-alarm access is available before TimerLock is used.
 * TimerLock's core purpose requires expiry to happen at the user-selected time,
 * including while another app is in the foreground or the device is idle.
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
        if (hasExactAlarmAccess()) {
            Intent open = new Intent(this, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(open);
            finish();
            return;
        }

        if (dialogVisible) return;
        dialogVisible = true;
        new AlertDialog.Builder(this)
                .setTitle("Precise Timer Access Required")
                .setMessage("TimerLock must be allowed to schedule exact alarms so the phone can lock at exactly 00:00 even when another app is open or the device is idle.\n\nTap Continue, then enable Alarms & reminders for TimerLock.")
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
}
