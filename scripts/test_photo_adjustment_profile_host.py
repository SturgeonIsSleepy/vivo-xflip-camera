"""Synthetic JSON contract only, never calls ADB or claims a device measurement."""
import copy
import json
import unittest
from photo_adjustment_profile import parse_profile


def fixture():
    return {'schema': 1, 'outcome': 'saved', 'wall_clock': 'elapsedRealtimeNanos',
            'process_cpu_scope': 'entire_app_process', 'thread_cpu_scope': 'calling_worker_only',
            'diagnostic_overhead_included': True,
            'stages': [{'stage': 'DECODE', 'intervals': 1, 'wall_ns': 1_000_000,
                        'process_cpu_ms': 2, 'calling_thread_cpu_ns': 100},
                       {'stage': 'NLM_FILTER', 'intervals': 8, 'wall_ns': 2_000_000,
                        'process_cpu_ms': 8, 'calling_thread_cpu_ns': 200}],
            'total': {'wall_ns': 3_000_000, 'process_cpu_ms': 10, 'calling_thread_cpu_ns': 300}}


def output(profile):
    return 'INSTRUMENTATION_RESULT: cpu_profile_json=' + json.dumps(profile) + '\r\n'


class ProfileContractTest(unittest.TestCase):
    def test_multicore_cpu_can_exceed_wall_and_intervals_aggregate(self):
        profile = fixture()
        self.assertEqual(profile, parse_profile(output(profile), 'saved'))

    def test_unsupported_thread_clock_is_null_not_zero(self):
        profile = fixture()
        profile['stages'][0]['calling_thread_cpu_ns'] = None
        profile['total']['calling_thread_cpu_ns'] = None
        self.assertIsNone(parse_profile(output(profile), 'saved')['total']['calling_thread_cpu_ns'])

    def test_invalid_wall_clock_can_be_exported_without_losing_cpu(self):
        profile = fixture()
        profile['stages'][1]['wall_ns'] = None
        profile['total']['wall_ns'] = None
        self.assertEqual(10, parse_profile(output(profile), 'saved')['total']['process_cpu_ms'])

    def test_cancelled_receipt_requires_cancelled_test(self):
        profile = fixture(); profile['outcome'] = 'cancelled'
        self.assertEqual('cancelled', parse_profile(output(profile), 'cancelled')['outcome'])
        with self.assertRaises(ValueError): parse_profile(output(profile), 'saved')

    def test_missing_truncated_or_duplicate_receipts_fail(self):
        for text in ('', 'INSTRUMENTATION_RESULT: cpu_profile_json={"schema":', output(fixture())*2):
            with self.subTest(text=text):
                with self.assertRaises(ValueError): parse_profile(text, 'saved')

    def test_wrong_units_scope_schema_or_outcome_fail(self):
        for key, value in (('schema', 2), ('schema', True), ('wall_clock', 'nanoTime'),
                           ('process_cpu_scope', 'opencv_only'), ('thread_cpu_scope', 'all_workers'),
                           ('outcome', 'failed'), ('diagnostic_overhead_included', 1)):
            profile = fixture(); profile[key] = value
            with self.subTest(key=key, value=value):
                with self.assertRaises(ValueError): parse_profile(output(profile), 'saved')

    def test_missing_negative_fractional_boolean_and_overflow_counters_fail(self):
        for value in (-1, 1.5, True, 2**63, '1'):
            profile = fixture(); profile['stages'][0]['wall_ns'] = value
            with self.subTest(value=value):
                with self.assertRaises(ValueError): parse_profile(output(profile), 'saved')
        profile = fixture(); del profile['stages'][0]['wall_ns']
        with self.assertRaises(ValueError): parse_profile(output(profile), 'saved')

    def test_invalid_stage_count_names_and_duplicates_fail(self):
        for stages in ([], [fixture()['stages'][0]]*12,
                       [dict(fixture()['stages'][0], stage='unknown')],
                       [fixture()['stages'][0]]*2,
                       [dict(fixture()['stages'][0], intervals=True)],
                       [dict(fixture()['stages'][0], intervals=0)]):
            profile = fixture(); profile['stages'] = stages
            with self.subTest(stages=stages):
                with self.assertRaises(ValueError): parse_profile(output(profile), 'saved')

    def test_exclusive_totals_must_match_and_unknown_stays_unknown(self):
        for value in (None, 99):
            profile = fixture(); profile['total']['wall_ns'] = value
            with self.assertRaises(ValueError): parse_profile(output(profile), 'saved')
        profile = fixture(); profile['stages'][0]['process_cpu_ms'] = None
        with self.assertRaises(ValueError): parse_profile(output(profile), 'saved')

    def test_input_is_not_mutated(self):
        profile = fixture(); before = copy.deepcopy(profile)
        parse_profile(output(profile), 'saved')
        self.assertEqual(before, profile)


if __name__ == '__main__':
    unittest.main()
