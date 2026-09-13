#!/usr/bin/env python3
"""Import an authenticated published grouped graph; never infer individual weights."""
import argparse,csv,hashlib,json
from pathlib import Path

def main():
 p=argparse.ArgumentParser();p.add_argument('--directory',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
 hashes={'edges.csv':'5b954bf628c0ba614cc840931122f234ab44a3ab43eb13ded25e7f87cf2f00dc','neurons.csv':'2a1253cfd57842193334b7eca375b4043d5e3fc9945cfbe7f71fa8d898711b01'}
 tables={}
 for name,expected in hashes.items():
  raw=(a.directory/name).read_bytes()
  if len(raw)>100000 or hashlib.sha256(raw).hexdigest()!=expected: raise ValueError('pinned file mismatch: '+name)
  tables[name]=list(csv.DictReader(raw.decode().splitlines()))
 neurons=tables['neurons.csv'];edges=tables['edges.csv']
 ids=[x['root_id'] for x in neurons]
 if len(set(ids))!=len(ids) or any(not i.isdecimal() for i in ids): raise ValueError('invalid individual IDs')
 groups=sorted({x['display_name'] for x in neurons})
 matrix=[[0]*len(groups) for _ in groups]
 for edge in edges:
  if edge['from'] not in groups or edge['to'] not in groups: raise ValueError('unmapped endpoint group')
  count=int(edge['count'])
  if count<=0: raise ValueError('invalid count')
  matrix[groups.index(edge['from'])][groups.index(edge['to'])]+=count
 report={'scope':'genuine published anatomical GROUP adjacency; not individual synaptic weights or physiological signs',
  'sourceRevision':'e31a2e26b9937dca72e5ca1c1960df6454d76114','sourcePath':'figures/vignettes/network_fig3h_{edges,neurons}.csv',
  'licenseEvidence':'https://github.com/htem/BANC-project/tree/e31a2e26b9937dca72e5ca1c1960df6454d76114#license',
  'projectReportedLicense':'CC-BY-4.0','sha256':hashes,'individualRosterCount':len(ids),'groupCount':len(groups),
  'directedGroupRows':len(edges),'sumPublishedCounts':sum(sum(row) for row in matrix),
  'groups':groups,'adjacencyCounts':matrix,'limitations':['Counts are grouped published anatomical observations, not executable model weights.',
  'Grouped or mixed neurotransmitter labels do not establish per-synapse sign.', 'No conductances, delays, muscle mapping, or behavioral validation supplied.']}
 a.output.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
if __name__=='__main__':main()
