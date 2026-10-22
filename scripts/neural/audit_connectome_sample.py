#!/usr/bin/env python3
"""Validate a pinned anatomical roster; never converts observations to motor weights."""
import argparse
import collections
import csv
import hashlib
import io
import json
import re
from pathlib import Path

MAX_BYTES = 5_000_000
MAX_ROWS = 100_000
REQUIRED = {'syn_id', 'pre_root_id', 'post_root_id', 'region', 'pre_status', 'post_status', 'Tags'}


def audit(path, manifest):
    for field in ('dataset', 'datasetVersion', 'sourceUrl', 'sourceRevision', 'sha256', 'licenseId', 'licenseEvidenceUrl', 'attribution', 'provenanceKind'):
        if not isinstance(manifest.get(field), str) or not manifest[field].strip():
            raise ValueError('missing provenance field: '+field)
    if manifest['provenanceKind'] not in ('anatomical_roster', 'synthetic_test'):
        raise ValueError('unknown provenance kind')
    if not re.fullmatch(r'[0-9a-f]{64}', manifest['sha256']):
        raise ValueError('invalid expected SHA-256')
    if not manifest['sourceUrl'].startswith('https://') or not manifest['licenseEvidenceUrl'].startswith('https://'):
        raise ValueError('provenance URLs must use HTTPS')
    size = Path(path).stat().st_size
    if size > MAX_BYTES or size != manifest.get('expectedBytes'):
        raise ValueError('unexpected or excessive file size')
    raw = Path(path).read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    if digest != manifest['sha256']:
        raise ValueError('SHA-256 mismatch; do not relabel changed data')
    reader = csv.DictReader(io.StringIO(raw.decode('utf-8-sig')))
    if not REQUIRED.issubset(reader.fieldnames or []):
        raise ValueError('missing anatomical roster columns')
    synapses, edges, nodes, graph = set(), collections.Counter(), set(), collections.defaultdict(set)
    regions, endpoints, tags = collections.Counter(), collections.Counter(), collections.Counter()
    fragment_rows = self_edges = rows = 0
    for row in reader:
        rows += 1
        if rows > MAX_ROWS:
            raise ValueError('too many rows')
        if None in row or any(row.get(k) is None for k in REQUIRED):
            raise ValueError('malformed CSV row')
        # IDs stay decimal strings: IEEE doubles silently corrupt large root IDs.
        syn, pre, post = (row[k].strip() for k in ('syn_id','pre_root_id','post_root_id'))
        if not all(re.fullmatch(r'[1-9][0-9]*', value) for value in (syn,pre,post)):
            raise ValueError('IDs must be positive decimal strings without float conversion')
        if syn in synapses:
            raise ValueError('duplicate synapse ID')
        synapses.add(syn)
        nodes.update((pre,post))
        edges[(pre,post)] += 1
        graph[pre].add(post); graph[post].add(pre)
        regions[row['region']] += 1
        endpoints.update((row['pre_status'],row['post_status']))
        tags[row['Tags'].strip()] += 1
        fragment_rows += int('fragment' in (row['pre_status'],row['post_status']))
        self_edges += int(pre == post)
    if rows != manifest.get('expectedRows') or rows == 0:
        raise ValueError('unexpected or empty row count')
    unseen, components = set(nodes), []
    while unseen:
        stack = [unseen.pop()]
        n = 0
        while stack:
            current = stack.pop(); n += 1
            for neighbor in graph[current]:
                if neighbor in unseen:
                    unseen.remove(neighbor); stack.append(neighbor)
        components.append(n)
    return {
        'auditVersion': 1, 'provenance': manifest, 'verifiedSha256': digest,
        'rows': rows, 'uniqueSynapseIds':len(synapses), 'uniqueEndpointIds':len(nodes),
        'observedDirectedPairs':len(edges), 'selfConnectionRows':self_edges,
        'weakComponents':len(components), 'largestWeakComponentNodes':max(components),
        'regionRows':dict(sorted(regions.items())), 'endpointStatusCounts':dict(sorted(endpoints.items())),
        'rowsWithFragmentEndpoint':fragment_rows, 'fractionRowsWithFragmentEndpoint':fragment_rows/rows,
        'blankReviewTagRows':tags[''],
        'interpretation':'Roster integrity and sampled anatomy only. Sampled-pair counts are not full connection weights. Blank tags are not correct/incorrect verdicts. No motor controller, physiology, precision/recall, flight accuracy or commercial embedding is validated.',
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', required=True, type=Path)
    parser.add_argument('--manifest', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    report = audit(args.input, json.loads(args.manifest.read_text()))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True)+'\n')
    print(json.dumps({k:v for k,v in report.items() if k not in ('provenance','interpretation')}))


if __name__ == '__main__':
    main()
