# Build and run Windows Clone 1

Double-click `START_HERE.cmd` and choose **Windows Clone 1**. Select your PAL game-partition image or browse to your complete extracted disc folder on any drive. In that folder, select `default.xbe` next to `media/`. Keep every file and subfolder under `media/` in its original location. If the [disc check fails](TROUBLESHOOTING.md#pal-disc-files-differ-from-the-reference), correct the input and rerun the launcher from the same toolkit folder to reuse the installed tools.

After disc preparation, the four numbered bars cover analysis, translation to C, configuration and compilation. During analysis and translation, the display shows the latest activity and an estimated percentage marked with `~`. During compilation it counts completed Ninja steps. The percentage starts over for each step.

The launcher opens a Direct3D 11 game window by default. If an earlier build plays audio in the background, see [Restore the game window](TROUBLESHOOTING.md#restore-the-game-window).

After a successful build, use `run_windows.cmd` to play again. The steps below are for manual setup.

Clone 1 adapts the game-specific **apexJCL/blinx1-recomp** project to the PAL disc. It uses **apexJCL/xboxrecomp** to generate guest C code and compile the Windows runtime. The recipe exports three pinned source revisions, applies two PAL patches, generates the guest code and compiles the executable in a new empty build directory.

## Prepare the inputs

1. On 64-bit Windows, install Git and 64-bit Python 3.12 or newer. In the Python environment used to invoke the build, install `capstone==5.0.9` and `pefile==2024.8.26`.
2. Make CMake and Ninja available on PATH. Set `LLVM_MINGW_ROOT` to the root of the unpacked LLVM-MinGW toolchain. The reference uses CMake 4.4.4, Ninja 1.13.2 and the 20260922 LLVM-MinGW distribution with the x86_64 target.
3. Place the **complete extracted PAL disc** in `inputs/windows/disc/`, including `default.xbe` and `media/`. The profile verifies the PAL inventory of 3,092 disc files and checks the XBE SHA-256 `3e4b51ed5e5a772b15e7d4112f12d37d89dd8bc77ad2528c89e287c32f43c1ab`. Keep the placement instructions in the parent folder.
4. The build command can fetch the three pinned repositories into `cache/src/clone1-game`, `cache/src/clone1-toolkit` and `cache/src/clone1-cli`. For offline operation, supply local Git repositories containing the pinned objects through `--game`, `--toolkit` and `--cli`. The [profile pins](../profiles/windows-clone1/pins.json) identify each revision and patch.

## Compile, then launch

In a command prompt at the root of the toolkit:

```bat
build_windows.cmd
run_windows.cmd
```

With the source caches available, `build_windows.cmd --check` verifies prerequisites offline. Use `--disc` to build from an existing extracted disc outside the repository. If you used that option, set `RECOMP_GAME_FILES` to the same disc folder before running `run_windows.cmd`. The launcher uses separate private directories under `build/windows-user/`. The emulated drive uses `hdd/` and title saves use `saves/`. To use an existing save, put a copy of the profile directories under `saves/UDATA/4d530013/`. You can override both roots with `RECOMP_HDD_DIR` and `RECOMP_SAVE_DIR`.

The profile writes `dist/windows/blinx1_recomp.exe` and its companion `config.default.toml`. Each build creates a new job under `build/windows-clone1/`. Its logs and `build-receipt.json` identify the generated source fingerprints, private executable and compilation units. See [Windows source provenance](WINDOWS_HOST_PROVENANCE.md) for licenses and [the build inventory](FINAL_AUDIT.md) for the profile inputs.
