package dev.liviano.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Reproductor ExoPlayer con decodificación por hardware y subtítulos de addons. */
@OptIn(markerClass = UnstableApi.class)
public class PlayerActivity extends Activity {
    private ExoPlayer player;
    private PlayerView view;
    private LinearLayout overlay;
    private JSONObject meta;
    private String url, videoId;
    private final List<JSONObject> subs = new ArrayList<>();
    private JSONObject chosenSub;
    private String pendingSubId;
    private boolean started;
    private long resumeAt;
    private final Runnable saver = new Runnable() {
        @Override
        public void run() {
            save(false);
            Net.MAIN.postDelayed(this, 15000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            meta = new JSONObject(getIntent().getStringExtra("meta"));
        } catch (Exception e) {
            finish();
            return;
        }
        url = getIntent().getStringExtra("url");
        videoId = getIntent().getStringExtra("videoId");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        view = new PlayerView(this);
        view.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        view.setShowSubtitleButton(true);
        view.setKeepContentOnPlayerReset(true);
        if (view.getSubtitleView() != null) {
            view.getSubtitleView().setFractionalTextSize(0.065f);
            view.getSubtitleView().setStyle(new CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null));
        }
        root.addView(view);

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.HORIZONTAL);
        overlay.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), 0);
        TextView label = Ui.text(this, getIntent().getStringExtra("label"), 16, Color.WHITE);
        label.setShadowLayer(4, 0, 0, Color.BLACK);
        overlay.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        overlay.addView(Ui.button(this, "Subtítulos", v -> showSubtitleMenu()));
        root.addView(overlay, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        view.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) vis -> overlay.setVisibility(vis));
        setContentView(root);

        buildPlayer();
        long[] p = Lib.resume(this, meta.optString("id"), videoId);
        if (p != null && p[0] > 0 && (p[1] <= 0 || p[0] < p[1] * 0.95)) {
            resumeAt = p[0];
            Toast.makeText(this, "Continuando desde " + DetailActivity.fmt(resumeAt), Toast.LENGTH_SHORT).show();
        }
        loadSubtitles();
        Net.MAIN.postDelayed(this::startIfNeeded, 3500);
    }

    private void buildPlayer() {
        Map<String, String> headers = new HashMap<>();
        try {
            String h = getIntent().getStringExtra("headers");
            if (h != null) {
                JSONObject o = new JSONObject(h);
                for (Iterator<String> it = o.keys(); it.hasNext(); ) {
                    String k = it.next();
                    headers.put(k, o.optString(k));
                }
            }
        } catch (Exception ignored) {
        }
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(headers.containsKey("User-Agent") ? headers.get("User-Agent") : Net.UA)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(30000)
                .setDefaultRequestProperties(headers);
        // Búfer acotado: la tele tiene ~512 MB de RAM.
        DefaultLoadControl load = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000, 40000, 2500, 5000)
                .setTargetBufferBytes(48 * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build();
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(this)
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
        player = new ExoPlayer.Builder(this, renderers)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this, http)))
                .setLoadControl(load)
                .build();
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setPreferredTextLanguage(Store.subLang(this))
                .build());
        player.addListener(new Player.Listener() {
            @Override
            public void onTracksChanged(Tracks tracks) { applyPendingSubtitle(tracks); }

            @Override
            public void onPlayerError(PlaybackException error) { showError(error); }
        });
        view.setPlayer(player);
    }

    private void loadSubtitles() {
        try {
            String s = getIntent().getStringExtra("subs");
            if (s != null) {
                JSONArray arr = new JSONArray(s);
                for (int i = 0; i < arr.length(); i++) if (arr.optJSONObject(i) != null) subs.add(arr.optJSONObject(i));
            }
        } catch (Exception ignored) {
        }
        String type = meta.optString("type");
        List<Addon> sources = new ArrayList<>();
        for (Addon a : Addon.all(this)) if (a.supports("subtitles", type, videoId)) sources.add(a);
        if (sources.isEmpty()) {
            startIfNeeded();
            return;
        }
        final int[] left = {sources.size()};
        for (Addon a : sources) {
            String u = a.url("subtitles", type, videoId, null);
            Net.async(() -> new JSONObject(Net.get(u)).optJSONArray("subtitles"), (arr, e) -> {
                if (isDestroyed()) return;
                if (arr != null) for (int i = 0; i < arr.length(); i++) if (arr.optJSONObject(i) != null) subs.add(arr.optJSONObject(i));
                if (--left[0] == 0) startIfNeeded();
            });
        }
    }

    /** Arranca cuando llegan los subtítulos (o tras 3,5 s), eligiendo uno del idioma preferido. */
    private void startIfNeeded() {
        if (started || isDestroyed()) return;
        started = true;
        String lang = Store.subLang(this);
        for (JSONObject s : subs) {
            if (langMatches(s.optString("lang"), lang)) {
                chosenSub = s;
                break;
            }
        }
        setMedia(resumeAt);
        Net.MAIN.postDelayed(saver, 15000);
    }

    private static boolean langMatches(String a, String pref) {
        if (a == null || a.isEmpty()) return false;
        a = a.toLowerCase();
        pref = pref.toLowerCase();
        if (a.equals(pref) || a.startsWith(pref)) return true;
        if (pref.equals("spa") || pref.equals("es")) return a.equals("es") || a.equals("spa") || a.equals("spl") || a.equals("esp");
        if (pref.equals("eng") || pref.equals("en")) return a.equals("en") || a.equals("eng");
        return false;
    }

    private static String subId(JSONObject s) {
        String id = s.optString("id");
        return id.isEmpty() ? "s" + Integer.toHexString(s.optString("url").hashCode()) : id;
    }

    private void setMedia(long position) {
        MediaItem.Builder item = new MediaItem.Builder().setUri(url);
        if (chosenSub != null) {
            String su = chosenSub.optString("url");
            String lower = su.toLowerCase();
            String mime = lower.contains(".vtt") ? MimeTypes.TEXT_VTT
                    : (lower.contains(".ass") || lower.contains(".ssa")) ? MimeTypes.TEXT_SSA
                    : MimeTypes.APPLICATION_SUBRIP;
            pendingSubId = subId(chosenSub);
            item.setSubtitleConfigurations(Collections.singletonList(
                    new MediaItem.SubtitleConfiguration.Builder(Uri.parse(su))
                            .setId(pendingSubId)
                            .setMimeType(mime)
                            .setLanguage(chosenSub.optString("lang"))
                            .setLabel(chosenSub.optString("lang") + " (addon)")
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()));
        }
        player.setMediaItem(item.build(), position);
        player.prepare();
        player.setPlayWhenReady(true);
    }

    private void applyPendingSubtitle(Tracks tracks) {
        if (pendingSubId == null) return;
        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < g.length; i++) {
                Format f = g.getTrackFormat(i);
                if (f.id != null && f.id.endsWith(pendingSubId)) {
                    player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(), i))
                            .build());
                    pendingSubId = null;
                    return;
                }
            }
        }
    }

    private void showSubtitleMenu() {
        if (player == null) return;
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("Desactivar subtítulos");
        actions.add(() -> player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()));

        for (Tracks.Group g : player.getCurrentTracks().getGroups()) {
            if (g.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < g.length; i++) {
                Format f = g.getTrackFormat(i);
                if (f.id != null && chosenSub != null && f.id.endsWith(subId(chosenSub))) continue;
                String name = f.label != null ? f.label : (f.language != null ? f.language : "Pista " + (i + 1));
                labels.add("Incluido: " + name + (g.isTrackSelected(i) ? "  ✓" : ""));
                final int idx = i;
                actions.add(() -> player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(new TrackSelectionOverride(g.getMediaTrackGroup(), idx)).build()));
            }
        }

        String pref = Store.subLang(this);
        List<JSONObject> sorted = new ArrayList<>(subs);
        sorted.sort((a, b) -> Boolean.compare(!langMatches(a.optString("lang"), pref), !langMatches(b.optString("lang"), pref)));
        Map<String, Integer> count = new HashMap<>();
        for (JSONObject s : sorted) {
            String lang = s.optString("lang", "?");
            int n = count.merge(lang, 1, Integer::sum);
            boolean active = chosenSub != null && subId(chosenSub).equals(subId(s));
            labels.add(lang + " #" + n + (active ? "  ✓" : ""));
            actions.add(() -> {
                chosenSub = s;
                player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build());
                setMedia(player.getCurrentPosition());
            });
        }

        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Subtítulos")
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .show();
    }

    private void showError(PlaybackException error) {
        if (isDestroyed()) return;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("No se pudo reproducir")
                .setMessage(error.getErrorCodeName() + "\n\nPrueba otra fuente o ábrela con el reproductor externo.")
                .setPositiveButton("Reproductor externo", (d, w) -> {
                    try {
                        startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW)
                                .setDataAndType(Uri.parse(url), "video/*"));
                    } catch (Exception ignored) {
                    }
                    finish();
                })
                .setNegativeButton("Volver", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_CAPTIONS) {
            showSubtitleMenu();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void save(boolean push) {
        if (player == null || !started) return;
        long dur = player.getDuration();
        long pos = player.getCurrentPosition();
        if (dur == C.TIME_UNSET || dur <= 0 || pos < 5000) return;
        Lib.saveProgress(this, meta, videoId, pos, dur, push);
    }

    @Override
    protected void onPause() {
        super.onPause();
        save(true);
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Net.MAIN.removeCallbacks(saver);
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
