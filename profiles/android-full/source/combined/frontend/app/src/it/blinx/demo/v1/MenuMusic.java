package it.blinx.demo.v1;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.lang.ref.WeakReference;

/** Foreground-only soundtrack shared by the two launcher screens. */
final class MenuMusic {
    private static final String[] ASSETS = {
        "menu_music/Bremix.mp3", "menu_music/boss_E_from_5s.flac"
    };
    private static MenuMusic instance;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AudioManager audioManager;
    private final AudioFocusRequest focusRequest;
    private WeakReference<Activity> owner = new WeakReference<Activity>(null);
    private MediaPlayer player;
    private int track, positionMs, generation, failedTracks;
    private boolean prepared, waitingForSeek, hasFocus, transientLoss;

    private MenuMusic(Context context) {
        this.context = context.getApplicationContext();
        audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes())
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(this::focusChanged, handler).build();
    }

    static void enter(Activity activity) {
        if (instance == null) instance = new MenuMusic(activity);
        instance.enterScreen(activity);
    }

    static void leave(Activity activity) {
        if (instance != null && instance.owner.get() == activity) instance.suspend();
    }

    /** Call synchronously before opening a game or the separate Music player. */
    static void suspendForOtherAudio() {
        if (instance != null) instance.suspend();
    }

    private static AudioAttributes attributes() {
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    }

    private void enterScreen(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        owner = new WeakReference<Activity>(activity);
        failedTracks = 0;
        transientLoss = false;
        if (!hasFocus) {
            hasFocus = audioManager == null || audioManager.requestAudioFocus(focusRequest)
                    == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
        if (hasFocus) startCurrent();
    }

    private boolean visible() {
        Activity activity = owner.get();
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private void startCurrent() {
        if (!visible() || !hasFocus || transientLoss || player != null || failedTracks >= ASSETS.length) return;
        final int token = ++generation;
        final MediaPlayer media = new MediaPlayer();
        player = media;
        prepared = false;
        waitingForSeek = false;
        media.setAudioAttributes(attributes());
        media.setLooping(false);
        media.setOnPreparedListener(value -> {
            if (!current(value, token)) return;
            prepared = true;
            if (positionMs > 0) {
                waitingForSeek = true;
                value.seekTo(Math.min(positionMs, Math.max(0, value.getDuration() - 1)), MediaPlayer.SEEK_CLOSEST);
            } else startPrepared(value, token);
        });
        media.setOnSeekCompleteListener(value -> {
            if (!current(value, token)) return;
            waitingForSeek = false;
            startPrepared(value, token);
        });
        media.setOnCompletionListener(value -> {
            if (!current(value, token)) return;
            releasePlayer(false);
            positionMs = 0;
            track = (track + 1) % ASSETS.length;
            failedTracks = 0;
            startCurrent();
        });
        media.setOnErrorListener((value, what, extra) -> {
            if (current(value, token)) fail(token);
            return true;
        });
        try (AssetFileDescriptor descriptor = context.getAssets().openFd(ASSETS[track])) {
            media.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
            media.prepareAsync();
        } catch (Exception error) {
            Log.w("BlinxMenuMusic", "Menu track unavailable", error);
            fail(token);
        }
    }

    private boolean current(MediaPlayer value, int token) {
        return value == player && token == generation;
    }

    private void startPrepared(MediaPlayer value, int token) {
        if (!current(value, token) || !prepared || waitingForSeek || !visible() || !hasFocus || transientLoss) return;
        try { value.start(); }
        catch (IllegalStateException error) { fail(token); }
    }

    private void fail(int token) {
        if (token != generation) return;
        releasePlayer(false);
        positionMs = 0;
        track = (track + 1) % ASSETS.length;
        failedTracks++;
        // Defer to avoid nested MediaPlayer callbacks; never spin on absent assets.
        final int retryGeneration = generation;
        handler.post(() -> { if (generation == retryGeneration) startCurrent(); });
    }

    private void focusChanged(int change) {
        if (!visible()) return;
        if (change == AudioManager.AUDIOFOCUS_GAIN) {
            hasFocus = true;
            transientLoss = false;
            startCurrent();
        } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            hasFocus = false;
            transientLoss = true;
            releasePlayer(true);
        } else if (change == AudioManager.AUDIOFOCUS_LOSS) {
            hasFocus = false;
            transientLoss = false;
            releasePlayer(true);
            if (audioManager != null) audioManager.abandonAudioFocusRequest(focusRequest);
        }
    }

    private void suspend() {
        owner.clear();
        releasePlayer(true);
        hasFocus = false;
        transientLoss = false;
        if (audioManager != null) audioManager.abandonAudioFocusRequest(focusRequest);
    }

    private void releasePlayer(boolean rememberPosition) {
        generation++;
        if (player != null) {
            if (rememberPosition && prepared && !waitingForSeek) {
                try { positionMs = player.getCurrentPosition(); }
                catch (IllegalStateException ignored) { }
            }
            player.release();
            player = null;
        }
        prepared = false;
        waitingForSeek = false;
    }
}
