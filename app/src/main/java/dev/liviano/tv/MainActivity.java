package dev.liviano.tv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

/** Inicio: "Seguir viendo" + catálogos de los addons. */
public class MainActivity extends Activity {
    private static final int MAX_ROWS = 14;
    private LinearLayout rows;
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 24));
        scroll.addView(root);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        TextView logo = Ui.text(this, "Liviano", 22, Ui.ACCENT);
        logo.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(logo, Ui.margins(this, 8, 0, 24, 0));
        TextView search = Ui.button(this, "Buscar", v -> startActivity(new Intent(this, SearchActivity.class)));
        bar.addView(search, Ui.margins(this, 0, 0, 10, 0));
        bar.addView(Ui.button(this, "Ajustes", v -> startActivity(new Intent(this, SettingsActivity.class))));
        root.addView(bar);

        status = Ui.text(this, "Cargando…", 14, Ui.MUTED);
        status.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), 0, 0);
        root.addView(status);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        root.addView(rows);
        setContentView(scroll);
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Store.dirty) {
            Store.dirty = false;
            reload();
        }
    }

    private void reload() {
        rows.removeAllViews();
        status.setVisibility(View.VISIBLE);
        status.setText("Cargando…");
        Net.async(() -> {
            Addon.ensure(this);
            try { Lib.refresh(this); } catch (Exception ignored) { }
            return null;
        }, (r, e) -> {
            if (isDestroyed()) return;
            if (e != null) {
                status.setText("Sin conexión: " + e.getMessage());
                return;
            }
            status.setVisibility(View.GONE);
            buildRows();
        });
    }

    private void buildRows() {
        JSONArray cw = Lib.continueWatching(this);
        if (cw.length() > 0) addRow("Seguir viendo").set(cw);

        int count = 0;
        for (Addon addon : Addon.all(this)) {
            JSONArray cats = addon.manifest.optJSONArray("catalogs");
            if (cats == null) continue;
            for (int i = 0; i < cats.length() && count < MAX_ROWS; i++) {
                JSONObject cat = cats.optJSONObject(i);
                if (cat == null || Addon.catalogNeedsExtra(cat)) continue;
                count++;
                String type = cat.optString("type");
                String title = cat.optString("name", addon.name()) + " · " + Ui.typeName(type);
                Ui.PosterAdapter ad = addRow(title);
                String url = addon.url("catalog", type, cat.optString("id"), null);
                Net.async(() -> new JSONObject(Net.get(url)).optJSONArray("metas"), (metas, err) -> {
                    if (isDestroyed()) return;
                    if (metas == null || metas.length() == 0) removeRow(ad);
                    else ad.set(metas);
                });
            }
        }
    }

    private Ui.PosterAdapter addRow(String title) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.addView(Ui.header(this, title));
        RecyclerView rv = Ui.posterRow(this);
        block.addView(rv);
        rows.addView(block);
        block.setTag(rv.getAdapter());
        return (Ui.PosterAdapter) rv.getAdapter();
    }

    private void removeRow(Ui.PosterAdapter ad) {
        for (int i = 0; i < rows.getChildCount(); i++) {
            if (rows.getChildAt(i).getTag() == ad) {
                rows.removeViewAt(i);
                return;
            }
        }
    }
}
