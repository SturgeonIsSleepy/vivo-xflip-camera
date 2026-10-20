"""Repeat the synthetic 13MP production check; profile the final run separately via meminfo."""
import argparse,json,re,shutil,statistics,subprocess,sys,threading,time
from pathlib import Path
from nlm_benchmark_contract import IDENTITY_SCOPE, require_same_base_apk

p=argparse.ArgumentParser();p.add_argument('--adb',default='adb');p.add_argument('--serial',required=True)
p.add_argument('--package',default='org.codeaurora.snapcam.lab');p.add_argument('--label',required=True)
p.add_argument('--runs',type=int,default=3);p.add_argument('--nlm-tile',type=int,default=0);p.add_argument('--nlm-threads',type=int,default=0);p.add_argument('fixtures',type=Path);p.add_argument('output',type=Path);a=p.parse_args()
assert a.runs>=1 and re.fullmatch(r'[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+',a.package)
base=[a.adb,'-s',a.serial];a.output.mkdir(parents=True,exist_ok=True)
def adb(*args):return subprocess.run(base+list(args),capture_output=True,check=True,timeout=12).stdout.decode(errors='replace')
apk_path=adb('shell','pm','path',a.package).strip().splitlines()[0].removeprefix('package:')
apk_hash=adb('shell','sha256sum',apk_path).split()[0]
results=[]
for index in range(1,a.runs+1):
 folder=a.output/f'run-{index}';folder.mkdir(exist_ok=True)
 shutil.copy2(a.fixtures/'nlm-full.jpg',folder/'nlm-full.jpg')
 stop=threading.Event();samples=[]
 def sample():
  started=time.monotonic()
  while not stop.is_set():
   try:
    text=adb('shell','dumpsys','meminfo',a.package)
    total=re.search(r'TOTAL PSS:\s*(\d+)\s+TOTAL RSS:\s*(\d+)',text)
    if total:
     row={'seconds':round(time.monotonic()-started,3),'total_pss_kib':int(total[1]),'total_rss_kib':int(total[2])}
     for name,label in (('Native Heap','native'),('Dalvik Heap','java')):
      match=re.search(r'^\s*'+name+r'\s+((?:\d+\s+){7}\d+)\s*$',text,re.M)
      if match:
       values=list(map(int,match[1].split()));row[label+'_heap_alloc_kib']=values[6];row[label+'_heap_pss_kib']=values[0]
     samples.append(row)
   except (subprocess.SubprocessError,OSError):pass
   stop.wait(.5)
 profiled=index==a.runs;thread=threading.Thread(target=sample,daemon=True)
 if profiled:thread.start()
 try:
  command=[sys.executable,str(Path(__file__).with_name('test_photo_adjustment_device.py')),'--adb',a.adb,'--serial',a.serial,'--package',a.package,'--scenario','full','--nlm-tile',str(a.nlm_tile),'--nlm-threads',str(a.nlm_threads),str(folder)]
  run=subprocess.run(command,capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=220)
  (folder/'device-test.log').write_text(run.stdout+run.stderr,encoding='utf-8');assert run.returncode==0,run.stdout+run.stderr
 finally:
  stop.set()
  if profiled:thread.join(timeout=15)
 report=json.loads((folder/'full-edit-report.json').read_text(encoding='utf-8'));report['sampled']=profiled
 require_same_base_apk([report],expected=apk_hash)
 if profiled:
  assert samples and not thread.is_alive(),'Memory sampler failed'
  (folder/'memory-samples.json').write_text(json.dumps(samples,indent=2),encoding='utf-8')
  report['memory_sample_count']=len(samples)
  report['observed_peaks_kib']={key:max(row[key] for row in samples if key in row) for key in samples[-1] if key!='seconds'}
 results.append(report);print(json.dumps({'label':a.label,'run':index,**report}),flush=True)
unsampled=[r['save_ms'] for r in results if not r['sampled']]
summary={'label':a.label,'apk_sha256':apk_hash,'runs':results,'unprofiled_median_save_ms':statistics.median(unsampled) if unsampled else None,
 'memory_method':'dumpsys meminfo every 0.5s plus command latency, final run only; observed peaks are lower bounds, not allocation tracing; includes fixture/test setup',
 'unprofiled_runs_are_separate':True,'synthetic_only':True}
summary['apk_identity_scope']=IDENTITY_SCOPE
(a.output/'profile.json').write_text(json.dumps(summary,indent=2)+'\n',encoding='utf-8')
