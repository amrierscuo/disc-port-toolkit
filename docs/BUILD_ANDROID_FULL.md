# Android Full

Use this profile to reproduce the private 0.2.7 frontend with Original Game, OXM12 Demo, touch controls, edition storage, demo languages, the model carousel and music screens.

## Source and package notice

The app appears on the Android launcher as **Disc Port Full**. Its package ID is `it.blinx.demo.v1`.

The preserved runtime notice describes the private APK used to identify the native libraries. Its distribution warning applies to that package, and its `source_receipt.json` refers to a historical private record. For this source export, the native-source lock identifies the revisions and library hashes, the Full inventory records which inputs the owner supplies, and the license guide gives the terms for each shared component. [Historical notice](../profiles/android-full/source/combined/runtime/NOTICE.txt) · [Native-source lock](../profiles/android-original/NATIVE_SOURCE.lock.json) · [Full inventory](../profiles/android-full/dependencies/DEPENDENCY_INVENTORY.json) · [Licenses](../LICENSES.md)

## Inputs and command

From the toolkit root, run:

```cmd
build_android_full.cmd --check
build_android_full.cmd
```

Place a complete PAL image at `inputs/android-full/pal/disc.iso` and a complete OXM12 Demo at `inputs/android-full/demo/disc.iso`. The common entry point also accepts a whole extracted Xbox disc in the corresponding `disc/` directory, subject to the packer and checks described in the root README. Every alternate layout must have the exact files, lengths and hashes of the supported edition, including its guest executable. A lone XBE cannot supply these disc assets.

The Full frontend also uses material prepared outside both discs. Provide the 263 private entries in the profile's `dependencies/DEPENDENCY_INVENTORY.json` beneath `inputs/android-full/resources/` using each literal `target` path. Example: `inputs/android-full/resources/assets/blinx_ui/A.png`. The 263 entries comprise 65 base UI/resources, 17 frontend overlays, 124 live carousel items, 33 static carousel items, 22 music-library items and 2 menu-music items. Their original byte lengths and SHA-256 values are required. The toolkit supplies 59 public source/notice entries and separately checks the two native libraries. `tools/prepare_menu_audio.py` in the profile is an optional recipe for the user's recordings. It does not regenerate all original reference hashes or assets. An independent user with only the two discs still lacks the private layers and prepared music.

Install Python 3.11+ for this profile's scripts (the root README specifies the minimum for the entire toolkit), Git, the Android 36 SDK, Build Tools 36.0.0 for Windows, and JDK 21 with `javac`. Set `ANDROID_SDK_ROOT` and `JAVA_HOME`. Prepare the hakuX/FEX source checkouts and exact ARM64 libraries using the shared instructions. The profile verifies those pins with Android Original's checker before compilation. Local packaging currently uses fingerprinted upstream libraries. Their compilation from the complete corresponding source and review for binary distribution remain open.

For a private installable APK, put your own PKCS#12 signing file at `inputs/android-full/signing/local.p12`. Supply the alias as `BLINX_FULL_KEY_ALIAS` and the store password through the environment as `BLINX_FULL_STOREPASS`. Use a key whose private key password is the store password. Set these in a local session and keep them private. This recipe never downloads a key or reuses the original author's identity. Android can update an existing installation only when its signing identity and other package requirements match.

## Build contract

The packager validates 324 rows, 324 distinct targets, and the two expected ARM64 library names. The published inventory preserves the historical identifiers for each row and records export-specific bytes separately. Private input hashes never default to the hash of whatever file the user provides. The helper creates a fresh stage in `build/android-full/`, invokes AAPT2, JAVAC, D8, zipalign and apksigner, then verifies signing, 16 KiB storage alignment, ABI, notices and embedded disc hashes. The final APK is copied to `dist/android-full/blinx-full.apk` after these checks. A build can need several GiB of scratch space.

| Edition | Historical image size | Historical image SHA-256 |
|---|---:|---|
| PAL | 1,018,363,904 | `16a98320a2435f1932c2dde35a8eb735d5dec3e1b1dd955231d1d78e60dd6551` |
| OXM12 Demo | 672,202,752 | `d743eb9ecee49272f85f03be1ecd497a5c5ff4a266c30ad564b3923c5fa75a74` |

The XISO layout may differ after a local rebuild. For an alternate image, the common verifier checks the whole logical disc against the reference. The Full packager writes the size and hash of the accepted container into its private disc manifest. When the Demo layout differs, it binds two constants in the staged copy of `DemoLocale.java` to that container. The Xbox executable fingerprint and the six-language overlay checks continue to run. The published Java source preserves its historical container constants.

## Inventory and installer checks

```cmd
python -B profiles\android-full\test_inventory.py
python -B profiles\android-full\source\combined\packaging\test_disc_installer.py
```

The second command needs `javac` and `java` on PATH. It checks 11 filesystem cases with Android stubs on a desktop JVM, including recovery when the disc was renamed into place before its marker was committed. A retry hashes the already-installed file and recreates the marker only on a match.
