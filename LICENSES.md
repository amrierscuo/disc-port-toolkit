# Licensing by component

| Path or component | Origin and terms |
| --- | --- |
| Common Python tools, wrappers, new project documentation | New toolkit work offered under GPL-3.0-or-later. LICENSE contains GPLv3. |
| profiles/android-original application, original project adapters, builders and generate_pad.py | Previous base source candidate, full-project controller bridge and the project's drawing/packing recipes. GPL-3.0-or-later covers the project-owned source. Two private PAL textures contribute pixels to the locally built skin. Game artwork rights are separate. |
| profiles/android-full frontend, wrappers, first-party app and export recipes | Project-owned sources are offered under GPL-3.0-or-later. See the [Full profile license](profiles/android-full/LICENSE) and [export change map](docs/EXPORT_CHANGES.md). |
| org/libsdl/app Java sources in either Android profile | SDL 3.4.18 Java glue from the pinned hakuX integration. SDLActivity.java includes hakuX's keepFullscreen() hook. Retain the original SDL zlib notice and hakuX GPL-3.0-or-later notice for the integration and modifications. |
| hakuX & FEX | Pinned upstream software. See hakuX COPYING/LICENSE and component notices. FEX has its own notice and third-party components. Distribution of a compiled core requires a separate corresponding-source review. |
| third_party/licenses and profiles/android-full/source/combined/runtime/provenance/upstream | Verbatim component-specific notices and upstream metadata accompanying the previous candidate, including SDL3, FEX, Vulkan and audio libraries. A notice alone does not include that project or its complete source. |
| preview/ documentation and code-verification.png | Toolkit-authored preview. The screenshot reproduces the GPL-3.0-or-later payload_digest function in tools/verify_inputs.py. See [screenshot provenance](preview/SCREENSHOT.md). |
| Clone 1 build recipe and dependencies | See the [Windows provenance review](docs/WINDOWS_HOST_PROVENANCE.md) for the precise origins of the PAL changes, the upstream projects and component-specific terms. |
| profiles/windows-clone1/game-pal.patch and toolkit-pal.patch | Disc Port Toolkit contributors license their original PAL changes under the [MIT License](profiles/windows-clone1/PAL-LICENSE.txt). The upstream lines in the diffs retain their original MIT notices and attributions as listed in the [Windows provenance review](docs/WINDOWS_HOST_PROVENANCE.md). |
| C/C++ toolchain, Android SDK/JDK and extract-xiso | Externally obtained build tools with their respective upstream terms. |
| Legacy proof, receipt, hashes | Historical evidence. Attribution and scope are described in provenance/. |
| Game data, translated guest code, demo resources, APK and prebuilt ARM64 libraries | Private inputs/outputs, excluded from this source distribution. |

Treat any more specific upstream file notice as authoritative for that file. A verified tool revision and matching binary digests do not close the corresponding-source review. The historical native libraries have not been rebuilt and compared from complete corresponding source. Review transitive source, build scripts and notices before considering any binary distribution.

## Android Full historical package

The [preserved runtime notice](profiles/android-full/source/combined/runtime/NOTICE.txt) describes a private Android build containing native libraries and game resources. Its distribution statement and reference to `source_receipt.json` concern that build. This source release lists the two external libraries and their hashes in the [native source lock](profiles/android-original/NATIVE_SOURCE.lock.json). The [Full build guide](docs/BUILD_ANDROID_FULL.md) identifies the PAL and demo discs and the resources required to build locally. Each included source and notice retains the terms listed above.
