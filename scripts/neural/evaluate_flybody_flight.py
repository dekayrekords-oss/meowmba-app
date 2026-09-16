#!/usr/bin/env python3
"""CPU-only fixed-policy tracking evaluation; no policy training or body transport."""
import os
os.environ['CUDA_VISIBLE_DEVICES']='-1'
os.environ['TF_CPP_MIN_LOG_LEVEL']='2'
os.environ['TF_NUM_INTRAOP_THREADS']='1'
os.environ['TF_NUM_INTEROP_THREADS']='1'
os.environ['OPENBLAS_NUM_THREADS']='1'
import argparse,hashlib,json,time,sys,subprocess,zipfile
from pathlib import Path

def main():
 p=argparse.ArgumentParser();p.add_argument('--workspace',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--duration',type=float,default=.3);p.add_argument('--sustained',action='store_true');a=p.parse_args()
 if not .1<=a.duration<=3:raise ValueError('duration must be .1..3 seconds')
 w=a.workspace.resolve();source=w/'source'
 if subprocess.check_output(['git','-C',str(source),'rev-parse','HEAD'],text=True).strip()!='d015e9bfe441bd90ae431bac24c55cb74bdbce26':raise ValueError('source changed')
 for filename,digest in [('policies.zip','2d9937c9af2baafad1690c1b318791bde417b4d26dd96d4385ab6723d5d58582'),('trajectories.zip','0d152331e38f2ca6bb1f3286c2500eab49b5ccef93c51a9cc9cff9bb6cd368d0')]:
  if hashlib.sha256((w/'flight-data'/filename).read_bytes()).hexdigest()!=digest:raise ValueError('data hash mismatch')
 if subprocess.check_output(['git','-C',str(source),'status','--porcelain'],text=True).strip():raise ValueError('dirty source')
 # A valid archive alone does not prove extracted runtime assets are unchanged.
 for archive,folder,prefix in [('policies.zip','policies','flight/'),('trajectories.zip','trajectories','wing_pattern_fmech.npy')]:
  with zipfile.ZipFile(w/'flight-data'/archive) as z:
   for info in z.infolist():
    if not info.is_dir() and info.filename.startswith(prefix):
     if (w/'flight-data'/folder/info.filename).read_bytes()!=z.read(info):raise ValueError('extracted asset changed')
 sys.path.insert(0,str(source))
 import numpy as np
 import tensorflow as tf
 import tensorflow_probability as tfp
 from flybody.fly_envs import flight_imitation
 from flybody.tasks.synthetic_trajectories import constant_speed_trajectory
 from flybody.tasks.task_utils import root2com
 policy=tf.saved_model.load(str(w/'flight-data/policies/flight'))
 started=time.monotonic();results=[]
 # Predeclared novel synthetic test conditions; no local optimization uses these.
 cases=[('hover',0.,0.),('forward15',15.,0.),('forward25',25.,0.),('forward20-lateral-perturbation',20.,.15)]
 if a.sustained:cases=[('hover',0.,0.),('forward20',20.,0.),('forward20-lateral-perturbation',20.,.15),('hover-repeat',0.,0.)]
 for name,speed,perturbation in cases:
  for controller in ['official_policy','zero_action_wing_pattern']:
   if name=='hover-repeat' and controller!='official_policy':continue
   if time.monotonic()-started>360:raise TimeoutError('evaluation cap exceeded')
   env=flight_imitation(wpg_pattern_path=str(w/'flight-data/trajectories/wing_pattern_fmech.npy'),random_state=np.random.RandomState(20260913),terminal_com_dist=2.)
   # Explicit research horizon override; official helper defaults to .6 seconds.
   env._time_limit=a.duration+.01
   env.task._time_limit=a.duration+.01
   dt=env.control_timestep()
   n=round(a.duration/dt)+16
   qpos,qvel=constant_speed_trajectory(n_steps=n,speed=speed,init_pos=(0,0,1),body_rot_angle_y=-47.5,control_timestep=dt)
   env.task._traj_generator.set_next_trajectory(qpos,qvel)
   ts=env.reset()
   if perturbation:
    position,quat=env.task._walker.get_pose(env.physics)
    env.task._walker.set_pose(env.physics,position+np.array([0,perturbation,0]),quat)
    env.physics.forward()
    # Perturb after sensing: controller observes the disturbance at the next step.
   spec=env.action_spec();errors=[];angles=[];heights=[];rewards=[];steps=0;trajectory=[];digest=hashlib.sha256();tic=time.monotonic()
   while not ts.last() and steps<round(a.duration/dt):
    if controller=='official_policy':
     obs={k:tf.constant(v[None,...],dtype=tf.float32) for k,v in ts.observation.items()}
     action=policy(obs).mean()[0].numpy()
    else:action=np.zeros(spec.shape,dtype=np.float32)
    # Equivalent to official CanonicalSpecWrapper, clip=True.
    action=spec.minimum+(np.clip(action,-1,1)+1)*.5*(spec.maximum-spec.minimum)
    ts=env.step(action);steps+=1
    pos,quat=env.task._walker.get_pose(env.physics)
    idx=min(int(round(env.physics.data.time/dt)),len(env.task._ref_qpos)-1)
    target=root2com(env.task._ref_qpos[idx])
    com=env.physics.named.data.subtree_com['walker/'].copy()
    errors.append(float(np.linalg.norm(com-target)))
    if steps%50==0:trajectory.append({'timeSeconds':float(env.physics.data.time),'comCm':com.tolist(),'targetCm':target.tolist(),'errorCm':errors[-1]})
    refquat=env.task._ref_qpos[idx,3:7]
    angles.append(float(2*np.arccos(np.clip(abs(np.dot(quat,refquat)),0,1))))
    heights.append(float(com[2]));rewards.append(float(ts.reward or 0))
    digest.update(env.physics.data.qpos.tobytes())
    if not np.isfinite(errors[-1]) or not np.all(np.isfinite(env.physics.data.qvel)):raise ValueError('nonfinite')
   results.append({'case':name,'controller':controller,'seed':20260913,'requestedSpeedCmPerSec':speed,'initialLateralPerturbationCm':perturbation,
    'controlSteps':steps,'physicsSteps':round(env.physics.data.time/env.physics.timestep()),'durationSeconds':float(env.physics.data.time),
    'requestedDurationSeconds':a.duration,'completedRequestedDuration':steps>=round(a.duration/dt),
    'positionRmseCm':float(np.sqrt(np.mean(np.square(errors)))),'maxPositionErrorCm':max(errors),'finalPositionErrorCm':errors[-1],
    'orientationRmseRadians':float(np.sqrt(np.mean(np.square(angles)))),'minimumComHeightCm':min(heights),
    'meanTaskReward':float(np.mean(rewards)),'wallSeconds':time.monotonic()-tic,'trajectorySha256':digest.hexdigest(),
    'sampledTrajectory':trajectory,'muJoCoWarningCounts':[int(x.number) for x in env.physics.data.warning]})
   print(json.dumps({k:v for k,v in results[-1].items() if k!='sampledTrajectory'}),flush=True)
   env.close()
 for r in results:r['meetsDeclaredSuccessCriteria']=r['completedRequestedDuration'] and r['maxPositionErrorCm']<.4 and not any(r['muJoCoWarningCounts'])
 report={'scope':'official pretrained synthetic RL controller, actual FlyBody aerodynamic physics; no connectome weights or biological control claim',
  'split':'predeclared synthetic conditions with no local fitting; external policy training overlap cannot be certified',
  'sourceRevision':'d015e9bfe441bd90ae431bac24c55cb74bdbce26','dataLicense':'Figshare25309105.v4 GPL3.0+',
  'successCriteria':{'requestedDurationCompleted':True,'maximumPositionErrorCm':.4,'noMuJoCoWarnings':True,'finiteState':True},
  'tensorflow':tf.__version__,'tensorflowProbability':tfp.__version__,'wallSeconds':time.monotonic()-started,'results':results}
 report['totalPhysicsSteps']=sum(r['physicsSteps'] for r in results)
 if a.sustained:
  first=next(r for r in results if r['case']=='hover' and r['controller']=='official_policy')
  repeat=next(r for r in results if r['case']=='hover-repeat')
  report['hoverRepeatTrajectoryIdentical']=first['trajectorySha256']==repeat['trajectorySha256']
 a.output.write_text(json.dumps(report,indent=2)+'\n')
if __name__=='__main__':main()
