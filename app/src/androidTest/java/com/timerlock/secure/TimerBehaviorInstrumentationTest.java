package com.timerlock.secure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TimerBehaviorInstrumentationTest {

    @Test
    public void alarmClockAndAccessibilityFallbackLockWhileAnotherAppIsForeground() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();

        TimerStore.reset(context);
        assertTrue("Exact alarm capability must exist without a QA appop override",
                TimerStore.hasExactAlarmAccess(context));
        assertTrue("Accessibility lock fallback must be enabled by the QA harness",
                TimerAccessibilityService.isEnabled(context));

        long serviceDeadline = System.currentTimeMillis() + 5_000L;
        boolean accessibilityConnected = false;
        while (System.currentTimeMillis() < serviceDeadline) {
            if (TimerStore.readLog(context).contains("ACCESSIBILITY_CONNECTED")) {
                accessibilityConnected = true;
                break;
            }
            Thread.sleep(100L);
        }
        assertTrue("Accessibility lock service must actually connect before timer test",
                accessibilityConnected);

        // Critical: do NOT start MainActivity or TimerService. This proves that the
        // exact alarm receiver can expire in the background and that the OEM fallback
        // can invoke Android's global lock while another app remains foreground.
        TimerStore.start(context, 8_000L);
        assertEquals(TimerState.ACTIVE, TimerStore.state(context));

        Intent openSettings = new Intent(Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(openSettings);
        Thread.sleep(1500L);

        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        assertNotNull(keyguard);
        assertFalse("Another app must remain usable before TimerLock expiry", keyguard.isKeyguardLocked());
        assertEquals(TimerState.ACTIVE, TimerStore.state(context));

        long deadline = System.currentTimeMillis() + 10_000L;
        boolean lockedAtExpiry = false;
        while (System.currentTimeMillis() < deadline) {
            if (keyguard.isKeyguardLocked()) {
                lockedAtExpiry = true;
                break;
            }
            Thread.sleep(200L);
        }

        // Authoritative result: Android's physical keyguard must be locked.
        assertTrue("Background expiry must physically lock Android", lockedAtExpiry);
        assertEquals(TimerState.EXPIRED, TimerStore.state(context));

        String log = TimerStore.readLog(context);
        assertTrue("High-priority alarm clock must be scheduled", log.contains("ALARM_CLOCK_SCHEDULED"));
        assertTrue("Independent elapsed-time backup must be scheduled", log.contains("ELAPSED_BACKUP_SCHEDULED"));
        assertTrue("AlarmReceiver must execute", log.contains("ALARM_RECEIVED"));
        assertTrue("Expiry must be confirmed in background", log.contains("EXPIRY_CONFIRMED"));
        assertTrue("Accessibility global lock must be requested", log.contains("LOCK_ACCESSIBILITY_REQUESTED"));
        assertTrue("Accessibility global lock action must be accepted", log.contains("ACCESSIBILITY_LOCK_ACCEPTED"));

        // Device Admin remains the independent second path in production code. A log
        // after the first successful global lock is best-effort because Android may
        // suspend app execution immediately when the keyguard takes over.
    }
}
