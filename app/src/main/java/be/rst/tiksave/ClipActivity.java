package be.rst.tiksave;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public class ClipActivity extends Activity {
    private boolean done;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent i = getIntent();
        if (Intent.ACTION_SEND.equals(i.getAction())) {
            done = true;
            handle(i.getStringExtra(Intent.EXTRA_TEXT));
        }
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (!focus || done) return;
        done = true;
        String t = null;
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData cd = cm.getPrimaryClip();
        if (cd != null && cd.getItemCount() > 0) {
            CharSequence s = cd.getItemAt(0).coerceToText(this);
            if (s != null) t = s.toString();
        }
        handle(t);
    }

    private void handle(String text) {
        String url = Downloader.extractUrl(text);
        if (url == null) Toast.makeText(this, "Aucun lien TikTok trouvé", Toast.LENGTH_SHORT).show();
        else Downloader.start(this, url);
        finish();
        overridePendingTransition(0, 0);
    }
}
