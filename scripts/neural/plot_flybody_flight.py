#!/usr/bin/env python3
"""Plot measured simulator states; never renders invented trajectory points."""
import argparse,json
from pathlib import Path

def main():
 p=argparse.ArgumentParser();p.add_argument('--input',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 import matplotlib
 matplotlib.use('Agg')
 import matplotlib.pyplot as plt
 report=json.loads(a.input.read_text())
 fig,axes=plt.subplots(3,2,figsize=(12,10),layout='constrained')
 for row,case in enumerate(['hover','forward20','forward20-lateral-perturbation']):
  for result in report['results']:
   if result['case']!=case:continue
   data=result['sampledTrajectory'];ts=[x['timeSeconds'] for x in data]
   color='#156f73' if result['controller']=='official_policy' else '#bd4d27'
   label='Official RL policy' if result['controller']=='official_policy' else 'Pattern-only baseline'
   axes[row,0].plot(ts,[x['errorCm'] for x in data],color=color,label=label)
   axes[row,1].plot(ts,[x['comCm'][2] for x in data],color=color,label=label)
  axes[row,0].axhline(.4,color='#777777',linestyle='--',linewidth=1,label='Declared error limit')
  axes[row,1].axhline(1,color='#777777',linestyle='--',linewidth=1,label='Reference height')
  axes[row,0].set(title=case,ylabel='CoM position error (cm)',xlabel='Simulation time (s)',xlim=(0,3),ylim=(0,1.1))
  axes[row,1].set(title=case,ylabel='CoM height (cm)',xlabel='Simulation time (s)',xlim=(0,3),ylim=(0,1.1))
  for ax in axes[row]:ax.grid(alpha=.2)
 for ax in axes[0]:ax.legend(fontsize=8)
 fig.suptitle('Measured FlyBody aerodynamic tracking — synthetic RL policy\nBaseline curves end at the last recorded sample before termination; no biological neural-controller claim',fontsize=14)
 fig.savefig(a.output,dpi=150)
 print(a.output)
if __name__=='__main__':main()
