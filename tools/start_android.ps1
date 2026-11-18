# Android Original prerequisites. start_dependencies.ps1 supplies the pinned download helpers.
function Test-StartAndroidJdk {
    param([string]$Directory)
    if (-not $Directory) { return $false }
    foreach ($file in @('bin/javac.exe', 'bin/java.exe', 'bin/keytool.exe', 'release')) {
        if (-not [IO.File]::Exists((Join-Path $Directory $file))) { return $false }
    }
    return [IO.File]::ReadAllText((Join-Path $Directory 'release')) -match '(?m)^JAVA_VERSION="21(?:[.+-]|")'
}

function Test-StartAndroidSdk {
    param([string]$Directory)
    if (-not $Directory) { return $false }
    foreach ($file in @('aapt.exe', 'aapt2.exe', 'zipalign.exe', 'lib/d8.jar', 'lib/apksigner.jar')) {
        if (-not [IO.File]::Exists((Join-Path $Directory "build-tools/36.0.0/$file"))) { return $false }
    }
    return [IO.File]::Exists((Join-Path $Directory 'platforms/android-36/android.jar'))
}

function Initialize-StartAndroidSdk {
    param([string]$Root)
    $toolCache = Join-Path $Root 'cache/start-here/tools'
    $javac = Get-Command javac.exe -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    $javaFromPath = if ($javac) { Split-Path (Split-Path $javac.Source -Parent) -Parent }
    $androidStudioJdk = Join-Path $env:ProgramFiles 'Android/Android Studio/jbr'
    $javaRoot = @($env:JAVA_HOME, $javaFromPath, $androidStudioJdk) |
        Where-Object { Test-StartAndroidJdk $_ } | Select-Object -First 1
    if (-not $javaRoot) {
        $javacPath = Get-StartArchiveTool -Cache $toolCache -Name 'temurin-21.0.13+9' `
            -Url 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_windows_hotspot_21.0.13_9.zip' `
            -Sha256 'a4dbaa05fca4bd89d84584e58c459bd732efebb1722af80ab13c6516832004f9' `
            -RelativeExe 'jdk-21.0.13+9/bin/javac.exe'
        $javaRoot = Split-Path (Split-Path $javacPath -Parent) -Parent
        if (-not (Test-StartAndroidJdk $javaRoot)) { throw 'The downloaded JDK is incomplete.' }
    }
    $env:JAVA_HOME = $javaRoot

    $privateSdk = Join-Path $toolCache 'android-sdk'
    $sdks = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android/Sdk'), $privateSdk)
    $sdk = $sdks | Where-Object { Test-StartAndroidSdk $_ } | Select-Object -First 1
    if (-not $sdk) {
        $manager = $null
        foreach ($folder in $sdks) {
            if (-not $folder) { continue }
            $installed = Join-Path $folder 'cmdline-tools/latest/bin/sdkmanager.bat'
            if ([IO.File]::Exists($installed)) { $manager = $installed; break }
        }
        if (-not $manager) {
            $toolsName = 'android-tools-15859902'
            $managerRelative = 'cmdline-tools/bin/sdkmanager.bat'
            $cachedManager = Join-Path (Join-Path $toolCache $toolsName) $managerRelative
            if (-not [IO.File]::Exists($cachedManager)) {
                Write-Host 'Google provides its command-line tools under the Android SDK License Agreement.'
                Write-Host 'Read the terms at https://developer.android.com/studio#command-line-tools-only'
                $answer = Read-Host 'Do you accept these terms and want to download the tools? [y/N]'
                if ($answer -notmatch '^(?i:y|yes)$') { throw 'Android SDK download cancelled.' }
            }
            $manager = Get-StartArchiveTool -Cache $toolCache -Name $toolsName `
                -Url 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' `
                -Sha256 '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a' `
                -RelativeExe $managerRelative
        }
        # Install missing components in this checkout rather than changing another SDK.
        $sdk = $privateSdk
        [void][IO.Directory]::CreateDirectory($sdk)
        Write-Host 'Installing Android Platform 36 and Build Tools 36.0.0. Google may ask you to read and accept package licenses.'
        Write-Host '[....................] 0% Android SDK packages (sdkmanager reports download and installation progress below)'
        & $manager "--sdk_root=$sdk" 'platforms;android-36' 'build-tools;36.0.0' | Out-Host
        if ($LASTEXITCODE -ne 0 -or -not (Test-StartAndroidSdk $sdk)) {
            throw 'Android SDK setup did not finish. Rerun START_HERE to resume, or select Platform 36 and Build Tools 36.0.0 in Android Studio.'
        }
        Write-Host '[####################] 100% Android SDK packages'
    }
    $env:ANDROID_HOME = $sdk
    $env:ANDROID_SDK_ROOT = $sdk
}

function Initialize-StartAndroidLibraries {
    param([string]$Root, [string]$HakuApk)
    $lock = Get-Content -LiteralPath (Join-Path $Root 'profiles/android-original/NATIVE_SOURCE.lock.json') -Raw | ConvertFrom-Json
    $names = @('libSDL3.so', 'libhakuxcore.so')
    $parent = Join-Path $Root 'cache/native'
    $target = Join-Path $parent 'arm64-v8a'
    $missing = @()
    foreach ($name in $names) {
        $file = Join-Path $target $name
        if (-not [IO.File]::Exists($file)) { $missing += $name }
        elseif ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $lock.libraries.$name) {
            throw "The cached library differs from the Android source lock: $file. Move it aside and rerun START_HERE."
        }
    }
    if ($HakuApk) {
        $apk = (Get-Item -LiteralPath $HakuApk -ErrorAction Stop).FullName
        if ((Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash -ne $lock.apk_sha256) {
            throw 'The selected APK does not match hakuX v1.3.0 in the Android source lock.'
        }
    }
    if ($missing.Count -eq 0) { return }
    if (-not $HakuApk) {
        $apk = Join-Path $Root 'cache/start-here/downloads/hakuX-1.3.0.apk'
        [void][IO.Directory]::CreateDirectory((Split-Path $apk -Parent))
        Save-StartDownload -Url 'https://github.com/rfandango/hakuX/releases/download/v1.3.0/hakuX-1.3.0.apk' `
            -Sha256 $lock.apk_sha256 -Destination $apk
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [void][IO.Directory]::CreateDirectory($parent)
    $stage = Join-Path $parent ('.hakux-' + [guid]::NewGuid().ToString('N'))
    [void][IO.Directory]::CreateDirectory($stage)
    try {
        Invoke-StartProgress -Label 'Extract and verify Android ARM64 libraries' -EstimateSeconds 6 -Action {
            $archive = [IO.Compression.ZipFile]::OpenRead($apk)
            try {
                foreach ($name in $names) {
                    $file = Join-Path $stage $name
                    $entry = $archive.GetEntry("lib/arm64-v8a/$name")
                    if (-not $entry) { throw "Missing ARM64 entry in hakuX: $name" }
                    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $file)
                    if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $lock.libraries.$name) {
                        throw "The hakuX library differs from its expected fingerprint: $name"
                    }
                }
            } finally { $archive.Dispose() }
        }
        if (-not [IO.Directory]::Exists($target)) {
            try { [IO.Directory]::Move($stage, $target); return }
            catch [IO.IOException] { if (-not [IO.Directory]::Exists($target)) { throw } }
        }
        foreach ($name in $missing) {
            $destination = Join-Path $target $name
            try { [IO.File]::Move((Join-Path $stage $name), $destination) }
            catch [IO.IOException] {
                if (-not [IO.File]::Exists($destination) -or
                    (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash -ne $lock.libraries.$name) { throw }
            }
        }
    } finally {
        if ([IO.Directory]::Exists($stage)) {
            foreach ($name in $names) { [IO.File]::Delete((Join-Path $stage $name)) }
            [IO.Directory]::Delete($stage)
        }
    }
}

function Initialize-StartAndroid {
    param([Parameter(Mandatory=$true)][string]$Root, [string]$HakuApk)
    $ErrorActionPreference = 'Stop'
    Initialize-StartAndroidLibraries -Root $Root -HakuApk $HakuApk
    Initialize-StartAndroidSdk -Root $Root
}
