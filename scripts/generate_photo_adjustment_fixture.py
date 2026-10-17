"""Synthetic inputs for manual RGB levels and mature OpenCV NLM, without private content."""
import argparse
from pathlib import Path
import subprocess
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

p=argparse.ArgumentParser();p.add_argument('output',type=Path);a=p.parse_args()
subprocess.run([sys.executable,str(Path(__file__).with_name('generate_photo_tone_fixture.py')),str(a.output)],check=True)
clean=Image.new('RGB',(960,720),(128,128,128));draw=ImageDraw.Draw(clean)
font_path=Path('C:/Windows/Fonts/arialbd.ttf')
font=ImageFont.truetype(str(font_path),38) if font_path.exists() else ImageFont.load_default(size=38)
for y in range(30,500,78): draw.text((35,y),'NLM TEXT 0123456789',font=font,fill=(50,50,50))
draw.rectangle((730,40,900,230),fill=(200,70,50));draw.rectangle((730,300,900,490),fill=(50,170,90))
clean.save(a.output/'nlm-truth.png')
rng=np.random.default_rng(225617)
noisy=np.clip(np.asarray(clean,dtype=np.float32)+rng.normal(0,8,(720,960,3)),0,255).astype(np.uint8)
image=Image.fromarray(noisy);exif=Image.Exif();exif[271]='Synthetic';exif[272]='Editor fixture';exif[274]=1
image.save(a.output/'nlm.jpg',quality=97,subsampling=0,exif=exif)
large=Image.new('RGB',(3072,4096))
for y in range(0,4096,720):
 for x in range(0,3072,960):large.paste(image,(x,y))
large.save(a.output/'nlm-full.jpg',quality=97,subsampling=0,exif=exif)
print(a.output)
