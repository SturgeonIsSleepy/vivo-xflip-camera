"""Run the debug-only app-UID codec harness. Keep the phone unlocked and the cover assistant active.
Usage: python test_stabilization_device.py --serial SERIAL --adb PATH_TO_ADB input.mp4 output.mp4
"""
import argparse
import base64
import hashlib
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--serial', required=True)
parser.add_argument('--adb', default='adb')
parser.add_argument('--package', choices=['org.codeaurora.snapcam', 'com.sturgeon.xflipcamera'], default='org.codeaurora.snapcam')
parser.add_argument('input', type=Path)
parser.add_argument('output', type=Path)
args = parser.parse_args()
prefix = [args.adb, '-s', args.serial]


def adb(*command, timeout=30):
    return subprocess.run(prefix + list(command), check=True, capture_output=True, timeout=timeout).stdout


adb('shell', 'run-as', args.package, 'mkdir', '-p', 'files')
adb('shell', 'run-as', args.package, 'sh', '-c', "': > files/video-stabilization-test.mp4'")
data = args.input.read_bytes()
for offset in range(0, len(data), 8192):
    payload = base64.b64encode(data[offset:offset + 8192]).decode('ascii')
    adb('shell', 'run-as', args.package, 'sh', '-c',
            "'echo " + payload + " | base64 -d >> files/video-stabilization-test.mp4'")
expected = hashlib.sha256(data).hexdigest()
actual = adb('shell', 'run-as', args.package, 'sha256sum', 'files/video-stabilization-test.mp4').decode().split()[0]
if actual != expected:
    raise RuntimeError('Device input hash mismatch')
# Start the activity from Shell immediately after instrumentation starts; a background app may be frozen by this ROM.
command = (f'am instrument -w {args.package}/com.sturgeon.xflipcamera.StabilizationDeviceTest & sleep 1; '
           f'am start -W --display 1 -n {args.package}/com.sturgeon.xflipcamera.SystemCaptureActivity; wait')
result = adb('shell', command, timeout=90).decode(errors='replace')
print(result)
if 'INSTRUMENTATION_CODE: -1' not in result:
    raise RuntimeError('Device stabilization failed')
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_bytes(base64.b64decode(adb('shell', 'run-as', args.package, 'base64', 'files/video-stabilization-result.mp4')))
print('output_bytes', args.output.stat().st_size)
