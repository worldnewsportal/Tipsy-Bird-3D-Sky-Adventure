import zlib,struct,math,os
def png(path,w,h,px):
    raw=b''.join(b'\x00'+bytes(px[y*w*4:(y+1)*w*4]) for y in range(h))
    def ch(t,d):
        c=struct.pack('>I',len(d))+t+d
        return c+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
    open(path,'wb').write(b'\x89PNG\r\n\x1a\n'+ch(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+ch(b'IDAT',zlib.compress(bytes(raw),9))+ch(b'IEND',b''))
def ell(x,y,cx,cy,rx,ry):
    return (math.hypot((x-cx)/rx,(y-cy)/ry)-1)*min(rx,ry)
SH=[('tail',-.58,.02,.15,.08,(255,143,58)),('body',0,.05,.52,.46,(255,200,48)),('belly',.06,.26,.32,.19,(255,240,196)),
    ('wing',-.2,.1,.26,.16,(255,143,58)),('beak',.6,.03,.16,.1,(255,90,54)),('eye',.24,-.14,.15,.15,(255,255,255)),
    ('pupil',.29,-.14,.075,.075,(43,29,68)),('hl',.32,-.17,.03,.03,(255,255,255))]
def render(w,h,bg,s,rnd=False):
    px=bytearray(w*h*4);m=min(w,h);e=2/m*1.0
    for j in range(h):
        for i in range(w):
            x=(i-w/2)/(m/2)/s;y=(j-h/2)/(m/2)/s
            if bg=='sky':
                t=j/h;c=[int(63+(255-63)*t**1.6),int(176+(237-176)*t**1.6),int(242+(196-242)*t**1.6),255]
            elif bg=='flat': c=[111,211,255,255]
            else: c=[0,0,0,0]
            for n,cx,cy,rx,ry,col in SH:
                d=ell(x,y,cx,cy,rx,ry)
                if n=='belly' and ell(x,y,0,.05,.52,.46)>0: continue
                a=max(0,min(1,.5-d/(e/s)))
                if a>0:
                    ca=c[3]/255;oa=a+ca*(1-a)
                    c=[int((col[k]*a+c[k]*ca*(1-a))/oa) for k in range(3)]+[int(oa*255)]
            if rnd and math.hypot(i-w/2+.5,j-h/2+.5)>w/2: c=[0,0,0,0]
            o=(j*w+i)*4;px[o:o+4]=bytes(c)
    return w,h,px
D={'mdpi':(48,108),'hdpi':(72,162),'xhdpi':(96,216),'xxhdpi':(144,324),'xxxhdpi':(192,432)}
for k,(l,f) in D.items():
    d=f'icons/mipmap-{k}';os.makedirs(d,exist_ok=True)
    png(f'{d}/ic_launcher.png',*render(l,l,'flat',1.45))
    png(f'{d}/ic_launcher_round.png',*render(l,l,'flat',1.45,True))
    png(f'{d}/ic_launcher_foreground.png',*render(f,f,'none',.62))
png('icons/splash.png',*render(540,960,'sky',.5))
