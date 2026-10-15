package dev.liviano.tv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Vistas construidas por código: sin XML de layouts ni AppCompat. */
final class Ui {
    static final int ACCENT = 0xFF7B5BF5;
    static final int TEXT = 0xFFEDEDF2;
    static final int MUTED = 0xFF9A98A8;
    static final int CARD = 0xFF1C1B24;

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static Drawable focusBg(Context c, int normal) {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        on.setColor(ACCENT);
        on.setCornerRadius(dp(c, 6));
        GradientDrawable off = new GradientDrawable();
        off.setColor(normal);
        off.setCornerRadius(dp(c, 6));
        s.addState(new int[]{android.R.attr.state_focused}, on);
        s.addState(new int[]{}, off);
        return s;
    }

    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    static TextView button(Context c, String label, View.OnClickListener l) {
        TextView b = text(c, label, 16, TEXT);
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(focusBg(c, CARD));
        b.setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8));
        b.setOnClickListener(l);
        return b;
    }

    static LinearLayout.LayoutParams margins(Context c, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    static void scaleOnFocus(View v) {
        v.setOnFocusChangeListener((view, has) -> {
            float s = has ? 1.08f : 1f;
            view.animate().scaleX(s).scaleY(s).setDuration(90).start();
        });
    }

    /** Fila horizontal de pósters. */
    static RecyclerView posterRow(Activity a) {
        RecyclerView rv = new RecyclerView(a);
        rv.setLayoutManager(new LinearLayoutManager(a, LinearLayoutManager.HORIZONTAL, false));
        rv.setAdapter(new PosterAdapter(a));
        rv.setClipToPadding(false);
        rv.setPadding(dp(a, 8), dp(a, 6), dp(a, 8), dp(a, 6));
        rv.setItemAnimator(null);
        return rv;
    }

    static final class PosterAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final Activity a;
        final List<JSONObject> items = new ArrayList<>();

        PosterAdapter(Activity a) { this.a = a; }

        void set(JSONArray metas) {
            items.clear();
            for (int i = 0; i < metas.length(); i++) {
                JSONObject m = metas.optJSONObject(i);
                if (m != null && !m.optString("id").isEmpty()) items.add(m);
            }
            notifyDataSetChanged();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout card = new LinearLayout(a);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setFocusable(true);
            card.setClickable(true);
            card.setBackground(focusBg(a, Color.TRANSPARENT));
            int pad = dp(a, 3);
            card.setPadding(pad, pad, pad, pad);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(dp(a, 106), ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(dp(a, 4), 0, dp(a, 4), 0);
            card.setLayoutParams(lp);
            ImageView img = new ImageView(a);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setBackgroundColor(CARD);
            card.addView(img, new LinearLayout.LayoutParams(dp(a, 100), dp(a, 150)));
            TextView t = text(a, "", 12, TEXT);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(dp(a, 2), dp(a, 3), dp(a, 2), 0);
            card.addView(t);
            scaleOnFocus(card);
            return new RecyclerView.ViewHolder(card) { };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder h, int pos) {
            JSONObject m = items.get(pos);
            LinearLayout card = (LinearLayout) h.itemView;
            Img.load((ImageView) card.getChildAt(0), m.optString("poster"), dp(a, 100));
            ((TextView) card.getChildAt(1)).setText(m.optString("name"));
            card.setOnClickListener(v -> DetailActivity.open(a, m));
        }

        @Override
        public int getItemCount() { return items.size(); }
    }

    /** Lista vertical de filas de texto (episodios, streams). */
    static final class ListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        interface Click { void on(int position, boolean longPress); }

        private final Activity a;
        final List<String[]> rows = new ArrayList<>();
        private final Click click;

        ListAdapter(Activity a, Click click) {
            this.a = a;
            this.click = click;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            row.setBackground(focusBg(a, CARD));
            row.setPadding(dp(a, 12), dp(a, 8), dp(a, 12), dp(a, 8));
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(a, 3), 0, dp(a, 3));
            row.setLayoutParams(lp);
            TextView title = text(a, "", 16, TEXT);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            row.addView(title);
            TextView sub = text(a, "", 13, MUTED);
            sub.setMaxLines(4);
            row.addView(sub);
            return new RecyclerView.ViewHolder(row) { };
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder h, int pos) {
            String[] r = rows.get(pos);
            LinearLayout row = (LinearLayout) h.itemView;
            ((TextView) row.getChildAt(0)).setText(r[0]);
            TextView sub = (TextView) row.getChildAt(1);
            sub.setText(r[1]);
            sub.setVisibility(r[1] == null || r[1].isEmpty() ? View.GONE : View.VISIBLE);
            row.setOnClickListener(v -> click.on(h.getBindingAdapterPosition(), false));
            row.setOnLongClickListener(v -> {
                click.on(h.getBindingAdapterPosition(), true);
                return true;
            });
        }

        @Override
        public int getItemCount() { return rows.size(); }
    }

    static RecyclerView list(Activity a, ListAdapter adapter) {
        RecyclerView rv = new RecyclerView(a);
        rv.setLayoutManager(new LinearLayoutManager(a));
        rv.setAdapter(adapter);
        rv.setItemAnimator(null);
        return rv;
    }

    static String typeName(String type) {
        if ("movie".equals(type)) return "Películas";
        if ("series".equals(type)) return "Series";
        if ("tv".equals(type)) return "TV";
        if ("channel".equals(type)) return "Canales";
        return type;
    }

    static TextView header(Context c, String s) {
        TextView t = text(c, s, 18, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(dp(c, 12), dp(c, 10), 0, 0);
        t.setGravity(Gravity.START);
        return t;
    }
}
