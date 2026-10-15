package dev.liviano.tv;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Un addon de Stremio y su manifest (protocolo público de addons). */
final class Addon {
    static final String[] DEFAULTS = {
            "https://v3-cinemeta.strem.io/manifest.json",
            "https://opensubtitles-v3.strem.io/manifest.json",
    };

    final String base;
    final JSONObject manifest;

    Addon(String transportUrl, JSONObject manifest) {
        String b = transportUrl;
        if (b.endsWith("/manifest.json")) b = b.substring(0, b.length() - "/manifest.json".length());
        this.base = b;
        this.manifest = manifest;
    }

    String name() { return manifest.optString("name", base); }

    boolean supports(String resource, String type, String id) {
        JSONArray res = manifest.optJSONArray("resources");
        if (res == null) return false;
        for (int i = 0; i < res.length(); i++) {
            Object r = res.opt(i);
            JSONArray types = manifest.optJSONArray("types");
            JSONArray prefixes = manifest.optJSONArray("idPrefixes");
            if (r instanceof String) {
                if (!resource.equals(r)) continue;
            } else if (r instanceof JSONObject) {
                JSONObject o = (JSONObject) r;
                if (!resource.equals(o.optString("name"))) continue;
                if (o.has("types")) types = o.optJSONArray("types");
                if (o.has("idPrefixes")) prefixes = o.optJSONArray("idPrefixes");
            } else {
                continue;
            }
            if (contains(types, type) && matchesPrefix(prefixes, id)) return true;
        }
        return false;
    }

    private static boolean contains(JSONArray a, String v) {
        if (a == null) return true;
        for (int i = 0; i < a.length(); i++) if (v.equals(a.optString(i))) return true;
        return false;
    }

    private static boolean matchesPrefix(JSONArray prefixes, String id) {
        if (prefixes == null || prefixes.length() == 0 || id == null) return true;
        for (int i = 0; i < prefixes.length(); i++) if (id.startsWith(prefixes.optString(i))) return true;
        return false;
    }

    String url(String resource, String type, String id, String extra) {
        return base + "/" + resource + "/" + type + "/" + enc(id) + (extra != null ? "/" + extra : "") + ".json";
    }

    static String enc(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8.name()).replace("+", "%20").replace("%3A", ":");
        } catch (Exception e) {
            return s;
        }
    }

    static List<Addon> all(Context c) {
        JSONArray arr = Store.addons(c);
        List<Addon> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null || o.optJSONObject("manifest") == null) continue;
            out.add(new Addon(o.optString("transportUrl"), o.optJSONObject("manifest")));
        }
        return out;
    }

    /** Si no hay addons guardados, descarga los predeterminados. Bloqueante. */
    static void ensure(Context c) throws Exception {
        if (Store.addons(c).length() > 0) return;
        JSONArray arr = new JSONArray();
        for (String u : DEFAULTS) arr.put(fetchEntry(u));
        Store.setAddons(c, arr);
    }

    static JSONObject fetchEntry(String transportUrl) throws Exception {
        JSONObject e = new JSONObject();
        e.put("transportUrl", transportUrl);
        e.put("manifest", new JSONObject(Net.get(transportUrl)));
        return e;
    }

    /** Catálogos que se pueden mostrar sin parámetros obligatorios. */
    static boolean catalogNeedsExtra(JSONObject cat) {
        JSONArray req = cat.optJSONArray("extraRequired");
        if (req != null && req.length() > 0) return true;
        JSONArray extra = cat.optJSONArray("extra");
        if (extra != null) {
            for (int i = 0; i < extra.length(); i++) {
                JSONObject e = extra.optJSONObject(i);
                if (e != null && e.optBoolean("isRequired")) return true;
            }
        }
        return false;
    }

    static boolean catalogSearchable(JSONObject cat) {
        JSONArray extra = cat.optJSONArray("extra");
        if (extra != null) {
            for (int i = 0; i < extra.length(); i++) {
                JSONObject e = extra.optJSONObject(i);
                if (e != null && "search".equals(e.optString("name"))) return true;
            }
        }
        JSONArray sup = cat.optJSONArray("extraSupported");
        if (sup != null) for (int i = 0; i < sup.length(); i++) if ("search".equals(sup.optString(i))) return true;
        return false;
    }
}
