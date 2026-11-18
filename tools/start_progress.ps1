# Run the command in its original PowerShell session. A second, local runspace
# can refresh stderr while the command blocks without taking its stdin or stdout.
function Invoke-StartProgress {
    param(
        [Parameter(Mandatory)][string]$Label,
        [Parameter(Mandatory)][double]$EstimateSeconds,
        [Parameter(Mandatory)][scriptblock]$Action,
        [hashtable]$Transfer,
        [switch]$LogLines
    )
    $stop = New-Object Threading.ManualResetEvent($false)
    $monitor = [PowerShell]::Create()
    $inline = !$LogLines -and ![Console]::IsErrorRedirected
    $clock = [Diagnostics.Stopwatch]::StartNew()
    $watch = {
        param($label, $estimate, $transfer, $inline, $clock, $stop)
        $previous = -1
        $lastDraw = 0
        $width = 0
        while (!$stop.WaitOne(500)) {
            $elapsed = [int][Math]::Floor($clock.Elapsed.TotalSeconds)
            $bytes = 0L
            $total = 0L
            if ($null -ne $transfer) {
                $total = [long]$transfer.Total
                try { $bytes = (New-Object IO.FileInfo($transfer.Path)).Length } catch { }
            }
            $prefix = '~'
            $units = ''
            if ($total -gt 0) {
                $percent = [int][Math]::Min(99, [Math]::Floor(100.0 * $bytes / $total))
                $prefix = ''
                $units = ' ({0:N1}/{1:N1} MiB)' -f ($bytes / 1MB), ($total / 1MB)
            } else {
                # Initial budget is approximate; a slow installation can outlive it.
                $percent = [int][Math]::Min(95, [Math]::Floor(100 * (1 - [Math]::Exp(-1.6 * $elapsed / $estimate))))
                if ($null -ne $transfer) { $units = ' ({0:N1} MiB)' -f ($bytes / 1MB) }
            }
            if (!$inline -and $elapsed - $lastDraw -lt 4) { continue }
            if ($percent -eq $previous -and $elapsed - $lastDraw -lt 10) { continue }
            $blocks = [int][Math]::Floor($percent / 5)
            $line = '[{0}{1}] {2}{3}% {4}{5} ({6}s)' -f ('#' * $blocks), ('.' * (20 - $blocks)), $prefix, $percent, $label, $units, $elapsed
            if ($inline) {
                $width = [Math]::Max($width, $line.Length)
                [Console]::Error.Write(("`r" + $line.PadRight($width)))
            } else {
                [Console]::Error.WriteLine($line)
            }
            $lastDraw = $elapsed
            $previous = $percent
        }
    }
    $handle = $null
    $success = $false
    try {
        [Console]::Error.WriteLine("[....................] 0% $Label")
        [void]$monitor.AddScript($watch.ToString()).AddArgument($Label).AddArgument($EstimateSeconds).AddArgument($Transfer).AddArgument($inline).AddArgument($clock).AddArgument($stop)
        $handle = $monitor.BeginInvoke()
        & $Action
        $success = $true
    } finally {
        [void]$stop.Set()
        if ($null -ne $handle) { [void]$monitor.EndInvoke($handle) }
        $monitor.Dispose()
        $stop.Dispose()
        if ($inline -and $clock.ElapsedMilliseconds -ge 500) { [Console]::Error.WriteLine() }
        if ($success) {
            [Console]::Error.WriteLine("[####################] 100% $Label")
        } else {
            [Console]::Error.WriteLine("Stopped: $Label")
        }
    }
}
