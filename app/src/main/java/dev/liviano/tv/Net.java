package dev.liviano.tv;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** HTTP mínimo sobre HttpURLConnection, sin librerías externas. */
final class Net {
    static final String UA = "Mozilla/5.0 (Linux; Android 9) Liviano/1.0";
    static final ExecutorService IO = Executors.newFixedThreadPool(4);
    static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Job<T> { T run() throws Exception; }
    interface Cb<T> { void done(T result, Exception error); }

    static <T> void async(Job<T> job, Cb<T> cb) {
        IO.execute(() -> {
            T r = null;
            Exception e = null;
            try { r = job.run(); } catch (Exception ex) { e = ex; }
            final T fr = r;
            final Exception fe = e;
            MAIN.post(() -> cb.done(fr, fe));
        });
    }

    static String get(String url) throws IOException { return request(url, null); }

    static String request(String url, String jsonBody) throws IOException {
        return new String(bytes(url, jsonBody), StandardCharsets.UTF_8);
    }

    static byte[] bytes(String url, String jsonBody) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(10000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", UA);
            if (jsonBody != null) {
                c.setDoOutput(true);
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream o = c.getOutputStream()) {
                    o.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            if (in == null) throw new IOException("HTTP " + code);
            byte[] data = readAll(in);
            if (code >= 400) throw new IOException("HTTP " + code);
            return data;
        } finally {
            c.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = i.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }
}
