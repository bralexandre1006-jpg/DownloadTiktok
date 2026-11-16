package be.rst.tiksave;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class WebGrab {
    private static final String JS = "(function(){" +
            "var v=document.querySelector('video');" +
            "if(v){var s=v.currentSrc||v.src;if(s&&s.indexOf('http')==0)return 'V'+s;}" +
            "var hasVid=!!document.querySelector('.videoSpritePlayButton,.Embed video,[class*=PlayButton],[aria-label*=lay]');" +
            "if(hasVid&&!window.__ts){window.__ts=1;var b=document.querySelector('.videoSpritePlayButton,[class*=PlayButton],[aria-label*=lay],.EmbeddedMedia');if(b)b.click();}" +
            "var i=document.querySelector('img.EmbeddedMediaImage,.EmbeddedMedia img');" +
            "if(i&&i.src)return (hasVid?'p':'P')+i.src;return '';})()";

    @SuppressLint("SetJavaScriptEnabled")
    static String grab(Context c, String url) throws InterruptedException {
        final String[] res = {null};
        final WebView[] wv = {null};
        CountDownLatch latch = new CountDownLatch(1);
        Handler h = new Handler(Looper.getMainLooper());
        h.post(() -> {
            try {
                WebView w = new WebView(c);
                wv[0] = w;
                w.getSettings().setJavaScriptEnabled(true);
                w.getSettings().setDomStorageEnabled(true);
                w.getSettings().setMediaPlaybackRequiresUserGesture(false);
                w.setWebViewClient(new WebViewClient() {
                    @Override
                    public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                        String u = r.getUrl().toString();
                        if (res[0] == null && u.contains(".mp4") && !u.contains("bytestart")
                                && (u.contains("cdninstagram") || u.contains("fbcdn"))) {
                            res[0] = "V" + u;
                            latch.countDown();
                        }
                        return null;
                    }
                });
                w.loadUrl(url);
                int[] n = {0};
                Runnable[] poll = new Runnable[1];
                poll[0] = () -> {
                    if (latch.getCount() == 0) return;
                    w.evaluateJavascript(JS, val -> {
                        String s = "";
                        try { s = new JSONArray("[" + val + "]").optString(0, ""); } catch (Exception ignored) {}
                        if (s.startsWith("V")) { res[0] = s; latch.countDown(); }
                        else if (s.startsWith("P") && n[0] > 3) { res[0] = s; latch.countDown(); }
                    });
                    if (++n[0] < 25) h.postDelayed(poll[0], 600);
                    else latch.countDown();
                };
                h.postDelayed(poll[0], 1500);
            } catch (Throwable t) {
                latch.countDown();
            }
        });
        latch.await(25, TimeUnit.SECONDS);
        h.post(() -> { if (wv[0] != null) { wv[0].stopLoading(); wv[0].destroy(); } });
        return res[0];
    }
}
