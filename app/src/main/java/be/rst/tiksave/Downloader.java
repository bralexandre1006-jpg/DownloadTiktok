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

    public static long parseCount(String s) {
        Matcher m = Pattern.compile("(\\d[\\d\\s\\u00a0\\u202f.,]*)\\s*([KkMm])?(?![\\p{L}])").matcher(s);
        if (!m.find()) return -1;
        String n = m.group(1).replaceAll("[\\s\\u00a0\\u202f]", "");
        String suf = m.group(2);
        try {
            if (suf == null) return Long.parseLong(n.replaceAll("[.,]", ""));
            double v = Double.parseDouble(n.replace(',', '.'));
            return Math.round(v * (suf.equalsIgnoreCase("k") ? 1_000 : 1_000_000));
        } catch (Exception e) { return -1; }
    }

    private static boolean near(long screen, long api) {
        if (screen < 0 || api < 0) return false;
        double tol = screen >= 10_000 ? Math.max(screen * 0.03, 100) : Math.max(screen * 0.03, 3);
        return Math.abs(screen - api) <= tol;
    }

    private static JSONObject post(String method, String query) throws Exception {
        Exception last = null;
        for (String base : new String[]{"https://tikwm.com/api/", "https://www.tikwm.com/api/"}) {
            try {
                HttpURLConnection con = (HttpURLConnection) new URL(base + method + "?" + query).openConnection();
                con.setRequestMethod("POST");
                con.setDoOutput(true);
                con.setRequestProperty("User-Agent", UA);
                con.setRequestProperty("Accept", "application/json");
                con.setConnectTimeout(15000);
                con.setReadTimeout(20000);
                con.getOutputStream().close();
                StringBuilder sb = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                    String l;
                    while ((l = r.readLine()) != null) sb.append(l);
                }
                return new JSONObject(sb.toString());
            } catch (Exception e) { last = e; }
        }
        throw last;
    }

    private static JSONObject http(Context c, String tag, String method, String url, String body) {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setRequestMethod(method);
            con.setRequestProperty("User-Agent", UA);
            con.setRequestProperty("Accept", "application/json");
            con.setConnectTimeout(15000);
            con.setReadTimeout(20000);
            if (body != null) {
                con.setDoOutput(true);
                con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                con.getOutputStream().write(body.getBytes("UTF-8"));
            }
            int code = con.getResponseCode();
            java.io.InputStream in = code >= 400 ? con.getErrorStream() : con.getInputStream();
            StringBuilder sb = new StringBuilder();
            if (in != null) try (BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
                String l;
                while ((l = r.readLine()) != null) sb.append(l);
            }
            String txt = sb.toString();
            log(c, tag + " HTTP " + code + " " + txt.substring(0, Math.min(140, txt.length())));
            if (code >= 400) return null;
            JSONObject j = new JSONObject(txt);
            return j.optInt("code", -1) == 0 ? j.optJSONObject("data") : null;
        } catch (Exception e) {
            log(c, tag + " err " + e);
            return null;
        }
    }

    private static JSONObject userPosts(Context c, String user, String cursor) throws Exception {
        String q = "unique_id=" + URLEncoder.encode(user, "UTF-8") + "&count=35&cursor=" + cursor;
        JSONObject d = http(c, "A", "POST", "https://tikwm.com/api/user/posts?" + q, "");
        if (d == null) d = http(c, "B", "POST", "https://www.tikwm.com/api/user/posts", q);
        if (d == null) d = http(c, "C", "GET", "https://www.tikwm.com/api/user/posts?" + q, null);
        return d;
    }

    private static JSONObject search(Context c, String kw) throws Exception {
        String q = "keywords=" + URLEncoder.encode(kw, "UTF-8") + "&count=30&cursor=0&HD=1";
        JSONObject d = http(c, "S1", "POST", "https://www.tikwm.com/api/feed/search", q);
        if (d == null) d = http(c, "S2", "GET", "https://www.tikwm.com/api/feed/search?" + q, null);
        return d;
    }

    private static String[] pick(JSONArray vids, long[] st, java.util.Set<String> cap, String fallbackUser, double[] best) {
        String[] r = null;
        int known = 0;
        for (long v : st) if (v >= 0) known++;
        for (int i = 0; i < vids.length(); i++) {
            JSONObject v = vids.optJSONObject(i);
            if (v == null) continue;
            int hits = 0;
            if (near(st[0], v.optLong("digg_count", -1))) hits++;
            if (near(st[1], v.optLong("comment_count", -1))) hits++;
            if (near(st[2], v.optLong("share_count", -1))) hits++;
            double cs = score(cap, v.optString("title", ""));
            boolean ok = hits >= 2 || (hits >= 1 && cs >= 0.5) || cs >= 0.75 || (known == 1 && hits == 1);
            double sc = hits + cs;
            if (ok && sc > best[0]) {
                best[0] = sc;
                JSONObject a = v.optJSONObject("author");
                String u = a != null ? a.optString("unique_id", fallbackUser) : fallbackUser;
                r = new String[]{u, v.optString("video_id", v.optString("id"))};
            }
        }
        return r;
    }

    public static void fromScreen(Context ctx, java.util.List<String> users, String caption, long[] st) {
        Context c = ctx.getApplicationContext();
        Handler h = new Handler(Looper.getMainLooper());
        log(c, "users=" + users + " | caption=" + caption + " | stats=" + java.util.Arrays.toString(st));
        if (users.isEmpty()) { toast(h, c, "Pseudo introuvable à l'écran"); result(false); return; }
        new Thread(() -> {
            java.util.Set<String> cap = words(caption);
            double[] best = {0};
            String[] found = null;
            try {
                String user = users.get(0);
                String cursor = "0";
                for (int page = 0; page < 4; page++) {
                    JSONObject d = userPosts(c, user, cursor);
                    if (d == null) break;
                    JSONArray vids = d.optJSONArray("videos");
                    if (vids == null || vids.length() == 0) break;
                    String[] r = pick(vids, st, cap, user, best);
                    if (r != null) found = r;
                    if (best[0] >= 2.5) break;
                    if (!d.optBoolean("hasMore", false)) break;
                    cursor = d.optString("cursor", "0");
                    Thread.sleep(1100);
                }
                if (found == null) {
                    Thread.sleep(1100);
                    JSONObject d = search(c, (user + " " + caption).trim());
                    if (d != null && d.optJSONArray("videos") != null) {
                        String[] r = pick(d.optJSONArray("videos"), st, cap, user, best);
                        if (r != null) found = r;
                    }
                }
            } catch (Exception e) {
                log(c, "erreur: " + e);
            }
            log(c, "best=" + best[0] + " found=" + (found == null ? "null" : found[0] + "/" + found[1]));
            if (found == null) {
                toast(h, c, "Vidéo introuvable");
                result(false);
                return;
            }
            start(c, "https://www.tiktok.com/@" + found[0] + "/video/" + found[1]);
        }).start();
    }

    public static void log(Context c, String m) {
        android.content.SharedPreferences p = c.getSharedPreferences("debug", Context.MODE_PRIVATE);
        String t = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT).format(new java.util.Date());
        String old = p.getString("log", "");
        String n = t + " " + m + "\n" + old;
        if (n.length() > 5000) n = n.substring(0, 5000);
        p.edit().putString("log", n).apply();
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
                JSONObject j = post("", "hd=1&url=" + URLEncoder.encode(url, "UTF-8"));
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
                log(c, "erreur download: " + e);
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
