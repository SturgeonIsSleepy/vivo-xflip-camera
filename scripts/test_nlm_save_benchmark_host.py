"""Run the real benchmark control flow with fake child reports and pixel comparisons.

No ADB, APK, JPEG decoder or device timing is exercised by these contract tests.
"""
import contextlib
import io
import json
from pathlib import Path
import runpy
import subprocess
import sys
import tempfile
import types
import unittest
from unittest import mock
from nlm_benchmark_contract import parse_apk_identity, require_same_base_apk

APK = 'a' * 64
OTHER_APK = 'b' * 64
SCRIPT = Path(__file__).with_name('benchmark_nlm_save_device.py')


class Pixels:
    def __sub__(self, other):
        return self

    def max(self):
        return 0


class BenchmarkContractTest(unittest.TestCase):
    def exercise(self, hashes=None, missing=False, order=None, profiled=False, sampled=False,
                 memory_hash=APK, memory_summary_hash=APK):
        hashes = hashes or [APK] * 4
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); fixtures = root / 'fixtures'; fixtures.mkdir()
            (fixtures / 'nlm-full.jpg').write_bytes(b'Not a JPEG: child/image operations are explicit fakes')
            output = root / 'output'; calls = []; reads = []; timing_index = 0

            def run(command, **kwargs):
                nonlocal timing_index
                name = Path(command[1]).name; folder = Path(command[-1]); folder.mkdir(parents=True, exist_ok=True)
                calls.append(command)
                schedule = [int(command[command.index(flag)+1]) for flag in ('--nlm-tile', '--nlm-threads')]
                report = {'save_ms': 100 + timing_index, 'opencv_threads': 2 if schedule[1] == 2 else 4,
                          'requested_schedule': schedule, 'original_preserved': True, 'instrumentation_passed': True,
                          'apk_identity_scope': 'standalone_base_apk'}
                if name == 'test_photo_adjustment_device.py':
                    if not missing: report['base_apk_sha256'] = hashes[timing_index]
                    if profiled: report['cpu_profile'] = {'not_a_speed_sample': True}
                    if sampled: report['sampled'] = True
                    timing_index += 1
                    (folder / 'full-edit-report.json').write_text(json.dumps(report))
                    (folder / 'full-edit-result.jpg').write_bytes(b'Fake pixels')
                elif name == 'profile_photo_adjustment_device.py':
                    report.update(base_apk_sha256=memory_hash, sampled=True, save_ms=1_000_000)
                    (folder / 'profile.json').write_text(json.dumps({'apk_sha256': memory_summary_hash, 'apk_identity_scope': 'standalone_base_apk', 'runs': [report]}))
                    (folder / 'run-1').mkdir();(folder / 'run-1/full-edit-result.jpg').write_bytes(b'Fake memory pixels')
                else:
                    raise AssertionError('Unexpected child: ' + name)
                return subprocess.CompletedProcess(command, 0, stdout='', stderr='')

            numpy = types.ModuleType('numpy'); numpy.asarray = lambda *a, **k: Pixels()
            numpy.abs = lambda value: value; numpy.int16 = object()
            def image_open(path):
                self.assertTrue(Path(path).is_file(), 'Benchmark used an absent/relabelled result')
                reads.append(str(path));return object()
            pil = types.ModuleType('PIL'); pil.Image = types.SimpleNamespace(open=image_open)
            argv = [str(SCRIPT), '--serial', 'fake-no-device']
            if order: argv += ['--order', order]
            argv += [str(fixtures), str(output)]
            with mock.patch.object(sys, 'argv', argv), mock.patch('subprocess.run', side_effect=run), \
                    mock.patch.dict(sys.modules, {'numpy': numpy, 'PIL': pil}), contextlib.redirect_stdout(io.StringIO()):
                try:
                    runpy.run_path(str(SCRIPT), run_name='__main__')
                except ValueError:
                    self.assertFalse((output / 'full-save-comparison.json').exists(), 'Rejected run must not publish summary')
                    raise
            return json.loads((output / 'full-save-comparison.json').read_text()), calls, reads

    def test_mixed_timing_apks_cannot_be_called_same_apk(self):
        with self.assertRaises(ValueError):
            self.exercise(hashes=[APK, OTHER_APK, APK, APK])

    def test_missing_per_run_identity_cannot_be_called_same_apk(self):
        with self.assertRaises(ValueError):
            self.exercise(missing=True)

    def test_default_abba_accepts_six_same_apk_runs_and_excludes_memory_times(self):
        report, calls, reads = self.exercise()
        self.assertEqual('ABBA', report['order']);self.assertTrue(report['same_apk'])
        self.assertEqual(APK, report['apk_sha256']);self.assertEqual('standalone_base_apk', report['apk_identity_scope'])
        self.assertEqual(['baseline','candidate','candidate','baseline'], [r['label'] for r in report['runs']])
        self.assertEqual({'baseline':101.5,'candidate':101.5}, report['median_save_ms'])
        self.assertTrue(report['memory_runs_excluded_from_timing_comparison'])
        self.assertEqual(6, len(calls));self.assertEqual(6, len(report['pixel_comparisons']))
        self.assertEqual('1-baseline', Path(reads[0]).parent.name)
        self.assertTrue(all('--cpu-profile' not in command for command in calls))

    def test_baab_preserves_labels_schedules_reference_and_order(self):
        report, calls, reads = self.exercise(order='BAAB')
        self.assertEqual('BAAB',report['order'])
        self.assertEqual(['candidate','baseline','baseline','candidate'], [r['label'] for r in report['runs']])
        self.assertEqual([[0,0],[512,2],[512,2],[0,0]], [r['requested_schedule'] for r in report['runs']])
        self.assertEqual([1,2,3,4], [r['order'] for r in report['runs']])
        self.assertEqual('1-candidate',Path(reads[0]).parent.name)
        self.assertEqual(6,len(calls));self.assertEqual(6,len(report['pixel_comparisons']))
        self.assertEqual({'baseline':101.5,'candidate':101.5},report['median_save_ms'])

    def test_memory_inner_run_mismatch_is_rejected_even_when_outer_hashes_agree(self):
        with self.assertRaises(ValueError):self.exercise(memory_hash=OTHER_APK)

    def test_memory_envelope_cannot_use_another_apk(self):
        with self.assertRaises(ValueError):self.exercise(memory_summary_hash=OTHER_APK)

    def test_cpu_profiled_run_cannot_enter_speed_median(self):
        with self.assertRaises(ValueError):self.exercise(profiled=True)

    def test_memory_sampled_run_cannot_enter_speed_median(self):
        with self.assertRaises(ValueError):self.exercise(sampled=True)

    def test_identity_scope_digest_format_and_missing_rows_are_rejected(self):
        cases = [[], [None], [{}], [{'base_apk_sha256':APK,'apk_identity_scope':'base_only'}]]
        cases += [[{'base_apk_sha256':digest,'apk_identity_scope':'standalone_base_apk'}]
                  for digest in ('',APK.upper(),APK+'a',None,123)]
        for rows in cases:
            with self.subTest(rows=rows):
                with self.assertRaises(ValueError):require_same_base_apk(rows)

    def test_instrumentation_identity_parsing_rejects_missing_duplicate_and_profile_alias(self):
        text = f'INSTRUMENTATION_RESULT: base_apk_sha256={APK}\r\nINSTRUMENTATION_RESULT: apk_identity_scope=standalone_base_apk\r\n'
        self.assertEqual(APK,parse_apk_identity(text)['base_apk_sha256'])
        alias = f'INSTRUMENTATION_RESULT: profile_base_apk_sha256={OTHER_APK}\n'
        self.assertEqual(APK,parse_apk_identity(alias+text)['base_apk_sha256'])
        for invalid in ('', text+text, alias, text.replace(APK,'A'*64)):
            with self.subTest(text=invalid):
                with self.assertRaises(ValueError):parse_apk_identity(invalid)


if __name__ == '__main__':
    unittest.main()
