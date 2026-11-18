package it.blinx.demo.v1;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    static final String EDITION = "edition";
    private static final int[] XBOX_LANGUAGES = {0, 2, 1, 3, 4, 5, 6};
    private String edition;
    private boolean demo;
    private static final AtomicBoolean INSTALLING = new AtomicBoolean();
    private static final String[] LANGUAGES = {
        "Original", "Japanese", "English", "German", "French", "Spanish", "Italian"
    };
    private static final String LANGUAGE = "game_language";
    private static final int DEFAULT_LANGUAGE = 2;
    private TextView status;
    private ImageButton language;
    private View start;
    private View share;
    private ProgressBar progress;
    private static final int INK = Color.rgb(16, 26, 34), PAPER = Color.rgb(236, 242, 245);

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        edition = getIntent().getStringExtra(EDITION);
        demo = "demo".equals(edition);
        if (!demo && !"pal".equals(edition)) {
            finish();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        android.widget.FrameLayout scene = new android.widget.FrameLayout(this);
        scene.setBackgroundColor(INK);
        android.widget.ImageView background = BlinxArt.image(this, "edition_tgs");
        background.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        scene.addView(background, new android.widget.FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0x7f101823, 0xe8101823}));
        scene.addView(shade, new android.widget.FrameLayout.LayoutParams(-1, -1));
        scene.addView(scroll, new android.widget.FrameLayout.LayoutParams(-1, -1));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setPadding(dp(34), dp(22), dp(34), dp(22));
        scroll.addView(body);

        android.widget.ImageView logo = BlinxArt.image(this, demo ? "logo" : "logo_pal");
        logo.setContentDescription(demo ? "Blinx The Time Sweeper Demo" : "Blinx The Time Sweeper");
        body.addView(logo, new LinearLayout.LayoutParams(0, dp(228), 3));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setPadding(dp(30), 0, 0, 0);
        body.addView(actions, new LinearLayout.LayoutParams(0, -2, 2));
        android.widget.ImageView time = BlinxArt.image(this, "time");
        LinearLayout.LayoutParams timeLayout = new LinearLayout.LayoutParams(dp(136), dp(34));
        timeLayout.gravity = Gravity.CENTER_HORIZONTAL;
        timeLayout.bottomMargin = dp(8);
        actions.addView(time, timeLayout);
        start = menuButton("Play", "Play", true);
        actions.addView(start, new LinearLayout.LayoutParams(-1, dp(58)));
        language = menuButton("Language", "Game language", false);
        updateLanguageLabel();
        language.setOnClickListener(view -> chooseLanguage());
        LinearLayout.LayoutParams languageLayout = new LinearLayout.LayoutParams(-1, dp(52));
        languageLayout.topMargin = dp(8);
        actions.addView(language, languageLayout);
        LinearLayout links = new LinearLayout(this);
        LinearLayout.LayoutParams linksLayout = new LinearLayout.LayoutParams(-1, -2);
        linksLayout.topMargin = dp(8);
        actions.addView(links, linksLayout);
        share = menuButton("Log", "Share diagnostics log", false);
        View stop = menuButton("Exit", "Back to game selection", false);
        LinearLayout.LayoutParams logLayout = new LinearLayout.LayoutParams(0, dp(48), 1);
        logLayout.setMarginEnd(dp(8));
        links.addView(share, logLayout);
        links.addView(stop, new LinearLayout.LayoutParams(0, dp(48), 1));
        stop.setOnClickListener(view -> finish());
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        actions.addView(progress, new LinearLayout.LayoutParams(-1, dp(6)));
        status = text("", 13, 0xffb1c4d8);
        actions.addView(status);
        setContentView(scene);
        start.setOnClickListener(view -> launchEdition()); share.setOnClickListener(view -> shareLogs());
        if (INSTALLING.get()) { start.setEnabled(false); status.setText("Preparing game files. Wait and reopen this screen."); }
    }

    @Override protected void onResume() {
        super.onResume();
        if (edition != null && (demo || "pal".equals(edition))) MenuMusic.enter(this);
    }

    @Override protected void onPause() {
        MenuMusic.leave(this);
        super.onPause();
    }

    @Override protected void onStop() {
        MenuMusic.leave(this);
        super.onStop();
    }

    private TextView text(String content, int size, int color) {
        TextView view = new TextView(this); view.setText(content); view.setTextSize(size); view.setTextColor(color); view.setLineSpacing(dp(2), 1); return view;
    }
    private ImageButton menuButton(String label, String description, boolean primary) {
        ImageButton view = BlinxArt.button(this, "launcher_" + label, description);
        view.setBackground(BlinxArt.frame(this, primary));
        view.setPadding(dp(20), dp(12), dp(20), dp(12));
        view.setFocusable(true);
        return view;
    }

    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }
    private void update(String message, int amount) {
        runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) { status.setText(message); progress.setProgress(amount); } });
    }
    private int selectedLanguage() {
        int selection = getPreferences(MODE_PRIVATE).getInt(languageKey(), DEFAULT_LANGUAGE);
        return selection >= 0 && selection < LANGUAGES.length ? selection : DEFAULT_LANGUAGE;
    }
    private void updateLanguageLabel() {
        String choice = LANGUAGES[selectedLanguage()];
        language.setImageBitmap(BlinxArt.bitmap(this, "launcher_Language_" + choice));
        language.setContentDescription("Language: " + choice);
    }

    private void chooseLanguage() {
        if (INSTALLING.get()) return;
        int current = selectedLanguage();
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(14), dp(20), dp(14));
        GradientDrawable backing = new GradientDrawable();
        backing.setColor(0xff111925);
        backing.setStroke(dp(1), 0xff758aa2);
        backing.setCornerRadius(dp(12));
        panel.setBackground(backing);
        scroll.addView(panel);

        ImageView title = BlinxArt.image(this, "launcher_Game_language");
        title.setContentDescription("Game language");
        LinearLayout.LayoutParams titleLayout = new LinearLayout.LayoutParams(dp(178), dp(28));
        titleLayout.gravity = Gravity.CENTER_HORIZONTAL;
        titleLayout.bottomMargin = dp(10);
        panel.addView(title, titleLayout);

        LinearLayout row = null;
        for (int index = 0; index < LANGUAGES.length; index++) {
            if (index % 2 == 0) {
                row = new LinearLayout(this);
                LinearLayout.LayoutParams rowLayout = new LinearLayout.LayoutParams(-1, -2);
                if (index > 0) rowLayout.topMargin = dp(6);
                panel.addView(row, rowLayout);
            }
            final int choice = index;
            boolean selected = choice == current;
            String label = LANGUAGES[choice];
            ImageButton option = menuButton(label, label, selected);
            option.setSelected(selected);
            option.setOnClickListener(view -> {
                getPreferences(MODE_PRIVATE).edit().putInt(languageKey(), choice).apply();
                updateLanguageLabel();
                if (choice != current) status.setText("Language saved.");
                dialog.dismiss();
            });
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, dp(46), 1);
            if (index % 2 == 0) cell.setMarginEnd(dp(8));
            row.addView(option, cell);
        }
        ImageButton cancel = menuButton("Cancel", "Cancel", false);
        cancel.setOnClickListener(view -> dialog.dismiss());
        row.addView(cancel, new LinearLayout.LayoutParams(0, dp(46), 1));

        dialog.setContentView(scroll);
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setDimAmount(.72f);
        dialog.show();
        window.setLayout(Math.min(dp(500), getResources().getDisplayMetrics().widthPixels - dp(32)), -2);
    }

    private String languageKey() {
        return demo ? LANGUAGE : "pal_language";
    }

    private void launchEdition() {
        if (!INSTALLING.compareAndSet(false, true)) return;
        int languageCode = XBOX_LANGUAGES[selectedLanguage()];
        start.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        new Thread(() -> {
            try {
                DiscInstaller.Paths paths = DiscInstaller.prepare(getApplicationContext(), edition, this::update);
                final String demoLocaleManifest = DemoLocale.prepare(getFilesDir(), edition,
                        new java.io.File(paths.discPath), languageCode);
                if (!demo) {
                    android.app.ActivityManager manager = (android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);
                    java.util.List<android.app.ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
                    if (processes != null) {
                        for (android.app.ActivityManager.RunningAppProcessInfo process : processes) {
                            if ((getPackageName() + ":game").equals(process.processName))
                                throw new java.io.IOException("The previous game is still closing. Try Play again.");
                        }
                    }
                    String imported = "Personal save seeding is disabled in this source-sharing variant.";
                    ((DemoApplication)getApplication()).appendLog(imported);
                    android.util.Log.i("BlinxSaveSeed", imported);
                }
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    start.setEnabled(true);
                    progress.setVisibility(View.GONE);
                    status.setText("");
                    MenuMusic.suspendForOtherAudio();
                    startActivity(AcceleratedGameActivity.intent(this, edition, paths.discPath,
                            languageCode, RuntimeSettings.isFpsEnabled(this))
                            .putExtra(AcceleratedGameActivity.DEMO_LOCALE_MANIFEST, demoLocaleManifest));
                });
            } catch (Exception error) {
                ((DemoApplication)getApplication()).appendLog("Disc preparation failed: " + error);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    status.setText("Could not start: " + error.getMessage());
                    start.setEnabled(true);
                    progress.setVisibility(View.GONE);
                });
            } finally {
                INSTALLING.set(false);
            }
        }, "Blinx-prepare-disc").start();
    }

    private void shareLogs() {
        share.setEnabled(false); status.setText("Preparing the diagnostics report...");
        new Thread(() -> {
            try {
                Diagnostics.collect(getApplicationContext());
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    Uri uri = Uri.parse("content://it.blinx.demo.v1.logs/diagnostics.txt");
                    Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain");
                    intent.putExtra(Intent.EXTRA_SUBJECT, "Blinx Demo V1 diagnostics");
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.setClipData(ClipData.newRawUri("Blinx diagnostics", uri));
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    share.setEnabled(true);
                    try { startActivity(Intent.createChooser(intent, "Share Blinx Demo log")); status.setText("The report includes the demo log and device information."); }
                    catch (android.content.ActivityNotFoundException error) { status.setText("No app can share the report."); }
                });
            } catch (Exception error) {
                runOnUiThread(() -> { if (!isDestroyed()) { share.setEnabled(true); status.setText("Report unavailable: " + error.getMessage()); } });
            }
        }, "Blinx-share").start();
    }
}
