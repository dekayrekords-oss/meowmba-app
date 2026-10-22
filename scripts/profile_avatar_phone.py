#!/usr/bin/env python3
"""Read-only reproducible ADB process/thermal sampling. No permission changes."""
import argparse, subprocess, time, json, pathlib
p=argparse.ArgumentParser();p.add_argument('--serial',required=True);p.add_argument('--seconds',type=int,default=60);p.add_argument('--out',required=True);p.add_argument('--label',default='observed');a=p.parse_args()
def adb(*args):
 return subprocess.run(['adb','-s',a.serial,*args],capture_output=True,text=True,timeout=15).stdout.strip()
pkg='com.example.meowmba'
pid=adb('shell','pidof',pkg).split(' ')[0]
header={'label':a.label,'epoch':time.time(),'pid':pid,'package':adb('shell','dumpsys','package',pkg),'battery':adb('shell','dumpsys','battery'),'thermal':adb('shell','dumpsys','thermalservice'),'activity':adb('shell','dumpsys','activity','activities')}
# Keep only version fields and resumed activity; omit unrelated app/account state.
header['package']='\n'.join(x.strip() for x in header['package'].splitlines() if any(k in x for k in ('versionCode=','versionName=','lastUpdateTime=')))
header['activity']='\n'.join(x.strip() for x in header['activity'].splitlines() if 'mResumedActivity' in x or 'topResumedActivity' in x)
def graphics():
 raw=adb('shell','dumpsys','gfxinfo',pkg)
 return {'monotonic':time.monotonic(), 'summary':'\n'.join(x.strip() for x in raw.splitlines() if x.strip().startswith(('Stats since:', 'Total frames rendered:', 'Janky frames:', '50th percentile:', '90th percentile:', '95th percentile:', '99th percentile:', '50th gpu percentile:', '95th gpu percentile:')))}
header['graphics_before']=graphics()
rows=[]; start=time.monotonic()
while time.monotonic()-start<a.seconds:
 t=time.monotonic()
 current_pid=adb('shell','pidof',pkg).split(' ')[0]
 foreground=adb('shell','dumpsys','activity','activities')
 sample={'pid':current_pid, 'meow_foreground':any(pkg in x for x in foreground.splitlines() if 'mResumedActivity' in x or 'topResumedActivity' in x), 'elapsed':t-start,'stat':adb('shell','cat',f'/proc/{current_pid}/stat') if current_pid else '', 'battery_temp':adb('shell','dumpsys','battery')}
 sample['battery_temp']='\n'.join(x.strip() for x in sample['battery_temp'].splitlines() if x.strip().startswith(('temperature:', 'level:', 'USB powered:')))
 rows.append(sample)
 time.sleep(max(0,2-(time.monotonic()-t)))
header['graphics_after']=graphics()
header['top']=adb('shell','top','-H','-b','-n','1','-p',pid) if pid else ''
header['meminfo']=adb('shell','dumpsys','meminfo',pkg)
header['thermal_end']=adb('shell','dumpsys','thermalservice')
header['samples']=rows
path=pathlib.Path(a.out);path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(header,indent=2))
print(f'{len(rows)} samples saved to {path}')
