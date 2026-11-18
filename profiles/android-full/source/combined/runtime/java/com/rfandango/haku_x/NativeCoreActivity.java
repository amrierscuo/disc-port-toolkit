// SPDX-License-Identifier: GPL-3.0-or-later
package com.rfandango.haku_x;

import org.libsdl.app.SDLActivity;

/** Exact JNI class name expected by the unchanged hakuX 1.3.0 library. */
public abstract class NativeCoreActivity extends SDLActivity {
    public static native void nativeSetOverlayPad(int buttons, int analogMask,
            float lx, float ly, float rx, float ry, float lt, float rt);
    public static native void nativeSetPaused(boolean paused);
    public static native float[] nativeGetFrameStats();
    public static native boolean nativeGetFrameShown();
    public static native String nativeGetFatal();
    public static native int nativeGetFatalKind();
    public static native int[] nativeGetShaderBuild();
    public static native boolean nativeGetQuit();
    public static native boolean nativeGetRelaunch();
}
