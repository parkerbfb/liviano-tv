package dev.liviano.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cargador de pósters reducido (RGB_565 + submuestreo) para no gastar RAM. */
final class Img {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);

    static void load(ImageView v, String url, int targetWidthPx) {
        v.setTag(url);
        v.setImageDrawable(null);
        if (url == null || url.isEmpty() || "null".equals(url)) return;
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            v.setImageBitmap(cached);
            return;
        }
        POOL.execute(() -> {
            if (!url.equals(v.getTag())) return;
            try {
                byte[] data = Net.bytes(url, null);
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, o);
                int sample = 1;
                while (o.outWidth / (sample * 2) >= targetWidthPx) sample *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap b = BitmapFactory.decodeByteArray(data, 0, data.length, o);
                if (b == null) return;
                CACHE.put(url, b);
                Net.MAIN.post(() -> {
                    if (url.equals(v.getTag())) v.setImageBitmap(b);
                });
            } catch (Exception ignored) {
            }
        });
    }
}
