[CmdletBinding()]
param(
    [Parameter(Position=0)][ValidateSet('windows','android-original')][string]$Profile,
    [Parameter(Position=1)][string]$Disc,
    [string]$HakuApk,
    [switch]$NoLaunch,
    [switch]$Play,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$interactive = !$Profile -or !$Disc
$savedDisc = Join-Path $Root 'cache\start-here\windows-disc.txt'
$env:PYTHONDONTWRITEBYTECODE = '1'
$env:PYTHONIOENCODING = 'utf-8'
[Console]::OutputEncoding = New-Object Text.UTF8Encoding($false)

function Select-StartProfile {
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    $form = New-Object Windows.Forms.Form
    $form.Text = 'BLiNX | Choose a build'
    $form.ClientSize = New-Object Drawing.Size(420,142)
    $form.Font = New-Object Drawing.Font('Segoe UI', 10)
    $form.FormBorderStyle = 'FixedDialog'
    $form.MaximizeBox = $false
    $form.StartPosition = 'CenterScreen'
    $label = New-Object Windows.Forms.Label
    $label.Text = 'What would you like to build from your PAL disc?'
    $label.SetBounds(18,16,390,35)
    $form.Controls.Add($label)
    foreach ($item in @(@('Windows Clone 1','OK',18), @('Android Original','Yes',214))) {
        $button = New-Object Windows.Forms.Button
        $button.Text = $item[0]
        $button.DialogResult = $item[1]
        $button.SetBounds($item[2],58,188,56)
        $form.Controls.Add($button)
    }
    try {
        switch ($form.ShowDialog()) {
            'OK' { return 'windows' }
            'Yes' { return 'android-original' }
        }
    } finally { $form.Dispose() }
}

function Select-StartDisc {
    Add-Type -AssemblyName System.Windows.Forms
    $dialog = New-Object Windows.Forms.OpenFileDialog
    $dialog.Title = 'Choose your PAL disc image, or default.xbe inside an extracted disc'
    $dialog.Filter = 'PAL disc image or extracted default.xbe|*.iso;*.xiso;default.xbe|All files|*.*'
    $dialog.RestoreDirectory = $true
    try {
        if ($dialog.ShowDialog() -eq 'OK') { return $dialog.FileName }
    } finally { $dialog.Dispose() }
}

function Start-LocalGame([string]$Directory) {
    if (!(Test-Path -LiteralPath (Join-Path $Directory 'default.xbe') -PathType Leaf)) {
        throw 'The selected game folder has moved. Run START_HERE.cmd and choose the disc again.'
    }
    $env:RECOMP_GAME_FILES = $Directory
    & (Join-Path $Root 'run_windows.cmd')
    if ($LASTEXITCODE -ne 0) { throw "The Windows game exited with code $LASTEXITCODE." }
}

if ($Help) {
    @'
Double-click START_HERE.cmd to choose a profile and PAL disc.
START_HERE.cmd windows "D:\Discs\Blinx.iso"
START_HERE.cmd android-original "D:\Discs\Blinx.iso"
Use a bare PAL game-partition XISO, an extracted disc folder or its default.xbe.
Options: -NoLaunch (build only), -HakuApk <matching local APK> (Android).
START_HERE.cmd -Play starts your last Windows build.
'@
    exit 0
}

$lock = $null
try {
    if ($Play) {
        if ($Profile -or $Disc -or $HakuApk -or $NoLaunch) { throw 'Use -Play on its own.' }
        if (!(Test-Path -LiteralPath $savedDisc -PathType Leaf)) { throw 'Build Windows Clone 1 with START_HERE.cmd first.' }
        Start-LocalGame ([IO.File]::ReadAllText($savedDisc))
        exit 0
    }
    if (![Environment]::Is64BitOperatingSystem -or ![Environment]::Is64BitProcess) {
        throw 'Use 64-bit Windows 10 or 11 and the 64-bit Windows shell.'
    }
    if (!$Profile) { $Profile = Select-StartProfile }
    if (!$Profile) { exit 0 }
    $Profile = $Profile.ToLowerInvariant()
    if (!$Disc) { $Disc = Select-StartDisc }
    if (!$Disc) { exit 0 }
    $discItem = Get-Item -LiteralPath $Disc
    if (!$discItem.PSIsContainer -and $discItem.Name -ieq 'default.xbe') {
        $discItem = Get-Item -LiteralPath $discItem.DirectoryName
    }
    if ($discItem.PSIsContainer) {
        if (!(Test-Path -LiteralPath (Join-Path $discItem.FullName 'default.xbe') -PathType Leaf) -or
            !(Test-Path -LiteralPath (Join-Path $discItem.FullName 'media') -PathType Container)) {
            throw 'Select the complete extracted PAL folder containing default.xbe and the media directory.'
        }
    } elseif ($discItem.Extension -notin @('.iso','.xiso')) {
        throw 'Select a PAL game-partition .iso/.xiso or the default.xbe inside a complete extracted disc.'
    } else {
        $stream = [IO.File]::OpenRead($discItem.FullName)
        try {
            $stream.Position = 32 * 2048
            $signature = New-Object byte[] 20
            if ($stream.Read($signature, 0, 20) -ne 20 -or
                [Text.Encoding]::ASCII.GetString($signature) -cne 'MICROSOFT*XBOX*MEDIA') {
                throw 'This file is not a bare Xbox game-partition image. Choose a PAL XISO or default.xbe in the extracted disc folder.'
            }
        } finally { $stream.Dispose() }
    }
    if ($HakuApk) {
        if ($Profile -ne 'android-original') { throw '-HakuApk is available only for Android Original.' }
        $HakuApk = (Get-Item -LiteralPath $HakuApk).FullName
    }
    $cache = Split-Path -Parent $savedDisc
    [void][IO.Directory]::CreateDirectory($cache)
    try {
        $lock = [IO.File]::Open((Join-Path $cache 'setup.lock'),'OpenOrCreate','ReadWrite','None')
    } catch [IO.IOException] { throw 'Another setup is using this project folder. Wait for it to finish.' }
    . (Join-Path $PSScriptRoot 'start_dependencies.ps1')
    Write-Host 'Each bar shows the current step. A ~ percentage is an estimate.'
    Write-Host "Preparing build tools for $Profile..."
    Initialize-StartTools -Profile $Profile -Root $Root
    Write-Host 'Checking the PAL disc...'
    $prepared = & python -B (Join-Path $PSScriptRoot 'start_disc.py') $Profile $discItem.FullName
    if ($LASTEXITCODE -ne 0 -or !$prepared) { throw 'Disc preparation failed. See the message above.' }
    if ($Profile -eq 'android-original') {
        . (Join-Path $PSScriptRoot 'start_android.ps1')
        Write-Host 'Preparing Android tools and runtime libraries...'
        Initialize-StartAndroid -Root $Root -HakuApk $HakuApk
    }
    Write-Host "Building $Profile..."
    if ($Profile -eq 'windows') {
        Write-Host 'Reading and translating the game code can take several minutes. Leave this window open.'
    }
    & python -B (Join-Path $PSScriptRoot 'build.py') $Profile --disc $prepared
    if ($LASTEXITCODE -ne 0) { throw "The build exited with code $LASTEXITCODE. See the message above." }
    if ($Profile -eq 'windows') {
        $product = Join-Path $Root 'dist\windows\blinx1_recomp.exe'
    } else {
        $product = Join-Path $Root 'dist\android-original\android-original.apk'
    }
    if (!(Test-Path -LiteralPath $product -PathType Leaf)) { throw "Expected build output is missing: $product" }
    if ($Profile -eq 'windows') {
        $temp = $savedDisc + '.tmp'
        [IO.File]::WriteAllText($temp, [string]$prepared)
        if ([IO.File]::Exists($savedDisc)) { [IO.File]::Replace($temp, $savedDisc, ($savedDisc + '.bak')) }
        else { [IO.File]::Move($temp, $savedDisc) }
    }
    $lock.Dispose()
    $lock = $null
    Write-Host "Ready: $product"
    if ($Profile -eq 'windows' -and !$NoLaunch) { Start-LocalGame $prepared }
    if ($Profile -eq 'android-original' -and $interactive -and !$NoLaunch) {
        Start-Process explorer.exe -ArgumentList ('/select,"' + $product + '"')
    }
} catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    if ($interactive -and !$Play) {
        Write-Host 'Press Enter to close.'
        [void][Console]::ReadLine()
    }
    exit 1
} finally {
    if ($null -ne $lock) { $lock.Dispose() }
}
