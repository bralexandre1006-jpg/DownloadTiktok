package be.rst.tiksave;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Downloader {
    private static final Pattern TT = Pattern.compile("https?://[^\\s]*tiktok\\.com[^\\s]*");
    private static final String UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36";

    public interface Listener { void done(boolean ok); }
    public static Listener listener;

    static void result(boolean ok) {
        new Handler(Looper.getMainLooper()).post(() -> { if (listener != null) listener.done(ok); });
    }

    private static java.util.Set<String> words(String s) {
        java.util.Set<String> w = new java.util.HashSet<>();
        for (String x : s.toLowerCase().split("[^\\p{L}\\p{N}]+")) if (x.length() > 1) w.add(x);
        return w;
    }

    private static double score(java.util.Set<String> cap, String title) {
        if (cap.isEmpty()) return 0;
        java.util.Set<String> t = words(title);
        if (t.isEmpty()) return 0;
        int common = 0;
        for (String x : cap) if (t.contains(x)) common++;
        return common / (double) Math.min(cap.size(), t.size());
    }

    private static JSONObject get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestProperty("User-Agent", UA);
        con.setConnectTimeout(15000);
        con.setReadTimeout(20000);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
            String l;
            while ((l = r.readLine()) != null) sb.append(l);
        }
        return new JSONObject(sb.toString());
    }

    public static void fromScreen(Context ctx, java.util.List<String> users, String caption) {
        Context c = ctx.getApplicationContext();
        Handler h = new Handler(Looper.getMainLooper());
        if (users.isEmpty()) { toast(h, c, "Pseudo introuvable à l'écran"); result(false); return; }
        new Thread(() -> {
            java.util.Set<String> cap = words(caption);
            String bestUser = null, bestId = null;
            double best = 0;
            try {
                outer:
                for (int u = 0; u < Math.min(users.size(), 3); u++) {
                    String user = users.get(u);
                    String cursor = "0";
                    for (int page = 0; page < 3; page++) {
                        JSONObject j = get("https://www.tikwm.com/api/user/posts?count=35&unique_id="
                                + URLEncoder.encode(user, "UTF-8") + "&cursor=" + cursor);
                        Thread.sleep(1100);
                        if (j.optInt("code", -1) != 0) break;
                        JSONObject d = j.getJSONObject("data");
                        JSONArray vids = d.optJSONArray("videos");
                        if (vids == null || vids.length() == 0) break;
                        for (int i = 0; i < vids.length(); i++) {
                            JSONObject v = vids.getJSONObject(i);
                            double s = score(cap, v.optString("title", ""));
                            if (s > best) { best = s; bestUser = user; bestId = v.optString("video_id"); }
                        }
                        if (best >= 0.75) break outer;
                        if (cap.isEmpty() && bestId == null) {
                            bestUser = user; bestId = vids.getJSONObject(0).optString("video_id");
                        }
                        if (!d.optBoolean("hasMore", false)) break;
                        cursor = d.optString("cursor", "0");
                    }
                }
            } catch (Exception e) {
                toast(h, c, "Échec : " + e.getMessage());
                result(false);
                return;
            }
            if (bestId == null || (!cap.isEmpty() && best < 0.4)) {
                toast(h, c, "Vidéo introuvable (debug dans Téléchargements)");
                result(false);
                return;
            }
            start(c, "https://www.tiktok.com/@" + bestUser + "/video/" + bestId);
        }).start();
    }

    public static String extractUrl(String t) {
        if (t == null) return null;
        Matcher m = TT.matcher(t);
        return m.find() ? m.group() : null;
    }

    public static void start(Context ctx, String url) {
        Context c = ctx.getApplicationContext();
        Handler h = new Handler(Looper.getMainLooper());
        toast(h, c, "Récupération de la vidéo…");
        new Thread(() -> {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(
                        "https://www.tikwm.com/api/?hd=1&url=" + URLEncoder.encode(url, "UTF-8")).openConnection();
                con.setRequestProperty("User-Agent", UA);
                con.setConnectTimeout(15000);
                con.setReadTimeout(20000);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                    String l;
                    while ((l = r.readLine()) != null) sb.append(l);
                }
                JSONObject j = new JSONObject(sb.toString());
                if (j.optInt("code", -1) != 0) throw new Exception(j.optString("msg", "API indisponible"));
                JSONObject d = j.getJSONObject("data");
                String id = d.optString("id", String.valueOf(System.currentTimeMillis()));
                DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
                JSONArray imgs = d.optJSONArray("images");
                if (imgs != null && imgs.length() > 0) {
                    for (int i = 0; i < imgs.length(); i++)
                        enqueue(dm, abs(imgs.getString(i)), Environment.DIRECTORY_PICTURES, "TikTok/" + id + "_" + (i + 1) + ".jpg");
                    toast(h, c, imgs.length() + " photos en téléchargement");
                } else {
                    String v = d.optString("hdplay", "");
                    if (v.isEmpty()) v = d.optString("play", "");
                    if (v.isEmpty()) throw new Exception("lien vidéo introuvable");
                    enqueue(dm, abs(v), Environment.DIRECTORY_MOVIES, "TikTok/" + id + ".mp4");
                    toast(h, c, "Téléchargement lancé ✔");
                }
                android.content.SharedPreferences s = c.getSharedPreferences("stats", Context.MODE_PRIVATE);
                String day = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.ROOT).format(new java.util.Date());
                int td = day.equals(s.getString("day", "")) ? s.getInt("today", 0) : 0;
                s.edit().putInt("total", s.getInt("total", 0) + 1).putString("day", day).putInt("today", td + 1).apply();
                result(true);
            } catch (Exception e) {
                toast(h, c, "Échec : " + e.getMessage());
                result(false);
            }
        }).start();
    }

    private static String abs(String u) {
        return u.startsWith("/") ? "https://www.tikwm.com" + u : u;
    }

    private static void enqueue(DownloadManager dm, String url, String dir, String name) {
        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
        r.addRequestHeader("User-Agent", UA);
        r.setTitle(name.substring(name.indexOf('/') + 1));
        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        r.setDestinationInExternalPublicDir(dir, name);
        dm.enqueue(r);
    }

    private static void toast(Handler h, Context c, String m) {
        h.post(() -> Toast.makeText(c, m, Toast.LENGTH_SHORT).show());
    }
}
