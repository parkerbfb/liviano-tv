package dev.liviano.tv;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Cuenta de Stremio (api.strem.io): login, addons y biblioteca / "Seguir viendo". */
final class Lib {
    private static final String API = "https://api.strem.io/api/";
    static final Map<String, JSONObject> items = new HashMap<>();

    static JSONObject call(String method, JSONObject body) throws Exception {
        JSONObject o = new JSONObject(Net.request(API + method, body.toString()));
        JSONObject err = o.optJSONObject("error");
        if (err != null) throw new Exception(err.optString("message", "Error de la API"));
        return o;
    }

    static String login(String email, String password) throws Exception {
        JSONObject b = new JSONObject();
        b.put("type", "Login");
        b.put("email", email);
        b.put("password", password);
        b.put("facebook", false);
        return call("login", b).getJSONObject("result").getString("authKey");
    }

    /** Trae los addons instalados en la cuenta y los guarda localmente. */
    static int syncAddons(Context c) throws Exception {
        JSONObject b = new JSONObject();
        b.put("type", "AddonCollectionGet");
        b.put("authKey", Store.authKey(c));
        b.put("update", true);
        JSONArray addons = call("addonCollectionGet", b).getJSONObject("result").getJSONArray("addons");
        JSONArray out = new JSONArray();
        for (int i = 0; i < addons.length(); i++) {
            JSONObject a = addons.getJSONObject(i);
            String url = a.optString("transportUrl");
            JSONObject m = a.optJSONObject("manifest");
            if (m == null || url.contains("127.0.0.1") || url.contains("localhost")) continue;
            JSONObject bh = m.optJSONObject("behaviorHints");
            if (bh != null && bh.optBoolean("configurationRequired")) continue;
            JSONObject e = new JSONObject();
            e.put("transportUrl", url);
            e.put("manifest", m);
            out.put(e);
        }
        Store.setAddons(c, out);
        return out.length();
    }

    static void refresh(Context c) throws Exception {
        String auth = Store.authKey(c);
        if (auth == null) return;
        JSONObject b = new JSONObject();
        b.put("authKey", auth);
        b.put("collection", "libraryItem");
        b.put("ids", new JSONArray());
        b.put("all", true);
        JSONArray arr = call("datastoreGet", b).getJSONArray("result");
        synchronized (items) {
            items.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                items.put(o.optString("_id"), o);
            }
        }
    }

    /** Metas para la fila "Seguir viendo". */
    static JSONArray continueWatching(Context c) {
        if (Store.authKey(c) == null) return Store.recent(c);
        List<JSONObject> list = new ArrayList<>();
        synchronized (items) {
            for (JSONObject o : items.values()) {
                JSONObject st = o.optJSONObject("state");
                if (st == null || st.optLong("timeOffset") <= 0) continue;
                if (o.optBoolean("removed") && !o.optBoolean("temp")) continue;
                list.add(o);
            }
        }
        list.sort((a, b) -> b.optJSONObject("state").optString("lastWatched")
                .compareTo(a.optJSONObject("state").optString("lastWatched")));
        JSONArray out = new JSONArray();
        for (JSONObject o : list) {
            if (out.length() >= 20) break;
            try {
                JSONObject m = new JSONObject();
                m.put("id", o.optString("_id"));
                m.put("type", o.optString("type"));
                m.put("name", o.optString("name"));
                m.put("poster", o.optString("poster"));
                out.put(m);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    /** Último video visto de un título (episodio en series). */
    static String lastVideo(Context c, String metaId) {
        synchronized (items) {
            JSONObject o = items.get(metaId);
            if (o != null && o.optJSONObject("state") != null) {
                String v = o.optJSONObject("state").optString("video_id", "");
                if (!v.isEmpty() && !"null".equals(v)) return v;
            }
        }
        JSONArray r = Store.recent(c);
        for (int i = 0; i < r.length(); i++) {
            JSONObject o = r.optJSONObject(i);
            if (o != null && metaId.equals(o.optString("id"))) return o.optString("lastVideo", null);
        }
        return null;
    }

    /** {posición, duración} guardada para un video, local o de la cuenta. */
    static long[] resume(Context c, String metaId, String videoId) {
        long[] local = Store.progressOf(c, videoId);
        if (local != null) return local;
        synchronized (items) {
            JSONObject o = items.get(metaId);
            JSONObject st = o == null ? null : o.optJSONObject("state");
            if (st != null && videoId.equals(st.optString("video_id")) && st.optLong("timeOffset") > 0) {
                return new long[]{st.optLong("timeOffset"), st.optLong("duration")};
            }
        }
        return null;
    }

    static void saveProgress(Context c, JSONObject meta, String videoId, long pos, long dur, boolean push) {
        boolean finished = dur > 0 && pos > dur * 0.92;
        long offset = finished ? 0 : pos;
        Store.saveProgress(c, videoId, offset, dur);
        if (!push) return;
        Store.pushRecent(c, meta, videoId, finished);
        String auth = Store.authKey(c);
        if (auth == null) return;
        try {
            String id = meta.optString("id");
            String now = iso(new Date());
            JSONObject item;
            synchronized (items) {
                item = items.get(id);
            }
            if (item == null) {
                item = new JSONObject();
                item.put("_id", id);
                item.put("name", meta.optString("name"));
                item.put("type", meta.optString("type"));
                item.put("poster", meta.optString("poster"));
                item.put("posterShape", "poster");
                item.put("removed", true);
                item.put("temp", true);
                item.put("_ctime", now);
                JSONObject bh = new JSONObject();
                bh.put("defaultVideoId", JSONObject.NULL);
                bh.put("featuredVideoId", JSONObject.NULL);
                bh.put("hasScheduledVideos", false);
                item.put("behaviorHints", bh);
            }
            JSONObject st = item.optJSONObject("state");
            if (st == null) {
                st = new JSONObject();
                st.put("timeWatched", 0);
                st.put("overallTimeWatched", 0);
                st.put("timesWatched", 0);
                st.put("flaggedWatched", 0);
                st.put("watched", "");
                st.put("noNotif", false);
            }
            st.put("lastWatched", now);
            st.put("timeOffset", offset);
            st.put("duration", dur);
            st.put("video_id", videoId);
            if (finished) st.put("timesWatched", st.optInt("timesWatched") + 1);
            item.put("state", st);
            item.put("_mtime", now);
            synchronized (items) {
                items.put(id, item);
            }
            JSONObject b = new JSONObject();
            b.put("authKey", auth);
            b.put("collection", "libraryItem");
            b.put("changes", new JSONArray().put(item));
            Net.IO.execute(() -> {
                try { call("datastorePut", b); } catch (Exception ignored) { }
            });
        } catch (Exception ignored) {
        }
    }

    private static String iso(Date d) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(d);
    }
}
