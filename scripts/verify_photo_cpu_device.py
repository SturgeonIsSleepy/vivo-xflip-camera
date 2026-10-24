"""Same-APK synthetic profile/plain acceptance; diagnostic timings are not speed benchmarks."""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import numpy as np
from PIL import Image
from nlm_benchmark_contract import require_same_base_apk

p=argparse.ArgumentParser()
p.add_argument('--adb',default='adb');p.add_argument('--serial',required=True)
p.add_argument('fixtures',type=Path);p.add_argument('output',type=Path);a=p.parse_args()
a.output.mkdir(parents=True,exist_ok=True)
base=[a.adb,'-s',a.serial];identity=None;runs=[]

def thermal():
    result=subprocess.run(base+['shell','dumpsys','thermalservice'],check=True,capture_output=True,text=True)
    match=re.search(r'Thermal Status:\s*(\d+)',result.stdout)
    return int(match.group(1)) if match else None

def run(label,scenario,profile):
    global identity
    folder=a.output/label;folder.mkdir(exist_ok=True)
    for name in ('nlm-full.jpg','nlm.jpg','nlm-truth.png'):
        shutil.copy2(a.fixtures/name,folder/name)
    before=thermal()
    command=[sys.executable,str(Path(__file__).with_name('test_photo_adjustment_device.py')),
             '--adb',a.adb,'--serial',a.serial,'--scenario',scenario]
    if profile:command.append('--cpu-profile')
    result=subprocess.run(command+[str(folder)],capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=220)
    (folder/'instrumentation.log').write_text(result.stdout+result.stderr,encoding='utf-8')
    if result.returncode:raise RuntimeError(result.stdout+result.stderr)
    report=json.loads((folder/f'{scenario}-edit-report.json').read_text())
    identity=require_same_base_apk([report],expected=identity)
    report.update(label=label,thermal_before=before,thermal_after=thermal())
    print(json.dumps({'label':label,'save_ms':report.get('save_ms'),'thermal_before':before,'thermal_after':report['thermal_after']}),flush=True)
    return report

warmup=run('warmup','denoise',False)
for label,profile in (('1-profile',True),('2-plain',False),('3-plain',False),('4-profile',True)):
    runs.append(run(label,'full',profile))
cancel=run('cancel-profile','cancel',True)
with Image.open(a.output/'1-profile/full-edit-result.jpg') as image:reference=np.asarray(image,dtype=np.int16)
comparisons=[]
for report in runs:
    with Image.open(a.output/report['label']/'full-edit-result.jpg') as image:
        error=int(np.abs(reference-np.asarray(image,dtype=np.int16)).max())
    if error!=0:raise ValueError('Profile/plain pixels differ')
    comparisons.append({'label':report['label'],'max_channel_error':error})
summary={'synthetic_only':True,'base_apk_sha256':identity,'order':'profile/plain/plain/profile',
         'warmup':warmup,'runs':runs,'cancel':cancel,'pixel_comparisons':comparisons,
         'temperature_and_frequency_not_fixed':True,'diagnostic_overhead_included':True,
         'not_a_speedup_benchmark':True}
(a.output/'cpu-device-verification.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
print('Profile/plain pixels identical; cancellation and identity checks passed.',flush=True)
