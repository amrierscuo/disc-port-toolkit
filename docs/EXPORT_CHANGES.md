# Export changes

Base reference ff832e9c and Full reference 924bd481 supplied reviewed source snapshots. The public tree was rebuilt from selected files. No private source directory, Git history, game payload or signing identity is bundled. The file ledger gives each path's source and license. The rows below record reviewed export adaptations. The complete current inventory is in PUBLICATION_MANIFEST.json. Byte-preserving copies are identified individually in the ledger.

The previous Full Git rule ignored the 22 upstream provenance files, although they existed on disk. The anchored rule now preserves all 22. The Full adapter had already disabled personal PAL save seeding before this export; MainActivity.java retains that export change. A private demo locale constant can be recomputed in the build staging directory after logical disc verification. Historical source bytes and inventory fingerprints remain available for comparison.

| Public path | Origin | Change or limit |
| --- | --- | --- |
| .gitattributes | New toolkit export | Preserve the exact bytes of the reviewed source across Git line-ending settings. |
| .gitignore | New toolkit export | Anchor scratch/upstream paths to the repository root, allow every required notice, and exclude private inputs. |
| ASSET_POLICY.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| CONTRIBUTING.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| GIT_PUBLISH_BY_USER.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| LICENSES.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| NOTICE.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| PUBLICATION_MANIFEST.json | New toolkit export | Generated public review metadata. |
| README.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| SECURITY.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| TREE_PUBLIC.txt | New toolkit export | Generated public review metadata. |
| build_android_full.cmd | New toolkit export | New source-only publication support. |
| build_android_original.cmd | New toolkit export | New source-only publication support. |
| build_windows.cmd | New toolkit export | New source-only publication support. |
| docs/ARCHITECTURE.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| docs/BUILD_ANDROID_FULL.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| docs/BUILD_ANDROID_ORIGINAL.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| docs/BUILD_WINDOWS.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| docs/EXCLUSIONS.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| docs/EXPORT_CHANGES.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| docs/FILE_MAP.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| docs/FINAL_AUDIT.json | New toolkit export | New review or usage instructions. Contains project metadata only. |
| docs/FINAL_AUDIT.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| docs/PUBLICATION_CHECKLIST.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| docs/TROUBLESHOOTING.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| docs/WINDOWS_HOST_PROVENANCE.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| docs/languages/de.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| docs/languages/es.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| docs/languages/fr.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| docs/languages/it.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| docs/languages/ja.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| inputs/README.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| inputs/android-full/demo/PLACE_DEMO_INPUT_HERE.txt | New toolkit export | New review or usage instructions. Contains project metadata only. |
| inputs/android-full/pal/PLACE_PAL_INPUT_HERE.txt | New toolkit export | New review or usage instructions. Contains project metadata only. |
| inputs/android-original/PLACE_YOUR_INPUT_HERE.txt | New toolkit export | New review or usage instructions. Contains project metadata only. |
| inputs/windows/PLACE_YOUR_INPUT_HERE.txt | New toolkit export | New review or usage instructions. Contains project metadata only. |
| local.inputs.example.json | New toolkit export | New source-only publication support. |
| preview/SCREENSHOT.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| preview/code-verification.png | Local browser capture of tools/verify_inputs.py: payload_digest, GPL-3.0-or-later | Reviewed source excerpt only. Exact PNG digest is pinned by the repository verifier. |
| preview/index.html | New toolkit export | New source-only publication support. |
| preview/preview.css | New toolkit export | New source-only publication support. |
| preview/preview.js | New toolkit export | New source-only publication support. |
| profiles/android-full/build.py | 02_ANDROID_COMPLETO/tools/REBUILD_FULL_PRIVATE.py | Replace private donor layout with portable staging, edition identity, 324 distinct targets, two libraries, verified source revisions, actual disc hashes and local signing. |
| profiles/android-full/dependencies/DEPENDENCY_INVENTORY.json | 02_ANDROID_COMPLETO/dependencies/DEPENDENCY_INVENTORY.json | Keep target, length, hash and group. Drop workstation paths, private history and signing records. Mark public source paths and reviewed export variants. |
| profiles/android-full/source/combined/frontend/app/AndroidManifest.xml | 02_ANDROID_COMPLETO/source/combined/frontend/app/AndroidManifest.xml | Display Disc Port Full on the Android launcher. The inventory records the original and export digests. |
| profiles/android-original/app/AndroidManifest.xml | 01_ANDROID_BASE/app/AndroidManifest.xml | Display Disc Port Original on the Android launcher. |
| profiles/android-full/source/combined/packaging/build_apk.py | 02_ANDROID_COMPLETO/source/combined/packaging/build_apk.py | Parameterize staging, library inputs and signing. Use shorter paths on Windows. Verify alignment, signature, discs and ABI. |
| profiles/android-full/source/combined/packaging/java/it/blinx/demo/v1/DiscInstaller.java | 02_ANDROID_COMPLETO/source/combined/packaging/java/it/blinx/demo/v1/DiscInstaller.java | Verify installed bytes and atomically recover a missing or stale disc marker. |
| profiles/android-full/source/combined/packaging/test_disc_installer.py | 02_ANDROID_COMPLETO/source/combined/packaging/test_disc_installer.py | Remove a workstation JDK path. Run deterministic recovery fixtures in ignored build/. |
| profiles/android-full/test_inventory.py | New toolkit export | Regression checks for the 324/324/2 contract and clean source/override paths. |
| profiles/android-original/app/src/org/blinx/android/base/DiscStore.java | New toolkit export | New interruption-safe install of a hash-checked disc and receipt. |
| profiles/android-original/app/src/org/blinx/android/base/ImportActivity.java | 01_ANDROID_BASE/app/src/org/blinx/android/base/ImportActivity.java | Replace generic picker with local embedded PAL asset and checksum-verified installation. |
| profiles/android-original/app/src/org/blinx/android/base/MinimalGameActivity.java | 01_ANDROID_BASE/app/src/org/blinx/android/base/MinimalGameActivity.java | Use committed disc location and new touch overlay. Retain SDL controller input. |
| profiles/android-original/app/src/org/blinx/android/base/TouchControls.java | 02_ANDROID_COMPLETO/source/combined/runtime/java/it/blinx/demo/v1/TouchControls.java | Keep the neutral controls for the explicit fallback setting. The default build uses the Full graphical touch layout. |
| profiles/android-original/app/src/org/blinx/android/base/XboxTouchInput.java | 02_ANDROID_COMPLETO/source/combined/runtime/java/it/blinx/demo/v1/XboxTouchInput.java | Adapt the package and bridge to the single-edition app. |
| profiles/android-original/build.py | 01_ANDROID_BASE/tools/build_base.py | Build a signed single-disc APK from the verified PAL disc and pinned libraries. Generate the graphical controls from owned disc textures or use a complete verified local skin. |
| profiles/android-original/generate_pad.py | Project-owned touch layout and drawing recipes: blinx_android_redesign.py, blinx_touch_complete.py, blinx_canva_pad.py, ui_art.py and refine_touch.py | Ports font crops, directional icons, sticks and nine-patch frame assembly to verified user disc inputs. Removes workstation paths and dependencies on earlier graphics exports. Produces matching private images on demand. |
| profiles/android-original/native_inputs.py | New toolkit export | Verify hakuX/FEX pins, gitlink, license and both historical ARM64 hashes. |
| profiles/android-original/pad_assets.py | New toolkit export | Verify touch resource provenance and assemble the same two-rail skin as Android Full in private build staging. |
| profiles/android-original/tests/DiscStoreTest.java | New toolkit export | Exercise recovery and paired receipt publication using synthetic files. |
| profiles/android-original/tests/test_pad_recipe.py | New toolkit export | New source-only publication support. |
| profiles/android-original/tests/test_pad_selection.py | New toolkit export | New source-only publication support. |
| provenance/OTS_STATUS_CURRENT.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| provenance/README.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| profiles/windows-clone1/PAL-LICENSE.txt | Disc Port Toolkit contributors | MIT license for the project-owned PAL additions. |
| third_party/README.md | New toolkit export | New review or usage instructions. Contains project metadata only. |
| third_party/pins.json | New toolkit export | New source-only publication support. |
| tools/build.py | New toolkit export | Dispatch only after identity preflight. Pass checked paths to the profile. |
| tools/create_anchor_after_commit.py | New toolkit export | Create an offline receipt from a committed tree after owner approval. |
| tools/disc_fingerprints.json | New toolkit export | Hashes/counts of logical disc inventories; no game bytes. |
| tools/fetch_dependencies.py | New toolkit export | Fetch or verify exact public tool-source commits in an isolated local cache. |
| tools/verify_inputs.py | New toolkit export | Read XBE certificates and hash all Android disc entries. Validate private repacking and return verified paths. |
| tools/verify_release.py | New toolkit export | New source-only publication support. |
| tools/verify_repository.py | New toolkit export | Check physical files, Git visibility, content policy, ledger, pins, syntax and inventory. |
| verify_repository.cmd | New toolkit export | New source-only publication support. |
