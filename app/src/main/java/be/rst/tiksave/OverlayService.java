package be.rst.tiksave;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
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

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable anchorTask = this::anchor;
    private WindowManager wm;
    private SharedPreferences sp;
    private BubbleView bubble;
    private WindowManager.LayoutParams lp;
    private ValueAnimator moveAnim;
    private boolean busy;
    private int size;

    interface Finder { AccessibilityNodeInfo find(); }
    interface Found { void on(AccessibilityNodeInfo n); }
    interface Match { boolean ok(AccessibilityNodeInfo n); }

    @Override
    protected void onServiceConnected() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        sp = getSharedPreferences("pos", MODE_PRIVATE);
        size = dp(46);
        Downloader.listener = ok -> {
            if (bubble != null) bubble.setState(ok ? BubbleView.OK : BubbleView.ERR);
            if (!ok && busy && android.os.Build.VERSION.SDK_INT >= 29) writeDump(this, "ECHEC");
            busy = false;
        };
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getPackageName() == null) return;
        String p = e.getPackageName().toString();
        int t = e.getEventType();
        if (t == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            if (TIKTOK.contains(p) && bubble != null) scheduleAnchor(450);
        } else {
            h.removeCallbacks(visTask);
            h.postDelayed(visTask, 200);
        }
    }

    private final Runnable visTask = this::updateVisibility;

    private void updateVisibility() {
        if (busy) return;
        String top = null;
        for (AccessibilityWindowInfo w : getWindows()) {
            if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION || !(w.isActive() || w.isFocused())) continue;
            AccessibilityNodeInfo r = w.getRoot();
            if (r != null && r.getPackageName() != null) { top = r.getPackageName().toString(); break; }
        }
        if (top == null || top.equals(getPackageName())) return;
        if (TIKTOK.contains(top)) { show(); scheduleAnchor(350); }
        else hide();
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        Downloader.listener = null;
        hide();
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------- Bulle ----------

    private void show() {
        if (bubble != null) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        bubble = new BubbleView(this);
        lp = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = sp.getInt("x", dm.widthPixels - size - dp(14));
        lp.y = sp.getInt("y", (int) (dm.heightPixels * 0.42f));
        bubble.setOnTouchListener(new Drag());
        wm.addView(bubble, lp);
        bubble.setScaleX(0f);
        bubble.setScaleY(0f);
        BubbleView b = bubble;
        b.animate().scaleX(1f).scaleY(1f).setInterpolator(new OvershootInterpolator(2.5f))
                .setDuration(380).withEndAction(b::startBreathing).start();
    }

    private void hide() {
        if (bubble == null) return;
        BubbleView b = bubble;
        bubble = null;
        if (moveAnim != null) moveAnim.cancel();
        b.stopAll();
        b.animate().scaleX(0f).scaleY(0f).setDuration(180)
                .withEndAction(() -> { try { wm.removeView(b); } catch (Exception ignored) {} }).start();
    }

    private void moveTo(int x, int y) {
        if (bubble == null) return;
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int tx = Math.max(0, Math.min(x, dm.widthPixels - size));
        int ty = Math.max(dp(24), Math.min(y, dm.heightPixels - size - dp(24)));
        if (moveAnim != null) moveAnim.cancel();
        int fx = lp.x, fy = lp.y;
        if (fx == tx && fy == ty) return;
        moveAnim = ValueAnimator.ofFloat(0f, 1f);
        moveAnim.setDuration(380);
        moveAnim.setInterpolator(new DecelerateInterpolator(2f));
        moveAnim.addUpdateListener(a -> {
            if (bubble == null) { a.cancel(); return; }
            float f = (float) a.getAnimatedValue();
            lp.x = (int) (fx + (tx - fx) * f);
            lp.y = (int) (fy + (ty - fy) * f);
            try { wm.updateViewLayout(bubble, lp); } catch (Exception ignored) {}
        });
        moveAnim.start();
    }

    private void scheduleAnchor(long delay) {
        h.removeCallbacks(anchorTask);
        h.postDelayed(anchorTask, delay);
    }

    private void anchor() {
        if (bubble == null || busy || sp.getBoolean("custom", false)) return;
        Point a = anchorPoint();
        if (a != null) moveTo(a.x, a.y);
    }

    private Point anchorPoint() {
        AccessibilityNodeInfo n = findAvatar();
        if (n == null) return null;
        Rect r = new Rect();
        n.getBoundsInScreen(r);
        return new Point(r.centerX() - size / 2, r.top - size - dp(6));
    }

    private class Drag implements android.view.View.OnTouchListener {
        float dx, dy; int sx, sy; boolean moved;

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouch(android.view.View v, MotionEvent ev) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (moveAnim != null) moveAnim.cancel();
                    dx = ev.getRawX(); dy = ev.getRawY(); sx = lp.x; sy = lp.y; moved = false;
                    bubble.press(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int mx = (int) (ev.getRawX() - dx), my = (int) (ev.getRawY() - dy);
                    if (Math.abs(mx) > dp(6) || Math.abs(my) > dp(6)) moved = true;
                    if (moved) { lp.x = sx + mx; lp.y = sy + my; wm.updateViewLayout(bubble, lp); }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    bubble.press(false);
                    if (!moved) { capture(); return true; }
                    Point a = anchorPoint();
                    if (a != null && Math.hypot(lp.x - a.x, lp.y - a.y) < dp(90)) {
                        sp.edit().putBoolean("custom", false).putInt("x", a.x).putInt("y", a.y).apply();
                        bubble.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        moveTo(a.x, a.y);
                    } else {
                        sp.edit().putBoolean("custom", true).putInt("x", lp.x).putInt("y", lp.y).apply();
                        moveTo(lp.x, lp.y);
                    }
                    return true;
            }
            return false;
        }
    }

    // ---------- Capture ----------

    private void capture() {
        if (busy || bubble == null) return;
        busy = true;
        bubble.setState(BubbleView.BUSY);
        h.postDelayed(() -> {
            if (busy) { busy = false; if (bubble != null) bubble.setState(BubbleView.IDLE); }
        }, 40000);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int W = dm.widthPixels, H = dm.heightPixels;
        java.util.LinkedHashSet<String> users = new java.util.LinkedHashSet<>();
        StringBuilder caption = new StringBuilder();
        StringBuilder dump = new StringBuilder();
        java.util.regex.Pattern U = java.util.regex.Pattern.compile("^@?([A-Za-z0-9_.]{2,24})$");
        java.util.regex.Pattern AT = java.util.regex.Pattern.compile("@([A-Za-z0-9_.]{2,24})");
        for (AccessibilityNodeInfo root : roots()) {
            walk(root, 0, (n, depth) -> {
                String t = n.getText() == null ? "" : n.getText().toString().trim();
                String d = n.getContentDescription() == null ? "" : n.getContentDescription().toString().trim();
                if (t.isEmpty() && d.isEmpty()) return;
                Rect b = new Rect(); n.getBoundsInScreen(b);
                for (int k = 0; k < Math.min(depth, 30); k++) dump.append("  ");
                dump.append(n.getClassName()).append(" | t=").append(t).append(" | d=").append(d)
                        .append(" | id=").append(n.getViewIdResourceName()).append(" | ").append(b.toShortString()).append('\n');
                boolean left = b.centerX() < W * 0.78f && b.centerY() > H * 0.45f;
                boolean right = b.centerX() > W * 0.7f;
                java.util.regex.Matcher am = AT.matcher(t + " " + d);
                while (am.find()) if (left || right) users.add(am.group(1));
                if (left) {
                    java.util.regex.Matcher um = U.matcher(t);
                    if (um.matches() && !t.matches("\\d+")) users.add(um.group(1));
                    if (t.length() > caption.length() && t.contains(" ")) { caption.setLength(0); caption.append(t); }
                }
                String dl = d.toLowerCase();
                if (right && (dl.contains("profil") || dl.contains("profile"))) {
                    for (String w : d.split("[\\s,]+")) {
                        String wl = w.toLowerCase();
                        if (U.matcher(w).matches() && !wl.equals("profile") && !wl.equals("profil")
                                && !wl.equals("photo") && !wl.equals("de") && !wl.equals("du")) users.add(w.replace("@", ""));
                    }
                }
            });
        }
        lastDump = "users=" + users + "\ncaption=" + caption + "\n\n" + dump;
        Downloader.fromScreen(this, new ArrayList<>(users), caption.toString());
    }

    static String lastDump = "";

    interface Visit { void on(AccessibilityNodeInfo n, int depth); }

    private void walk(AccessibilityNodeInfo n, int depth, Visit v) {
        if (n == null || depth > 80) return;
        if (n.isVisibleToUser()) v.on(n, depth);
        for (int i = 0; i < n.getChildCount(); i++) walk(n.getChild(i), depth + 1, v);
    }

    @android.annotation.TargetApi(29)
    static void writeDump(android.content.Context c, String reason) {
        try {
            android.content.ContentValues v = new android.content.ContentValues();
            v.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "TikSave_debug.txt");
            v.put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain");
            v.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
            android.net.Uri u = c.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u == null) return;
            try (java.io.OutputStream o = c.getContentResolver().openOutputStream(u)) {
                o.write((reason + "\n" + lastDump).getBytes("UTF-8"));
            }
        } catch (Exception ignored) {}
    }

    private void fail(String m) {
        busy = false;
        if (bubble != null) bubble.setState(BubbleView.ERR);
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
        return c == null ? "" : c.toString().toLowerCase().replaceAll("\\s+", " ");
    }

    private AccessibilityNodeInfo findFirst(Match m) {
        for (AccessibilityNodeInfo root : roots()) {
            AccessibilityNodeInfo n = search(root, m);
            if (n != null) return n;
        }
        return null;
    }

    private AccessibilityNodeInfo findAvatar() {
        int w = getResources().getDisplayMetrics().widthPixels;
        return findFirst(node -> {
            String d = low(node.getContentDescription());
            if (!(d.contains("profil") || d.contains("profile") || d.contains("avatar"))) return false;
            Rect b = new Rect(); node.getBoundsInScreen(b);
            return b.centerX() > w * 0.7f && b.width() >= dp(28) && b.width() <= dp(90)
                    && Math.abs(b.width() - b.height()) < dp(14);
        });
    }

    private AccessibilityNodeInfo findShare() {
        int half = getResources().getDisplayMetrics().widthPixels / 2;
        return findFirst(node -> {
            String d = low(node.getContentDescription());
            if (!(d.contains("partag") || d.contains("share"))) return false;
            Rect b = new Rect(); node.getBoundsInScreen(b);
            return b.centerX() > half && b.width() > 0;
        });
    }

    private AccessibilityNodeInfo findCopy() {
        return findFirst(node -> {
            String t = low(node.getText()) + "|" + low(node.getContentDescription());
            if (!(t.contains("copier le lien") || t.contains("copy link") || t.contains("copier lien"))) return false;
            Rect b = new Rect(); node.getBoundsInScreen(b);
            return b.width() > 0;
        });
    }

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
