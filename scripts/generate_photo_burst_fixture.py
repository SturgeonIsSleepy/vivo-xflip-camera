"""Deterministic synthetic JPEG bursts with clean geometric references; no real photos."""
import argparse,json,math
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw,ImageFont

parser=argparse.ArgumentParser()
parser.add_argument('output',type=Path)
args=parser.parse_args()
args.output.mkdir(parents=True,exist_ok=True)
w,h=960,720
rng=np.random.default_rng(2256)
y,x=np.mgrid[:h,:w]
base=np.stack((70+x*0.08+np.zeros_like(y),95+y*0.07+np.zeros_like(x),130+x*0.025+y*0.03),axis=2).clip(0,255).astype(np.uint8)
canvas=Image.fromarray(base)
draw=ImageDraw.Draw(canvas)
font=ImageFont.truetype('C:/Windows/Fonts/arial.ttf',30) if Path('C:/Windows/Fonts/arial.ttf').exists() else ImageFont.load_default(size=30)
for py in range(24,h-70,100):
 for px in range(24,w-180,210):
  draw.text((px,py),'XFlip 0123 ABC',font=font,fill=(235,240,230))
for i in range(100):
 px,py=rng.integers(12,w-32),rng.integers(12,h-32)
 draw.rectangle((int(px),int(py),int(px+12),int(py+12)),fill=tuple(int(v) for v in rng.integers(25,240,3)))
draw.rectangle((40,570,310,685),fill=(120,130,140))
base=np.asarray(canvas).copy()
poses=[(0,0,0),(2.4,-1.7,0.32),(-3.1,2.2,-0.27)]
for scenario in ('good','moving','rotated','unrelated','duplicate','cancel'):
 for i,(dx,dy,degrees) in enumerate(poses):
  angle=math.radians(degrees);c,s=math.cos(angle),math.sin(angle)
  a=np.array([[c,-s,w/2+dx-c*w/2+s*h/2],[s,c,h/2+dy-s*w/2-c*h/2],[0,0,1]])
  inverse=np.linalg.inv(a)
  clean=np.asarray(Image.fromarray(base).transform((w,h),Image.Transform.AFFINE,tuple(inverse[:2].reshape(-1)),Image.Resampling.BICUBIC)).copy()
  if scenario=='moving' and i>0:
   clean[300:390,100+i*170:190+i*170]=[235,35,45]
  if scenario=='unrelated':
   clean=rng.integers(35,220,(h,w,3),dtype=np.uint8)
  Image.fromarray(clean).save(args.output/f'{scenario}-truth-{i}.png')
  noisy=np.clip(clean.astype(float)+rng.normal(0,8,clean.shape),0,255).astype(np.uint8)
  image=Image.fromarray(noisy)
  exif=Image.Exif();exif[271]='Synthetic';exif[272]='XFlip fixture';exif[274]=1
  if scenario=='rotated':
   image=image.transpose(Image.Transpose.ROTATE_90);exif[274]=6
  image.save(args.output/f'{scenario}-{i}.jpg',quality=95,subsampling=0,exif=exif)
 if scenario=='duplicate':
  data=(args.output/f'{scenario}-0.jpg').read_bytes()
  for i in (1,2):(args.output/f'{scenario}-{i}.jpg').write_bytes(data)
(args.output/'fixture.json').write_text(json.dumps({'size':[w,h],'noise_sigma_rgb':8,'poses_dx_dy_degrees':poses,'seed':2256,'synthetic':True},indent=2)+'\n')
print(args.output)
