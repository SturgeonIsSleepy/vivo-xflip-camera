param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [string]$Adb = 'adb',
    [string]$Package = 'org.codeaurora.snapcam',
    [int]$DisplayId = 1
)
$ErrorActionPreference = 'Stop'
if ($Package -notmatch '^[A-Za-z0-9_.]+$') { throw 'Invalid package name' }
& $Adb -s $Serial shell am start -W --display $DisplayId -n "$Package/com.sturgeon.xflipcamera.SystemCaptureActivity"
if ($LASTEXITCODE -ne 0) { throw 'Could not open camera entry' }
$packagePath = (& $Adb -s $Serial shell pm path $Package | Select-Object -First 1).Trim()
if (!$packagePath.StartsWith('package:/data/app/')) { throw 'Installed APK path unavailable' }
$apkPath = $packagePath.Substring(8)
if ($apkPath.Contains("'")) { throw 'Unsupported APK path' }
$existing = (& $Adb -s $Serial shell pidof xflip-preview-bridge).Trim()
if ($existing -match '^\d+( \d+)*$') { & $Adb -s $Serial shell kill $existing }
$command = "CLASSPATH='$apkPath' nohup app_process /system/bin --nice-name=xflip-preview-bridge com.sturgeon.xflipcamera.PreviewWorkerStarter '$Package' >/data/local/tmp/xflip-preview-bridge.log 2>&1 </dev/null &"
& $Adb -s $Serial shell $command
if ($LASTEXITCODE -ne 0) { throw 'Preview bridge command failed' }
Start-Sleep -Milliseconds 1000
& $Adb -s $Serial shell cat /data/local/tmp/xflip-preview-bridge.log
& $Adb -s $Serial shell pidof xflip-preview-bridge
