package dev.liviano.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Preferencias locales: sesión, addons, ajustes y progreso. */
final class Store {
    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("liviano", Context.MODE_PRIVATE);
    }

    private static SharedPreferences progress(Context c) {
        return c.getApplicationContext().getSharedPreferences("progress", Context.MODE_PRIVATE);
    }

    static String authKey(Context c) { return p(c).getString("authKey", null); }
    static String email(Context c) { return p(c).getString("email", ""); }

    static void setSession(Context c, String authKey, String email) {
        p(c).edit().putString("authKey", authKey).putString("email", email).apply();
    }

    static String server(Context c) { return p(c).getString("server", ""); }
    static void setServer(Context c, String s) { p(c).edit().putString("server", s).apply(); }

    static String subLang(Context c) { return p(c).getString("subLang", "spa"); }
    static void setSubLang(Context c, String s) { p(c).edit().putString("subLang", s).apply(); }

    /** Lista de {transportUrl, manifest}. */
    static JSONArray addons(Context c) {
        try {
            return new JSONArray(p(c).getString("addons", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    static void setAddons(Context c, JSONArray a) { p(c).edit().putString("addons", a.toString()).apply(); }

    static boolean dirty;

    static void saveProgress(Context c, String videoId, long pos, long dur) {
        progress(c).edit().putString(videoId, pos + "," + dur).apply();
    }

    /** {posición, duración} en ms, o null. */
    static long[] progressOf(Context c, String videoId) {
        String s = progress(c).getString(videoId, null);
        if (s == null) return null;
        String[] parts = s.split(",");
        try {
            return new long[]{Long.parseLong(parts[0]), Long.parseLong(parts[1])};
        } catch (Exception e) {
            return null;
        }
    }

    /** Historial local para "Seguir viendo" cuando no hay sesión. */
    static JSONArray recent(Context c) {
        try {
            return new JSONArray(p(c).getString("recent", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    static void pushRecent(Context c, JSONObject meta, String videoId, boolean finished) {
        try {
            JSONArray old = recent(c);
            JSONArray out = new JSONArray();
            String id = meta.optString("id");
            if (!finished || "series".equals(meta.optString("type"))) {
                JSONObject m = new JSONObject();
                m.put("id", id);
                m.put("type", meta.optString("type"));
                m.put("name", meta.optString("name"));
                m.put("poster", meta.optString("poster"));
                m.put("lastVideo", videoId);
                out.put(m);
            }
            for (int i = 0; i < old.length() && out.length() < 30; i++) {
                JSONObject o = old.getJSONObject(i);
                if (!id.equals(o.optString("id"))) out.put(o);
            }
            p(c).edit().putString("recent", out.toString()).apply();
        } catch (JSONException ignored) {
        }
    }
}
