package dev.liviano.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Lista de fuentes de todos los addons para un video. OK = reproducir, mantener OK = reproductor externo. */
public class StreamsActivity extends Activity {
    private final List<JSONObject> streams = new ArrayList<>();
    private Ui.ListAdapter adapter;
    private TextView status;
    private JSONObject meta;
    private String videoId, label;
    private int pending, loaded;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            meta = new JSONObject(getIntent().getStringExtra("meta"));
        } catch (Exception e) {
            finish();
            return;
        }
        videoId = getIntent().getStringExtra("videoId");
        label = getIntent().getStringExtra("label");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), Ui.dp(this, 8));
        TextView t = Ui.text(this, label, 20, Ui.TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(t);
        status = Ui.text(this, "Buscando fuentes…", 13, Ui.MUTED);
        status.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 6));
        root.addView(status);
        adapter = new Ui.ListAdapter(this, (pos, longPress) -> {
            if (pos >= 0 && pos < streams.size()) play(streams.get(pos), longPress);
        });
        root.addView(Ui.list(this, adapter), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        load();
    }

    private void load() {
        String type = meta.optString("type");
        for (Addon a : Addon.all(this)) {
            if (!a.supports("stream", type, videoId)) continue;
            pending++;
            loaded++;
            String url = a.url("stream", type, videoId, null);
            String addonName = a.name();
            Net.async(() -> new JSONObject(Net.get(url)).optJSONArray("streams"), (arr, e) -> {
                if (isDestroyed()) return;
                pending--;
                if (arr != null) add(arr, addonName);
                updateStatus();
            });
        }
        updateStatus();
    }

    private void add(JSONArray arr, String addonName) {
        boolean hasServer = !Store.server(this).isEmpty();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.optJSONObject(i);
            if (s == null) continue;
            boolean torrent = !s.has("url") && s.has("infoHash");
            if (!s.has("url") && !torrent) continue; // YouTube / enlaces externos: no aplican
            streams.add(s);
            String name = s.optString("name", addonName).replace('\n', ' ');
            String desc = s.optString("description", s.optString("title"));
            if (torrent) name += hasServer ? "   [torrent]" : "   [torrent · requiere servidor]";
            adapter.rows.add(new String[]{name, desc});
        }
        adapter.notifyDataSetChanged();
    }

    private void updateStatus() {
        if (pending > 0) status.setText("Buscando fuentes… (" + streams.size() + ")");
        else if (streams.isEmpty()) status.setText(loaded == 0
                ? "No tienes addons de fuentes. Inicia sesión en Ajustes con tu cuenta de Stremio."
                : "No se encontraron fuentes.");
        else status.setText(streams.size() + " fuentes  ·  OK reproducir  ·  mantener OK = reproductor externo");
    }

    static String resolve(Activity a, JSONObject s) {
        if (s.has("url")) return s.optString("url");
        String server = Store.server(a);
        if (!s.has("infoHash") || server.isEmpty()) return null;
        if (server.endsWith("/")) server = server.substring(0, server.length() - 1);
        int idx = s.has("fileIdx") && !s.isNull("fileIdx") ? s.optInt("fileIdx") : -1;
        return server + "/" + s.optString("infoHash") + "/" + idx;
    }

    private void play(JSONObject s, boolean external) {
        String url = resolve(this, s);
        if (url == null) {
            Toast.makeText(this, "Fuente torrent: configura un servidor en Ajustes o usa un addon con debrid.", Toast.LENGTH_LONG).show();
            return;
        }
        if (external) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*"));
            } catch (Exception e) {
                Toast.makeText(this, "No hay reproductor externo.", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        JSONObject headers = null;
        JSONObject bh = s.optJSONObject("behaviorHints");
        if (bh != null && bh.optJSONObject("proxyHeaders") != null) headers = bh.optJSONObject("proxyHeaders").optJSONObject("request");
        JSONArray subs = s.optJSONArray("subtitles");
        startActivity(new Intent(this, PlayerActivity.class)
                .putExtra("url", url)
                .putExtra("headers", headers == null ? null : headers.toString())
                .putExtra("subs", subs == null ? null : subs.toString())
                .putExtra("meta", meta.toString())
                .putExtra("videoId", videoId)
                .putExtra("label", label));
    }
}
