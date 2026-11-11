package be.rst.tiksave;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        l.setPadding(pad, pad * 3, pad, pad);
        l.setBackgroundColor(0xFF111111);

        TextView title = new TextView(this);
        title.setText("TikSave");
        title.setTextSize(32);
        title.setTextColor(0xFFFE2C55);
        l.addView(title);

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, pad, 0, pad);
        status.setGravity(Gravity.CENTER);
        l.addView(status);

        l.addView(btn("1. Autoriser les paramètres restreints", () ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())))));
        l.addView(btn("2. Activer TikSave (Accessibilité)", () ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        l.addView(btn("Ouvrir TikTok", () -> {
            for (String p : new String[]{"com.zhiliaoapp.musically", "com.ss.android.ugc.trill"}) {
                Intent i = getPackageManager().getLaunchIntentForPackage(p);
                if (i != null) { startActivity(i); return; }
            }
        }));

        TextView help = new TextView(this);
        help.setText("Sur TikTok, appuie sur la bulle rouge ⬇ pour télécharger la vidéo affichée.\n"
                + "Glisse la bulle pour la déplacer.\n\nAlternative : Partager → Plus → TikSave.\n\n"
                + "Fichiers : Movies/TikTok et Pictures/TikTok");
        help.setTextColor(0xFFAAAAAA);
        help.setPadding(0, pad, 0, 0);
        help.setGravity(Gravity.CENTER);
        l.addView(help);

        setContentView(l);
    }

    private Button btn(String t, Runnable r) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    @Override
    protected void onResume() {
        super.onResume();
        String en = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        boolean on = en != null && en.contains(getPackageName());
        status.setText(on ? "✔ Service actif" : "✖ Service désactivé");
        status.setTextColor(on ? Color.GREEN : Color.RED);
    }
}
