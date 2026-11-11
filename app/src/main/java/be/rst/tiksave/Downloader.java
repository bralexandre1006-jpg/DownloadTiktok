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

    private static final String WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static java.net.CookieManager cookies;

    private static String fetch(String url, String[] finalUrl) throws Exception {
        if (cookies == null) {
            cookies = new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL);
            java.net.CookieHandler.setDefault(cookies);
        }
        String cur = url;
        for (int hop = 0; hop < 6; hop++) {
            HttpURLConnection con = (HttpURLConnection) new URL(cur).openConnection();
            con.setInstanceFollowRedirects(false);
            con.setRequestProperty("User-Agent", WEB_UA);
            con.setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8");
            con.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            con.setConnectTimeout(15000);
            con.setReadTimeout(20000);
            int code = con.getResponseCode();
            if (code >= 300 && code < 400 && con.getHeaderField("Location") != null) {
                cur = new URL(new URL(cur), con.getHeaderField("Location")).toString();
                continue;
            }
            if (finalUrl != null) finalUrl[0] = cur;
            java.io.InputStream in = code >= 400 ? con.getErrorStream() : con.getInputStream();
            StringBuilder sb = new StringBuilder();
            if (in != null) try (BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
                String l;
                while ((l = r.readLine()) != null) sb.append(l).append('\n');
            }
            if (code >= 400) throw new Exception("HTTP " + code);
            return sb.toString();
        }
        throw new Exception("trop de redirections");
    }

    private static String cookieHeader() {
        StringBuilder sb = new StringBuilder();
        if (cookies != null) for (java.net.HttpCookie ck : cookies.getCookieStore().getCookies()) {
            if (ck.getDomain() != null && ck.getDomain().contains("tiktok")) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(ck.getName()).append('=').append(ck.getValue());
            }
        }
        return sb.toString();
    }

    private static JSONObject findItem(String html, String id) throws Exception {
        Matcher m = Pattern.compile("<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\"[^>]*>(.*?)</script>", Pattern.DOTALL).matcher(html);
        if (m.find()) {
            JSONObject root = new JSONObject(m.group(1));
            JSONObject scope = root.optJSONObject("__DEFAULT_SCOPE__");
            if (scope != null) {
                JSONObject det = scope.optJSONObject("webapp.video-detail");
                if (det == null) det = scope.optJSONObject("webapp.reflow.video.detail");
                if (det != null && det.optJSONObject("itemInfo") != null)
                    return det.getJSONObject("itemInfo").optJSONObject("itemStruct");
            }
        }
        m = Pattern.compile("<script id=\"SIGI_STATE\"[^>]*>(.*?)</script>", Pattern.DOTALL).matcher(html);
        if (m.find()) {
            JSONObject im = new JSONObject(m.group(1)).optJSONObject("ItemModule");
            if (im != null) return im.optJSONObject(id);
        }
        return null;
    }

    public static void start(Context ctx, String url) {
        Context c = ctx.getApplicationContext();
        Handler h = new Handler(Looper.getMainLooper());
        toast(h, c, "Téléchargement…");
        new Thread(() -> {
            try {
                log(c, "lien: " + url);
                String[] fin = new String[1];
                String html = fetch(url, fin);
                Matcher idm = Pattern.compile("/(?:video|photo)/(\\d+)").matcher(fin[0]);
                String id = idm.find() ? idm.group(1) : String.valueOf(System.currentTimeMillis());
                log(c, "page: " + fin[0] + " (" + html.length() + " car.)");
                if (!fin[0].contains("/video/") && !fin[0].contains("/photo/"))
                    throw new Exception("lien non reconnu");
                JSONObject item = findItem(html, id);
                if (item == null) {
                    html = fetch("https://www.tiktok.com/@i/video/" + id, null);
                    item = findItem(html, id);
                }
                if (item == null) throw new Exception("données vidéo absentes de la page");
                String ck = cookieHeader();
                JSONObject ip = item.optJSONObject("imagePost");
                JSONArray imgs = ip != null ? ip.optJSONArray("images") : null;
                if (imgs != null && imgs.length() > 0) {
                    for (int i = 0; i < imgs.length(); i++) {
                        JSONObject im = imgs.getJSONObject(i).optJSONObject("imageURL");
                        JSONArray ul = im != null ? im.optJSONArray("urlList") : null;
                        if (ul != null && ul.length() > 0)
                            enqueue(c, ul.getString(0), ck, Environment.DIRECTORY_PICTURES, "TikTok/" + id + "_" + (i + 1) + ".jpg");
                    }
                    toast(h, c, imgs.length() + " photos enregistrées ✔");
                } else {
                    JSONObject v = item.getJSONObject("video");
                    java.util.List<String> cands = new java.util.ArrayList<>();
                    JSONArray br = v.optJSONArray("bitrateInfo");
                    if (br != null && br.length() > 0) {
                        JSONObject pa = br.getJSONObject(0).optJSONObject("PlayAddr");
                        JSONArray ul = pa != null ? pa.optJSONArray("UrlList") : null;
                        if (ul != null) for (int i = 0; i < ul.length(); i++) cands.add(ul.getString(i));
                    }
                    if (!v.optString("playAddr").isEmpty()) cands.add(v.optString("playAddr"));
                    if (!v.optString("downloadAddr").isEmpty()) cands.add(v.optString("downloadAddr"));
                    if (cands.isEmpty()) throw new Exception("adresse vidéo absente");
                    log(c, cands.size() + " sources, cookies=" + (ck.isEmpty() ? "non" : "oui"));
                    Exception last = null;
                    boolean done = false;
                    for (String src : cands) {
                        try { enqueue(c, src, ck, Environment.DIRECTORY_MOVIES, "TikTok/" + id + ".mp4"); done = true; break; }
                        catch (Exception ex) { last = ex; log(c, "source KO: " + ex.getMessage()); }
                    }
                    if (!done) throw last;
                    toast(h, c, "Vidéo enregistrée ✔");
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

    @android.annotation.TargetApi(29)
    private static void enqueue(Context c, String url, String cookie, String dir, String name) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setRequestProperty("User-Agent", WEB_UA);
        con.setRequestProperty("Referer", "https://www.tiktok.com/");
        if (cookie != null && !cookie.isEmpty()) con.setRequestProperty("Cookie", cookie);
        con.setConnectTimeout(15000);
        con.setReadTimeout(30000);
        int code = con.getResponseCode();
        if (code >= 400) throw new Exception("fichier HTTP " + code);
        boolean video = name.endsWith(".mp4");
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name.substring(name.indexOf('/') + 1));
        v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, video ? "video/mp4" : "image/jpeg");
        v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, dir + "/TikTok");
        v.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1);
        android.net.Uri col = video ? android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                : android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        android.net.Uri u = c.getContentResolver().insert(col, v);
        if (u == null) throw new Exception("stockage indisponible");
        long total = 0;
        try (java.io.InputStream in = con.getInputStream();
             java.io.OutputStream out = c.getContentResolver().openOutputStream(u)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); total += n; }
        }
        v.clear();
        v.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0);
        c.getContentResolver().update(u, v, null, null);
        log(c, "enregistré " + name + " (" + total / 1024 + " Ko)");
        if (total < 10_000) throw new Exception("fichier vide (" + total + " o)");
    }

    private static void toast(Handler h, Context c, String m) {
        h.post(() -> Toast.makeText(c, m, Toast.LENGTH_SHORT).show());
    }
}
