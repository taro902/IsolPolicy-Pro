package io.github.taro902.isolationpolicy.ui.pixel;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PixelIconQuantizer {

    private static final int CACHE_BYTES = 4 * 1024 * 1024;
    private static final int QUANT_GRID = 16;

    private final LruCache<String, Bitmap> cache;
    private final ExecutorService pool;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean shutdown;

    public PixelIconQuantizer() {
        this.cache = new LruCache<String, Bitmap>(CACHE_BYTES) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getAllocationByteCount();
            }
        };
        this.pool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "IsolIconLoader");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY + 1);
            return t;
        });
    }

    public void loadInto(
            ImageView target,
            PackageManager pm,
            ApplicationInfo appInfo,
            boolean pixelize,
            int sizePx
    ) {
        if (target == null || appInfo == null || shutdown) {
            return;
        }
        String key = (pixelize ? "P:" : "N:") + appInfo.packageName;
        target.setTag(key);

        Bitmap cached = cache.get(key);
        if (cached != null && !cached.isRecycled()) {
            target.setImageBitmap(cached);
            return;
        }

        target.setImageDrawable(null);
        pool.execute(() -> {
            if (shutdown) {
                return;
            }
            try {
                Drawable raw = appInfo.loadIcon(pm);
                Bitmap rendered = renderIcon(raw, pixelize, sizePx);
                if (rendered != null) {
                    cache.put(key, rendered);
                    mainHandler.post(() -> {
                        if (!shutdown && key.equals(target.getTag())) {
                            target.setImageBitmap(rendered);
                        }
                    });
                }
            } catch (Throwable ignored) {
            }
        });
    }

    public void clearCache() {
        cache.evictAll();
    }

    public void shutdown() {
        shutdown = true;
        pool.shutdownNow();
        cache.evictAll();
    }

    private static Bitmap renderIcon(Drawable drawable, boolean pixelize, int sizePx) {
        if (drawable == null || sizePx <= 0) {
            return null;
        }
        if (!pixelize) {
            Bitmap out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            drawable.setBounds(0, 0, sizePx, sizePx);
            drawable.draw(c);
            return out;
        }

        Bitmap tiny = Bitmap.createBitmap(QUANT_GRID, QUANT_GRID, Bitmap.Config.ARGB_8888);
        Canvas tc = new Canvas(tiny);
        drawable.setBounds(0, 0, QUANT_GRID, QUANT_GRID);
        drawable.draw(tc);

        Bitmap out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas oc = new Canvas(out);
        Paint p = new Paint();
        p.setFilterBitmap(false);
        p.setAntiAlias(false);
        p.setDither(false);
        oc.drawBitmap(tiny, new Rect(0, 0, QUANT_GRID, QUANT_GRID), new Rect(0, 0, sizePx, sizePx), p);
        tiny.recycle();
        return out;
    }
}