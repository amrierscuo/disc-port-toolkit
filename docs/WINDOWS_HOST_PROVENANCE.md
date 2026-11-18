# Windows source provenance: Clone 1

The Windows profile prepares the **first playable PAL clone** using three pinned projects and two small PAL patches. The following revisions and licenses were checked against local Git objects. Anonymous downloads of the root license at each of the three pinned commits also matched the local SHA-256 hashes on 18 November 2026. The local PAL commit IDs identify the source of each patch. A user can apply those patches to the upstream base commits.

| Component | Upstream revision | Terms and role |
| --- | --- | --- |
| apexJCL/blinx1-recomp | `18324c3c8ae2c4edcd6487bbe9f04043fa47bdeb` | Game-specific Windows host and build definition. MIT, copyright apexJCL. The license also credits sp00nz's MIT templates used by `src/main.c`, `src/recomp_manual.c` and `CMakeLists.txt`. [License in this draft](../profiles/windows-clone1/upstream-notices/blinx1-recomp-LICENSE.txt) |
| apexJCL/xboxrecomp, a fork of sp00nznet/xboxrecomp | `e5a7087201cea886b3b5d72e50f5515581920526` | Code generator and linked Xbox compatibility runtime. Default MIT, copyright sp00nz, with the component exceptions below. The touched Python module is covered by the root MIT terms. [License](../profiles/windows-clone1/upstream-notices/xboxrecomp-LICENSE.txt) and [pinned component notice](https://github.com/apexJCL/xboxrecomp/blob/e5a7087201cea886b3b5d72e50f5515581920526/NOTICE) |
| apexJCL/xboxrecomp-cli | `277c3366aa4fe59ec9271949db83728956be70dd`, v0.15.0 | Build-time CLI. Its root license has the same Git blob as the game project's license (`dd7c9f631d0adef3d8e643e127e599834d3fe513`). Its repository also carries separate MIT notices for xboxrecomp and Tabler Icons. The Windows profile references the upstream checkout. [Pinned CLI](https://github.com/apexJCL/xboxrecomp-cli/tree/277c3366aa4fe59ec9271949db83728956be70dd) |

## The two PAL patches

The game patch corresponds to local commit `3f34be12015c4c0ba955abfd1ad8e37e9c465c86` over the game base. The toolkit patch corresponds to local commit `0c26386161da49bc682da19793c3e9b30dc35522` over the toolkit base.

Disc Port Toolkit contributors license their original PAL changes in `game-pal.patch` and `toolkit-pal.patch` under the [MIT License](../profiles/windows-clone1/PAL-LICENSE.txt). Both diffs include lines from their respective upstream files, including sp00nz template code in the game patch. When redistributing the patches, include the PAL license and the applicable [game host](../profiles/windows-clone1/upstream-notices/blinx1-recomp-LICENSE.txt) and [toolkit](../profiles/windows-clone1/upstream-notices/xboxrecomp-LICENSE.txt) notices. The project's build tools and documentation are offered under [GPL-3.0-or-later](../LICENSE).

| Changed path | Origin and local contribution |
| --- | --- |
| `config/seed_pal_runtime.json` | Local analysis metadata recording three observed PAL callback addresses. |
| `game.toml` | Upstream manifest with a local change selecting the PAL seed file. |
| `src/main.c` | Upstream host and template-derived code. The PAL patch sets its entry point and graphics fence location and disables a draw-distance hook written for another revision. |
| `src/recomp_manual.c` | Upstream template-derived host code. The local guard disables the other revision's manual wrappers. |
| `tools/recomp/config.py` | Upstream MIT generator. For the BLiNX title the local modification restricts candidate executable sections during indirect-target discovery. |

## Components used when building privately

| Component | License and delivery boundary |
| --- | --- |
| MCPX audio and NV2A code derived from xemu | The pinned NOTICE lists **LGPL-2.1-or-later** and supplies the 2.1 text. Individual APU and NV2A headers say “version 2 ... or any later version” or “LGPL v2+”. Preserve those original, occasionally broader, notices. LGPL 2.1 is a common compliance option for these files. These libraries are statically linked in Clone 1. A future binary distribution would need to address license notices, corresponding library source including changes, and relinking rights under the applicable license terms. [Game project's notice](../profiles/windows-clone1/upstream-notices/blinx1-recomp-NOTICE.txt) and [LGPL 2.1, section 6](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.en.html) |
| Enhancements overlay | Enabled in the reference build. microui is MIT, stb_truetype offers MIT or public domain, and the embedded Atkinson Hyperlegible Regular font is **SIL OFL 1.1**. Its generated C representation retains the font's provenance. The toolkit supplies original notices, including the Braille Institute attribution. [Pinned license inventory](https://github.com/apexJCL/xboxrecomp/tree/e5a7087201cea886b3b5d72e50f5515581920526/LICENSES) |
| Postprocessing | The optional librashader v0.12.0 DLL is **MPL-2.0**, and the interface header is MIT. The provided RetroArch preset collection has per-file MIT, Unlicense and public-domain terms. The option is enabled in the historical CMake cache, which by itself does not demonstrate loading the DLL during gameplay. [Shader inventory](https://github.com/apexJCL/xboxrecomp/blob/e5a7087201cea886b3b5d72e50f5515581920526/assets/shaders/README.md) |
| Optional DSP JIT | The historical Windows configuration has `XBOXRECOMP_APU_DSP=OFF`. Enabling it would add dsp56300, the Rust runtime and dependencies with separate MIT, Apache and other notices enumerated upstream. |
| Build tools and platform libraries | LLVM-MinGW, Python, CMake, Ninja, Capstone and pefile are separately obtained toolchain components. The Windows build uses platform graphics, input and system APIs. No grant from the project covers distribution of external SDKs or toolchain binaries. |

## What the public Windows sources cover

The profile contains the build recipe, revision pins, PAL patches and attribution. The user supplies the supported PAL game. Translated guest C and the resulting Windows executable are generated privately. The host source license provides no grant for distributing the game-derived C, XBE, assets, saves or audiovisual captures. For a source-only release the original [game project](https://github.com/apexJCL/blinx1-recomp/tree/18324c3c8ae2c4edcd6487bbe9f04043fa47bdeb), [toolkit](https://github.com/apexJCL/xboxrecomp/tree/e5a7087201cea886b3b5d72e50f5515581920526) and their respective license texts remain identifiable independently of this project's build documentation.
