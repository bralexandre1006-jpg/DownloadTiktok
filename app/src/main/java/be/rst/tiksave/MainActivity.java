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
    private TextView statusTitle, total, today;
    private View statusDot, statusPulse;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (android.os.Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        setContentView(R.layout.activity_main);
        applyInsets();
        animateOrbs();

        statusTitle = findViewById(R.id.statusTitle);
        statusDot = findViewById(R.id.statusDot);
        statusPulse = findViewById(R.id.statusPulse);
        total = findViewById(R.id.total);
        today = findViewById(R.id.today);

        click(R.id.openTikTok, () -> openApp("TikTok", "https://www.tiktok.com",
                "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go"));
        click(R.id.openInsta, () -> openApp("Instagram", "https://www.instagram.com", "com.instagram.android"));
        click(R.id.openX, () -> openApp("X", "https://x.com", "com.twitter.android"));
        click(R.id.pasteBtn, this::paste);

        click(R.id.diag, this::showDiag);

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
                glow = findViewById(R.id.splashGlow),
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

        splash.postDelayed(() -> {
            logo.animate().scaleX(0.6f).scaleY(0.6f).alpha(0f).setDuration(300).start();
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

    private void showDiag() {
        SharedPreferences p = getSharedPreferences("debug", MODE_PRIVATE);
        String txt = "LOG\n" + p.getString("log", "(vide)") + "\n\nECRAN\n" + p.getString("dump", "(vide)");
        TextView tv = new TextView(this);
        tv.setText(txt);
        tv.setTextColor(0xFFDDDDDD);
        tv.setTextSize(11);
        tv.setTextIsSelectable(true);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(tv);
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Diagnostic")
                .setView(sv)
                .setPositiveButton("Copier", (d, w) -> {
                    ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                            .setPrimaryClip(android.content.ClipData.newPlainText("TikSave", txt));
                    Toast.makeText(this, "Copié", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Effacer", (d, w) -> p.edit().clear().apply())
                .setNegativeButton("Fermer", null)
                .show();
    }

    private void paste() {
        startActivity(new Intent(this, ClipActivity.class));
    }

    private void openApp(String name, String web, String... pkgs) {
        for (String p : pkgs) {
            Intent i = getPackageManager().getLaunchIntentForPackage(p);
            if (i != null) { startActivity(i); return; }
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(web)));
        } catch (Exception e) {
            Toast.makeText(this, "Impossible d'ouvrir " + name, Toast.LENGTH_SHORT).show();
        }
    }

    private void applyInsets() {
        View content = findViewById(R.id.content), bar = findViewById(R.id.bottomBar);
        float d = getResources().getDisplayMetrics().density;
        int top = content.getPaddingTop(), bottom = content.getPaddingBottom(), barB = bar.getPaddingBottom();
        findViewById(android.R.id.content).setOnApplyWindowInsetsListener((v, in) -> {
            int t = in.getSystemWindowInsetTop(), nb = in.getSystemWindowInsetBottom();
            if (android.os.Build.VERSION.SDK_INT >= 28 && in.getDisplayCutout() != null)
                t = Math.max(t, in.getDisplayCutout().getSafeInsetTop());
            content.setPadding(content.getPaddingLeft(), Math.max(top, t + (int) (20 * d)),
                    content.getPaddingRight(), bottom + nb);
            bar.setPadding(bar.getPaddingLeft(), bar.getPaddingTop(), bar.getPaddingRight(), barB + nb);
            return in;
        });
    }

    private void animateOrbs() {
        View orbs = findViewById(R.id.orbs);
        if (android.os.Build.VERSION.SDK_INT >= 31)
            orbs.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(60f, 60f, android.graphics.Shader.TileMode.DECAL));
        float d = getResources().getDisplayMetrics().density;
        int[] ids = {R.id.orb1, R.id.orb2, R.id.orb3};
        float[][] mv = {{60, 80}, {-70, 50}, {50, -70}};
        for (int i = 0; i < ids.length; i++) {
            ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(findViewById(ids[i]),
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0, mv[i][0] * d),
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0, mv[i][1] * d),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.15f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.15f));
            a.setDuration(9000 + i * 2500L);
            a.setRepeatCount(ValueAnimator.INFINITE);
            a.setRepeatMode(ValueAnimator.REVERSE);
            a.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
            a.start();
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
        statusDot.setBackgroundTintList(ColorStateList.valueOf(0xFF2BD67B));
        statusPulse.setBackgroundTintList(ColorStateList.valueOf(0xFF2BD67B));
        statusTitle.setText("Prêt : Partager → TikSave");

        SharedPreferences s = getSharedPreferences("stats", MODE_PRIVATE);
        String day = new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date());
        countUp(total, s.getInt("total", 0));
        countUp(today, day.equals(s.getString("day", "")) ? s.getInt("today", 0) : 0);
    }
}
