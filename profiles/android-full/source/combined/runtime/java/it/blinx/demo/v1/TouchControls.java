// SPDX-License-Identifier: GPL-3.0-or-later
package it.blinx.demo.v1;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import java.util.ArrayList;
import java.util.List;

final class TouchControls extends FrameLayout {
    interface KeySink { void key(int code, boolean down); }
    private final KeySink keySink;
    // The Xbox controller bridge may sample at 100 ms intervals.
    private static final long MIN_PRESS_MS = 150;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<TouchKey> keys = new ArrayList<>();
    private final Rail left;
    private final Rail right;
    private final Stick moveStick;
    private final Stick cameraStick;

    TouchControls(Context context, KeySink keySink) {
        super(context);
        this.keySink = keySink;
        setMotionEventSplittingEnabled(true);
        left = rail(Gravity.LEFT);
        right = rail(Gravity.RIGHT);

        left.place(button("LT", "Left trigger", KeyEvent.KEYCODE_1), 0, 0, 1, 52);
        left.place(button("Back", "Back", KeyEvent.KEYCODE_DEL), 1, 0, 1, 52);
        left.place(new Dpad(context), -1, 1, 2, 120);
        moveStick = new Stick(context, "Movement", KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_D,
                KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_S);
        left.place(moveStick, -1, 3, 3, 112);
        left.place(button("LS", "Left stick click", KeyEvent.KEYCODE_SHIFT_LEFT), -1, 6, 1, 46);

        right.place(button("RT", "Right trigger", KeyEvent.KEYCODE_3), 0, 0, 1, 52);
        right.place(button("Start", "Start, pause", KeyEvent.KEYCODE_ENTER), 1, 0, 1, 54);
        right.place(button("White", "White button", KeyEvent.KEYCODE_Q), 0, 1, 1, 44);
        right.place(button("Black", "Black button", KeyEvent.KEYCODE_E), 1, 1, 1, 44);
        right.place(button("Y", "Y", KeyEvent.KEYCODE_V), 0, 2, 1, 48);
        right.place(button("B", "B, back", KeyEvent.KEYCODE_X), 1, 2, 1, 48);
        right.place(button("X", "X", KeyEvent.KEYCODE_C), 0, 3, 1, 48);
        right.place(button("A", "A, select or jump", KeyEvent.KEYCODE_Z), 1, 3, 1, 52);
        cameraStick = new Stick(context, "Right stick, camera", KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_L,
                KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_K);
        right.place(cameraStick, -1, 4, 2, 100);
        right.place(button("RS", "Right stick click", KeyEvent.KEYCODE_CTRL_LEFT), -1, 6, 1, 46);
    }

    private Rail rail(int side) {
        Rail rail = new Rail(getContext());
        rail.setMotionEventSplittingEnabled(true);
        rail.setClipChildren(true);
        rail.setBackgroundColor(0xff101823);
        addView(rail, new LayoutParams(0, -1, side));
        return rail;
    }

    void setRailWidth(int width) {
        for (Rail rail : new Rail[] {left, right}) {
            LayoutParams params = (LayoutParams)rail.getLayoutParams();
            params.width = width;
            rail.setLayoutParams(params);
        }
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private View button(String label, String description, int key) {
        android.widget.ImageButton view = BlinxArt.button(getContext(), "touch_symbol_" + label, description);
        view.setFocusable(false);
        boolean action = label.equals("A") || label.equals("B") || label.equals("X") || label.equals("Y");
        view.setBackground(BlinxArt.frame(getContext(), action));
        view.setAlpha(.76f);
        view.setPadding(0, 0, 0, 0);
        TouchKey binding = new TouchKey(key, view);
        keys.add(binding);
        view.setOnClickListener(v -> {
            binding.press();
            binding.release();
        });
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    binding.press();
                    return true;
                case MotionEvent.ACTION_UP:
                    v.performClick();
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    binding.clear();
                    return true;
                default:
                    return true;
            }
        });
        return view;
    }

    private void send(int code, boolean down) {
        keySink.key(code, down);
        Log.i("BlinxTouch", KeyEvent.keyCodeToString(code) + (down ? " down" : " up"));
    }

    void releaseAll() {
        for (TouchKey key : keys) key.clear();
        moveStick.reset();
        cameraStick.reset();
    }

    private final class TouchKey {
        final int code;
        final View view;
        final Runnable end = this::clear;
        boolean down;
        long pressedAt;

        TouchKey(int code, View view) {
            this.code = code;
            this.view = view;
        }

        void press() {
            handler.removeCallbacks(end);
            if (down) return;
            down = true;
            pressedAt = SystemClock.uptimeMillis();
            view.setPressed(true);
            view.setAlpha(1f);
            send(code, true);
        }

        void release() {
            handler.removeCallbacks(end);
            handler.postAtTime(end, Math.max(SystemClock.uptimeMillis(), pressedAt + MIN_PRESS_MS));
        }

        void clear() {
            handler.removeCallbacks(end);
            if (!down) return;
            down = false;
            view.setPressed(false);
            view.setAlpha(.76f);
            send(code, false);
        }
    }

    private static final class Slot {
        final View view;
        final int column;
        final int row;
        final int rows;
        final int maxDp;

        Slot(View view, int column, int row, int rows, int maxDp) {
            this.view = view;
            this.column = column;
            this.row = row;
            this.rows = rows;
            this.maxDp = maxDp;
        }
    }

    private final class Rail extends ViewGroup {
        private static final int ROWS = 7;
        private final List<Slot> slots = new ArrayList<>();

        Rail(Context context) { super(context); }

        void place(View child, int column, int row, int rows, int maxDp) {
            slots.add(new Slot(child, column, row, rows, maxDp));
            addView(child, new ViewGroup.LayoutParams(0, 0));
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int height = MeasureSpec.getSize(heightSpec);
            setMeasuredDimension(width, height);
            for (Slot slot : slots) {
                int cellWidth = slot.column < 0 ? width : width / 2;
                int cellHeight = height * (slot.row + slot.rows) / ROWS - height * slot.row / ROWS;
                int size = Math.max(0, Math.min(dp(slot.maxDp), Math.min(cellWidth, cellHeight) - dp(4)));
                int exact = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
                slot.view.measure(exact, exact);
            }
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l;
            int height = b - t;
            for (Slot slot : slots) {
                int centerX = slot.column < 0 ? width / 2 : width * (slot.column * 2 + 1) / 4;
                int centerY = height * (slot.row * 2 + slot.rows) / (ROWS * 2);
                int size = slot.view.getMeasuredWidth();
                int x = centerX - size / 2;
                int y = centerY - size / 2;
                slot.view.layout(x, y, x + size, y + size);
            }
        }
    }

    private final class Dpad extends ViewGroup {
        private final int[] cells = {1, 3, 5, 7};

        Dpad(Context context) {
            super(context);
            setMotionEventSplittingEnabled(true);
            addView(button("Up", "D-pad up", KeyEvent.KEYCODE_DPAD_UP));
            addView(button("Left", "D-pad left", KeyEvent.KEYCODE_DPAD_LEFT));
            addView(button("Right", "D-pad right", KeyEvent.KEYCODE_DPAD_RIGHT));
            addView(button("Down", "D-pad down", KeyEvent.KEYCODE_DPAD_DOWN));
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int size = MeasureSpec.getSize(widthSpec);
            setMeasuredDimension(size, size);
            int cell = MeasureSpec.makeMeasureSpec(size / 3, MeasureSpec.EXACTLY);
            for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(cell, cell);
        }

        @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int cell = (r - l) / 3;
            for (int i = 0; i < cells.length; i++) {
                int x = cells[i] % 3 * cell;
                int y = cells[i] / 3 * cell;
                getChildAt(i).layout(x, y, x + cell, y + cell);
            }
        }
    }

    private final class Stick extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Bitmap plate;
        private final Bitmap puck;
        private final RectF destination = new RectF();
        private int pointer = -1;
        private int horizontal;
        private int vertical;
        private float thumbX;
        private float thumbY;

        private final int leftKey, rightKey, upKey, downKey;

        Stick(Context context, String description, int leftKey, int rightKey, int upKey, int downKey) {
            super(context);
            plate = BlinxArt.bitmap(context, "touch_stick");
            puck = BlinxArt.bitmap(context, "touch_thumb");
            this.leftKey = leftKey;
            this.rightKey = rightKey;
            this.upKey = upKey;
            this.downKey = downKey;
            setContentDescription(description);
        }

        @Override protected void onDraw(Canvas canvas) {
            float center = getWidth() / 2f;
            destination.set(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(plate, null, destination, paint);
            float radius = Math.min(dp(18), center / 3f);
            destination.set(center + thumbX - radius, center + thumbY - radius,
                    center + thumbX + radius, center + thumbY + radius);
            canvas.drawBitmap(puck, null, destination, paint);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            int index = event.getActionIndex();
            if (action == MotionEvent.ACTION_DOWN) pointer = event.getPointerId(index);
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP
                    || (action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(index) == pointer)) {
                reset();
            } else if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                int at = event.findPointerIndex(pointer);
                if (at >= 0) move(event.getX(at), event.getY(at));
            }
            return true;
        }

        private void move(float x, float y) {
            float center = getWidth() / 2f;
            float dx = x - center;
            float dy = y - center;
            float length = (float) Math.hypot(dx, dy);
            float scale = Math.min(1f, (center * .65f) / Math.max(1f, length));
            thumbX = dx * scale;
            thumbY = dy * scale;
            int h = direction(dx);
            int v = direction(dy);
            setDirections(h, v);
            invalidate();
        }

        private int direction(float offset) {
            float deadzone = getWidth() * .17f;
            return offset < -deadzone ? -1 : offset > deadzone ? 1 : 0;
        }

        private void setDirections(int h, int v) {
            horizontal = changeAxis(horizontal, h, leftKey, rightKey);
            vertical = changeAxis(vertical, v, upKey, downKey);
        }

        private int changeAxis(int from, int to, int negative, int positive) {
            if (from != to) {
                if (from != 0) send(from < 0 ? negative : positive, false);
                if (to != 0) send(to < 0 ? negative : positive, true);
            }
            return to;
        }

        void reset() {
            pointer = -1;
            thumbX = thumbY = 0;
            setDirections(0, 0);
            invalidate();
        }
    }
}
