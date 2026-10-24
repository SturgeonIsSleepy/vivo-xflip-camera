"""Closed-cover production editor lifecycle acceptance using only the fixed synthetic fixture."""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import subprocess

p=argparse.ArgumentParser();p.add_argument('--adb',default='adb');p.add_argument('--serial',required=True)
p.add_argument('fixture',type=Path);p.add_argument('output',type=Path);a=p.parse_args()
a.output.mkdir(parents=True,exist_ok=True);pkg='org.codeaurora.snapcam.lab';prefix=[a.adb,'-s',a.serial]

def adb(*args):
    return subprocess.run(prefix+list(args),check=True,capture_output=True,timeout=100).stdout.decode(errors='replace')

def apk():
    paths=adb('shell','pm','path',pkg).splitlines()
    if len(paths)!=1 or not paths[0].startswith('package:/data/app/'):
        raise ValueError('Expected one standalone installed APK')
    digest=adb('shell','sha256sum',paths[0][8:]).split()[0]
    if not re.fullmatch('[0-9a-f]{64}',digest):raise ValueError('Invalid APK digest')
    return digest

identity=apk();data=a.fixture.read_bytes();folder='files/tone-fixtures';target=folder+'/nlm-full.jpg'
adb('shell','run-as',pkg,'mkdir','-p',folder)
r=subprocess.run(prefix+['shell','-T','run-as',pkg,'sh','-c',"'base64 -d > "+target+"'"],
                 input=base64.b64encode(data)+b'\n',capture_output=True,timeout=60)
if r.returncode or adb('shell','run-as',pkg,'sha256sum',target).split()[0]!=hashlib.sha256(data).hexdigest():
    raise ValueError('Synthetic fixture transfer failed')
command=(f'am instrument -w {pkg}/com.sturgeon.xflipcamera.EditorLifecycleDeviceTest & sleep 1; '
         f'am start -W --display 1 -n {pkg}/com.sturgeon.xflipcamera.SystemCaptureActivity --ez manual_entry true; wait')
try:
    result=adb('shell',command)
    (a.output/'instrumentation.log').write_text(result,encoding='utf-8')
finally:
    adb('shell','am','start','-W','--display','1','-n',pkg+'/com.sturgeon.xflipcamera.SystemCaptureActivity','--ez','manual_entry','true')
if 'INSTRUMENTATION_CODE: -1' not in result or apk()!=identity:raise ValueError(result)
keys=('editor_recreate_and_settings','editor_reopens_after_cancel','in_progress_back_cancel','no_new_media',
      'output_only_recreate','rapid_levels_and_compare','synthetic_only','synthetic_original_preserved')
report={'base_apk_sha256':identity,'apk_identity_scope':'standalone_base_apk','display_id':1}
for key in keys:
    if f'INSTRUMENTATION_RESULT: {key}=true' not in result:raise ValueError('Missing '+key)
    report[key]=True
if 'INSTRUMENTATION_RESULT: display_id=1' not in result:raise ValueError('Wrong display')
(a.output/'editor-lifecycle.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps(report,indent=2))
