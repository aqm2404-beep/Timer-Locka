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
    public void alarmClockReceiverLocksWhileAnotherAppIsForegroundWithoutTimerService() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();

        TimerStore.reset(context);
        assertTrue("Exact alarm access must be granted by the QA harness", TimerStore.hasExactAlarmAccess(context));

        // Critical: do NOT start MainActivity or TimerService. This test proves
        // AlarmManager -> AlarmReceiver -> physical Android keyguard independently
        // of the UI and foreground watchdog service.
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

        // This is the strongest assertion: Android's real keyguard must actually be locked.
        assertTrue("AlarmReceiver must physically lock Android without reopening TimerLock", lockedAtExpiry);
        assertEquals(TimerState.EXPIRED, TimerStore.state(context));

        String log = TimerStore.readLog(context);
        assertTrue("QA must prove the high-priority alarm clock was scheduled", log.contains("ALARM_CLOCK_SCHEDULED"));
        assertTrue("QA must prove the independent elapsed-time backup was scheduled", log.contains("ELAPSED_BACKUP_SCHEDULED"));
        assertTrue("QA must prove AlarmReceiver actually executed", log.contains("ALARM_RECEIVED"));
        assertTrue("QA must prove expiry was confirmed in background", log.contains("EXPIRY_CONFIRMED"));
        assertTrue("QA must prove Device Admin lock was requested", log.contains("LOCK_DPM_REQUESTED"));
        assertTrue("QA must prove Device Admin returned from lockNow without exception", log.contains("LOCK_DPM_ACCEPTED"));

        // Do not require a log write after lockNow(): an immediate successful device lock can
        // suspend execution before that later diagnostic write becomes observable. The physical
        // keyguard assertion above is the authoritative success criterion.
    }
}
