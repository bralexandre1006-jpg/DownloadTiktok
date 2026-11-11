package be.rst.tiksave;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.view.View;
import android.view.animation.LinearInterpolator;

public class BubbleView extends View {
    public static final int IDLE = 0, BUSY = 1, OK = 2, ERR = 3;
    private static final int CYAN = 0xFF25F4EE, PINK = 0xFFFE2C55;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Bitmap logo;
    private final Rect src;
    private final RectF dst = new RectF();
    private float rot, r, stroke;
    private int state = IDLE;
    private ValueAnimator spin;
    private ObjectAnimator breathe;

    public BubbleView(Context c) {
        super(c);
        logo = BitmapFactory.decodeResource(getResources(), R.drawable.logo);
        int w = logo.getWidth();
        src = new Rect((int) (w * 0.2f), (int) (w * 0.2f), (int) (w * 0.8f), (int) (w * 0.8f));
        fill.setColor(0xF0000000);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.ROUND);
        setElevation(8 * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        float cx = w / 2f, cy = h / 2f;
        r = Math.min(w, h) * 0.40f;
        stroke = r * 0.14f;
        ring.setStrokeWidth(stroke);
        ring.setShader(new SweepGradient(cx, cy, new int[]{0xFFFFFFFF, 0xFF6E6E6E, 0xFFFFFFFF}, null));
        float in = r * 0.62f;
        dst.set(cx - in, cy - in, cx + in, cy + in);
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        c.drawCircle(cx, cy, r, fill);
        c.save();
        c.rotate(rot, cx, cy);
        if (state == BUSY) c.drawArc(cx - r, cy - r, cx + r, cy + r, 0, 260, false, ring);
        else c.drawCircle(cx, cy, r, ring);
        c.restore();
        c.drawBitmap(logo, src, dst, img);
    }

    public void setState(int s) {
        state = s;
        if (spin != null) spin.cancel();
        ring.setColorFilter(null);
        if (s == BUSY) {
            spin = ValueAnimator.ofFloat(0, 360);
            spin.setDuration(700);
            spin.setRepeatCount(ValueAnimator.INFINITE);
            spin.setInterpolator(new LinearInterpolator());
            spin.addUpdateListener(a -> { rot = (float) a.getAnimatedValue(); invalidate(); });
            spin.start();
        } else if (s == OK || s == ERR) {
            ring.setColorFilter(new android.graphics.PorterDuffColorFilter(
                    s == OK ? 0xFF2BD67B : PINK, android.graphics.PorterDuff.Mode.SRC_IN));
            if (s == OK) {
                if (breathe != null) breathe.pause();
                animate().scaleX(1.25f).scaleY(1.25f).setDuration(140)
                        .withEndAction(() -> animate().scaleX(1f).scaleY(1f).setDuration(220)
                                .withEndAction(() -> { if (breathe != null) breathe.resume(); }).start()).start();
            } else {
                ObjectAnimator.ofFloat(this, View.TRANSLATION_X, 0, 8, -8, 6, -6, 3, 0).setDuration(420).start();
            }
            postDelayed(() -> { if (state == s) setState(IDLE); }, 1400);
        }
        invalidate();
    }

    public void startBreathing() {
        breathe = ObjectAnimator.ofPropertyValuesHolder(this,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.07f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.07f));
        breathe.setDuration(1300);
        breathe.setRepeatMode(ValueAnimator.REVERSE);
        breathe.setRepeatCount(ValueAnimator.INFINITE);
        breathe.start();
    }

    public void stopAll() {
        if (breathe != null) breathe.cancel();
        if (spin != null) spin.cancel();
    }

    public void press(boolean down) {
        if (breathe != null) { if (down) breathe.pause(); else breathe.resume(); }
        animate().scaleX(down ? 0.85f : 1f).scaleY(down ? 0.85f : 1f).setDuration(120).start();
    }
}
