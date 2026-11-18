# Android Original

On your phone the launcher displays **Disc Port Original**.

Double-click `START_HERE.cmd` and choose **Android Original**. Select your PAL game-partition image or browse to your complete extracted disc folder on any drive. In that folder, select `default.xbe` next to `media/`. Keep every file and subfolder under `media/` in its original location. The launcher prepares the tools, disc and ARM64 libraries. If the [disc check fails](TROUBLESHOOTING.md#pal-disc-files-differ-from-the-reference), correct the input and rerun the launcher from the same toolkit folder to reuse the installed tools.

The steps below are for manual setup.

This profile builds a personal PAL APK with touch controls and SDL support for a physical controller. The app installs the embedded game disc into its private storage on first launch and reuses that copy on subsequent launches.

## Prepare the build

1. On Windows install Python 3.12 or later, JDK 21, Android SDK platform 36 and Build Tools 36.0.0. Set `JAVA_HOME` and `ANDROID_HOME`. Install Pillow with `python -m pip install Pillow==12.0.0` for the default touch controls. The reference build used the Windows CPython 3.13 wheel. The target device needs ARM64, Android 9 or later, and Vulkan 1.1.
2. Place a bare PAL game-partition XISO at `inputs/android-original/disc.iso`, or place the complete extracted disc under `inputs/android-original/disc/` with its `default.xbe` and `media/`. The PAL reference contains 3,092 disc files.
3. Provide the two ARM64 libraries in `cache/native/arm64-v8a/`. Check their hashes against `profiles/android-original/NATIVE_SOURCE.lock.json`. If obtaining these files from your own hakuX v1.3.0 APK, verify its `apk_sha256` before extracting `lib/arm64-v8a/libSDL3.so` and `lib/arm64-v8a/libhakuxcore.so`.
4. The build can fetch the pinned hakuX and FEX sources from an empty cache. To prepare that cache in advance, run `python -B tools/fetch_dependencies.py hakux fex`. The required commits are `a90f01ee4629977b36a76175cbd781d383056293` and `52018bf46a8279b22bce738548ab3f40db467064`. Add `--check` to inspect an existing cache offline.

```bat
build_android_original.cmd --check
build_android_original.cmd
```

The output is `dist/android-original/android-original.apk`. The builder creates a local signing identity in `cache/local-signing/`. Keep that identity private and available to sign future updates. Reserve space for several copies of the disc during the PC build. On the phone, allow for the installed APK, roughly another gigabyte for the private disc and space for saves.

## Touch controls

The default build opens directly into the game with the Full project's two side rails, sixteen labelled buttons, two analog sticks and amber or steel frames. Original uses the same touch layout and input implementation as Full. From a verified PAL disc the builder reads `media/font_cgothic_bold.dds` and `media/h_waku_1a.dds`, renders the twenty control resources locally and checks their fingerprints. The public toolkit contains the drawing instructions. The generated artwork stays in the personal APK.

If you already have the twenty matching project resources, place them under `inputs/android-original/controls/` using the original `assets/blinx_ui/` and `res/drawable-xhdpi/` layout. An existing `inputs/android-full/resources/` is also supported. Original checks its own controls directory first, then Full's directory, then generates the artwork from the PAL disc. An incomplete or modified private set stops the build and identifies the file to correct. With a matching private set, Pillow is optional. For an explicitly neutral layout, set `BLINX_ORIGINAL_PAD=neutral` before running the same build command. Clear the variable to restore the project design.

## Disc identity and interrupted installation

The verifier reads the embedded XBE certificate and SHA-256, then checks the names, lengths and hashes of the full disc inventory against the PAL reference. The packager verifies its working copy and embeds the image hash and size in the APK. The phone verifies both before activating the disc. A rename within the same filesystem publishes the verified disc and its receipt together. A matching legacy `game/disc.iso` can be recovered even if its receipt was never written. Saves use the separate `game/hdd` directory.
