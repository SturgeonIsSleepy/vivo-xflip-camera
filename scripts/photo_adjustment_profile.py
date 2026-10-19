"""Validate the synthetic device's bounded profiling receipt; no ADB or image imports."""
import json
import re

STAGES = frozenset(('DECODE', 'NLM_SETUP', 'NLM_ROW_WINDOW', 'NLM_TILE_INPUT',
                    'NLM_FILTER', 'NLM_TILE_OUTPUT', 'NLM_RELEASE', 'INPUT_LEVELS',
                    'TONE_DETAIL', 'JPEG_TRANSACTION', 'BITMAP_RELEASE'))
COUNTERS = ('wall_ns', 'process_cpu_ms', 'calling_thread_cpu_ns')


def parse_profile(output, expected_outcome):
    lines = re.findall(r'(?m)^.*?\bcpu_profile_json=([^\r\n]*)\r?$', output)
    if len(lines) != 1:
        raise ValueError('Expected one complete CPU profile receipt')
    profile = json.loads(lines[0])
    if not isinstance(profile, dict) or type(profile.get('schema')) is not int or profile['schema'] != 1:
        raise ValueError('Unsupported CPU profile schema')
    required = {'outcome': expected_outcome, 'wall_clock': 'elapsedRealtimeNanos',
                'process_cpu_scope': 'entire_app_process',
                'thread_cpu_scope': 'calling_worker_only', 'diagnostic_overhead_included': True}
    if expected_outcome not in ('saved', 'cancelled'):
        raise ValueError('Only a successful/cancelled synthetic test may be accepted')
    if any(type(profile.get(k)) is not type(v) or profile[k] != v for k, v in required.items()):
        raise ValueError('Profile outcome or clock scope mismatch')
    stages = profile.get('stages')
    total = profile.get('total')
    if not isinstance(stages, list) or not 1 <= len(stages) <= len(STAGES) or not isinstance(total, dict):
        raise ValueError('Missing or unbounded stage measurements')
    seen = set()
    for stage in stages:
        if not isinstance(stage, dict) or not isinstance(stage.get('stage'), str):
            raise ValueError('Invalid stage')
        name = stage['stage']
        if name not in STAGES or name in seen:
            raise ValueError('Unknown/duplicate stage')
        seen.add(name)
        count = stage.get('intervals')
        if type(count) is not int or not 1 <= count <= 2**63-1:
            raise ValueError('Invalid interval count')
    for counter in COUNTERS:
        values = []
        for row in [*stages, total]:
            if counter not in row:
                raise ValueError('Missing counter: ' + counter)
            value = row[counter]
            if value is not None and (type(value) is not int or not 0 <= value <= 2**63-1):
                raise ValueError('Invalid counter: ' + counter)
            values.append(value)
        valid = all(v is not None for v in values[:-1])
        expected = sum(values[:-1]) if valid else None
        if expected is not None and expected > 2**63-1:
            expected = None
        if values[-1] != expected:
            raise ValueError('Exclusive stage sum mismatch: ' + counter)
    return profile
