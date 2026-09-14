package com.timerlock.secure;

import static org.junit.Assert.assertEquals;
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
public class BackgroundLockInstrumentedTest {

    @Test
    public void activeTimerPressHomeLocksDevice() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context context = instrumentation.getTargetContext();

        TimerStore.reset(context);
        TimerStore.start(context, 60_000L);
        assertEquals(TimerState.ACTIVE, TimerStore.state(context));

        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(open);
        Thread.sleep(1500L);

        try (ParcelFileDescriptor ignored = instrumentation.getUiAutomation()
                .executeShellCommand("input keyevent KEYCODE_HOME")) {
            // Command dispatch is asynchronous; polling below waits for the lifecycle guard.
        }

        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        long deadline = System.currentTimeMillis() + 5000L;
        boolean locked = false;
        while (System.currentTimeMillis() < deadline) {
            if (keyguard != null && keyguard.isKeyguardLocked()) {
                locked = true;
                break;
            }
            Thread.sleep(200L);
        }

        assertTrue("Device should lock after TimerLock leaves foreground while ACTIVE", locked);
    }
}
