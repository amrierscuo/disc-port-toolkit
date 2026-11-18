# Owner's publication guide

Use a dedicated source-only checkout for publication. Keep your development repository, game files, generated code, preview clips, builds and signing material in a separate location. The following commands use PowerShell from the source checkout.

## Choose the identity for the public history

For a first publication, start from the reviewed files in a new independent repository. Keep any existing private history in its original location. If a clean release repository already exists, continue there. Choose the public display name and the exact `noreply` address shown in your [GitHub email settings](https://github.com/settings/emails).

```powershell
git config --local user.name (Read-Host 'Public author name')
git config --local user.email (Read-Host 'GitHub noreply address')
git var GIT_AUTHOR_IDENT
git var GIT_COMMITTER_IDENT
```

Check both effective identities before creating a commit. Changing these settings only affects subsequent commits. [GitHub commit email documentation](https://docs.github.com/en/account-and-profile/how-tos/email-preferences/setting-your-commit-email-address)

## Stage the reviewed files

Run the verifier in the release checkout. It checks file contents and also detects unexpected files, including ignored files. Compare its reported manifest digest with the separately reviewed digest for this release. Stage the manifest itself and exactly the paths it lists.

```powershell
.\verify_repository.cmd
if ($LASTEXITCODE -ne 0) { throw 'Source verification failed.' }
$manifest = Get-Content -Raw -LiteralPath PUBLICATION_MANIFEST.json | ConvertFrom-Json
$approved = @('PUBLICATION_MANIFEST.json') + @($manifest.files.path)
foreach ($path in $approved) {
    git --literal-pathspecs add -- $path
    if ($LASTEXITCODE -ne 0) { throw "Could not stage $path" }
}
$indexed = @(git -c core.quotePath=false ls-files)
if ($LASTEXITCODE -ne 0) { throw 'Could not read the Git index.' }
if (Compare-Object $approved $indexed -CaseSensitive) { throw 'The index differs from the reviewed file list.' }
git diff --cached --stat
git diff --cached
```

For later releases, explicitly stage any previously reviewed paths that the new manifest removes, then repeat the checks. Keep required third-party credits and notices with their source files.

## Inspect the publication candidate

After committing the reviewed changes locally, inspect the tree, every reachable commit's author and committer, and the branches, tags and remote destination.

```powershell
git status --short
git log --all --format="%h %an <%ae> | %cn <%ce>"
git rev-list --max-parents=0 --all
git for-each-ref --format="%(refname)"
git remote -v
```

The first release from a new repository should have one parentless commit, a clean worktree and the approved public identities. Review the intended private GitHub destination before uploading the release branch. Check its visibility, advertised references and any hosted releases or artifacts before deciding whether to make it public. Source verification covers the toolkit files. Locally generated APKs, game executables and media belong outside the release.
