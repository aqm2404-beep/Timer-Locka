package com.timerlock.secure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.ParcelFileDescriptor;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TimerBehaviorInstrumentationTest {

    @Test
    public void homeDoesNotLockBeforeExpiryButExpiryLocks() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();

        TimerStore.reset(context);
        TimerStore.start(context, 6_000L);
        assertEquals(TimerState.ACTIVE, TimerStore.state(context));

        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(open);
        Thread.sleep(1000L);

        try (ParcelFileDescriptor ignored = instrumentation.getUiAutomation()
                .executeShellCommand("input keyevent KEYCODE_HOME")) {
            // Home dispatch is asynchronous.
        }

        Thread.sleep(1500L);
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        assertNotNull(keyguard);
        assertFalse("Leaving TimerLock while ACTIVE must not lock the phone", keyguard.isKeyguardLocked());
        assertEquals(TimerState.ACTIVE, TimerStore.state(context));

        long deadline = System.currentTimeMillis() + 8000L;
        boolean lockedAtExpiry = false;
        while (System.currentTimeMillis() < deadline) {
            if (keyguard.isKeyguardLocked()) {
                lockedAtExpiry = true;
                break;
            }
            Thread.sleep(200L);
        }

        assertTrue("Phone should lock when the countdown reaches zero", lockedAtExpiry);
        assertEquals(TimerState.EXPIRED, TimerStore.state(context));
    }
}
