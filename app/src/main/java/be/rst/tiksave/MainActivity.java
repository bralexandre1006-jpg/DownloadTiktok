package be.rst.tiksave;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextView statusTitle, badge1, badge2, total, today;
    private View statusDot, statusPulse;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(R.layout.activity_main);

        statusTitle = findViewById(R.id.statusTitle);
        statusDot = findViewById(R.id.statusDot);
        statusPulse = findViewById(R.id.statusPulse);
        badge1 = findViewById(R.id.badge1);
        badge2 = findViewById(R.id.badge2);
        total = findViewById(R.id.total);
        today = findViewById(R.id.today);

        click(R.id.step1, () -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        click(R.id.step2, () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        click(R.id.openTikTok, this::openTikTok);
        click(R.id.pasteBtn, this::paste);
        click(R.id.pasteCard, this::paste);
        click(R.id.resetBubble, () -> {
            getSharedPreferences("pos", MODE_PRIVATE).edit().putBoolean("custom", false).apply();
            Toast.makeText(this, "La bulle se recalera sur la photo de profil", Toast.LENGTH_SHORT).show();
        });

        try {
            ((TextView) findViewById(R.id.version)).setText("TikSave v" +
                    getPackageManager().getPackageInfo(getPackageName(), 0).versionName + " · R.S.T SRL");
        } catch (Exception ignored) {}

        ObjectAnimator pulse = ObjectAnimator.ofPropertyValuesHolder(statusPulse,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 2.6f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 2.6f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.6f, 0f));
        pulse.setDuration(1400);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.start();

        if (b == null) playSplash();
        else findViewById(R.id.splash).setVisibility(View.GONE);
    }

    private void playSplash() {
        View splash = findViewById(R.id.splash), logo = findViewById(R.id.splashLogo),
                glow = findViewById(R.id.splashGlow), title = findViewById(R.id.splashTitle),
                bar = findViewById(R.id.bottomBar);
        ViewGroup content = findViewById(R.id.content);
        float d = getResources().getDisplayMetrics().density;

        for (int i = 0; i < content.getChildCount(); i++) {
            View c = content.getChildAt(i);
            c.setAlpha(0f);
            c.setTranslationY(50 * d);
        }
        bar.setTranslationY(140 * d);

        logo.setScaleX(0.2f); logo.setScaleY(0.2f); logo.setAlpha(0f); logo.setRotation(-135f);
        logo.animate().scaleX(1f).scaleY(1f).alpha(1f).rotation(0f)
                .setInterpolator(new OvershootInterpolator(1.4f)).setDuration(750).start();

        glow.setScaleX(0.3f); glow.setScaleY(0.3f); glow.setAlpha(0f);
        glow.animate().scaleX(1.2f).scaleY(1.2f).alpha(1f).setStartDelay(250).setDuration(800).start();

        title.setAlpha(0f); title.setTranslationY(24 * d);
        title.animate().alpha(1f).translationY(0).setStartDelay(450).setDuration(450)
                .setInterpolator(new DecelerateInterpolator()).start();

        splash.postDelayed(() -> {
            logo.animate().scaleX(0.6f).scaleY(0.6f).alpha(0f).setDuration(300).start();
            title.animate().alpha(0f).translationY(-12 * d).setStartDelay(0).setDuration(250).start();
            splash.animate().alpha(0f).setStartDelay(120).setDuration(380)
                    .withEndAction(() -> splash.setVisibility(View.GONE)).start();
            for (int i = 0; i < content.getChildCount(); i++) {
                content.getChildAt(i).animate().alpha(1f).translationY(0)
                        .setStartDelay(200 + i * 55L).setDuration(520)
                        .setInterpolator(new DecelerateInterpolator(2.2f)).start();
            }
            bar.animate().translationY(0).setStartDelay(450).setDuration(600)
                    .setInterpolator(new OvershootInterpolator(1.1f)).start();
        }, 1500);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void click(int id, Runnable r) {
        View v = findViewById(id);
        v.setOnTouchListener((view, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN)
                view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start();
            else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL)
                view.animate().scaleX(1f).scaleY(1f).setInterpolator(new OvershootInterpolator(3f)).setDuration(250).start();
            return false;
        });
        v.setOnClickListener(x -> r.run());
    }

    private void paste() {
        startActivity(new Intent(this, ClipActivity.class));
    }

    private void openTikTok() {
        for (String p : new String[]{"com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go"}) {
            Intent i = getPackageManager().getLaunchIntentForPackage(p);
            if (i != null) { startActivity(i); return; }
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com")));
        } catch (Exception e) {
            Toast.makeText(this, "Impossible d'ouvrir TikTok", Toast.LENGTH_SHORT).show();
        }
    }

    private void countUp(TextView t, int to) {
        int from;
        try { from = Integer.parseInt(t.getText().toString()); } catch (Exception e) { from = 0; }
        if (from == to) return;
        ValueAnimator a = ValueAnimator.ofInt(from, to);
        a.setDuration(900);
        a.setStartDelay(findViewById(R.id.splash).getVisibility() == View.VISIBLE ? 1700 : 0);
        a.setInterpolator(new DecelerateInterpolator(2f));
        a.addUpdateListener(x -> t.setText(String.valueOf(x.getAnimatedValue())));
        a.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        String en = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean on = en != null && en.contains(getPackageName());
        int c = on ? 0xFF2BD67B : 0xFFFF453A;
        statusDot.setBackgroundTintList(ColorStateList.valueOf(c));
        statusPulse.setBackgroundTintList(ColorStateList.valueOf(c));
        statusTitle.setText(on ? "Bulle active sur TikTok" : "Service désactivé — termine la configuration");
        for (TextView bdg : new TextView[]{badge1, badge2}) {
            bdg.setBackgroundTintList(on ? ColorStateList.valueOf(0xFFF2F2F2) : null);
            bdg.setTextColor(on ? 0xFF000000 : 0xFFFFFFFF);
            bdg.setText(on ? "✓" : (bdg == badge1 ? "1" : "2"));
        }

        SharedPreferences s = getSharedPreferences("stats", MODE_PRIVATE);
        String day = new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date());
        countUp(total, s.getInt("total", 0));
        countUp(today, day.equals(s.getString("day", "")) ? s.getInt("today", 0) : 0);
    }
}
