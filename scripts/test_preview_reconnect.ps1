param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [Parameter(Mandatory=$true)][string]$EvidenceDirectory,
    [string]$Adb = 'adb',
    [string]$Package = 'org.codeaurora.snapcam',
    [switch]$BasicCapture
)
$ErrorActionPreference = 'Stop'
if ($Package -notmatch '^[A-Za-z0-9_.]+$') { throw 'Invalid package name' }
$bridge = ((& $Adb -s $Serial shell pidof xflip-preview-bridge) -join ' ').Trim()
if ($bridge -notmatch '^\d+$') { throw 'Start the installed-version bridge first' }
$supervisor = ((& $Adb -s $Serial shell cat /data/local/tmp/xflip-preview-supervisor.pid) -join '').Trim()
if ($supervisor -notmatch '^\d+$') { throw 'Use the current supervised initialization script' }
New-Item -ItemType Directory -Path $EvidenceDirectory -Force | Out-Null
$results = @()
for ($attempt = 1; $attempt -le 3; $attempt++) {
    & $Adb -s $Serial shell am force-stop $Package
    $clock = [Diagnostics.Stopwatch]::StartNew()
    $launch = & $Adb -s $Serial shell am start -W --display 1 -n "$Package/com.sturgeon.xflipcamera.SystemCaptureActivity"
    if ($LASTEXITCODE -ne 0 -or !($launch -match '^Status: ok')) { throw 'Could not launch the cover activity' }
    $ready = $false
    do {
        $state = (& $Adb -s $Serial shell dumpsys activity service "$Package/com.sturgeon.xflipcamera.PreviewMirrorService") -join "`n"
        $windows = (& $Adb -s $Serial shell dumpsys window windows) -join "`n"
        $frames = [regex]::Match($state, 'mirrored_frames=(\d+)')
        $controlCount = @('XFlip cover settings', 'XFlip cover photo', 'XFlip cover video' | Where-Object { $windows.Contains($_) }).Count
        $expectedControls = if ($BasicCapture) { 0 } else { 3 }
        $ready = $state.Contains('phase=1,') -and $state.Contains('video=false') -and $frames.Success -and [int]$frames.Groups[1].Value -ge 10 -and $controlCount -eq $expectedControls
        if (!$ready) { Start-Sleep -Milliseconds 300 }
    } while (!$ready -and $clock.Elapsed.TotalSeconds -lt 10)
    $currentBridge = ((& $Adb -s $Serial shell pidof xflip-preview-bridge) -join ' ').Trim()
    $currentSupervisor = ((& $Adb -s $Serial shell cat /data/local/tmp/xflip-preview-supervisor.pid) -join '').Trim()
    if (!$ready -or $currentBridge -notmatch '^\d+$' -or $currentSupervisor -ne $supervisor) { throw "Reconnect failed on attempt $attempt" }
    $results += [pscustomobject]@{attempt=$attempt;app_pid=((& $Adb -s $Serial shell pidof $Package)-join ' ').Trim();bridge_pid=$currentBridge;same_bridge_pid=($currentBridge -eq $bridge);same_supervisor_pid=$true;recovery_seconds=[Math]::Round($clock.Elapsed.TotalSeconds,3);mirrored_frames=[int]$frames.Groups[1].Value;phase=1;native_control_windows=$controlCount}
    & $Adb -s $Serial shell screencap -d 4630946993847360387 -p /sdcard/Download/xflip-reconnect-check.png
    & $Adb -s $Serial pull /sdcard/Download/xflip-reconnect-check.png (Join-Path $EvidenceDirectory "reconnect-$attempt.png") | Out-Null
}
& $Adb -s $Serial shell input -d 1 keyevent 4
Start-Sleep -Milliseconds 500
$remaining = (& $Adb -s $Serial shell dumpsys activity service "$Package/com.sturgeon.xflipcamera.PreviewMirrorService") -join "`n"
if ($remaining.Contains('SERVICE ')) { throw 'Preview service remained after camera exit' }
$results | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $EvidenceDirectory 'reconnect-results.json') -Encoding UTF8
$results | Format-Table
