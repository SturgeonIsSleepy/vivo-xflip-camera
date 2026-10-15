"""Deterministic tone/detail fixtures with no private content."""
import argparse
from pathlib import Path
import numpy as np
from PIL import Image,ImageDraw,ImageFont,ImageFilter
p=argparse.ArgumentParser();p.add_argument('output',type=Path);args=p.parse_args();args.output.mkdir(parents=True,exist_ok=True)
w,h=960,720;rng=np.random.default_rng(2257);yy,xx=np.mgrid[:h,:w]
font=ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf',42) if Path('C:/Windows/Fonts/arialbd.ttf').exists() else ImageFont.load_default(size=42)
for scenario in ('levels','narrow','detail','hdr','flat','color','rotated','cancel'):
 if scenario=='levels':
  gray=np.tile(np.linspace(40,215,w,dtype=np.uint8),(h,1));data=np.repeat(gray[:,:,None],3,axis=2)
 elif scenario=='hdr':
  gray=np.where(xx<w//2,30+8*np.sin(xx/13)+5*np.sin(yy/9),225+10*np.sin(xx/13)+5*np.sin(yy/9));gray=np.clip(gray,0,255).astype(np.uint8)
  gray[:,:12]=0;gray[:,-12:]=255;data=np.repeat(gray[:,:,None],3,axis=2)
 elif scenario=='flat':
  data=np.clip(120+rng.normal(0,4,(h,w,1)),0,255).astype(np.uint8);data=np.repeat(data,3,axis=2)
 elif scenario=='color':
  data=np.full((h,w,3),128,np.uint8)
  colors=[(190,90,50),(50,185,90),(60,80,190),(150,150,150),(70,70,70),(220,220,220)]
  for i,c in enumerate(colors):data[(i//3)*360:(i//3+1)*360,(i%3)*320:(i%3+1)*320]=c
 else:
  im=Image.new('RGB',(w,h),(145,145,145));d=ImageDraw.Draw(im)
  for y in range(30,h-40,90):d.text((35,y),'XFLIP TEXT 0123456789',font=font,fill=(95,95,95))
  data=np.asarray(im.filter(ImageFilter.GaussianBlur(0.65))).copy();data=np.clip(data.astype(float)+rng.normal(0,1,data.shape),0,255).astype(np.uint8)
 im=Image.fromarray(data);im.save(args.output/f'{scenario}-truth.png')
 exif=Image.Exif();exif[271]='Synthetic';exif[272]='Tone fixture';exif[274]=1
 if scenario=='rotated':im=im.transpose(Image.Transpose.ROTATE_90);exif[274]=6
 im.save(args.output/f'{scenario}.jpg',quality=97,subsampling=0,exif=exif)
print(args.output)
