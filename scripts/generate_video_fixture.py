"""Generate a four-second synthetic scene with known two-axis movement and an AAC tone.
Requires numpy, Pillow and imageio-ffmpeg. Output directory is the first argument.
"""
import sys
import wave
from pathlib import Path

import imageio_ffmpeg as ffmpeg
import numpy as np
from PIL import Image, ImageDraw

folder = Path(sys.argv[1])
folder.mkdir(parents=True, exist_ok=True)
width, height, fps, count = 360, 640, 30, 120
texture = np.random.default_rng(123).integers(50, 200, (height // 12 + 1, width // 12 + 1), dtype=np.uint8)
texture = np.repeat(np.repeat(texture, 12, axis=0), 12, axis=1)[:height, :width]
scene = Image.fromarray(np.repeat(texture[:, :, None], 3, axis=2))
draw = ImageDraw.Draw(scene)
draw.rectangle((width // 2 - 10, height // 2 - 10, width // 2 + 10, height // 2 + 10), fill=(10, 250, 20))
draw.rectangle((65, 85, 100, 110), fill=(250, 30, 10))
scene = np.array(scene)
samples = np.arange(count * 44100 // fps)
audio = (np.sin(2 * np.pi * 440 * samples / 44100) * 1500).astype('<i2')
tone = folder / 'fixture-tone.wav'
with wave.open(str(tone), 'wb') as output:
    output.setnchannels(1)
    output.setsampwidth(2)
    output.setframerate(44100)
    output.writeframes(audio.tobytes())
writer = ffmpeg.write_frames(str(folder / 'synthetic-shake.mp4'), (width, height), fps=fps,
        codec='libx264', quality=8, macro_block_size=1, audio_path=str(tone), audio_codec='aac')
writer.send(None)
try:
    for i in range(count):
        dx = round(12 * np.sin(2 * np.pi * i / 15))
        dy = round(8 * np.cos(2 * np.pi * i / 20))
        writer.send(np.roll(scene, (dy, dx), axis=(0, 1)).tobytes())
finally:
    writer.close()
print(folder / 'synthetic-shake.mp4')
