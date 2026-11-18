package it.blinx.demo.v1;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** A single fixed preview slot backed by rendered images of the original models. */
public final class MonsterCarousel extends FrameLayout {
    private static final String TAG = "MonsterCarousel";
    private static final long INTERVAL_MS = 2000;
    private static final long FADE_MS = 220;
    private static final int MAX_BITMAP_EDGE = 512;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ImageView front;
    private ImageView back;
    private final TextView label;
    private final List<Entry> entries = new ArrayList<>();
    private ExecutorService decoder;
    private Future<?> pending;
    private boolean attached;
    private boolean enabled = true;
    private boolean running;
    private boolean showLabels = true;
    private long generation;
    private int current = -1;
    private final Runnable advance = this::loadNext;

    private static final class Entry {
        final String name;
        final String asset;
        Entry(String name, String asset) { this.name = name; this.asset = asset; }
    }
    public MonsterCarousel(Context context) { this(context, null); }
    public MonsterCarousel(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClipChildren(true);
        setClipToPadding(true);
        front = image(context);
        back = image(context);
        back.setAlpha(0f);
        label = new TextView(context);
        label.setTextColor(Color.rgb(237, 241, 245));
        label.setTextSize(13);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        FrameLayout.LayoutParams caption = new FrameLayout.LayoutParams(-1, dp(36), Gravity.BOTTOM);
        addView(label, caption);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private ImageView image(Context context) {
        ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -1);
        params.bottomMargin = dp(36);
        addView(view, params);
        return view;
    }
    public void setShowLabels(boolean show) {
        showLabels = show;
        label.setVisibility(show ? VISIBLE : GONE);
        for (ImageView view : new ImageView[] {front, back}) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)view.getLayoutParams();
            params.bottomMargin = show ? dp(36) : 0;
            view.setLayoutParams(params);
        }
    }
    /** Call from onResume if the owner explicitly called stop in onPause. */
    public void start() {
        enabled = true;
        resumeIfVisible();
    }
    /** Cancels decoding/animation and releases drawable references immediately. */
    public void stop() {
        enabled = false;
        suspend(true);
    }
    /** Clears images and requests worker shutdown; late decode results are discarded. */
    public void release() {
        enabled = false;
        suspend(true);
        if (decoder != null) { decoder.shutdownNow(); decoder = null; }
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        resumeIfVisible();
    }
    @Override protected void onDetachedFromWindow() {
        attached = false;
        suspend(true);
        if (decoder != null) { decoder.shutdownNow(); decoder = null; }
        super.onDetachedFromWindow();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (handler == null || label == null) return;
        if (visibility == VISIBLE) resumeIfVisible(); else suspend(true);
    }
    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (handler == null || label == null) return;
        if (isShown()) resumeIfVisible(); else suspend(true);
    }
    private void resumeIfVisible() {
        if (!enabled || !attached || running || getWindowVisibility() != VISIBLE || !isShown()) return;
        if (decoder == null || decoder.isShutdown()) decoder = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "Blinx-model-preview"); thread.setDaemon(true); return thread;
        });
        running = true;
        generation++;
        handler.post(advance);
    }
    private void suspend(boolean clearImages) {
        running = false;
        generation++;
        handler.removeCallbacks(advance);
        if (pending != null) { pending.cancel(true); pending = null; }
        if (front != null) front.animate().cancel();
        if (back != null) back.animate().cancel();
        if (clearImages && front != null && back != null) {
            front.setImageDrawable(null); back.setImageDrawable(null);
            front.setAlpha(1f); back.setAlpha(0f);
        }
    }
    private void loadNext() {
        if (!running || decoder == null || pending != null) return;
        final long request = generation;
        final int requestedIndex = current + 1;
        final List<Entry> existing = new ArrayList<>(entries);
        pending = decoder.submit(() -> {
            Bitmap image = null;
            List<Entry> available = existing;
            try {
                available = existing.isEmpty() ? readIndex() : existing;
                if (available.isEmpty()) throw new IOException("No verified model previews");
                final int index = requestedIndex % available.size();
                final Entry entry = available.get(index);
                final List<Entry> loadedEntries = available;
                image = decode(entry.asset);
                if (image == null) throw new IOException("Cannot decode " + entry.asset);
                final Bitmap ready = image;
                handler.post(() -> {
                    if (!running || request != generation) { ready.recycle(); return; }
                    pending = null;
                    if (entries.isEmpty()) entries.addAll(loadedEntries);
                    current = index;
                    show(ready, entry.name);
                    handler.postDelayed(advance, INTERVAL_MS);
                });
            } catch (Exception error) {
                if (image != null) image.recycle();
                Log.w(TAG, "Preview unavailable", error);
                final List<Entry> loadedEntries = available;
                handler.post(() -> {
                    if (!running || request != generation) return;
                    pending = null;
                    // A bad entry cannot lock the carousel. Keep the current frame.
                    if (entries.isEmpty()) entries.addAll(loadedEntries);
                    if (!entries.isEmpty()) current = requestedIndex % entries.size();
                    handler.postDelayed(advance, INTERVAL_MS);
                });
            }
        });
    }
    private void show(Bitmap image, String name) {
        front.animate().cancel(); back.animate().cancel();
        boolean first = front.getDrawable() == null;
        back.setImageBitmap(image);
        back.setAlpha(first ? 1f : 0f);
        ImageView outgoing = front;
        front = back;
        back = outgoing;
        label.setText(name);
        label.setVisibility(showLabels ? VISIBLE : GONE);
        setContentDescription(name);
        if (first) { back.setImageDrawable(null); back.setAlpha(0f); return; }
        front.animate().alpha(1f).setDuration(FADE_MS).start();
        back.animate().alpha(0f).setDuration(FADE_MS).withEndAction(() -> {
            if (back == outgoing) outgoing.setImageDrawable(null);
        }).start();
        // Visible bitmaps are released by dropping the drawable references after
        // the fade, never recycled while Android's render thread may use them.
    }
    private List<Entry> readIndex() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream in = getContext().getAssets().open("monsters/index.json")) {
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) >= 0) {
                if (n == 0) continue;
                if (bytes.size() + n > 262144) throw new IOException("Preview index too large");
                bytes.write(buffer, 0, n);
            }
        }
        JSONArray items = new JSONObject(bytes.toString("UTF-8")).getJSONArray("items");
        if (items.length() > 256) throw new IOException("Too many previews");
        List<Entry> result = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String asset = item.getString("asset");
            if (!asset.matches("monsters/[a-zA-Z0-9_-]+\\.png")) throw new IOException("Unsafe preview asset path");
            result.add(new Entry(item.getString("name"), asset));
        }
        return result;
    }
    private Bitmap decode(String asset) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream in = getContext().getAssets().open(asset)) { BitmapFactory.decodeStream(in, null, options); }
        if (options.outWidth < 1 || options.outHeight < 1 || options.outWidth > 4096 || options.outHeight > 4096)
            throw new IOException("Invalid preview dimensions");
        int sample = 1;
        while (Math.max(options.outWidth, options.outHeight) / sample > MAX_BITMAP_EDGE) sample *= 2;
        options.inSampleSize = sample;
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream in = getContext().getAssets().open(asset)) { return BitmapFactory.decodeStream(in, null, options); }
    }
}
