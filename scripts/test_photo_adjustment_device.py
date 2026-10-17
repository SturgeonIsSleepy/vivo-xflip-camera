"""App-UID editor verification with identical Android JPEG97 baselines and independent metrics."""
import argparse,base64,hashlib,json,re,subprocess
from pathlib import Path
import numpy as np
from PIL import Image
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--adb',default='adb');p.add_argument('--package',default='org.codeaurora.snapcam.lab');p.add_argument('--scenario',choices=['manual','color','denoise','rotated','identity','cancel','full'],required=True);p.add_argument('directory',type=Path);a=p.parse_args()
pkg=a.package;assert re.fullmatch(r'[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+',pkg);prefix=[a.adb,'-s',a.serial]
def adb(*cmd,timeout=180):return subprocess.run(prefix+list(cmd),check=True,capture_output=True,timeout=timeout).stdout.decode(errors='replace')
fixture={'manual':'levels','identity':'levels','denoise':'nlm','cancel':'nlm','full':'nlm-full'}.get(a.scenario,a.scenario)
remote='files/tone-fixtures';adb('shell','run-as',pkg,'mkdir','-p',remote)
data=(a.directory/f'{fixture}.jpg').read_bytes();path=f'{remote}/{fixture}.jpg'
r=subprocess.run(prefix+['shell','-T','run-as',pkg,'sh','-c',"'base64 -d > "+path+"'"],input=base64.b64encode(data)+b'\n',capture_output=True,timeout=60)
assert r.returncode==0 and adb('shell','run-as',pkg,'sha256sum',path).split()[0]==hashlib.sha256(data).hexdigest()
cmd=(f'am instrument -w -e scenario {a.scenario} {pkg}/com.sturgeon.xflipcamera.PhotoAdjustmentDeviceTest & sleep 1; '
 f'am start -W --display 1 -n {pkg}/com.sturgeon.xflipcamera.SystemCaptureActivity --ez manual_entry true; wait')
result=adb('shell',cmd);print(result)
assert 'INSTRUMENTATION_CODE: -1' in result and 'original_preserved=true' in result
report={'scenario':a.scenario,'original_preserved':True,'instrumentation_passed':True,'processing_ms':int(re.search(r'processing_ms=(\d+)',result).group(1))}
match=re.search(r'save_ms=(\d+)',result)
if match:report['save_ms']=int(match.group(1))
if a.scenario!='cancel':
 for kind in ('result','baseline'):
  target=a.directory/f'{a.scenario}-edit-{kind}.jpg';target.write_bytes(base64.b64decode(adb('shell','run-as',pkg,'base64',f'{remote}/{target.name}')))
 before=Image.open(a.directory/f'{a.scenario}-edit-baseline.jpg');after=Image.open(a.directory/f'{a.scenario}-edit-result.jpg')
 assert before.size==after.size and after.getexif().get(274)==1
 b=np.asarray(before,dtype=float);c=np.asarray(after,dtype=float)
 report.update({'dimensions':list(after.size),'description':after.getexif().get(270)})
 if a.scenario=='manual':assert c.min()<3 and c.max()>252 and b.min()>20 and b.max()<240
 if a.scenario=='identity':assert np.array_equal(b,c)
 if a.scenario=='rotated':assert after.size==(960,720)
 if a.scenario=='color':
  for i in range(6):
   roi=np.s_[i//3*360+60:i//3*360+280,i%3*320+60:i%3*320+260,:]
   expected=np.clip(np.round((b[roi].mean((0,1))-32)*255/192),0,255)
   assert np.max(np.abs(c[roi].mean((0,1))-expected))<4
 if a.scenario=='denoise':
  truth=np.asarray(Image.open(a.directory/'nlm-truth.png'),dtype=float)
  report['noise_mse_ratio']=float(np.mean((c-truth)**2)/np.mean((b-truth)**2))
  flat=np.s_[560:680,40:450,:]
  report['flat_noise_std_ratio']=float(c[flat].std()/b[flat].std())
  t=truth.mean(2);mask=np.abs(np.diff(t,axis=1))>15
  report['text_edge_contrast_ratio']=float(np.abs(np.diff(c.mean(2),axis=1))[mask].mean()/np.abs(np.diff(b.mean(2),axis=1))[mask].mean())
  report['tile_max_channel_error']=int(re.search(r'tile_max_channel_error=(\d+)',result).group(1))
  assert report['noise_mse_ratio']<.85 and report['text_edge_contrast_ratio']>.95 and report['tile_max_channel_error']<=1
else:assert 'cancelled=true' in result
(a.directory/f'{a.scenario}-edit-report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps(report,indent=2))
