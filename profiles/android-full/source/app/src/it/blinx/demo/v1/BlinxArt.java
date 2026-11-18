package it.blinx.demo.v1;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.ImageButton;
import android.widget.ImageView;
import java.io.IOException;
import java.io.InputStream;

final class BlinxArt {
    private BlinxArt() {}

    static Bitmap bitmap(Context context, String name) {
        try (InputStream stream = context.getAssets().open("blinx_ui/" + name + ".png")) {
            return BitmapFactory.decodeStream(stream);
        } catch (IOException error) {
            throw new IllegalStateException("Missing Blinx artwork " + name, error);
        }
    }

    static ImageView image(Context context, String name) {
        ImageView view = new ImageView(context);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setImageBitmap(bitmap(context, name));
        return view;
    }

    static ImageButton button(Context context, String name, String description) {
        ImageButton view = new ImageButton(context);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setImageBitmap(bitmap(context, name));
        view.setContentDescription(description);
        return view;
    }

    static Drawable frame(Context context, boolean primary) {
        int normal = primary ? R.drawable.launcher_amber : R.drawable.launcher_steel;
        int active = primary ? R.drawable.launcher_steel : R.drawable.launcher_amber;
        StateListDrawable frame = new StateListDrawable();
        Drawable disabled = context.getDrawable(normal).mutate();
        disabled.setAlpha(112);
        frame.addState(new int[] {-android.R.attr.state_enabled}, disabled);
        frame.addState(new int[] {android.R.attr.state_pressed}, context.getDrawable(active));
        frame.addState(new int[] {android.R.attr.state_focused}, context.getDrawable(R.drawable.launcher_amber));
        frame.addState(new int[0], context.getDrawable(normal));
        return frame;
    }
}
