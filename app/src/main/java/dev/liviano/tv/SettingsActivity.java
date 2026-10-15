package dev.liviano.tv;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

public class SettingsActivity extends Activity {
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 24), Ui.dp(this, 16), Ui.dp(this, 24), Ui.dp(this, 32));
        scroll.addView(root);
        setContentView(scroll);
        build();
    }

    private void build() {
        root.removeAllViews();
        section("Cuenta de Stremio");
        if (Store.authKey(this) != null) {
            note("Conectado como " + Store.email(this) + ". Tus addons y \"Seguir viendo\" se sincronizan.");
            LinearLayout row = row();
            row.addView(Ui.button(this, "Recargar addons", v -> syncAddons()), Ui.margins(this, 0, 0, 10, 0));
            row.addView(Ui.button(this, "Cerrar sesión", v -> {
                Store.setSession(this, null, "");
                Store.setAddons(this, new JSONArray());
                Lib.items.clear();
                Store.dirty = true;
                build();
            }));
        } else {
            note("Inicia sesión para usar los mismos addons que en Stremio.");
            EditText email = field("Correo", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, "");
            EditText pass = field("Contraseña", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, "");
            row().addView(Ui.button(this, "Entrar", v -> login(email.getText().toString().trim(), pass.getText().toString())));
        }

        section("Addons instalados");
        StringBuilder sb = new StringBuilder();
        for (Addon a : Addon.all(this)) sb.append("• ").append(a.name()).append('\n');
        note(sb.length() == 0 ? "(ninguno)" : sb.toString().trim());
        EditText addUrl = field("URL de manifest para agregar un addon", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, "");
        row().addView(Ui.button(this, "Agregar addon", v -> addAddon(addUrl.getText().toString().trim())));

        section("Subtítulos");
        EditText lang = field("Idioma preferido (spa, eng, por…)", InputType.TYPE_CLASS_TEXT, Store.subLang(this));
        row().addView(Ui.button(this, "Guardar idioma", v -> {
            Store.setSubLang(this, lang.getText().toString().trim().toLowerCase());
            Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show();
        }));

        section("Servidor de torrents (opcional)");
        note("Solo para fuentes torrent sin debrid. Ej: http://IP-DE-TU-PC:11470 (servidor de Stremio en tu PC).");
        EditText server = field("http://IP:11470", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, Store.server(this));
        row().addView(Ui.button(this, "Guardar servidor", v -> {
            Store.setServer(this, server.getText().toString().trim());
            Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show();
        }));
    }

    private void login(String email, String pass) {
        if (email.isEmpty() || pass.isEmpty()) return;
        Toast.makeText(this, "Entrando…", Toast.LENGTH_SHORT).show();
        Net.async(() -> {
            String key = Lib.login(email, pass);
            Store.setSession(this, key, email);
            return Lib.syncAddons(this);
        }, (n, e) -> {
            if (isDestroyed()) return;
            if (e != null) {
                Store.setSession(this, null, "");
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, "Listo: " + n + " addons", Toast.LENGTH_SHORT).show();
            Store.dirty = true;
            build();
        });
    }

    private void syncAddons() {
        Net.async(() -> Lib.syncAddons(this), (n, e) -> {
            if (isDestroyed()) return;
            Toast.makeText(this, e != null ? "Error: " + e.getMessage() : n + " addons", Toast.LENGTH_SHORT).show();
            Store.dirty = true;
            build();
        });
    }

    private void addAddon(String url) {
        if (url.isEmpty()) return;
        String u = url.replaceFirst("^stremio://", "https://");
        if (!u.endsWith("manifest.json")) u = u.replaceAll("/+$", "") + "/manifest.json";
        String fu = u;
        Net.async(() -> Addon.fetchEntry(fu), (entry, e) -> {
            if (isDestroyed()) return;
            if (e != null) {
                Toast.makeText(this, "No se pudo agregar: " + e.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            JSONArray arr = Store.addons(this);
            arr.put(entry);
            Store.setAddons(this, arr);
            Store.dirty = true;
            build();
        });
    }

    private void section(String s) {
        TextView t = Ui.text(this, s, 18, Ui.ACCENT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 4));
        root.addView(t);
    }

    private void note(String s) { root.addView(Ui.text(this, s, 14, Ui.MUTED)); }

    private EditText field(String hint, int type, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setText(value);
        e.setTextColor(Ui.TEXT);
        e.setHintTextColor(Ui.MUTED);
        root.addView(e);
        return e;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, Ui.dp(this, 6), 0, 0);
        root.addView(r);
        return r;
    }
}
