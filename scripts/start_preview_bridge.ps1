param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = 'adb',
    [string]$Package = 'org.codeaurora.snapcam',
    [int]$DisplayId = 1
)
$ErrorActionPreference = 'Stop'
if ($Package -notmatch '^[A-Za-z0-9_.]+$') { throw 'Invalid package name' }
$launchResult = & $Adb -s $Serial shell am start -W --display $DisplayId -n "$Package/com.sturgeon.xflipcamera.SystemCaptureActivity"
$launchResult
if ($LASTEXITCODE -ne 0 -or !($launchResult -match '^Status: ok')) { throw 'Could not open camera entry; wake the cover display and open the cover assistant first' }
$packagePath = (& $Adb -s $Serial shell pm path $Package | Select-Object -First 1).Trim()
if (!$packagePath.StartsWith('package:/data/app/')) { throw 'Installed APK path unavailable' }
$apkPath = $packagePath.Substring(8)
if ($apkPath.Contains("'")) { throw 'Unsupported APK path' }
$supervisor = ((& $Adb -s $Serial shell cat /data/local/tmp/xflip-preview-supervisor.pid 2>$null) -join '').Trim()
if ($supervisor -match '^\d+$') {
    $identity = ((& $Adb -s $Serial shell cat "/proc/$supervisor/cmdline" 2>$null) -join '')
    if ($identity.Contains('xflip-preview-supervisor')) { & $Adb -s $Serial shell kill $supervisor }
}
$existing = ((& $Adb -s $Serial shell pidof xflip-preview-bridge) -join ' ').Trim()
if ($existing -match '^\d+( \d+)*$') { & $Adb -s $Serial shell kill $existing }
# The GPU process can abort inside vendor HWUI when the client surface disappears abruptly.
# Keep restart ownership in a separate shell process, independent of both app and GPU worker.
if ($apkPath.Contains('"')) { throw 'Unsupported APK path' }
$loop = 'echo $$ > /data/local/tmp/xflip-preview-supervisor.pid; while true; do CLASSPATH="' + $apkPath + '" app_process /system/bin --nice-name=xflip-preview-bridge com.sturgeon.xflipcamera.PreviewWorkerStarter "' + $Package + '"; sleep 1; done'
$command = "nohup sh -c '$loop' xflip-preview-supervisor >/data/local/tmp/xflip-preview-bridge.log 2>&1 </dev/null &"
& $Adb -s $Serial shell $command
if ($LASTEXITCODE -ne 0) { throw 'Preview bridge command failed' }
Start-Sleep -Milliseconds 1000
& $Adb -s $Serial shell cat /data/local/tmp/xflip-preview-bridge.log
& $Adb -s $Serial shell pidof xflip-preview-bridge
