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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Downloader {
    private static final Pattern TT = Pattern.compile("https?://(?:[\\w-]+\\.)*(?:tiktok\\.com|instagram\\.com|twitter\\.com|x\\.com)/[^\\s]*");
    private static final String UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36";

    public interface Listener { void done(boolean ok); }
    public static Listener listener;

    static void result(boolean ok) {
        new Handler(Looper.getMainLooper()).post(() -> { if (listener != null) listener.done(ok); });
    }

    public static void log(Context c, String m) {
        android.content.SharedPreferences p = c.getSharedPreferences("debug", Context.MODE_PRIVATE);
        String t = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT).format(new java.util.Date());
        String n = t + " " + m + "\n" + p.getString("log", "");
        if (n.length() > 5000) n = n.substring(0, 5000);
        p.edit().putString("log", n).apply();
    }

    public static long parseCount(String s) {
        Matcher m = Pattern.compile("(\\d[\\d\\s\\u00a0\\u202f.,]*)\\s*([KkMm])?(?![\\p{L}])").matcher(s);
        if (!m.find()) return -1;
        String n = m.group(1).replaceAll("[\\s\\u00a0\\u202f]", "");
        String suf = m.group(2);
        try {
            if (suf == null) return Long.parseLong(n.replaceAll("[.,]", ""));
            return Math.round(Double.parseDouble(n.replace(',', '.')) * (suf.equalsIgnoreCase("k") ? 1_000 : 1_000_000));
        } catch (Exception e) { return -1; }
    }

    private static String http(String u, String post, String... hdr) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
        con.setRequestProperty("User-Agent", UA);
        for (int i = 0; i + 1 < hdr.length; i += 2) con.setRequestProperty(hdr[i], hdr[i + 1]);
        con.setConnectTimeout(15000);
        con.setReadTimeout(20000);
        if (post != null) {
            con.setDoOutput(true);
            con.getOutputStream().write(post.getBytes("UTF-8"));
        }
        int code = con.getResponseCode();
        InputStream in = code < 400 ? con.getInputStream() : con.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) try (BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append('\n');
        }
        if (code >= 400) throw new Exception("HTTP " + code);
        return sb.toString();
    }

    private static JSONObject tikwm(String u) throws Exception {
        return new JSONObject(http("https://www.tikwm.com/api/?hd=1&url=" + URLEncoder.encode(u, "UTF-8"), null));
    }

    private static String canonical(String url) {
        String cur = url;
        try {
            for (int hop = 0; hop < 6; hop++) {
                if (cur.matches(".*/(video|photo)/\\d+.*")) break;
                HttpURLConnection con = (HttpURLConnection) new URL(cur).openConnection();
                con.setInstanceFollowRedirects(false);
                con.setRequestProperty("User-Agent", UA);
                con.setConnectTimeout(10000);
                con.setReadTimeout(10000);
                String loc = con.getHeaderField("Location");
                con.disconnect();
                if (loc == null) break;
                cur = new URL(new URL(cur), loc).toString();
            }
        } catch (Exception ignored) {}
        Matcher m = Pattern.compile("@([^/?#]+)/(video|photo)/(\\d+)").matcher(cur);
        if (m.find()) return "https://www.tiktok.com/@" + m.group(1) + "/video/" + m.group(3);
        m = Pattern.compile("/(video|photo)/(\\d+)").matcher(cur);
        if (m.find()) return "https://www.tiktok.com/@i/video/" + m.group(2);
        return cur;
    }

    public static String extractUrl(String t) {
        if (t == null) return null;
        Matcher m = TT.matcher(t);
        return m.find() ? m.group() : null;
    }

    public static void start(Context ctx, String url) {
        Context c = ctx.getApplicationContext();
        Handler h = new Handler(Looper.getMainLooper());
        toast(h, c, "Récupération du média…");
        new Thread(() -> {
            try {
                log(c, "lien copié: " + url);
                String host = Uri.parse(url).getHost();
                host = host == null ? "" : host.toLowerCase(java.util.Locale.ROOT);
                List<String[]> items;
                if (host.endsWith("instagram.com")) items = instagram(c, url);
                else if (host.endsWith("twitter.com") || host.equals("x.com") || host.endsWith(".x.com")) items = twitter(c, url);
                else items = tiktok(c, url);
                if (items.isEmpty()) throw new Exception("aucun média trouvé");
                DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
                int photos = 0;
                for (String[] it : items) {
                    enqueue(dm, it[0], it[1], it[2]);
                    if (it[1].equals(Environment.DIRECTORY_PICTURES)) photos++;
                }
                if (items.size() == 1) toast(h, c, "Téléchargement lancé ✔");
                else if (photos == items.size()) toast(h, c, photos + " photos en téléchargement");
                else toast(h, c, items.size() + " médias en téléchargement");
                android.content.SharedPreferences s = c.getSharedPreferences("stats", Context.MODE_PRIVATE);
                String day = new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.ROOT).format(new java.util.Date());
                int td = day.equals(s.getString("day", "")) ? s.getInt("today", 0) : 0;
                s.edit().putInt("total", s.getInt("total", 0) + 1).putString("day", day).putInt("today", td + 1).apply();
                result(true);
            } catch (Exception e) {
                log(c, "erreur: " + e);
                toast(h, c, "Échec : " + e.getMessage());
                result(false);
            }
        }).start();
    }

    private static String fname(String folder, String id, int i, int n, boolean photo) {
        return folder + "/" + id + (n > 1 ? "_" + (i + 1) : "") + (photo ? ".jpg" : ".mp4");
    }

    private static String[] item(String url, boolean photo, String name) {
        return new String[]{url, photo ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_MOVIES, name};
    }

    private static List<String[]> tiktok(Context c, String url) throws Exception {
        String canon = canonical(url);
        log(c, "lien complet: " + canon);
        JSONObject j = tikwm(canon);
        if (j.optInt("code", -1) != 0 && !canon.equals(url)) j = tikwm(url);
        log(c, "tikwm code=" + j.optInt("code", -1) + " " + j.optString("msg"));
        if (j.optInt("code", -1) != 0) throw new Exception(j.optString("msg", "API indisponible"));
        JSONObject d = j.getJSONObject("data");
        String id = d.optString("id", String.valueOf(System.currentTimeMillis()));
        List<String[]> out = new ArrayList<>();
        JSONArray imgs = d.optJSONArray("images");
        if (imgs != null && imgs.length() > 0) {
            for (int i = 0; i < imgs.length(); i++)
                out.add(item(abs(imgs.getString(i)), true, "TikTok/" + id + "_" + (i + 1) + ".jpg"));
        } else {
            String v = d.optString("hdplay", "");
            if (v.isEmpty()) v = d.optString("play", "");
            if (v.isEmpty()) throw new Exception("lien vidéo introuvable");
            out.add(item(abs(v), false, "TikTok/" + id + ".mp4"));
        }
        return out;
    }

    private static List<String[]> twitter(Context c, String url) throws Exception {
        Matcher m = Pattern.compile("/status(?:es)?/(\\d+)").matcher(url);
        if (!m.find()) throw new Exception("lien X invalide");
        String id = m.group(1);
        JSONObject j = new JSONObject(http("https://api.fxtwitter.com/status/" + id, null));
        log(c, "fxtwitter code=" + j.optInt("code", -1) + " " + j.optString("message"));
        JSONObject t = j.optJSONObject("tweet");
        JSONObject media = t == null ? null : t.optJSONObject("media");
        JSONArray all = media == null ? null : media.optJSONArray("all");
        if (all == null || all.length() == 0) throw new Exception("aucun média dans ce post");
        List<String[]> out = new ArrayList<>();
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.getJSONObject(i);
            boolean photo = "photo".equals(o.optString("type"));
            String u = o.optString("url", "");
            if (!u.isEmpty()) out.add(item(u, photo, fname("Twitter", id, i, all.length(), photo)));
        }
        return out;
    }

    private static List<String[]> instagram(Context c, String url) throws Exception {
        Matcher m = Pattern.compile("/(?:p|reels?|tv)/([\\w-]+)").matcher(url);
        if (!m.find()) throw new Exception("lien Instagram invalide");
        String sc = m.group(1);
        List<String[]> out = new ArrayList<>();
        try {
            String vars = URLEncoder.encode("{\"shortcode\":\"" + sc + "\"}", "UTF-8");
            JSONObject j = new JSONObject(http("https://www.instagram.com/graphql/query",
                    "doc_id=8845758582119845&variables=" + vars,
                    "Content-Type", "application/x-www-form-urlencoded",
                    "X-IG-App-ID", "936619743392459",
                    "X-FB-Friendly-Name", "PolarisPostActionLoadPostQueryQuery",
                    "Referer", "https://www.instagram.com/"));
            JSONObject node = j.getJSONObject("data").optJSONObject("xdt_shortcode_media");
            if (node != null) {
                JSONObject side = node.optJSONObject("edge_sidecar_to_children");
                JSONArray edges = side == null ? null : side.optJSONArray("edges");
                if (edges != null && edges.length() > 0) {
                    for (int i = 0; i < edges.length(); i++)
                        igNode(out, edges.getJSONObject(i).getJSONObject("node"), sc, i, edges.length());
                } else igNode(out, node, sc, 0, 1);
            }
            log(c, "insta graphql: " + out.size() + " média(s)");
        } catch (Exception e) {
            log(c, "insta graphql erreur: " + e);
        }
        if (out.isEmpty()) {
            String html = http("https://www.instagram.com/p/" + sc + "/embed/captioned/", null);
            String v = igField(html, "video_url");
            if (v != null) out.add(item(v, false, "Instagram/" + sc + ".mp4"));
            else {
                String p = igField(html, "display_url");
                if (p != null) out.add(item(p, true, "Instagram/" + sc + ".jpg"));
            }
            log(c, "insta embed: " + out.size() + " média(s)");
        }
        if (out.isEmpty()) throw new Exception("post privé ou introuvable");
        return out;
    }

    private static void igNode(List<String[]> out, JSONObject n, String sc, int i, int total) {
        boolean video = n.optBoolean("is_video") && !n.optString("video_url").isEmpty();
        String u = video ? n.optString("video_url") : n.optString("display_url");
        if (!u.isEmpty()) out.add(item(u, !video, fname("Instagram", sc, i, total, !video)));
    }

    private static String igField(String html, String key) {
        Matcher m = Pattern.compile(key + "[\\\\\"]*:[\\\\\"]*(https:(?:[^\"\\\\]|\\\\+/|\\\\+u0026)+)").matcher(html);
        if (!m.find()) return null;
        return m.group(1).replaceAll("\\\\+/", "/").replaceAll("\\\\+u0026", "&");
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
