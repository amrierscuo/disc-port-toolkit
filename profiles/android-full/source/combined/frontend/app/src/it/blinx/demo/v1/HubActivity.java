package it.blinx.demo.v1;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

public final class HubActivity extends Activity {
    private LiveMonsterCarousel carousel;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout scene = new FrameLayout(this);
        scene.setBackgroundColor(0xff111925);
        ImageView sky = BlinxArt.image(this, "hub_sky");
        sky.setScaleType(ImageView.ScaleType.CENTER_CROP);
        scene.addView(sky, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout shade = new FrameLayout(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0xe0101823, 0x99101823, 0x40101823}));
        scene.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        shade.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(36), dp(18), dp(28), dp(18));
        row.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(row);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        row.addView(actions, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView logo = BlinxArt.image(this, "logo_pal");
        logo.setContentDescription("Blinx The Time Sweeper");
        actions.addView(logo, new LinearLayout.LayoutParams(-1, dp(96)));
        addChoice(actions, "Original Game", "Original Game",
                new Intent(this, MainActivity.class).putExtra(MainActivity.EDITION, "pal"), true);
        addChoice(actions, "Demo", "Demo",
                new Intent(this, MainActivity.class).putExtra(MainActivity.EDITION, "demo"), false);
        addChoice(actions, "Music", "Music", new Intent(this, MusicActivity.class), false);

        FrameLayout showcase = new FrameLayout(this);
        LinearLayout.LayoutParams showcaseLayout = new LinearLayout.LayoutParams(0, dp(270), 1);
        showcaseLayout.gravity = Gravity.TOP;
        showcaseLayout.setMarginStart(dp(30));
        row.addView(showcase, showcaseLayout);
        carousel = new LiveMonsterCarousel(this);
        carousel.setShowLabels(false);
        showcase.addView(carousel, new FrameLayout.LayoutParams(-1, -1, Gravity.TOP | Gravity.END));
        setContentView(scene);
    }

    private void addChoice(LinearLayout parent, String asset, String title, Intent intent, boolean primary) {
        ImageButton button = BlinxArt.button(this, "hub_" + asset.replace(' ', '_'), title);
        button.setBackground(BlinxArt.frame(this, primary));
        button.setPadding(dp(22), dp(15), dp(22), dp(15));
        button.setOnClickListener(view -> startActivity(intent));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, dp(50));
        layout.topMargin = dp(7);
        parent.addView(button, layout);
    }

    @Override protected void onResume() {
        super.onResume();
        MenuMusic.enter(this);
    }

    @Override protected void onPause() {
        MenuMusic.leave(this);
        super.onPause();
    }

    @Override protected void onStart() {
        super.onStart();
        carousel.start();
    }

    @Override protected void onStop() {
        MenuMusic.leave(this);
        carousel.stop();
        super.onStop();
    }

    @Override protected void onDestroy() {
        carousel.release();
        super.onDestroy();
    }

    private int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }
}
