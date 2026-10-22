from pathlib import Path
import subprocess, json, textwrap
from PIL import Image, ImageDraw, ImageFont
OUT=Path('/Users/bobrossjr/Documents/ChatGPT/Meow Assistanrt/video-output/first-video')
OUT.mkdir(parents=True,exist_ok=True)
FONT='/System/Library/Fonts/Supplemental/Arial.ttf'
BOLD='/System/Library/Fonts/Supplemental/Arial Bold.ttf'
scenes=[
('SMALL IDEAS.','REAL APPS.','Build along with Dekay Records.', 'Welcome to Dekay Records. We take everyday ideas and turn them into apps. This channel follows the work, from the first sketch to a working build.'),
('MEET','MEOW ASSISTANT.','An Android companion with personality.', 'One of those projects is Meow Assistant: an Android home screen and AI companion. We will explore its features, one video at a time.'),
('MAKE IT.','BREAK IT. FIX IT.','Ideas → builds → tests → lessons', 'We will also build simple apps, try new tools, and show how we code with AI. When something breaks, we will explain the clues, the fix, and how we checked it.'),
('BEHIND THE APPS:','MEOW SERVER.','Setup · maintenance · troubleshooting', 'You will see how we run and manage Meow, our server: the setup, the maintenance, and the troubleshooting that keeps our projects moving.'),
('NEXT CHAPTER:','AIOS.','Upcoming project · follow the bring-up', 'And coming up is AIOS. We will document that project as it takes shape, sharing the experiments and what we learn along the way.'),
('COME BUILD','WITH US.','dekayrecords.com  /  projects & videos', 'Subscribe and bring an idea. Find our projects and videos at Dekay Records dot com. Our music releases and studio stories live there too.')]
font=lambda n,b=False:ImageFont.truetype(BOLD if b else FONT,n)
parts=[]; subs=[]; clock=0
for i,(a,b,c,voice) in enumerate(scenes):
    txt=OUT/f'{i}.txt'; txt.write_text(voice)
    subprocess.run(['say','-v','Samantha','-r','165','-f',str(txt),'-o',str(OUT/f'{i}.aiff')],check=True)
    dur=float(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration','-of','default=noprint_wrappers=1:nokey=1',str(OUT/f'{i}.aiff')]))+0.7
    im=Image.new('RGB',(1920,1080),'#09151b'); d=ImageDraw.Draw(im)
    d.rectangle((80,80,90,910),fill='#cdff7d')
    d.text((135,86),'DEKAY RECORDS  /  BUILD JOURNAL',font=font(28,True),fill='#cdff7d')
    d.text((135,236),a,font=font(82,True),fill='#f6f3e9')
    d.text((135,338),b,font=font(82,True),fill='#cdff7d')
    d.text((140,466),c,font=font(34),fill='#bdcbd0')
    # Original abstract progress diagram; no app code, simulated UI, or private data.
    for j,label in enumerate(['IDEA','BUILD','LEARN']):
        x=145+j*405
        d.rounded_rectangle((x,580,x+330,688),radius=14,outline='#527078',width=3)
        d.text((x+38,615),label,font=font(30,True),fill='#eef2ec')
        if j<2:d.line((x+342,635,x+392,635),fill='#cdff7d',width=4)
    d.text((140,748),f'0{i+1} / 06',font=font(25),fill='#91a3a9')
    if i==1:d.text((390,748),'Feature demos to follow · development previews vary by build',font=font(25),fill='#91a3a9')
    d.rectangle((0,842,1920,1080),fill='#050b10')
    for k,line in enumerate(textwrap.wrap(voice,100)):
        d.text((140,873+k*43),line,font=font(33),fill='#ffffff')
    im.save(OUT/f'{i}.png')
    part=OUT/f'{i}.mp4'
    subprocess.run(['ffmpeg','-v','error','-y','-loop','1','-i',str(OUT/f'{i}.png'),'-i',str(OUT/f'{i}.aiff'),'-vf',f"fade=t=in:st=0:d=0.3,drawbox=x=0:y=1068:w=iw:h=12:color=0x34504b:t=fill",'-af','apad','-t',str(dur),'-r','24','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-c:a','aac','-b:a','160k',str(part)],check=True)
    parts.append(part);subs.append((clock,clock+dur,voice));clock+=dur
(OUT/'concat.txt').write_text(''.join("file '"+str(p)+"'\n" for p in parts))
subprocess.run(['ffmpeg','-v','error','-y','-f','concat','-safe','0','-i',str(OUT/'concat.txt'),'-c','copy','-movflags','+faststart',str(OUT/'dekay-records-first-video.mp4')],check=True)
def stamp(t):
    n=round(t*1000);return f'{n//3600000:02}:{n//60000%60:02}:{n//1000%60:02},{n%1000:03}'
(OUT/'captions.srt').write_text('\n\n'.join(f'{i+1}\n{stamp(s)} --> {stamp(e)}\n{v}' for i,(s,e,v) in enumerate(subs))+'\n')
(OUT/'narration.txt').write_text('\n\n'.join(s[3] for s in scenes))
print(json.dumps({'video':str(OUT/'dekay-records-first-video.mp4'),'seconds':clock}))
