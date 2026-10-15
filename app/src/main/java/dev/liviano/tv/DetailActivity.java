package dev.liviano.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Ficha: información, temporadas y episodios. */
public class DetailActivity extends Activity {
    private JSONObject meta;
    private TextView title, info, desc;
    private LinearLayout actions, seasons;
    private ImageView poster;
    private RecyclerView episodesView;
    private Ui.ListAdapter episodes;
    private final List<JSONObject> shown = new ArrayList<>();
    private JSONArray videos;

    static void open(Activity a, JSONObject m) {
        a.startActivity(new Intent(a, DetailActivity.class).putExtra("meta", m.toString()));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            meta = new JSONObject(getIntent().getStringExtra("meta"));
        } catch (Exception e) {
            finish();
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 12));

        ScrollView leftScroll = new ScrollView(this);
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        leftScroll.addView(left);
        root.addView(leftScroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        title = Ui.text(this, meta.optString("name"), 24, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        left.addView(title);
        info = Ui.text(this, "", 14, Ui.MUTED);
        info.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        left.addView(info);
        actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        left.addView(actions);
        HorizontalScrollView seasonScroll = new HorizontalScrollView(this);
        seasons = new LinearLayout(this);
        seasons.setOrientation(LinearLayout.HORIZONTAL);
        seasonScroll.addView(seasons);
        left.addView(seasonScroll, Ui.margins(this, 0, 10, 0, 0));
        desc = Ui.text(this, "", 14, Ui.TEXT);
        desc.setPadding(0, Ui.dp(this, 10), Ui.dp(this, 10), 0);
        desc.setMaxLines(9);
        left.addView(desc);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        root.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.FIT_START);
        right.addView(poster, new LinearLayout.LayoutParams(Ui.dp(this, 180), Ui.dp(this, 270)));
        episodes = new Ui.ListAdapter(this, (pos, longPress) -> {
            if (pos < 0 || pos >= shown.size()) return;
            JSONObject ep = shown.get(pos);
            openStreams(ep.optString("id"), epLabel(ep));
        });
        episodesView = Ui.list(this, episodes);
        episodesView.setVisibility(View.GONE);
        right.addView(episodesView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        Img.load(poster, meta.optString("poster"), Ui.dp(this, 180));
        loadMeta();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videos != null || "movie".equals(meta.optString("type"))) buildActions();
        episodes.notifyDataSetChanged();
    }

    private void loadMeta() {
        String type = meta.optString("type");
        String id = meta.optString("id");
        List<Addon> sources = new ArrayList<>();
        for (Addon a : Addon.all(this)) if (a.supports("meta", type, id)) sources.add(a);
        Net.async(() -> {
            for (Addon a : sources) {
                try {
                    JSONObject m = new JSONObject(Net.get(a.url("meta", type, id, null))).optJSONObject("meta");
                    if (m != null) return m;
                } catch (Exception ignored) {
                }
            }
            return null;
        }, (m, e) -> {
            if (isDestroyed()) return;
            if (m != null) apply(m);
            else buildActions();
        });
    }

    private void apply(JSONObject m) {
        try {
            for (String k : new String[]{"name", "poster", "type"}) {
                if (!m.optString(k).isEmpty() && meta.optString(k).isEmpty()) meta.put(k, m.optString(k));
            }
            if (!meta.has("defaultVideo")) {
                JSONObject bh = m.optJSONObject("behaviorHints");
                if (bh != null && !bh.optString("defaultVideoId").isEmpty() && !bh.isNull("defaultVideoId")) {
                    meta.put("defaultVideo", bh.optString("defaultVideoId"));
                }
            }
        } catch (Exception ignored) {
        }
        title.setText(m.optString("name", meta.optString("name")));
        if (meta.optString("poster").isEmpty() || !m.optString("poster").isEmpty()) {
            Img.load(poster, m.optString("poster", meta.optString("poster")), Ui.dp(this, 180));
        }
        StringBuilder sb = new StringBuilder();
        append(sb, m.optString("releaseInfo", m.optString("year")));
        append(sb, m.optString("runtime"));
        if (!m.optString("imdbRating").isEmpty()) append(sb, "★ " + m.optString("imdbRating"));
        JSONArray genres = m.optJSONArray("genres");
        if (genres != null && genres.length() > 0) {
            List<String> g = new ArrayList<>();
            for (int i = 0; i < Math.min(3, genres.length()); i++) g.add(genres.optString(i));
            append(sb, String.join(", ", g));
        }
        info.setText(sb.toString());
        desc.setText(m.optString("description"));
        JSONArray v = m.optJSONArray("videos");
        if (v != null && v.length() > 0 && !"movie".equals(meta.optString("type"))) {
            videos = v;
            buildSeasons();
        }
        buildActions();
    }

    private static void append(StringBuilder sb, String s) {
        if (s == null || s.isEmpty() || "null".equals(s)) return;
        if (sb.length() > 0) sb.append("  ·  ");
        sb.append(s);
    }

    private void buildActions() {
        actions.removeAllViews();
        String id = meta.optString("id");
        if (videos == null) {
            String videoId = meta.optString("defaultVideo", id);
            long[] p = Lib.resume(this, id, videoId);
            String label = p != null && p[0] > 0 ? "Continuar (" + fmt(p[0]) + ")" : "Ver";
            TextView play = Ui.button(this, "▶  " + label, v -> openStreams(videoId, meta.optString("name")));
            actions.addView(play, Ui.margins(this, 0, 0, 10, 0));
            play.requestFocus();
        } else {
            String last = Lib.lastVideo(this, id);
            JSONObject ep = last == null ? null : findVideo(last);
            if (ep != null) {
                TextView cont = Ui.button(this, "▶  Continuar " + epCode(ep), v -> openStreams(ep.optString("id"), epLabel(ep)));
                actions.addView(cont, Ui.margins(this, 0, 0, 10, 0));
                cont.requestFocus();
            }
        }
    }

    private JSONObject findVideo(String vid) {
        for (int i = 0; i < videos.length(); i++) {
            JSONObject v = videos.optJSONObject(i);
            if (v != null && vid.equals(v.optString("id"))) return v;
        }
        return null;
    }

    private void buildSeasons() {
        TreeSet<Integer> set = new TreeSet<>();
        for (int i = 0; i < videos.length(); i++) set.add(videos.optJSONObject(i).optInt("season"));
        List<Integer> order = new ArrayList<>(set);
        if (order.size() > 1 && order.get(0) == 0) order.add(order.remove(0));
        seasons.removeAllViews();
        int initial = order.get(0);
        String last = Lib.lastVideo(this, meta.optString("id"));
        JSONObject lastEp = last == null ? null : findVideo(last);
        if (lastEp != null) initial = lastEp.optInt("season");
        for (int s : order) {
            TextView btn = Ui.button(this, s == 0 ? "Especiales" : "T" + s, v -> showSeason(s));
            btn.setOnFocusChangeListener((v, has) -> { if (has) showSeason(s); });
            seasons.addView(btn, Ui.margins(this, 0, 0, 8, 0));
        }
        poster.setVisibility(View.GONE);
        episodesView.setVisibility(View.VISIBLE);
        showSeason(initial);
    }

    private int currentSeason = Integer.MIN_VALUE;

    private void showSeason(int season) {
        if (season == currentSeason) return;
        currentSeason = season;
        shown.clear();
        for (int i = 0; i < videos.length(); i++) {
            JSONObject v = videos.optJSONObject(i);
            if (v.optInt("season") == season) shown.add(v);
        }
        shown.sort((a, b) -> Integer.compare(a.optInt("episode", a.optInt("number")), b.optInt("episode", b.optInt("number"))));
        episodes.rows.clear();
        for (JSONObject ep : shown) {
            String name = ep.optString("name", ep.optString("title"));
            String extra = ep.optString("released");
            if (extra.length() >= 10) extra = extra.substring(0, 10);
            long[] p = Store.progressOf(this, ep.optString("id"));
            if (p != null && p[0] > 0) extra += "   ▶ " + fmt(p[0]);
            else if (p != null && p[1] > 0) extra += "   ✓ visto";
            episodes.rows.add(new String[]{epCode(ep) + "  " + name, extra});
        }
        episodes.notifyDataSetChanged();
        episodesView.scrollToPosition(0);
    }

    private static String epCode(JSONObject ep) {
        return "T" + ep.optInt("season") + "E" + ep.optInt("episode", ep.optInt("number"));
    }

    private String epLabel(JSONObject ep) {
        return meta.optString("name") + " · " + epCode(ep) + " " + ep.optString("name", ep.optString("title"));
    }

    static String fmt(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
                : String.format("%d:%02d", s / 60, s % 60);
    }

    private void openStreams(String videoId, String label) {
        startActivity(new Intent(this, StreamsActivity.class)
                .putExtra("meta", meta.toString())
                .putExtra("videoId", videoId)
                .putExtra("label", label));
    }
}
