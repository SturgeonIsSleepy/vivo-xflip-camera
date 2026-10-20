"""Evidence gates shared by synthetic save/memory benchmark wrappers."""
import re


IDENTITY_SCOPE = 'standalone_base_apk'


def require_same_base_apk(reports, expected=None):
    if not reports:
        raise ValueError('Missing per-run APK evidence')
    hashes = []
    for report in reports:
        if not isinstance(report, dict):
            raise ValueError('Invalid per-run report')
        digest = report.get('base_apk_sha256')
        if report.get('apk_identity_scope') != IDENTITY_SCOPE or not isinstance(digest, str) \
                or re.fullmatch(r'[0-9a-f]{64}', digest) is None:
            raise ValueError('Missing/invalid standalone base APK identity')
        hashes.append(digest)
    expected = hashes[0] if expected is None else expected
    if any(digest != expected for digest in hashes):
        raise ValueError('APK changed between benchmark runs')
    return expected


def parse_apk_identity(output):
    hashes = re.findall(r'(?m)^.*?\bbase_apk_sha256=([^\r\n]*)\r?$', output)
    scopes = re.findall(r'(?m)^.*?\bapk_identity_scope=([^\r\n]*)\r?$', output)
    if len(hashes) != 1 or scopes != [IDENTITY_SCOPE]:
        raise ValueError('Missing/duplicate per-run APK identity')
    identity = dict(base_apk_sha256=hashes[0], apk_identity_scope=scopes[0])
    require_same_base_apk([identity])
    return identity


def require_unprofiled(report):
    if 'cpu_profile' in report or report.get('sampled', False):
        raise ValueError('Profiled/sample-contaminated run cannot enter speed comparison')
