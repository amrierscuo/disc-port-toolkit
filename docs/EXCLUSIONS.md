# Exclusions

The 263 withheld Full resource targets are listed individually in profiles/android-full/dependencies/DEPENDENCY_INVENTORY.json. Read the private rows' exact target, byte count and digest there. Rebuilds require the owner's authorized copies. Both native library filenames and hashes are in profiles/android-original/NATIVE_SOURCE.lock.json.

| Outside the public toolkit | Reason |
| --- | --- |
| PAL/Demo XBE, extracted media, XISO, translated guest C/C++ | Personal game inputs or game-derived data |
| Historical and newly built APKs, EXEs, compiled libraries, object files, SDK, compiler archives | Private generated artifacts or separately supplied dependencies |
| Game saves, seeds, signing stores, passwords, tokens | Private state or credentials |
| Old Git directories, bundles, source ZIPs, APKs and stage receipts with workstation paths | Private history or redundant artifacts. The public legacy proof only identifies earlier items by hash. |

Earlier source-candidate files not copied verbatim or used as named adaptation inputs are listed below. Old private build workflows and audit scripts were replaced. Historical recipes referencing unavailable donor paths were omitted. Manifests and receipts for the previous trees are superseded by this tree's ledger.

- 01_ANDROID_BASE/.gitignore
- 01_ANDROID_BASE/ASSET_POLICY.txt
- 01_ANDROID_BASE/local.inputs.example.json
- 01_ANDROID_BASE/MANIFEST.json
- 01_ANDROID_BASE/README.md
- 01_ANDROID_BASE/RELEASE_STATUS.json
- 01_ANDROID_BASE/SOURCE_ORIGIN.json
- 01_ANDROID_BASE/TIMESTAMPS.txt
- 01_ANDROID_BASE/tools/fetch_native_source.py
- 01_ANDROID_BASE/tools/timestamp.py
- 01_ANDROID_BASE/tools/verify_release.py
- 02_ANDROID_COMPLETO/.gitignore
- 02_ANDROID_COMPLETO/ASSET_POLICY.txt
- 02_ANDROID_COMPLETO/local.inputs.example.json
- 02_ANDROID_COMPLETO/MANIFEST.json
- 02_ANDROID_COMPLETO/README.md
- 02_ANDROID_COMPLETO/recipes/historical/combined/carousel/render_previews.py
- 02_ANDROID_COMPLETO/recipes/historical/combined/carousel/renderer_patched.py
- 02_ANDROID_COMPLETO/recipes/historical/combined/music/prepare_music.py
- 02_ANDROID_COMPLETO/recipes/historical/combined/music/verify_music.py
- 02_ANDROID_COMPLETO/recipes/historical/legacy_renderer/native_visuals.py
- 02_ANDROID_COMPLETO/recipes/historical/task_027/language/candidate/test_demo_locale.py
- 02_ANDROID_COMPLETO/recipes/historical/task_027/music/prepare_menu_music.py
- 02_ANDROID_COMPLETO/recipes/historical/task_027/music/test_menu_music.py
- 02_ANDROID_COMPLETO/recipes/historical/ui_019/blinx_canva_pad.py
- 02_ANDROID_COMPLETO/recipes/historical/ui_019/build_blinx_launcher_captions.py
- 02_ANDROID_COMPLETO/recipes/historical/ui_019/ui_art.py
- 02_ANDROID_COMPLETO/RELEASE_STATUS.json
- 02_ANDROID_COMPLETO/source/app/AndroidManifest.xml
- 02_ANDROID_COMPLETO/source/app/src/it/blinx/demo/v1/GameActivity.java
- 02_ANDROID_COMPLETO/source/app/src/it/blinx/demo/v1/MainActivity.java
- 02_ANDROID_COMPLETO/source/app/src/it/blinx/demo/v1/TouchControls.java
- 02_ANDROID_COMPLETO/source/combined/carousel_live/export_live.py
- 02_ANDROID_COMPLETO/source/combined/frontend/build_launcher_icon.py
- 02_ANDROID_COMPLETO/source/combined/packaging/adapt_pal_saves_for_hakux.py
- 02_ANDROID_COMPLETO/source/combined/packaging/export_pal_saves.py
- 02_ANDROID_COMPLETO/source/combined/packaging/prepare_payloads.py
- 02_ANDROID_COMPLETO/source/combined/packaging/test_pal_save_installer.py
- 02_ANDROID_COMPLETO/source/combined/prepare_frontend.py
- 02_ANDROID_COMPLETO/SOURCE_ORIGIN.json
- 02_ANDROID_COMPLETO/TIMESTAMPS.txt
- 02_ANDROID_COMPLETO/tools/fetch_native_source.py
- 02_ANDROID_COMPLETO/tools/timestamp.py
- 02_ANDROID_COMPLETO/tools/verify_release.py
