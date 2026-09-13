package com.timerlock.secure;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordManager {
    private static final String PREF = "timerlock_admin";
    private static final String KEY_SALT = "salt";
    private static final String KEY_HASH = "hash";
    private static final String KEY_FAILS = "fails";
    private static final String KEY_LOCK_UNTIL = "lock_until";
    private static final int ITERATIONS = 160_000;
    private static final int KEY_BITS = 256;

    private PasswordManager() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static boolean hasPassword(Context context) {
        SharedPreferences p = prefs(context);
        return p.contains(KEY_SALT) && p.contains(KEY_HASH);
    }

    public static boolean setPassword(Context context, char[] password) {
        if (password == null || password.length < 6) return false;
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            byte[] hash = derive(password, salt);
            prefs(context).edit()
                    .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                    .putInt(KEY_FAILS, 0)
                    .putLong(KEY_LOCK_UNTIL, 0L)
                    .apply();
            Arrays.fill(hash, (byte) 0);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public static long lockoutRemainingMs(Context context) {
        return Math.max(0L, prefs(context).getLong(KEY_LOCK_UNTIL, 0L) - System.currentTimeMillis());
    }

    public static boolean verify(Context context, char[] password) {
        SharedPreferences p = prefs(context);
        if (lockoutRemainingMs(context) > 0) {
            Arrays.fill(password, '\0');
            return false;
        }
        try {
            String saltB64 = p.getString(KEY_SALT, null);
            String hashB64 = p.getString(KEY_HASH, null);
            if (saltB64 == null || hashB64 == null) return false;
            byte[] salt = Base64.decode(saltB64, Base64.NO_WRAP);
            byte[] expected = Base64.decode(hashB64, Base64.NO_WRAP);
            byte[] actual = derive(password, salt);
            boolean ok = MessageDigest.isEqual(expected, actual);
            Arrays.fill(actual, (byte) 0);
            if (ok) {
                p.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, 0L).apply();
                return true;
            }
            int fails = p.getInt(KEY_FAILS, 0) + 1;
            long delay = 0L;
            if (fails >= 15) delay = 5 * 60_000L;
            else if (fails >= 10) delay = 60_000L;
            else if (fails >= 5) delay = 30_000L;
            p.edit().putInt(KEY_FAILS, fails)
                    .putLong(KEY_LOCK_UNTIL, delay == 0 ? 0L : System.currentTimeMillis() + delay)
                    .apply();
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private static byte[] derive(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }
}
