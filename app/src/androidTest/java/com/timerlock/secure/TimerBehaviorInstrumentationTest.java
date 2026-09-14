package com.timerlock.secure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TimerBehaviorInstrumentationTest {

    @Test
    public void anotherAppRemainsUsableUntilCountdownExpiresThenPhoneLocks() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();

        TimerStore.reset(context);
        TimerStore.start(context, 8_000L);
        assertEquals(TimerState.ACTIVE, TimerStore.state(context));

        Intent openTimer = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(openTimer);
        Thread.sleep(800L);

        Intent service = new Intent(context, TimerService.class);
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
        else context.startService(service);
        Thread.sleep(700L);

        Intent openSettings = new Intent(Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(openSettings);
        Thread.sleep(1500L);

        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        assertNotNull(keyguard);
        assertFalse("Using another app while TimerLock is ACTIVE must not lock the phone", keyguard.isKeyguardLocked());
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

        assertTrue("Phone should lock only when the countdown reaches zero", lockedAtExpiry);
        assertEquals(TimerState.EXPIRED, TimerStore.state(context));
    }
}
