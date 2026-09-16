package com.timerlock.secure;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Locale;

/**
 * Single TimerLock-owned overlay. Overlay permission is granted to TimerLock once;
 * there is deliberately no per-application allow list or installed-app scanning.
 */
public final class FloatingBubbleController {
    private static final String PREFS = "timerlock_overlay";
    private static final String KEY_X = "bubble_x";
    private static final String KEY_Y = "bubble_y";

    private final Context app;
    private final WindowManager windowManager;
    private final SharedPreferences prefs;
    private TextView bubble;
    private WindowManager.LayoutParams params;
    private boolean attached;

    public FloatingBubbleController(Context context) {
        app = context.getApplicationContext();
        windowManager = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean hasPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(app);
    }

    public void show(long remainingMs) {
        if (!hasPermission() || windowManager == null) {
            hide();
            return;
        }
        ensureView();
        update(remainingMs);
        if (!attached) {
            try {
                windowManager.addView(bubble, params);
                attached = true;
            } catch (Exception e) {
                TimerStore.log(app, "OVERLAY_ADD_FAILED", e.getClass().getSimpleName());
            }
        }
    }

    public void update(long remainingMs) {
        if (bubble != null) bubble.setText(formatAdaptive(remainingMs));
    }

    public void hide() {
        if (attached && windowManager != null && bubble != null) {
            try { windowManager.removeView(bubble); } catch (Exception ignored) {}
        }
        attached = false;
    }

    public void destroy() {
        hide();
        bubble = null;
        params = null;
    }

    private void ensureView() {
        if (bubble != null) return;

        int size = dp(70);
        bubble = new TextView(app);
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(13f);
        bubble.setGravity(Gravity.CENTER);
        bubble.setSingleLine(true);
        bubble.setElevation(dp(8));
        bubble.setPadding(dp(5), dp(5), dp(5), dp(5));

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(Color.rgb(24, 27, 33));
        background.setStroke(dp(2), Color.rgb(255, 70, 70));
        bubble.setBackground(background);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                size,
                size,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = prefs.getInt(KEY_X, dp(12));
        params.y = prefs.getInt(KEY_Y, dp(180));

        bubble.setOnTouchListener(new View.OnTouchListener() {
            private int startX;
            private int startY;
            private float downX;
            private float downY;
            private boolean moved;

            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = params.x;
                        startY = params.y;
                        downX = event.getRawX();
                        downY = event.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = Math.round(event.getRawX() - downX);
                        int dy = Math.round(event.getRawY() - downY);
                        if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) moved = true;
                        params.x = startX + dx;
                        params.y = startY + dy;
                        clampToScreen();
                        try { windowManager.updateViewLayout(bubble, params); } catch (Exception ignored) {}
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!moved && event.getActionMasked() == MotionEvent.ACTION_UP) {
                            openTimerLock();
                        } else {
                            snapToNearestEdge();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void clampToScreen() {
        Point p = screenSize();
        int margin = dp(8);
        int size = params.width;
        params.x = Math.max(margin, Math.min(params.x, Math.max(margin, p.x - size - margin)));
        params.y = Math.max(margin, Math.min(params.y, Math.max(margin, p.y - size - margin)));
    }

    private void snapToNearestEdge() {
        Point p = screenSize();
        int margin = dp(8);
        int size = params.width;
        int center = params.x + size / 2;
        params.x = center < p.x / 2 ? margin : Math.max(margin, p.x - size - margin);
        clampToScreen();
        prefs.edit().putInt(KEY_X, params.x).putInt(KEY_Y, params.y).apply();
        try { windowManager.updateViewLayout(bubble, params); } catch (Exception ignored) {}
    }

    @SuppressWarnings("deprecation")
    private Point screenSize() {
        Point p = new Point();
        windowManager.getDefaultDisplay().getSize(p);
        return p;
    }

    private void openTimerLock() {
        try {
            Intent intent = new Intent(app, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            app.startActivity(intent);
        } catch (Exception e) {
            TimerStore.log(app, "OVERLAY_OPEN_FAILED", e.getClass().getSimpleName());
        }
    }

    private int dp(int value) {
        return Math.round(value * app.getResources().getDisplayMetrics().density);
    }

    static String formatAdaptive(long ms) {
        long total = Math.max(0L, (ms + 999L) / 1000L);
        long h = total / 3600L;
        long m = (total % 3600L) / 60L;
        long s = total % 60L;
        if (h > 0) return String.format(Locale.US, "%dh %02dm", h, m);
        return String.format(Locale.US, "%02d:%02d", m, s);
    }
}
