"""Production Android tone/detail checks with the same JPEG97 single-frame baseline."""
import argparse,base64,hashlib,json,re,subprocess
from pathlib import Path
import numpy as np
from PIL import Image
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--adb',default='adb');p.add_argument('--package',default='org.codeaurora.snapcam');p.add_argument('--scenario',choices=['levels','narrow','detail','hdr','flat','color','rotated','cancel'],required=True);p.add_argument('directory',type=Path);a=p.parse_args()
pkg=a.package;assert re.fullmatch(r'[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+',pkg);prefix=[a.adb,'-s',a.serial]
def adb(*cmd,timeout=40):return subprocess.run(prefix+list(cmd),check=True,capture_output=True,timeout=timeout).stdout.decode(errors='replace')
remote='files/tone-fixtures';adb('shell','run-as',pkg,'mkdir','-p',remote)
data=(a.directory/f'{a.scenario}.jpg').read_bytes();path=f'{remote}/{a.scenario}.jpg'
r=subprocess.run(prefix+['shell','-T','run-as',pkg,'sh','-c',"'base64 -d > "+path+"'"],input=base64.b64encode(data)+b'\n',capture_output=True,timeout=25)
assert r.returncode==0 and adb('shell','run-as',pkg,'sha256sum',path).split()[0]==hashlib.sha256(data).hexdigest()
cmd=(f'am instrument -w -e scenario {a.scenario} {pkg}/com.sturgeon.xflipcamera.PhotoEnhancementDeviceTest & sleep 1; '
 f'am start -W --display 1 -n {pkg}/com.sturgeon.xflipcamera.SystemCaptureActivity --ez manual_entry true; wait')
result=adb('shell',cmd,timeout=90);print(result)
assert 'INSTRUMENTATION_CODE: -1' in result and 'original_preserved=true' in result
report={'scenario':a.scenario,'original_preserved':True,'instrumentation_passed':True}
match=re.search(r'processing_ms=(\d+)',result);report['processing_ms']=int(match.group(1))
if a.scenario!='cancel':
 for kind in ('result','baseline'):
  target=a.directory/f'{a.scenario}-{kind}.jpg';target.write_bytes(base64.b64decode(adb('shell','run-as',pkg,'base64',f'{remote}/{target.name}')))
 before=Image.open(a.directory/f'{a.scenario}-baseline.jpg');after=Image.open(a.directory/f'{a.scenario}-result.jpg')
 b=np.asarray(before,dtype=float);c=np.asarray(after,dtype=float);bl=b.mean(2);cl=c.mean(2)
 assert before.size==after.size==(960,720) and after.getexif().get(274)==1
 report.update({'dimensions':list(after.size),'sha256':hashlib.sha256((a.directory/f'{a.scenario}-result.jpg').read_bytes()).hexdigest(),'description':after.getexif().get(270),'min_before':float(bl.min()),'max_before':float(bl.max()),'min_after':float(cl.min()),'max_after':float(cl.max())})
 if a.scenario=='levels':assert cl.min()<5 and cl.max()>250 and bl.min()>25 and bl.max()<230
 if a.scenario=='narrow':assert cl.min()<5 and cl.max()>250 and 'expanded=true' in report['description']
 if a.scenario in ('detail','rotated'):
  mask=np.abs(np.diff(bl,axis=1))>5;ratio=float(np.abs(np.diff(cl,axis=1))[mask].mean()/np.abs(np.diff(bl,axis=1))[mask].mean());report['edge_contrast_ratio']=ratio;assert ratio>1.02
 if a.scenario=='hdr':
  dark=np.s_[60:650,50:420];bright=np.s_[60:650,540:900]
  report.update({'shadow_mean_before':float(bl[dark].mean()),'shadow_mean_after':float(cl[dark].mean()),'highlight_mean_before':float(bl[bright].mean()),'highlight_mean_after':float(cl[bright].mean()),'highlight_texture_ratio':float(cl[bright].std()/bl[bright].std())})
  assert cl[dark].mean()>bl[dark].mean()*1.1 and cl[bright].mean()<bl[bright].mean()*.98 and cl[bright].std()>bl[bright].std()*.75
  assert cl[:,0].max()<3 and cl[:,-1].min()>252
 if a.scenario=='flat':
  ratio=float(np.mean((c-120)**2)/np.mean((b-120)**2));report['flat_noise_mse_ratio']=ratio;assert ratio<1.4
 if a.scenario=='color':
  for i in range(3):
   area=np.s_[60:280,i*320+60:i*320+260,:];assert np.argmax(c[area].mean((0,1)))==np.argmax(b[area].mean((0,1)))
  assert np.max(np.abs(c[400:650,50:250,0]-c[400:650,50:250,1]))<3
else:assert 'cancelled=true' in result
(a.directory/f'{a.scenario}-report.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
