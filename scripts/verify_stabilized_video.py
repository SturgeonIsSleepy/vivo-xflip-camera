"""Track the green marker in the generated fixture, independently of the Android estimator.

Requires numpy and imageio-ffmpeg. Usage: python verify_stabilized_video.py input.mp4 output.mp4 report.json
"""
import hashlib
import json
import sys
from pathlib import Path

import imageio_ffmpeg as ffmpeg
import numpy as np


def measure(path):
    reader = ffmpeg.read_frames(str(path))
    metadata = next(reader)
    width, height = metadata['size']
    positions = []
    black_edges = 0.0
    for data in reader:
        frame = np.frombuffer(data, np.uint8).reshape(height, width, 3)
        marker = (frame[:, :, 1] > 180) & (frame[:, :, 0] < 70) & (frame[:, :, 2] < 100)
        y, x = np.nonzero(marker)
        if len(x) < 30:
            raise AssertionError('Synthetic marker missing')
        positions.append((x.mean(), y.mean()))
        edges = np.concatenate((frame[0], frame[-1], frame[:, 0], frame[:, -1]))
        black_edges = max(black_edges, float((edges.max(axis=1) < 5).mean()))
    values = np.array(positions)
    # Exclude the half-second boundaries; separately retain all frames for continuity checks.
    trim = round(metadata['fps'] * 0.5)
    values = values[trim:-trim]
    time = np.arange(len(values))
    residual = values - np.stack([np.polyval(np.polyfit(time, values[:, i], 1), time) for i in range(2)], axis=1)
    return metadata, len(positions), np.sqrt(np.mean(residual * residual, axis=0)), black_edges


source, output, report = map(Path, sys.argv[1:4])
before, count_before, rms_before, _ = measure(source)
after, count_after, rms_after, black_edges = measure(output)
ratio = rms_after / 1.10 / rms_before
assert before['size'] == after['size'], 'Resolution changed'
assert count_before == count_after, 'Frame count changed'
assert abs(before['duration'] - after['duration']) < 0.15, 'Duration changed'
assert after.get('audio_codec') == before.get('audio_codec') == 'aac', 'Audio not preserved'
assert after['codec'] == 'h264', 'Unexpected output codec'
assert black_edges < 0.001, 'Exposed black borders'
assert (ratio < 0.65).all(), f'Motion compensation did not reduce both axes: {ratio}'
result = {
    'fixture': source.name,
    'source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
    'output_sha256': hashlib.sha256(output.read_bytes()).hexdigest(),
    'resolution': list(after['size']),
    'input_frames': count_before, 'output_frames': count_after,
    'input_duration_seconds': before['duration'], 'output_duration_seconds': after['duration'],
    'video_codec': after['codec'], 'audio_codec': after['audio_codec'], 'zoom': 1.10,
    'input_marker_rms_px': rms_before.tolist(), 'output_marker_rms_px': rms_after.tolist(),
    'motion_ratio_after_removing_zoom': ratio.tolist(), 'max_black_border_fraction': black_edges,
    'passed': True,
}
report.parent.mkdir(parents=True, exist_ok=True)
report.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps(result, indent=2))
