# Windows PowerShell 5.1. Portable tools live under cache/start-here.
# The hashes below are from the respective publishers' GitHub release assets.
. (Join-Path $PSScriptRoot 'start_progress.ps1')

function Save-StartDownload {
    param(
        [Parameter(Mandatory)][uri]$Url,
        [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{64}$')][string]$Sha256,
        [Parameter(Mandatory)][string]$Destination
    )
    if ($Url.Scheme -ne 'https') { throw 'Tool downloads require HTTPS.' }
    $file = [IO.Path]::GetFullPath($Destination)
    if ([IO.File]::Exists($file) -and (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -eq $Sha256) { return }
    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($file))
    $part = "$file.$([guid]::NewGuid().ToString('N')).part"
    try {
        $asset = [uri]::UnescapeDataString($Url.Segments[-1])
        $transfer = [hashtable]::Synchronized(@{ Path = $part; Total = 0L })
        $curl = Get-Command curl.exe -CommandType Application -ErrorAction SilentlyContinue
        Invoke-StartProgress -Label "Download $asset" -EstimateSeconds 45 -Transfer $transfer -Action {
            if ($curl) {
                # Read only the final response length. Redirect headers may contain signed URLs.
                $body = $false
                & $curl.Source --fail --silent --show-error --location --retry 2 --connect-timeout 20 --proto '=https' --proto-redir '=https' --dump-header - --output $part $Url.AbsoluteUri | ForEach-Object {
                    if ($_ -match '^HTTP/\S+\s+(\d{3})') {
                        $body = [int]$Matches[1] -eq 200
                        $transfer.Total = 0L
                    } elseif ($body -and $_ -match '^Content-Length:\s*(\d+)\s*$') {
                        $transfer.Total = [long]$Matches[1]
                    }
                }
                if ($LASTEXITCODE -ne 0) { throw "Download failed ($LASTEXITCODE): $Url" }
            } else {
                [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
                $client = New-Object Net.WebClient
                try {
                    $response = $client.OpenRead($Url)
                    try {
                        $transfer.Total = [long]$client.ResponseHeaders['Content-Length']
                        $output = [IO.File]::Create($part)
                        try { $response.CopyTo($output) } finally { $output.Dispose() }
                    } finally { $response.Dispose() }
                } finally { $client.Dispose() }
            }
            if ((Get-FileHash -LiteralPath $part -Algorithm SHA256).Hash -ne $Sha256) {
                throw "The download from $Url differs from its published SHA-256."
            }
            if ([IO.File]::Exists($file)) {
                [IO.File]::Replace($part, $file, [System.Management.Automation.Language.NullString]::Value)
            } else {
                [IO.File]::Move($part, $file)
            }
        }
    } finally {
        if ([IO.File]::Exists($part)) { [IO.File]::Delete($part) }
    }
}

function Get-StartArchiveTool {
    param(
        [Parameter(Mandatory)][string]$Cache,
        [Parameter(Mandatory)][ValidatePattern('^[a-zA-Z0-9][a-zA-Z0-9._+-]*$')][string]$Name,
        [Parameter(Mandatory)][uri]$Url,
        [Parameter(Mandatory)][ValidatePattern('^[a-fA-F0-9]{64}$')][string]$Sha256,
        [Parameter(Mandatory)][string]$RelativeExe
    )
    $cachePath = [IO.Path]::GetFullPath($Cache)
    $destination = Join-Path $cachePath $Name
    $program = [IO.Path]::GetFullPath((Join-Path $destination $RelativeExe))
    if (-not $program.StartsWith($destination + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw "Invalid path inside the tool archive: $RelativeExe"
    }
    $receipt = Join-Path $destination '.archive-sha256'
    if ([IO.File]::Exists($program) -and [IO.File]::Exists($receipt) -and
        [IO.File]::ReadAllText($receipt).Trim() -eq $Sha256) { return $program }
    if ([IO.Directory]::Exists($destination)) { throw "Unrecognized cached tool directory: $destination" }
    $archiveName = [uri]::UnescapeDataString($Url.Segments[-1])
    if ($archiveName -ne [IO.Path]::GetFileName($archiveName)) { throw 'Invalid tool archive name.' }
    $archive = Join-Path (Join-Path $cachePath 'downloads') $archiveName
    Save-StartDownload -Url $Url -Sha256 $Sha256 -Destination $archive
    $temporary = Join-Path $cachePath ('.unpack-' + [guid]::NewGuid().ToString('N'))
    [void][IO.Directory]::CreateDirectory($temporary)
    try {
        $tar = Join-Path $env:SystemRoot 'System32/tar.exe'
        if (-not [IO.File]::Exists($tar)) { throw 'The Windows tar.exe utility is required to unpack portable tools.' }
        Invoke-StartProgress -Label "Extract $Name" -EstimateSeconds 25 -Action {
            & $tar -xf $archive -C $temporary
            if ($LASTEXITCODE -ne 0 -or -not [IO.File]::Exists((Join-Path $temporary $RelativeExe))) {
                throw "Unable to unpack $Name ($RelativeExe). Check free space and use a short toolkit path."
            }
            [IO.File]::WriteAllText((Join-Path $temporary '.archive-sha256'), $Sha256)
            [IO.Directory]::Move($temporary, $destination)
        }
    } finally {
        if ([IO.Directory]::Exists($temporary) -and [IO.Path]::GetDirectoryName($temporary) -eq $cachePath) {
            [IO.Directory]::Delete($temporary, $true)
        }
    }
    return $program
}

function Add-StartPath {
    param([Parameter(Mandatory)][string]$Directory)
    $env:PATH = $Directory + ';' + (($env:PATH -split ';' | Where-Object { $_ -and $_ -ne $Directory }) -join ';')
}

function Find-StartPython {
    foreach ($entry in @(Get-Command python.exe -All -CommandType Application -ErrorAction SilentlyContinue)) {
        if (-not $entry -or $entry.Source -like '*\Microsoft\WindowsApps\*') { continue }
        $probe = & $entry.Source -I -c "import sys,struct,venv,ensurepip; print(int(sys.implementation.name=='cpython' and (3,12)<=sys.version_info[:2]<=(3,14) and struct.calcsize('P')==8))" 2>$null
        if ($LASTEXITCODE -eq 0 -and $probe -eq '1') { return $entry.Source }
    }
}

function Initialize-StartTools {
    param(
        [Parameter(Mandatory)][ValidateSet('windows','android-original')][string]$Profile,
        [Parameter(Mandatory)][string]$Root
    )
    $cache = Join-Path $Root 'cache/start-here/tools'
    $github = 'https://github.com'
    $git = Get-Command git.exe -CommandType Application -ErrorAction SilentlyContinue
    if (-not $git) {
        $gitPath = Get-StartArchiveTool -Cache $cache -Name 'mingit-2.56.1' -RelativeExe 'cmd/git.exe' `
            -Url "$github/git-for-windows/git/releases/download/v2.56.1.windows.1/MinGit-2.56.1-64-bit.zip" `
            -Sha256 '4a92850b6e917da4fd010669b614b55dccf5b0ec333a534cc4be4700dfefdafd'
        Add-StartPath (Split-Path -Parent $gitPath)
    }
    $basePython = Find-StartPython
    if (-not $basePython) {
        $basePython = Get-StartArchiveTool -Cache $cache -Name 'cpython-3.13.18' -RelativeExe 'python/python.exe' `
            -Url "$github/astral-sh/python-build-standalone/releases/download/20261110/cpython-3.13.18%2B20261110-x86_64-pc-windows-msvc-install_only.tar.gz" `
            -Sha256 '7e93813bd362c6413f30788b248590e2f169df2d17dcac81aafbcc7a6eeb542c'
    }
    $venv = Join-Path $Root 'cache/start-here/venv'
    $python = Join-Path $venv 'Scripts/python.exe'
    $ready = $false
    if ([IO.File]::Exists($python)) {
        $version = & $python -I -c 'import sys; print(int(sys.prefix!=sys.base_prefix))' 2>$null
        $ready = $LASTEXITCODE -eq 0 -and $version -eq '1'
    }
    if (-not $ready) {
        Invoke-StartProgress -Label 'Prepare the local Python environment' -EstimateSeconds 10 -Action {
            & $basePython -I -m venv $venv | Out-Host
            if ($LASTEXITCODE -ne 0) { throw 'Could not prepare Python in cache/start-here/venv.' }
        }
    }
    Add-StartPath (Split-Path -Parent $python)
    $env:VIRTUAL_ENV = $venv
    $env:PYTHONNOUSERSITE = '1'
    $requirements = @('Pillow==12.0.0')
    if ($Profile -eq 'windows') {
        $pins = Get-Content -LiteralPath (Join-Path $Root 'profiles/windows-clone1/pins.json') -Raw | ConvertFrom-Json
        $requirements = @($pins.python_packages.PSObject.Properties | ForEach-Object { $_.Name + '==' + $_.Value })
    }
    # No package index request on subsequent runs with the required wheels.
    $probe = "from importlib.metadata import distributions; import sys; got={p.metadata['Name'].lower():p.version for p in distributions()}; sys.exit(any(got.get(n.lower())!=v for n,v in (x.split('==') for x in sys.argv[1:])))"
    & $python -I -c $probe @requirements
    if ($LASTEXITCODE -ne 0) {
        # Published PyPI wheel digests: Windows x64 CPython 3.12, 3.13 and 3.14.
        $wheelHashes = @{
            'capstone==5.0.9' = @('732cedbbb56d42e723f14d7af6387f1454194a820b4b96b56d1e53f865ef85d0')
            'pefile==2024.8.26' = @('76f8b485dcd3b1bb8166f1128d395fa3d87af26360c2358fb75b80019b957c6f')
            'Pillow==12.0.0' = @('9fe611163f6303d1619bbcb653540a4d60f9e55e622d60a3108be0d5b441017a',
                '4cf7fed4b4580601c4345ceb5d4cbf5a980d030fd5ad07c4d2ec589f95f09905',
                '8dc232e39d409036af549c86f24aed8273a40ffa459981146829a324e0848b4b')
        }
        $lines = foreach ($spec in $requirements) {
            if (-not $wheelHashes.ContainsKey($spec)) { throw "Missing pinned wheel for $spec." }
            $spec + ' ' + (($wheelHashes[$spec] | ForEach-Object { "--hash=sha256:$_" }) -join ' ')
        }
        $lockFile = Join-Path $venv "$Profile-wheels.txt"
        [IO.File]::WriteAllLines($lockFile, [string[]]$lines)
        $pipCache = Join-Path $Root 'cache/start-here/pip-cache'
        Invoke-StartProgress -Label 'Install Python build packages' -EstimateSeconds 15 -LogLines -Action {
            & $python -I -m pip --isolated --disable-pip-version-check --cache-dir $pipCache install --index-url 'https://pypi.org/simple' --only-binary=:all: --require-hashes -r $lockFile | Out-Host
            if ($LASTEXITCODE -ne 0) { throw "Could not install the required Python packages: $($requirements -join ', ')." }
        }
    }
    if ($Profile -ne 'windows') { return }

    $cmake = Get-Command cmake.exe -CommandType Application -ErrorAction SilentlyContinue
    if (-not $cmake -or (@(& $cmake.Source --version)[0] -ne "cmake version $($pins.cmake_version)")) {
        $cmakePath = Get-StartArchiveTool -Cache $cache -Name 'cmake-4.4.4' -RelativeExe 'cmake-4.4.4-windows-x86_64/bin/cmake.exe' `
            -Url "$github/Kitware/CMake/releases/download/v4.4.4/cmake-4.4.4-windows-x86_64.zip" `
            -Sha256 'bace36e94b31c68ab6fa295f26dfa11219e0701cf7c94b0284a7d1cb13dac536'
        Add-StartPath (Split-Path -Parent $cmakePath)
    }
    $ninja = Get-Command ninja.exe -CommandType Application -ErrorAction SilentlyContinue
    if (-not $ninja -or (@(& $ninja.Source --version)[0] -ne $pins.ninja_version)) {
        $ninjaPath = Get-StartArchiveTool -Cache $cache -Name 'ninja-1.13.2' -RelativeExe 'ninja.exe' `
            -Url "$github/ninja-build/ninja/releases/download/v1.13.2/ninja-win.zip" `
            -Sha256 '07fc8261b42b20e71d1720b39068c2e14ffcee6396b76fb7a795fb460b78dc65'
        Add-StartPath (Split-Path -Parent $ninjaPath)
    }
    $compiler = 'bin/x86_64-w64-mingw32-clang.exe'
    if ($env:LLVM_MINGW_ROOT -and [IO.File]::Exists((Join-Path $env:LLVM_MINGW_ROOT $compiler))) {
        if ((Get-FileHash -LiteralPath (Join-Path $env:LLVM_MINGW_ROOT $compiler)).Hash -eq '20ba1856942744291a6aaaf9f2c94f6d0cd7cac5aa9557fce96471dc7ef25d6f') { return }
    }
    $llvm = 'llvm-mingw-20260922-ucrt-x86_64'
    $cc = Get-StartArchiveTool -Cache $cache -Name $llvm -RelativeExe "$llvm/$compiler" `
        -Url "$github/mstorsjo/llvm-mingw/releases/download/20260922/$llvm.zip" `
        -Sha256 'e3ad77d117a4bea19a7a3b333341824d79a5a371004a10e25b8504e7b3047666'
    $env:LLVM_MINGW_ROOT = Split-Path -Parent (Split-Path -Parent $cc)
}
