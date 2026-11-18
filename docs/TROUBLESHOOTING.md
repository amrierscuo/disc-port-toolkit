# Troubleshooting

## PAL disc files differ from the reference

If an older launcher reports **3,092 entries** and the following SHA-256, your files already match the PAL reference.

```text
070b3aac44f73eed1c5c8b17264885468fe246a13ee82fd96105e1d6208ce901
```

Open PowerShell in the toolkit folder, run `git pull --ff-only`, then run `.\START_HERE.cmd` and select the same disc again. If you downloaded the toolkit as a ZIP, obtain the current source ZIP instead. The earlier reference included a local directory listing at `media/tree.txt`. You can leave your disc exactly as it is. Running from the same toolkit folder reuses the tools already installed.

For a different unrecognized fingerprint, check the selected folder against your own PAL backup. It can be on any drive, outside the toolkit. Select `default.xbe` beside `media/`, including everything inside `media/` in its original location. The verifier checks relative paths, sizes and contents. You can also select the PAL game-partition XISO directly.

If your DVD backup includes a separate `xdemos/` bonus folder, keep that backup intact. Create a new folder on your PC and copy the root `default.xbe` and the entire `media/` folder into it. Select `default.xbe` in that new folder.

## Restore the game window

If you hear Clone 1 after a successful Windows build but see no game window, close that instance of `blinx1_recomp.exe` in Task Manager under **Details**. Open PowerShell in the toolkit folder and run:

```powershell
git pull --ff-only
.\run_windows.cmd
```

The updated launcher uses Direct3D 11 and opens the existing executable with the saved disc location. To try it with an older launcher, use `$env:RECOMP_PB_BACKEND = 'd3d11'` in the same PowerShell window before running `.\run_windows.cmd`. If you deliberately set a rendering backend or enabled the framebuffer window, the updated launcher respects that choice.

## Other setup issues
- If a download is interrupted, run the same command again. The launcher reuses completed downloads and reports any file whose fingerprint differs. To supply an existing matching hakuX APK, add `-HakuApk "D:\Apps\hakuX-1.3.0.apk"` to the Android command.
- If you move the Windows disc folder, run the launcher and choose its new location. For updates to an Android Original installation, keep the signing identity in `cache/local-signing/`.

- If the launcher reports an unsupported `default.xbe`, check the PAL title ID and XBE SHA-256 in [the disc fingerprints](../tools/disc_fingerprints.json).
- Source cache missing: use python -B tools/fetch_dependencies.py hakux fex on an online machine. Program exits with the name of the missing revision or file. Preserve a verified local cache for later use.
- If a compiler, source input or tool is missing, consult the guide for that profile. Use --check for an offline preflight.
- Missing Full resource: inspect the exact target, expected size and hash in the Full inventory. A valid PAL and Demo disc still leave private hub assets to supply.
- If the Clone 1 build rejects the disc or source revision, check the PAL input hashes and pinned revisions in [the Windows guide](BUILD_WINDOWS.md).
- Publication audit reports extra files: use a source-only review checkout. Move private build artifacts to your own private build directory before publishing.
