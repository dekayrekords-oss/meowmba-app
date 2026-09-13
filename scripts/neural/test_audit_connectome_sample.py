import hashlib
import tempfile
import unittest
from pathlib import Path
from audit_connectome_sample import audit


class ConnectomeAuditTest(unittest.TestCase):
    DATA = ('syn_id,pre_root_id,post_root_id,region,pre_status,post_status,Tags\n'
            '1,720575940379279301,720575940379279302,brain,proofread,fragment,\n'
            '2,720575940379279302,720575940379279303,vnc,fragment,proofread,\n'
            '3,720575940379279303,720575940379279303,vnc,proofread,proofread,\n')

    def run_audit(self, data=None, patch=None):
        raw = (data or self.DATA).encode()
        manifest = {'dataset':'synthetic validation fixture','datasetVersion':'test1',
            'sourceUrl':'https://example.invalid/synthetic.csv','sourceRevision':'synthetic1',
            'sha256':hashlib.sha256(raw).hexdigest(),'expectedBytes':len(raw),'expectedRows':3,
            'licenseId':'original-test-fixture','licenseEvidenceUrl':'https://example.invalid/license',
            'attribution':'Meow test fixture; no biological data','provenanceKind':'synthetic_test'}
        manifest.update(patch or {})
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/'roster.csv'
            path.write_bytes(raw)
            return audit(path, manifest)

    def test_large_neuron_ids_stay_distinct_and_fragment_rows_are_counted(self):
        result = self.run_audit()
        self.assertEqual(result['uniqueEndpointIds'],3)
        self.assertEqual(result['weakComponents'],1)
        self.assertEqual(result['selfConnectionRows'],1)
        self.assertEqual(result['rowsWithFragmentEndpoint'],2)
        self.assertEqual(result['blankReviewTagRows'],3)

    def test_corrupt_or_differently_sized_data_cannot_match_provenance(self):
        for patch in [{'sha256':'0'*64},{'expectedBytes':1},{'expectedRows':2}]:
            with self.assertRaises(ValueError):
                self.run_audit(patch=patch)

    def test_float_ids_and_duplicate_synapse_ids_are_rejected(self):
        for raw in [self.DATA.replace('720575940379279301','7.20575940379279301e17'), self.DATA.replace('\n3,','\n2,')]:
            with self.assertRaises(ValueError):
                self.run_audit(raw)

    def test_missing_license_or_unknown_provenance_is_not_silently_accepted(self):
        for patch in [{'licenseId':''},{'provenanceKind':'real_motor_brain'},{'sourceUrl':'http://example.invalid'}]:
            with self.assertRaises(ValueError):
                self.run_audit(patch=patch)

    def test_same_bytes_and_manifest_produce_same_report(self):
        self.assertEqual(self.run_audit(), self.run_audit())


if __name__ == '__main__':
    unittest.main()
