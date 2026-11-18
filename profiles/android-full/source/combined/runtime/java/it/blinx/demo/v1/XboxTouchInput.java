// SPDX-License-Identifier: GPL-3.0-or-later
package it.blinx.demo.v1;

import android.view.KeyEvent;
import java.util.HashSet;
import java.util.Set;

/** Converts the existing touch artwork's key bindings to actual Xbox pad state. */
final class XboxTouchInput {
    interface Output {
        void set(int digital, int analogMask, float lx, float ly,
                float rx, float ry, float lt, float rt);
    }
    private final Output output;
    private final Set<Integer> held = new HashSet<>();
    XboxTouchInput(Output output) { this.output = output; }

    void key(int code, boolean down) {
        if (down) held.add(code); else held.remove(code);
        publish();
    }
    void clear() { held.clear(); publish(); }
    private int bit(int code, int value) { return held.contains(code) ? value : 0; }
    private float axis(int negative, int positive) {
        return (held.contains(positive) ? 1f : 0f) - (held.contains(negative) ? 1f : 0f);
    }
    private void publish() {
        // native/dev/usb.h: low eight bits are the Xbox digital buttons.
        int digital = bit(KeyEvent.KEYCODE_DPAD_UP, 1) | bit(KeyEvent.KEYCODE_DPAD_DOWN, 2)
                | bit(KeyEvent.KEYCODE_DPAD_LEFT, 4) | bit(KeyEvent.KEYCODE_DPAD_RIGHT, 8)
                | bit(KeyEvent.KEYCODE_ENTER, 16) | bit(KeyEvent.KEYCODE_DEL, 32)
                | bit(KeyEvent.KEYCODE_SHIFT_LEFT, 64) | bit(KeyEvent.KEYCODE_CTRL_LEFT, 128);
        // The six analog-button bits are A, B, X, Y, black, white, in that order.
        int analog = bit(KeyEvent.KEYCODE_Z, 1) | bit(KeyEvent.KEYCODE_X, 2)
                | bit(KeyEvent.KEYCODE_C, 4) | bit(KeyEvent.KEYCODE_V, 8)
                | bit(KeyEvent.KEYCODE_E, 16) | bit(KeyEvent.KEYCODE_Q, 32);
        output.set(digital, analog,
                axis(KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_D), axis(KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_S),
                axis(KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_L), axis(KeyEvent.KEYCODE_I, KeyEvent.KEYCODE_K),
                held.contains(KeyEvent.KEYCODE_1) ? 1f : 0f, held.contains(KeyEvent.KEYCODE_3) ? 1f : 0f);
    }
}
