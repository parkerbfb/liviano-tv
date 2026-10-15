package dev.liviano.tv;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

public class SearchActivity extends Activity {
    private LinearLayout rows;
    private TextView status;
    private int generation;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 24));
        scroll.addView(root);

        EditText q = new EditText(this);
        q.setHint("Buscar películas y series…");
        q.setTextColor(Ui.TEXT);
        q.setHintTextColor(Ui.MUTED);
        q.setSingleLine(true);
        q.setInputType(InputType.TYPE_CLASS_TEXT);
        q.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        q.setOnEditorActionListener((v, actionId, ev) -> {
            boolean enter = ev != null && ev.getKeyCode() == KeyEvent.KEYCODE_ENTER && ev.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || enter) {
                search(v.getText().toString().trim());
                return true;
            }
            return false;
        });
        root.addView(q);
        status = Ui.text(this, "", 14, Ui.MUTED);
        root.addView(status);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        root.addView(rows);
        setContentView(scroll);
        q.requestFocus();
    }

    private void search(String query) {
        if (query.isEmpty()) return;
        int gen = ++generation;
        rows.removeAllViews();
        status.setText("Buscando…");
        String extra = "search=" + Addon.enc(query);
        for (Addon addon : Addon.all(this)) {
            JSONArray cats = addon.manifest.optJSONArray("catalogs");
            if (cats == null) continue;
            for (int i = 0; i < cats.length(); i++) {
                JSONObject cat = cats.optJSONObject(i);
                if (cat == null || !Addon.catalogSearchable(cat)) continue;
                String type = cat.optString("type");
                LinearLayout block = new LinearLayout(this);
                block.setOrientation(LinearLayout.VERTICAL);
                block.addView(Ui.header(this, cat.optString("name", addon.name()) + " · " + Ui.typeName(type)));
                RecyclerView rv = Ui.posterRow(this);
                block.addView(rv);
                rows.addView(block);
                String url = addon.url("catalog", type, cat.optString("id"), extra);
                Net.async(() -> new JSONObject(Net.get(url)).optJSONArray("metas"), (metas, err) -> {
                    if (isDestroyed() || gen != generation) return;
                    status.setText("");
                    if (metas == null || metas.length() == 0) rows.removeView(block);
                    else ((Ui.PosterAdapter) rv.getAdapter()).set(metas);
                });
            }
        }
    }
}
