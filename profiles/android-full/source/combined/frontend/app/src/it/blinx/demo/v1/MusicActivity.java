package it.blinx.demo.v1;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline playback of the original level-entry scores. No services or network. */
public final class MusicActivity extends Activity {
    private static final String ROOT = "music/";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ArrayList<Track> tracks = new ArrayList<Track>();
    private final ArrayList<LinearLayout> rows = new ArrayList<LinearLayout>();
    private MediaPlayer player;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private BackgroundView background;
    private TextView nowPlaying, stateText, elapsed, total;
    private Button playButton;
    private SeekBar seek;
    private int selected = 0, resumeMs = 0;
    private volatile int generation = 0;
    private boolean ready, playing, seeking, disposed;

    private static final class Track {
        final String title, asset, background, role;
        final int durationMs;
        Track(JSONObject value) throws Exception {
            title = value.getString("title");
            asset = value.getString("asset");
            background = value.getString("background");
            role = value.getString("role");
            durationMs = value.getInt("duration_ms");
            if (!asset.matches("music/audio/[A-Za-z0-9._-]+\\.mp3") ||
                    !background.matches("music/backgrounds/ssl_r[0-9]+\\.png")) {
                throw new IllegalArgumentException("Invalid local media path");
            }
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        MenuMusic.suspendForOtherAudio();
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        try {
            JSONObject catalog = new JSONObject(readText(ROOT + "catalog.json"));
            JSONArray data = catalog.getJSONArray("tracks");
            for (int i = 0; i < data.length(); i++) tracks.add(new Track(data.getJSONObject(i)));
            if (tracks.size() != 9) throw new IllegalStateException("Expected nine original entry scores");
            if (state != null) {
                selected = Math.max(0, Math.min(tracks.size() - 1, state.getInt("selected", 0)));
                resumeMs = Math.max(0, state.getInt("position", 0));
            }
            createUi();
            int savedPosition = resumeMs;
            choose(selected, false);
            resumeMs = Math.min(savedPosition, tracks.get(selected).durationMs);
            seek.setProgress(resumeMs);
            elapsed.setText(time(resumeMs));
            handler.post(progress);
        } catch (Exception error) {
            TextView message = text("Music is unavailable.\nReturn and reopen this screen.", 20, Color.WHITE);
            message.setGravity(Gravity.CENTER);
            message.setBackgroundColor(Color.rgb(14, 27, 43));
            setContentView(message);
        }
    }

    private void createUi() throws Exception {
        FrameLayout root = new FrameLayout(this);
        background = new BackgroundView();
        root.addView(background, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackgroundColor(0xa80a1526);
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(5), dp(12), dp(7));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            content.setPadding(dp(12) + insets.getSystemWindowInsetLeft(), dp(5) + insets.getSystemWindowInsetTop(),
                    dp(12) + insets.getSystemWindowInsetRight(), dp(7) + insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Button back = button("Back", false);
        back.setContentDescription("Back to the launcher");
        back.setOnClickListener(view -> closeMusic());
        header.addView(back, new LinearLayout.LayoutParams(dp(78), dp(44)));
        TextView heading = text("Music", 24, 0xffffc966);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setMaxLines(1);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, -2, 1);
        headingParams.leftMargin = dp(14);
        header.addView(heading, headingParams);
        ImageView logo = BlinxArt.image(this, "logo_pal");
        logo.setContentDescription("Blinx The Time Sweeper");
        header.addView(logo, new LinearLayout.LayoutParams(dp(142), dp(49)));
        content.addView(header, new LinearLayout.LayoutParams(-1, dp(53)));

        // Both columns receive the same remaining height below the compact header.
        // The player cannot consume the list's height on a short landscape display.
        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams columnsParams = new LinearLayout.LayoutParams(-1, 0, 1);
        columnsParams.topMargin = dp(6);
        content.addView(columns, columnsParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setContentDescription("Original stage entry tracks");
        scroll.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, 0, dp(4), dp(4));
        for (int i = 0; i < tracks.size(); i++) {
            final int index = i;
            Track track = tracks.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(44));
            row.setClickable(true);
            row.setFocusable(true);
            row.setContentDescription(track.title + ", " + time(track.durationMs));
            row.setOnClickListener(view -> choose(index, true));
            TextView label = text(String.format(Locale.US, "%02d  %s", i + 1, track.title), 15, Color.WHITE);
            label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            label.setMaxLines(2);
            label.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            TextView detail = text(time(track.durationMs), 13, 0xffd0e0ec);
            detail.setPadding(dp(6), 0, 0, 0);
            row.addView(detail, new LinearLayout.LayoutParams(-2, -2));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(5);
            list.addView(row, rowParams);
            rows.add(row);
        }
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        columns.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1.12f));

        ScrollView playerScroll = new ScrollView(this);
        playerScroll.setFillViewport(true);
        LinearLayout.LayoutParams playerParams = new LinearLayout.LayoutParams(0, -1, 1);
        playerParams.leftMargin = dp(12);
        columns.addView(playerScroll, playerParams);
        LinearLayout playerPanel = new LinearLayout(this);
        playerPanel.setOrientation(LinearLayout.VERTICAL);
        playerPanel.setPadding(dp(14), dp(10), dp(14), dp(10));
        playerPanel.setBackgroundColor(0xb614263a);
        playerScroll.addView(playerPanel, new ScrollView.LayoutParams(-1, -2));

        TextView nowLabel = text("Selected track", 13, 0xffc3d8e9);
        playerPanel.addView(nowLabel);
        nowPlaying = text("", 21, Color.WHITE);
        nowPlaying.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        nowPlaying.setPadding(0, dp(2), 0, 0);
        playerPanel.addView(nowPlaying);
        stateText = text("Choose a track", 14, 0xffc3d8e9);
        stateText.setPadding(0, dp(2), 0, dp(8));
        stateText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        playerPanel.addView(stateText);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        playButton = button("Play", true);
        playButton.setOnClickListener(view -> togglePlayback());
        controls.addView(playButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button stop = button("Stop", false);
        stop.setOnClickListener(view -> { releasePlayer(true); stateText.setText("Stopped"); updateControls(); });
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        stopParams.leftMargin = dp(8);
        controls.addView(stop, stopParams);
        playerPanel.addView(controls);
        seek = new SeekBar(this);
        seek.setContentDescription("Track position");
        seek.setProgressTintList(ColorStateList.valueOf(0xffffc966));
        seek.setThumbTintList(ColorStateList.valueOf(0xffffc966));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar bar) { seeking = true; }
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) { if (fromUser) elapsed.setText(time(value)); }
            public void onStopTrackingTouch(SeekBar bar) {
                resumeMs = bar.getProgress();
                if (player != null && ready) player.seekTo(resumeMs);
                seeking = false;
            }
        });
        playerPanel.addView(seek, new LinearLayout.LayoutParams(-1, dp(38)));
        LinearLayout times = new LinearLayout(this);
        elapsed = text("0:00", 13, 0xffd4e4f0);
        total = text("0:00", 13, 0xffd4e4f0);
        total.setGravity(Gravity.END);
        times.addView(elapsed, new LinearLayout.LayoutParams(0, -2, 1));
        times.addView(total, new LinearLayout.LayoutParams(0, -2, 1));
        playerPanel.addView(times);
        setContentView(root);
        root.requestApplyInsets();
    }

    private void choose(int index, boolean start) {
        releasePlayer(true);
        selected = index;
        Track track = tracks.get(index);
        nowPlaying.setText(track.title);
        stateText.setText(start ? "Loading..." : "Ready");
        seek.setMax(track.durationMs);
        seek.setProgress(0);
        elapsed.setText("0:00");
        total.setText(time(track.durationMs));
        try { background.setImage(readBitmap(track.background)); } catch (Exception ignored) { background.setImage(null); }
        for (int i = 0; i < rows.size(); i++) {
            LinearLayout row = rows.get(i);
            row.setBackground(BlinxArt.frame(this, i == index));
            row.setPadding(dp(12), dp(5), dp(12), dp(5));
            row.setSelected(i == index);
        }
        updateControls();
        if (start) startSelected();
    }

    private void togglePlayback() {
        if (player != null && ready) {
            if (playing) { player.pause(); playing = false; stateText.setText("Paused"); }
            else if (requestFocus()) { player.start(); playing = true; stateText.setText("Playing"); }
            updateControls();
        } else startSelected();
    }

    private void startSelected() {
        releasePlayer(false);
        final int token = ++generation;
        final Track track = tracks.get(selected);
        stateText.setText("Loading...");
        playButton.setEnabled(false);
        try {
            AssetFileDescriptor descriptor = getAssets().openFd(track.asset);
            MediaPlayer media = new MediaPlayer();
            try { media.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength()); }
            catch (Exception error) { media.release(); throw error; }
            finally { descriptor.close(); }
            prepare(media, token);
        } catch (Exception compressedAsset) {
            // AssetFileDescriptor requires an uncompressed ZIP entry. Keep the fallback private.
            io.execute(() -> {
                try {
                    File file = new File(getCacheDir(), "blinx-selected-music.mp3");
                    try (InputStream source = getAssets().open(track.asset); FileOutputStream target = new FileOutputStream(file)) {
                        byte[] bytes = new byte[65536]; int count;
                        while ((count = source.read(bytes)) != -1) {
                            if (disposed || generation != token) return;
                            target.write(bytes, 0, count);
                        }
                    }
                    handler.post(() -> {
                        if (disposed || generation != token) return;
                        MediaPlayer media = new MediaPlayer();
                        try { media.setDataSource(file.getAbsolutePath()); prepare(media, token); }
                        catch (Exception error) { media.release(); fail(token); }
                    });
                } catch (Exception error) { handler.post(() -> fail(token)); }
            });
        }
    }

    private void prepare(final MediaPlayer media, final int token) {
        if (disposed || generation != token) { media.release(); return; }
        player = media;
        media.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        media.setLooping(false);
        media.setOnPreparedListener(value -> {
            if (disposed || generation != token || player != value) return;
            ready = true;
            seek.setMax(value.getDuration());
            total.setText(time(value.getDuration()));
            if (resumeMs > 0) value.seekTo(Math.min(resumeMs, value.getDuration()));
            if (requestFocus()) { value.start(); playing = true; stateText.setText("Playing"); }
            else { stateText.setText("Ready"); }
            updateControls();
        });
        media.setOnCompletionListener(value -> {
            if (player != value) return;
            releasePlayer(true); stateText.setText("Finished"); updateControls();
        });
        media.setOnErrorListener((value, what, extra) -> { fail(token); return true; });
        media.prepareAsync();
    }

    private boolean requestFocus() {
        if (audioManager == null) return true;
        if (focusRequest == null) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setOnAudioFocusChangeListener(change -> {
                        if (change == AudioManager.AUDIOFOCUS_LOSS) { releasePlayer(false); stateText.setText("Paused"); updateControls(); }
                        else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                            if (player != null && ready && playing) { player.pause(); playing = false; stateText.setText("Paused"); updateControls(); }
                        }
                    }, handler).build();
        }
        return audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void fail(int token) {
        if (disposed || generation != token) return;
        releasePlayer(false); stateText.setText("Unable to play this track"); updateControls();
    }

    private void releasePlayer(boolean reset) {
        generation++;
        if (player != null) {
            if (ready && !reset) { try { resumeMs = player.getCurrentPosition(); } catch (IllegalStateException ignored) {} }
            player.release(); player = null;
        }
        ready = false; playing = false;
        if (reset) { resumeMs = 0; if (seek != null) seek.setProgress(0); if (elapsed != null) elapsed.setText("0:00"); }
        if (audioManager != null && focusRequest != null) audioManager.abandonAudioFocusRequest(focusRequest);
    }

    private void updateControls() { if (playButton != null) { playButton.setEnabled(true); playButton.setText(playing ? "Pause" : "Play"); } }

    private final Runnable progress = new Runnable() {
        @Override public void run() {
            if (disposed) return;
            if (player != null && ready && !seeking) {
                try { int value = player.getCurrentPosition(); seek.setProgress(value); elapsed.setText(time(value)); }
                catch (IllegalStateException ignored) {}
            }
            handler.postDelayed(this, 400);
        }
    };

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("selected", selected);
        if (player != null && ready) { try { resumeMs = player.getCurrentPosition(); } catch (IllegalStateException ignored) {} }
        state.putInt("position", resumeMs);
        super.onSaveInstanceState(state);
    }
    @Override protected void onResume() {
        super.onResume();
        MenuMusic.suspendForOtherAudio();
    }
    @Override protected void onPause() {
        releasePlayer(false);
        if (stateText != null) stateText.setText("Paused");
        updateControls();
        super.onPause();
    }
    @Override public void onBackPressed() { closeMusic(); }
    private void closeMusic() { releasePlayer(true); finish(); }
    @Override protected void onDestroy() {
        disposed = true; releasePlayer(true); handler.removeCallbacksAndMessages(null); io.shutdownNow();
        if (background != null) background.setImage(null);
        super.onDestroy();
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private Button button(String title, boolean primary) {
        Button button = new Button(this); button.setText(title); button.setTextColor(Color.WHITE); button.setTextSize(16);
        button.setAllCaps(false); button.setMinHeight(dp(44)); button.setMinimumWidth(0); button.setMinWidth(0);
        button.setBackground(BlinxArt.frame(this, primary));
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
    }
    private Bitmap readBitmap(String path) throws Exception { try (InputStream source = getAssets().open(path)) { Bitmap value = BitmapFactory.decodeStream(source); if (value == null) throw new IllegalStateException("Invalid bitmap"); return value; } }
    private String readText(String path) throws Exception { try (InputStream source = getAssets().open(path); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) { byte[] buffer = new byte[8192]; int n; while ((n = source.read(buffer)) != -1) bytes.write(buffer, 0, n); return bytes.toString("UTF-8"); } }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static String time(int value) { int seconds = Math.max(0, value / 1000); return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60); }

    private final class BackgroundView extends View {
        private Bitmap image;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        BackgroundView() { super(MusicActivity.this); }
        void setImage(Bitmap value) { image = value; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            canvas.drawColor(0xff0a1526);
            if (image == null || getWidth() == 0 || getHeight() == 0) return;
            // Stage Select uses the top 384 rows of the original 512-square SSL texture.
            int sourceHeight = image.getHeight() * 3 / 4;
            float scale = Math.max((float)getWidth() / image.getWidth(), (float)getHeight() / sourceHeight);
            float w = image.getWidth() * scale, h = sourceHeight * scale;
            float x = (getWidth() - w) / 2f, y = (getHeight() - h) / 2f;
            canvas.drawBitmap(image, new Rect(0, 0, image.getWidth(), sourceHeight), new RectF(x, y, x + w, y + h), paint);
        }
    }
}
