package be.rst.tiksave;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OverlayService extends AccessibilityService {
    private static final Set<String> TIKTOK = new HashSet<>(Arrays.asList(
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
            "com.zhiliaoapp.musically.go", "com.ss.android.ugc.aweme"));
    private static final int RED = 0xDDFE2C55, GREY = 0xDD777777;

    private final Handler h = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private TextView bubble;
    private WindowManager.LayoutParams lp;
    private GradientDrawable bg;
    private boolean busy;

    interface Finder { AccessibilityNodeInfo find(); }
    interface Found { void on(AccessibilityNodeInfo n); }

    @Override
    protected void onServiceConnected() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String p = e.getPackageName().toString();
        if (TIKTOK.contains(p)) show();
        else if (!busy && !ignored(p)) hide();
    }

    private boolean ignored(String p) {
        if (p.equals(getPackageName()) || p.equals("com.android.systemui")) return true;
        String ime = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return ime != null && ime.startsWith(p + "/");
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        hide();
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void show() {
        if (bubble != null) return;
        int s = dp(52);
        bubble = new TextView(this);
        bubble.setText("⬇");
        bubble.setTextSize(22);
        bubble.setTextColor(Color.WHITE);
        bubble.setGravity(Gravity.CENTER);
        bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(RED);
        bg.setStroke(dp(2), Color.WHITE);
        bubble.setBackground(bg);
        bubble.setElevation(dp(6));

        lp = new WindowManager.LayoutParams(s, s,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        SharedPreferences sp = getSharedPreferences("pos", MODE_PRIVATE);
        lp.x = sp.getInt("x", getResources().getDisplayMetrics().widthPixels - s - dp(10));
        lp.y = sp.getInt("y", dp(180));

        bubble.setOnTouchListener(new android.view.View.OnTouchListener() {
            float dx, dy; int sx, sy; boolean moved;
            @Override
            public boolean onTouch(android.view.View v, MotionEvent ev) {
                switch (ev.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = ev.getRawX(); dy = ev.getRawY(); sx = lp.x; sy = lp.y; moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int mx = (int) (ev.getRawX() - dx), my = (int) (ev.getRawY() - dy);
                        if (Math.abs(mx) > dp(6) || Math.abs(my) > dp(6)) moved = true;
                        if (moved) { lp.x = sx + mx; lp.y = sy + my; wm.updateViewLayout(bubble, lp); }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) getSharedPreferences("pos", MODE_PRIVATE).edit().putInt("x", lp.x).putInt("y", lp.y).apply();
                        else capture();
                        return true;
                }
                return false;
            }
        });
        wm.addView(bubble, lp);
    }

    private void hide() {
        if (bubble == null) return;
        try { wm.removeView(bubble); } catch (Exception ignored) {}
        bubble = null;
    }

    private void setBusy(boolean b) {
        busy = b;
        if (bg != null) bg.setColor(b ? GREY : RED);
    }

    private void capture() {
        if (busy) return;
        setBusy(true);
        h.postDelayed(() -> setBusy(false), 6000);
        waitFor(this::findShare, 8, share -> {
            tap(share);
            waitFor(this::findCopy, 12, copy -> {
                tap(copy);
                h.postDelayed(() -> {
                    Intent i = new Intent(this, ClipActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    startActivity(i);
                    h.postDelayed(() -> setBusy(false), 1500);
                }, 700);
            }, () -> fail("« Copier le lien » introuvable"));
        }, () -> fail("Bouton Partager introuvable"));
    }

    private void fail(String m) {
        setBusy(false);
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    private void waitFor(Finder f, int tries, Found ok, Runnable ko) {
        AccessibilityNodeInfo n = f.find();
        if (n != null) ok.on(n);
        else if (tries <= 0) ko.run();
        else h.postDelayed(() -> waitFor(f, tries - 1, ok, ko), 250);
    }

    private List<AccessibilityNodeInfo> roots() {
        List<AccessibilityNodeInfo> r = new ArrayList<>();
        for (AccessibilityWindowInfo w : getWindows()) {
            if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            AccessibilityNodeInfo n = w.getRoot();
            if (n != null && n.getPackageName() != null && TIKTOK.contains(n.getPackageName().toString())) r.add(n);
        }
        if (r.isEmpty() && getRootInActiveWindow() != null) r.add(getRootInActiveWindow());
        return r;
    }

    private static String low(CharSequence c) {
        return c == null ? "" : c.toString().toLowerCase();
    }

    private AccessibilityNodeInfo findShare() {
        int half = getResources().getDisplayMetrics().widthPixels / 2;
        for (AccessibilityNodeInfo root : roots()) {
            AccessibilityNodeInfo n = search(root, node -> {
                String d = low(node.getContentDescription());
                if (!(d.contains("partag") || d.contains("share"))) return false;
                Rect b = new Rect(); node.getBoundsInScreen(b);
                return b.centerX() > half && b.width() > 0;
            });
            if (n != null) return n;
        }
        return null;
    }

    private AccessibilityNodeInfo findCopy() {
        for (AccessibilityNodeInfo root : roots()) {
            AccessibilityNodeInfo n = search(root, node -> {
                String t = low(node.getText()) + "|" + low(node.getContentDescription());
                if (!(t.contains("copier le lien") || t.contains("copy link") || t.contains("copier lien"))) return false;
                Rect b = new Rect(); node.getBoundsInScreen(b);
                return b.width() > 0;
            });
            if (n != null) return n;
        }
        return null;
    }

    interface Match { boolean ok(AccessibilityNodeInfo n); }

    private AccessibilityNodeInfo search(AccessibilityNodeInfo n, Match m) {
        if (n == null) return null;
        if (n.isVisibleToUser() && m.ok(n)) return n;
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo r = search(n.getChild(i), m);
            if (r != null) return r;
        }
        return null;
    }

    private void tap(AccessibilityNodeInfo n) {
        Rect r = new Rect();
        n.getBoundsInScreen(r);
        Path p = new Path();
        p.moveTo(r.exactCenterX(), r.exactCenterY());
        dispatchGesture(new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(p, 0, 60)).build(), null, null);
    }
}
