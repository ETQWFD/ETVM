package com.et.vm;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingControlService extends Service {

    private WindowManager wm;
    private TextView bubble;
    private View panel;
    private boolean panelVisible = false;
    private WindowManager.LayoutParams bubbleLp;
    private WindowManager.LayoutParams panelLp;

    private static java.lang.ref.WeakReference<MainActivity> host = new java.lang.ref.WeakReference<>(null);

    public static void setHost(MainActivity a) {
        host = new java.lang.ref.WeakReference<>(a);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (bubble == null) initViews();
        return START_STICKY;
    }

    private void initViews() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        bubble = new TextView(this);
        bubble.setText("ET");
        bubble.setTextColor(0xFF0B0F1A);
        bubble.setTextSize(16);
        bubble.setGravity(Gravity.CENTER);
        bubble.setBackgroundColor(0xFF00E5FF);
        bubble.setPadding(0, 0, 0, 0);
        bubble.setAlpha(0.95f);

        bubbleLp = new WindowManager.LayoutParams(
                dp(46), dp(46),
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        bubbleLp.gravity = Gravity.TOP | Gravity.START;
        bubbleLp.x = dp(12);
        bubbleLp.y = dp(160);

        bubble.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy, lx, ly;
            boolean moved = false;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = e.getRawX(); sy = e.getRawY();
                        lx = bubbleLp.x; ly = bubbleLp.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - sx, dy = e.getRawY() - sy;
                        if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) moved = true;
                        bubbleLp.x = (int) (lx + dx);
                        bubbleLp.y = (int) (ly + dy);
                        try { wm.updateViewLayout(bubble, bubbleLp); } catch (Exception ignored) {}
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) togglePanel();
                        return true;
                }
                return false;
            }
        });

        try {
            wm.addView(bubble, bubbleLp);
        } catch (Exception e) {
            stopSelf();
            return;
        }

        // 面板
        panel = LayoutInflater.from(this).inflate(R.layout.float_panel, null);
        panelLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        panelLp.gravity = Gravity.TOP | Gravity.START;
        panelLp.x = dp(12);
        panelLp.y = dp(210);

        bindButton(R.id.fb_home, "home");
        bindButton(R.id.fb_back, "back");
        bindButton(R.id.fb_menu, "menu");
        bindButton(R.id.fb_volup, "volup");
        bindButton(R.id.fb_voldown, "voldown");
        panel.findViewById(R.id.fb_close).setOnClickListener(v -> stopSelf());
    }

    private void bindButton(int id, final String key) {
        Button b = panel.findViewById(id);
        b.setOnClickListener(v -> {
            MainActivity a = host.get();
            if (a != null) {
                a.dispatchFloatKey(key);
            } else {
                Toast.makeText(this, "虚拟机未运行", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void togglePanel() {
        panelVisible = !panelVisible;
        if (panelVisible) {
            try { wm.addView(panel, panelLp); } catch (Exception ignored) {}
        } else {
            try { wm.removeView(panel); } catch (Exception ignored) {}
        }
    }

    @Override
    public void onDestroy() {
        if (wm != null) {
            try { if (bubble != null) wm.removeView(bubble); } catch (Exception ignored) {}
            try { if (panel != null) wm.removeView(panel); } catch (Exception ignored) {}
        }
        bubble = null; panel = null;
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
