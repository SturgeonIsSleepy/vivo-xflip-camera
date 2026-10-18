"""Fixed NLM quality, same APK, reverse-order device scheduling comparison on synthetic input."""
import argparse,base64,hashlib,json,re,statistics,subprocess
from pathlib import Path
from PIL import Image
p=argparse.ArgumentParser();p.add_argument('--adb',default='adb');p.add_argument('--serial',required=True)
p.add_argument('--package',default='org.codeaurora.snapcam.lab');p.add_argument('fixtures',type=Path);p.add_argument('output',type=Path);a=p.parse_args()
assert re.fullmatch(r'[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+',a.package)
base=[a.adb,'-s',a.serial];a.output.mkdir(parents=True,exist_ok=True)
with Image.open(a.fixtures/'nlm-full.jpg') as image:assert image.size==(3072,4096)
def adb(*args,timeout=30):return subprocess.run(base+list(args),capture_output=True,check=True,timeout=timeout).stdout.decode(errors='replace')
folder='files/tone-fixtures';adb('shell','run-as',a.package,'mkdir','-p',folder)
for name in ('nlm.jpg','nlm-full.jpg'):
 data=(a.fixtures/name).read_bytes();path=f'{folder}/{name}'
 r=subprocess.run(base+['shell','-T','run-as',a.package,'sh','-c',"'base64 -d > "+path+"'"],input=base64.b64encode(data)+b'\n',capture_output=True,timeout=60)
 assert r.returncode==0 and adb('shell','run-as',a.package,'sha256sum',path).split()[0]==hashlib.sha256(data).hexdigest()
command=(f'am instrument -w {a.package}/com.sturgeon.xflipcamera.NlmSchedulingDeviceTest & sleep 1; '
 f'am start -W --display 1 -n {a.package}/com.sturgeon.xflipcamera.SystemCaptureActivity --ez manual_entry true; wait')
text=adb('shell',command,timeout=360);(a.output/'instrumentation.log').write_text(text,encoding='utf-8')
assert 'INSTRUMENTATION_CODE: -1' in text and 'all_pixels_equal=true' in text,text
adb('shell','am','start','-W','--display','1','-n',f'{a.package}/com.sturgeon.xflipcamera.SystemCaptureActivity','--ez','manual_entry','true')
cases=json.loads(re.search(r'INSTRUMENTATION_RESULT: cases=(\[[^\r\n]+\])',text).group(1))
summary=[]
for tile,threads in ((512,2),(512,4),(768,4),(1024,2),(1024,4)):
 rows=[r for r in cases if r['tile']==tile and r['threads']==threads]
 summary.append({'tile':tile,'threads':threads,'runs_ms':[r['denoise_ms'] for r in rows],'median_ms':statistics.median(r['denoise_ms'] for r in rows)})
report={'cases':cases,'summary':summary,'all_raw_pixels_equal':True,'synthetic_only':True,'dimensions':[3072,4096],
 'input_jpeg_sha256':hashlib.sha256((a.fixtures/'nlm-full.jpg').read_bytes()).hexdigest(),
 'warmup':'one 960x720 baseline NLM','order':'forward then reverse','temperature_or_frequency_not_fixed':True,'timing':'denoise only, excludes decode/hash/encode/publish'}
(a.output/'scheduling.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(summary,indent=2),flush=True)
