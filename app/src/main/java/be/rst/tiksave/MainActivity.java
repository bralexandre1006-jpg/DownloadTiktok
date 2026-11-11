package be.rst.tiksave;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
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

public class MainActivity extends Activity {
    private static final int GREEN = 0xFF2BD67B, RED = 0xFFFE2C55;
    private TextView statusTitle, statusSub, badge1, badge2;
    private View statusDot, statusPulse;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        setContentView(R.layout.activity_main);

        statusTitle = findViewById(R.id.statusTitle);
        statusSub = findViewById(R.id.statusSub);
        statusDot = findViewById(R.id.statusDot);
        statusPulse = findViewById(R.id.statusPulse);
        badge1 = findViewById(R.id.badge1);
        badge2 = findViewById(R.id.badge2);

        click(R.id.step1, () -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        click(R.id.step2, () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        click(R.id.openTikTok, this::openTikTok);
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
                glow = findViewById(R.id.splashGlow), title = findViewById(R.id.splashTitle);
        ViewGroup content = findViewById(R.id.content);
        float d = getResources().getDisplayMetrics().density;

        for (int i = 0; i < content.getChildCount(); i++) {
            View c = content.getChildAt(i);
            c.setAlpha(0f);
            c.setTranslationY(50 * d);
        }

        logo.setScaleX(0.2f); logo.setScaleY(0.2f); logo.setAlpha(0f); logo.setRotation(-180f);
        logo.animate().scaleX(1f).scaleY(1f).alpha(1f).rotation(0f)
                .setInterpolator(new OvershootInterpolator(1.6f)).setDuration(750).start();

        glow.setScaleX(0.4f); glow.setScaleY(0.4f); glow.setAlpha(0f);
        glow.animate().scaleX(1.3f).scaleY(1.3f).alpha(1f).setStartDelay(200).setDuration(700)
                .withEndAction(() -> glow.animate().scaleX(1f).scaleY(1f).alpha(0.6f).setDuration(400).start())
                .start();

        title.setAlpha(0f); title.setTranslationY(20 * d);
        title.animate().alpha(1f).translationY(0).setStartDelay(450).setDuration(450)
                .setInterpolator(new DecelerateInterpolator()).start();

        splash.postDelayed(() -> {
            logo.animate().scaleX(2.4f).scaleY(2.4f).alpha(0f).setDuration(380).start();
            title.animate().alpha(0f).setStartDelay(0).setDuration(200).start();
            splash.animate().alpha(0f).setDuration(420).withEndAction(() -> splash.setVisibility(View.GONE)).start();
            for (int i = 0; i < content.getChildCount(); i++) {
                content.getChildAt(i).animate().alpha(1f).translationY(0)
                        .setStartDelay(150 + i * 60L).setDuration(500)
                        .setInterpolator(new DecelerateInterpolator(2f)).start();
            }
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

    private void openTikTok() {
        for (String p : new String[]{"com.zhiliaoapp.musically", "com.ss.android.ugc.trill"}) {
            Intent i = getPackageManager().getLaunchIntentForPackage(p);
            if (i != null) { startActivity(i); return; }
        }
        Toast.makeText(this, "TikTok n'est pas installé", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        String en = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean on = en != null && en.contains(getPackageName());
        int c = on ? GREEN : RED;
        statusDot.setBackgroundTintList(ColorStateList.valueOf(c));
        statusPulse.setBackgroundTintList(ColorStateList.valueOf(c));
        statusTitle.setText(on ? "Prêt à télécharger" : "Service désactivé");
        statusSub.setText(on ? "La bulle apparaît sur TikTok" : "Termine la configuration ci-dessous");
        for (TextView bdg : new TextView[]{badge1, badge2}) {
            bdg.setBackgroundTintList(on ? ColorStateList.valueOf(GREEN) : null);
            bdg.setText(on ? "✓" : (bdg == badge1 ? "1" : "2"));
        }
    }
}
