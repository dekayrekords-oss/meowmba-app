import unittest
from profile_termux_phone import cpu_ticks, scoped_processes, uid_number


class TermuxProfileTest(unittest.TestCase):
    def test_uid_forms_and_scope(self):
        self.assertEqual(10312, uid_number('u0_a312'))
        self.assertEqual(1010312, uid_number('u10_a312'))
        self.assertEqual(10312, uid_number('10312'))
        self.assertIsNone(uid_number('shell'))
        processes = scoped_processes('UID PID PPID NAME\nu0_a312 71 1 proot\n10312 72 71 opencode\n10200 73 1 other\nshell 74 1 sh', {10312})
        self.assertEqual(['proot', 'opencode'], [p['name'] for p in processes])

    def test_stat_with_spaces_parentheses_and_missing_values(self):
        fields = ['S'] + ['0'] * 21
        fields[11], fields[12], fields[19] = '90', '10', '500'
        self.assertEqual((100, 500), cpu_ticks('71 (odd (name)) ' + ' '.join(fields)))
        self.assertIsNone(cpu_ticks('Permission denied'))
        self.assertIsNone(cpu_ticks('71 (name) S 1'))


if __name__ == '__main__':
    unittest.main()
