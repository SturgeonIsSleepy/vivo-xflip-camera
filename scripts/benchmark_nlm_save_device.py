"""Same-APK ABBA/BAAB save comparison, then two separately sampled memory runs."""
import argparse,json,shutil,statistics,subprocess,sys
from pathlib import Path
import numpy as np
from PIL import Image
from nlm_benchmark_contract import IDENTITY_SCOPE, require_same_base_apk, require_unprofiled
p=argparse.ArgumentParser();p.add_argument('--adb',default='adb');p.add_argument('--serial',required=True);p.add_argument('--order',choices=['ABBA','BAAB'],default='ABBA');p.add_argument('fixtures',type=Path);p.add_argument('output',type=Path);a=p.parse_args()
a.output.mkdir(parents=True,exist_ok=True);scripts=Path(__file__).parent
common=['--adb',a.adb,'--serial',a.serial];runs=[];apk_hash=None
labels=tuple('baseline' if letter=='A' else 'candidate' for letter in a.order)
for index,label in enumerate(labels,1):
 folder=a.output/f'{index}-{label}';folder.mkdir(exist_ok=True);shutil.copy2(a.fixtures/'nlm-full.jpg',folder/'nlm-full.jpg')
 schedule=[512,2] if label=='baseline' else [0,0]
 command=[sys.executable,str(scripts/'test_photo_adjustment_device.py'),*common,'--scenario','full','--nlm-tile',str(schedule[0]),'--nlm-threads',str(schedule[1]),str(folder)]
 result=subprocess.run(command,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=220)
 (folder/'instrumentation.log').write_text(result.stdout+result.stderr,encoding='utf-8');assert result.returncode==0,result.stdout+result.stderr
 report=json.loads((folder/'full-edit-report.json').read_text(encoding='utf-8'));report.update({'label':label,'order':index})
 assert report['opencv_threads']==(2 if label=='baseline' else 4)
 require_unprofiled(report);apk_hash=require_same_base_apk([report],expected=apk_hash)
 runs.append(report);print(json.dumps(report),flush=True)
profiles={}
for label in ('baseline','candidate'):
 schedule=[512,2] if label=='baseline' else [0,0];folder=a.output/f'memory-{label}'
 command=[sys.executable,str(scripts/'profile_photo_adjustment_device.py'),*common,'--runs','1','--label',label,'--nlm-tile',str(schedule[0]),'--nlm-threads',str(schedule[1]),str(a.fixtures),str(folder)]
 subprocess.run(command,check=True,timeout=240)
 profiles[label]=json.loads((folder/'profile.json').read_text(encoding='utf-8'))
 memory=profiles[label]
 if not isinstance(memory.get('runs'),list) or len(memory['runs'])!=1:raise ValueError('Missing memory run evidence')
 require_same_base_apk([dict(base_apk_sha256=memory.get('apk_sha256'),apk_identity_scope=memory.get('apk_identity_scope')),
                        *memory['runs']],expected=apk_hash)
reference=np.asarray(Image.open(a.output/f'1-{labels[0]}/full-edit-result.jpg'),dtype=np.int16)
comparisons=[]
for index,label in enumerate(labels,1):
 image=np.asarray(Image.open(a.output/f'{index}-{label}/full-edit-result.jpg'),dtype=np.int16)
 error=int(np.abs(reference-image).max());assert error==0
 comparisons.append({'case':f'{index}-{label}','max_channel_error':error})
for label in ('baseline','candidate'):
 image=np.asarray(Image.open(a.output/f'memory-{label}/run-1/full-edit-result.jpg'),dtype=np.int16)
 error=int(np.abs(reference-image).max());assert error==0
 comparisons.append({'case':'memory-'+label,'max_channel_error':error})
medians={label:statistics.median(r['save_ms'] for r in runs if r['label']==label) for label in ('baseline','candidate')}
report={'same_apk':True,'apk_identity_scope':IDENTITY_SCOPE,'apk_sha256':apk_hash,'order':a.order,'synthetic_only':True,'runs':runs,'median_save_ms':medians,
 'observed_time_reduction_percent':100*(1-medians['candidate']/medians['baseline']),'memory':profiles,'pixel_comparisons':comparisons,
 'frequency_and_temperature_not_fixed':True,'memory_runs_excluded_from_timing_comparison':True}
(a.output/'full-save-comparison.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print(json.dumps({'median_save_ms':medians,'time_reduction_percent':report['observed_time_reduction_percent']}),flush=True)
