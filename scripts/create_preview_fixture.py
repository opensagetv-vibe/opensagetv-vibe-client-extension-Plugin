#!/usr/bin/env python3
"""VCE-017 owned first-play preview/root fixture; never edit an existing tree."""
import argparse,hashlib,json,os,subprocess,shutil
from pathlib import Path
from xml.sax.saxutils import quoteattr

def create(output,work,ffmpeg,dvdauthor):
    output=Path(output).resolve();work=Path(work).resolve()
    if output.exists() or work.exists():raise ValueError('New output and work trees required')
    if output==work or output in work.parents or work in output.parents:raise ValueError('Separate output/work required')
    work.mkdir(parents=True);output.parent.mkdir(parents=True,exist_ok=True)
    logs=[]
    def run(cmd):
        done=subprocess.run(cmd,env=dict(os.environ,VIDEO_FORMAT='NTSC'),stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
        logs.append(done.stdout)
        if done.returncode:raise RuntimeError(done.stdout[-5000:])
    for name,color,seconds,tone in [('preview','red',30,440),('root','blue',8,660),('main','green',60,880)]:
        run([ffmpeg,'-hide_banner','-loglevel','warning','-f','lavfi','-i',f'color=c={color}:s=720x480:r=30000/1001',
             '-f','lavfi','-i',f'sine=frequency={tone}:sample_rate=48000','-t',str(seconds),'-vf',
             "drawtext=fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:text='VCE017 "+name.upper()+
             "':fontcolor=white:fontsize=46:x=35:y=160,drawtext=fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf:text='%{pts\\:hms}':fontcolor=white:fontsize=34:x=35:y=250",
             '-target','ntsc-dvd','-aspect','16:9','-b:v','2500k','-g','15','-c:a','ac3','-ac','2','-b:a','192k',str(work/(name+'.vob'))])
    video='<video format="ntsc" aspect="16:9"/><audio format="ac3" lang="en"/>'
    # XML button commands alone do not create a usable DVD highlight. Author
    # real HLI/SPU button metadata before allowing this fixture to gate Play.
    convert=shutil.which('magick') or shutil.which('convert')
    if not convert:raise RuntimeError('ImageMagick required for real menu button')
    for name,color in [('highlight','yellow'),('select','green')]:
        run([convert,'-size','720x480','xc:none','+antialias','-fill','none','-stroke',color,
             '-strokewidth','4','-draw','rectangle 20,130 699,229',str(work/(name+'.png'))])
    spu='<subpictures format="NTSC"><stream><spu start="00:00:00.00" highlight='+quoteattr(str(work/'highlight.png'))+' select='+quoteattr(str(work/'select.png'))+' transparent="000000" force="yes"><button name="play" x0="20" y0="130" x1="700" y1="230" up="play" down="play" left="play" right="play"/></spu></stream></subpictures>'
    (work/'menu.xml').write_text(spu,encoding='utf-8')
    with (work/'root.vob').open('rb') as source,(work/'root-menu.vob').open('wb') as target:
        done=subprocess.run(['spumux','-m','dvd',str(work/'menu.xml')],stdin=source,stdout=target,stderr=subprocess.PIPE)
    logs.append(done.stderr.decode(errors='replace'))
    if done.returncode:raise RuntimeError(logs[-1])
    vob=lambda name:'<vob file='+quoteattr(str(work/(name+'.vob')))+'/>'
    xml='<dvdauthor dest='+quoteattr(str(output))+'><vmgm><fpc>jump title 1;</fpc></vmgm><titleset>'
    # DVD SPRM8 stores the button number in bits 10..15, not as a raw 1.
    # Author a valid default so the test cannot mask broken menu metadata by
    # selecting a button through a separate commissioning-only operation.
    xml+='<menus lang="en">'+video+'<pgc entry="root"><pre>button=1024;</pre>'+vob('root-menu')+'<post>jump cell 1;</post><button name="play">jump title 2;</button></pgc></menus>'
    xml+='<titles>'+video+'<pgc>'+vob('preview')+'<post>call menu entry root;</post></pgc><pgc>'+vob('main')+'<post>call menu entry root;</post></pgc></titles></titleset></dvdauthor>'
    author=work/'author.xml';author.write_text(xml,encoding='utf-8');run([dvdauthor,'-x',str(author)])
    report={'task':'VCE-017','schemaVersion':3,'firstPlay':'title1/red/PREVIEW/30s','root':'blue/ROOT/loop8s/real-default-button1','main':'title2/green/MAIN/60s',
            'files':{f.relative_to(output).as_posix():hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted((output/'VIDEO_TS').iterdir()) if f.is_file()}}
    (output/'fixture.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');(work/'author.log').write_text('\n'.join(logs),encoding='utf-8')
    return report

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--output',required=True);p.add_argument('--work',required=True)
    p.add_argument('--ffmpeg',required=True);p.add_argument('--dvdauthor',default='dvdauthor');a=p.parse_args()
    print(json.dumps(create(a.output,a.work,a.ffmpeg,a.dvdauthor),indent=2))
