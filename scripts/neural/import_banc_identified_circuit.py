#!/usr/bin/env python3
"""Extract real neuron pairs for a pinned identified roster; no physiological fit."""
import argparse,csv,hashlib,json,time
from pathlib import Path

def main():
 p=argparse.ArgumentParser();p.add_argument('--edges',type=Path,required=True);p.add_argument('--neurons',type=Path,required=True);p.add_argument('--subset',type=Path,required=True);p.add_argument('--report',type=Path,required=True);a=p.parse_args()
 import pyarrow.dataset as ds
 from collections import Counter
 start=time.monotonic();md5=hashlib.md5();sha=hashlib.sha256()
 if a.edges.stat().st_size!=305250378:raise ValueError('unexpected source size')
 with a.edges.open('rb') as f:
  while chunk:=f.read(1024*1024):md5.update(chunk);sha.update(chunk)
 if md5.hexdigest()!='394406f8a9bdf093c895f95aff4f6c49':raise ValueError('Dataverse checksum mismatch')
 raw=a.neurons.read_bytes()
 if hashlib.sha256(raw).hexdigest()!='2a1253cfd57842193334b7eca375b4043d5e3fc9945cfbe7f71fa8d898711b01':raise ValueError('roster changed')
 neurons=list(csv.DictReader(raw.decode().splitlines()));groups={r['root_id']:r['display_name'] for r in neurons}
 if len(groups)!=76:raise ValueError('roster count changed')
 dataset=ds.dataset(str(a.edges),format='ipc')
 subset=dataset.to_table(filter=ds.field('pre').isin(list(groups)) & ds.field('post').isin(list(groups)),use_threads=False)
 rows=subset.to_pylist()
 if len(rows)>76*76:raise ValueError('unexpected duplicate neuron-pair granularity')
 pairs=set();groupcounts=Counter();endpoints=set()
 for r in rows:
  pair=(r['pre'],r['post'])
  if pair in pairs or r['count']<=0:raise ValueError('duplicate or invalid pair')
  pairs.add(pair);endpoints.update(pair);groupcounts[(groups[pair[0]],groups[pair[1]])]+=r['count']
 rows.sort(key=lambda r:(r['pre'],r['post']))
 with a.subset.open('w') as f:
  writer=csv.DictWriter(f,fieldnames=['pre','post','count','norm','post_count','pre_count']);writer.writeheader();writer.writerows(rows)
 report={'scope':'genuine BANC v888 v2 individual-neuron anatomical counts induced on 76 identified Figure3h neurons; not fitted physiology',
 'dataverseDataset':'doi:10.7910/DVN/7WTH1N','dataverseRelease':'3.0','dataFileId':13992792,'license':'CC-BY-4.0',
 'licenseEvidence':'https://dataverse.harvard.edu/api/datasets/:persistentId/?persistentId=doi:10.7910/DVN/7WTH1N',
 'sourceBytes':a.edges.stat().st_size,'sourceMd5':md5.hexdigest(),'sourceSha256':sha.hexdigest(),
 'rosterSourceRevision':'e31a2e26b9937dca72e5ca1c1960df6454d76114','rosterNeurons':len(groups),'connectedRosterNeurons':len(endpoints),
 'directedNeuronPairs':len(rows),'sumSynapticCounts':sum(r['count'] for r in rows),'pairsCountAtLeast5':sum(r['count']>=5 for r in rows),
 'groupCountSummary':[{'preGroup':x,'postGroup':y,'count':n} for (x,y),n in sorted(groupcounts.items())],
 'subsetSha256':hashlib.sha256(a.subset.read_bytes()).hexdigest(),'wallSeconds':time.monotonic()-start,
 'limitations':['Induced subset omits all connections to neurons outside roster.','Anatomical counts and target-input fractions are not conductances or delays.','No claim of reconstructing fly brain physiology or flight.','Published group vignette and full edge export can use different display thresholds; do not silently equate them.']}
 a.report.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps({k:v for k,v in report.items() if k!='groupCountSummary'}))
if __name__=='__main__':main()
