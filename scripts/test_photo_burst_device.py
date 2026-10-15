"""Run the debug-only production fusion path and independently measure known clean fixtures."""
import argparse,base64,hashlib,json,re,subprocess
from pathlib import Path
import numpy as np
from PIL import Image,ImageOps

parser=argparse.ArgumentParser()
parser.add_argument('--serial',required=True)
parser.add_argument('--adb',default='adb')
parser.add_argument('--package',choices=['org.codeaurora.snapcam','com.sturgeon.xflipcamera'],default='org.codeaurora.snapcam')
parser.add_argument('--scenario',choices=['good','moving','rotated','unrelated','duplicate','cancel'],default='good')
parser.add_argument('directory',type=Path)
args=parser.parse_args()
prefix=[args.adb,'-s',args.serial]
def adb(*command,timeout=40):
 return subprocess.run(prefix+list(command),check=True,capture_output=True,timeout=timeout).stdout.decode(errors='replace')
destination='files/burst-fixtures'
adb('shell','run-as',args.package,'mkdir','-p',destination)
for i in range(3):
 path=f'{destination}/{args.scenario}-{i}.jpg'
 data=(args.directory/f'{args.scenario}-{i}.jpg').read_bytes()
 expected=hashlib.sha256(data).hexdigest()
 try:
  if adb('shell','run-as',args.package,'sha256sum',path).split()[0]==expected:continue
 except subprocess.CalledProcessError:pass
 bulk=subprocess.run(prefix+['shell','-T','run-as',args.package,'sh','-c',"'base64 -d > "+path+"'"],
  input=base64.b64encode(data)+b'\n',capture_output=True,timeout=25)
 actual=adb('shell','run-as',args.package,'sha256sum',path).split()[0]
 if bulk.returncode!=0 or actual!=expected:
  adb('shell','run-as',args.package,'sh','-c',"': > "+path+"'")
  for offset in range(0,len(data),18000):
   payload=base64.b64encode(data[offset:offset+18000]).decode('ascii')
   adb('shell','run-as',args.package,'sh','-c',"'echo "+payload+" | base64 -d >> "+path+"'")
 assert adb('shell','run-as',args.package,'sha256sum',path).split()[0]==expected
command=(f'am instrument -w -e scenario {args.scenario} {args.package}/com.sturgeon.xflipcamera.PhotoBurstDeviceTest & sleep 1; '
 f'am start -W --display 1 -n {args.package}/com.sturgeon.xflipcamera.SystemCaptureActivity --ez manual_entry true; wait')
result=adb('shell',command,timeout=120)
print(result)
if 'INSTRUMENTATION_CODE: -1' not in result or 'originals_preserved=true' not in result:raise RuntimeError('Device fusion failed')
report={'scenario':args.scenario,'originals_preserved':True,'instrumentation_passed':True}
for key in ('accepted_frames','reference_index','processing_ms'):
 match=re.search(r'INSTRUMENTATION_RESULT: '+key+r'=(\d+)',result)
 if match:report[key]=int(match.group(1))
if args.scenario not in ('unrelated','duplicate','cancel'):
 output=args.directory/f'{args.scenario}-result.jpg'
 output.write_bytes(base64.b64decode(adb('shell','run-as',args.package,'base64',destination+'/'+output.name)))
 baseline_path=args.directory/f'{args.scenario}-baseline.jpg'
 baseline_path.write_bytes(base64.b64decode(adb('shell','run-as',args.package,'base64',destination+'/'+baseline_path.name)))
 index=report['reference_index']
 clean=np.asarray(Image.open(args.directory/f'{args.scenario}-truth-{index}.png').convert('RGB'),dtype=float)
 original=np.asarray(ImageOps.exif_transpose(Image.open(args.directory/f'{args.scenario}-{index}.jpg')).convert('RGB'),dtype=float)
 baseline=np.asarray(Image.open(baseline_path).convert('RGB'),dtype=float)
 merged_image=Image.open(output);merged=np.asarray(merged_image.convert('RGB'),dtype=float)
 assert merged.shape==clean.shape and merged_image.getexif().get(274)==1
 # Fixed flat region lies clear of all geometric borders and moving objects.
 region=np.s_[590:660,70:280,:]
 source_error=float(np.mean((original[region]-clean[region])**2))
 before=float(np.mean((baseline[region]-clean[region])**2));after=float(np.mean((merged[region]-clean[region])**2))
 true_luma=clean.mean(axis=2);orig_luma=baseline.mean(axis=2);out_luma=merged.mean(axis=2)
 edge=np.abs(np.diff(true_luma,axis=1))>35
 edge[:32]=False;edge[-32:]=False;edge[:,:32]=False;edge[:,-32:]=False
 contrast=float(np.abs(np.diff(out_luma,axis=1))[edge].mean()/np.abs(np.diff(orig_luma,axis=1))[edge].mean())
 report.update({'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),'dimensions':list(merged_image.size),'source_noise_mse':source_error,'flat_noise_mse_before':before,'flat_noise_mse_after':after,'noise_mse_ratio':after/before,'edge_contrast_ratio':contrast,'orientation':1})
 if args.scenario=='moving':
  area=np.s_[310:380,270:610,:]
  ghost_before=float(np.mean((baseline[area]-clean[area])**2));ghost_after=float(np.mean((merged[area]-clean[area])**2))
  report['moving_region_mse_ratio']=ghost_after/ghost_before
  assert ghost_after < ghost_before*1.3+8, 'Moving object ghost'
 assert after < before*0.85, 'Insufficient noise improvement' 
 assert contrast >0.85, 'Lost edge contrast'
else:
 assert 'INSTRUMENTATION_RESULT: rejection=' in result
 report['rejected_as_expected']=True
(args.directory/f'{args.scenario}-report.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
