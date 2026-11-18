# Owner review

1. Open the dedicated source checkout. Compare `TREE_PUBLIC.txt` with `PUBLICATION_MANIFEST.json` and review the Windows Clone 1, Android Original and Android Full entries.
2. Check the licenses and attribution for the shared material in [Licenses](../LICENSES.md), [Asset policy](../ASSET_POLICY.md) and [Windows host provenance](WINDOWS_HOST_PROVENANCE.md). The Android Full build uses a locally supplied game and demo.
3. Run `verify_repository.cmd`. Compare the reported manifest digest to the separately reviewed digest. Check that the source archive matches the files in the proposed release commit.
4. Follow the [owner's publication guide](../GIT_PUBLISH_BY_USER.md) to stage only the reviewed paths. Inspect the staged diff, author and committer identities, reachable commits, references and destination. An independent source snapshot allows the original development history to stay private.
5. Review the private GitHub repository and its hosted assets before changing visibility. Keep game and demo files, generated controller artwork, game-derived executables, APKs, saves, signing material and private clips in local storage.
