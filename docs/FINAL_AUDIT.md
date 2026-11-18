# Source and build inventory

| Profile | Private inputs | Local output |
| --- | --- | --- |
| [Windows Clone 1](BUILD_WINDOWS.md) | Complete extracted PAL disc | `dist/windows/blinx1_recomp.exe` and `config.default.toml` |
| [Android Original](BUILD_ANDROID_ORIGINAL.md) | PAL disc and two ARM64 libraries | `dist/android-original/android-original.apk` |
| [Android Full](BUILD_ANDROID_FULL.md) | PAL disc, OXM12 demo disc, two ARM64 libraries, 263 hub resources and a local signing identity | `dist/android-full/blinx-full.apk` |

## Source revisions and disc identity

Clone 1 generates guest code from the user's PAL executable. Its three upstream revisions and the two PAL patch hashes are listed in the [Windows profile pins](../profiles/windows-clone1/pins.json).

The Android profiles use hakuX and FEX. The build takes two locally supplied libraries, `libSDL3.so` and `libhakuxcore.so`. Their SHA-256 fingerprints and source revisions are in the [native source lock](../profiles/android-original/NATIVE_SOURCE.lock.json). Rebuilding both libraries from complete corresponding source remains open.

The PAL reference contains 3,092 disc files and the OXM12 demo contains 3,043. The verifier also accepts the earlier PAL reference copy with its exact additional directory listing, `media/tree.txt`. The [disc fingerprints](../tools/disc_fingerprints.json) identify the Xbox title, executable and logical file inventory for each edition. The [Full resource inventory](../profiles/android-full/dependencies/DEPENDENCY_INVENTORY.json) lists 324 entries comprising 59 public source or notice files, 263 privately supplied resources and the two native libraries.

## Repository contents

The [publication manifest](../PUBLICATION_MANIFEST.json) records each file's origin, license, size and SHA-256. The [license map](../LICENSES.md) and [asset policy](../ASSET_POLICY.md) describe the terms for source and locally produced files. A [machine-readable inventory](FINAL_AUDIT.json) lists the three profiles and their reference files.
