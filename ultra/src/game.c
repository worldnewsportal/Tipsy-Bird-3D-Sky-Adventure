/* Tipsy Bird Ultra v2 — pure C + raylib 5.5, NativeActivity, 100% offline
   Exact-clone of original game data (8 maps / 35 skins / keys / daily / 25-coin revive)
   + performance pack (LOD, cull, graded particles, no MSAA) + relative steering
   + full Arabic UI (Amiri font, shaped strings) + English fallback toggle. */
#include "raylib.h"
#define PLATFORM_ANDROID 1
#include <android/native_activity.h>
#include <android/log.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <time.h>
#include <sys/stat.h>

#include "data_gen.h"
#include "ar_font.h"
#include "ar_strings.h"

/* ---------------- config (original physics values) ---------------- */
#define OP        6.0f    /* original obstacle spacing */
#define SPD0      15.0f
#define SPD1      34.0f
#define G         32.0f
#define FLAPV     10.5f
#define BR        0.55f
#define CEILY     20.0f
#define GROUNDY   -4.5f
#define CEILY2    7.2f

#define NMAPS     8
#define NSKINS    35
#define NMsn      5
#define PMAX      260

#define ST_SPLASH 0
#define ST_MENU   1
#define ST_WORLDS 2
#define ST_READY  3
#define ST_PLAY   4
#define ST_PAUSE  5
#define ST_DYING  6
#define ST_OVER   7
#define ST_SHOP   8
#define ST_SET    9
#define ST_MSN    10
#define ST_DAILY  11

/* button ids */
enum { B_NONE=0,B_PLAY=1,B_WORLDS,B_SHOP,B_MSN,B_SET,B_BACK,B_RESUME,B_MENU,
       B_RETRY,B_REVIVE,B_GIVEUP,B_SND,B_MUS,B_QUAL,B_SHAKE,B_WIPE,B_PAUSE,
       B_SENP,B_FPST,B_LANG,B_MAPSEL=30,B_MAPOPEN=40,B_DAILY=60,B_CLAIM=61,
       B_SKIN=100 /* +0..34 */, B_MSNCLAIM=140 };

typedef struct { Color tc,sc,ic; } Theme;

/* colors (original palette) */
#define CREAM  (Color){255,246,220,255}
#define INK    (Color){58,42,92,255}
#define ORG    (Color){255,122,47,255}
#define ORGD   (Color){196,72,15,255}
#define TEAL   (Color){39,193,176,255}
#define AURUM  (Color){255,210,63,255}
#define ROSEC  (Color){255,126,182,255}
#define WHT    (Color){255,255,255,255}

static inline Color U(unsigned v){ return (Color){(unsigned char)(v>>16),(unsigned char)(v>>8),(unsigned char)v,255}; }
static inline Color Ua(unsigned v,int a){ return (Color){(unsigned char)(v>>16),(unsigned char)(v>>8),(unsigned char)v,(unsigned char)a}; }

/* ---------------- rng ---------------- */
static unsigned rs_=0x9e3779b9u;
static inline unsigned rxs(){ rs_^=rs_<<13; rs_^=rs_>>17; rs_^=rs_<<5; return rs_; }
static inline float rr(float a,float b){ return a+(b-a)*(float)(rxs()&0xffffff)/16777216.0f; }
static inline int ri(int a,int b){ return a+(int)(rxs()%(unsigned)(b-a+1)); }
static inline float clampf(float v,float a,float b){ return v<a?a:(v>b?b:v); }
static inline float lerpf(float a,float t,float b){ return a+(b-a)*t; }
static inline Color LC(Color a, Color b, float t){
    t=clampf(t,0,1);
    return (Color){(unsigned char)lerpf(a.r,t,b.r),(unsigned char)lerpf(a.g,t,b.g),
                   (unsigned char)lerpf(a.b,t,b.b),(unsigned char)lerpf(a.a,t,b.a)};
}

/* ---------------- save (v3 : TBU3) ---------------- */
#define SV_MAGIC 0x54425533u
typedef struct {
    unsigned magic;
    int best;            /* global best (also used as primary score) */
    int coins, keys;
    int skin;
    unsigned short ownedMask[3];  /* 35 skins -> bits */
    unsigned char mapOpen;        /* bitmask maps 0..7 (0..2 always open) */
    int mapBest[NMAPS];
    int qual, shakeOn, sfxV, musV, sens, showFps, lang;   /* lang 0=AR 1=EN */
    int runs; long long totCoins;
    int msn[NMsn*3];     /* x3: goal,kind,prog */
    int msnDone[NMsn];
    int lastDay;         /* yyyymmdd of last daily chest */
    int dailyStreak;
    unsigned magic2;
} Sv;
static Sv S;

static const char* pkgPath(){
    static char path[128]={0};
    if(!path[0]){
        FILE*f=fopen("/proc/self/cmdline","rb");
        char pkg[96]={0}; if(f){ size_t n=fread(pkg,1,95,f); (void)n; fclose(f); }
        snprintf(path,sizeof path,"/data/data/%s/files",pkg[0]?pkg:"com.tipsybird.raylib");
    }
    return path;
}
static void svLoad(){
    memset(&S,0,sizeof S);
    S.magic=SV_MAGIC; S.magic2=SV_MAGIC;
    S.sens=100; S.sfxV=100; S.musV=70; S.qual=2; S.shakeOn=1; S.showFps=0; S.lang=0;
    S.mapOpen=0x07;   /* first 3 maps always open (matches original) */
    S.skin=0; S.best=0; S.coins=25; S.keys=1;
    char p[192]; snprintf(p,sizeof p,"%s/tipsy_ultra.bin",pkgPath());
    FILE*f=fopen(p,"rb");
    if(!f) return;
    Sv t; memset(&t,0,sizeof t);
    size_t n=fread(&t,1,sizeof t,f); fclose(f);
    if(n<sizeof t || t.magic!=SV_MAGIC || t.magic2!=SV_MAGIC) return; /* corrupt or old v2 */
    S=t;
    if(S.sens<=0) S.sens=100;
    if(S.qual<0||S.qual>2) S.qual=2;
    if(S.mapBest[7]<0) S.mapBest[7]=0;
    S.mapOpen|=0x07;
        if(S.coins<0) S.coins=0;
    if(S.keys<0) S.keys=0;
}
static void svSave(){
    S.magic=SV_MAGIC; S.magic2=SV_MAGIC;
    char p[192]; snprintf(p,sizeof p,"%s",pkgPath());
    mkdir(p,0755);
    snprintf(p,sizeof p,"%s/tipsy_ultra.bin",pkgPath());
    FILE*f=fopen(p,"wb");
    if(!f) return;
    fwrite(&S,1,sizeof S,f); fclose(f);
}
static void svLoadDefaults(){ S.magic=SV_MAGIC; S.magic2=SV_MAGIC; S.sens=100; S.sfxV=100; S.musV=70; S.qual=2; S.shakeOn=1; S.showFps=0; S.lang=0; S.mapOpen=0x07; S.skin=0; S.best=0; S.coins=25; S.keys=1; }
static void svWipe(){ memset(&S,0,sizeof S); svLoadDefaults(); svSave(); }

static int todayInt(){
    time_t t=time(NULL); struct tm*tm=localtime(&t);
    return (tm->tm_year+1900)*10000+(tm->tm_mon+1)*100+tm->tm_mday;
}
static int days(int y,int m,int d){ y-=m<3; return (y*365+y/4-y/100+y/400)+(367*(m-3))/12+d; }
static int dayDiff(int ymd1,int ymd2){ /* returns days between approx (for daily cooldown) */
    struct {int y,m,d;} a,b; a.d=ymd1%100; a.m=(ymd1/100)%100; a.y=ymd1/10000;
    b.d=ymd2%100; b.m=(ymd2/100)%100; b.y=ymd2/10000;
    return days(b.y,b.m,b.d)-days(a.y,a.m,a.d);
}

static inline int owned(int i){ return (S.ownedMask[i>>4]>>(i&15))&1; }
static inline void setOwned(int i){ S.ownedMask[i>>4]|=(unsigned short)(1u<<(i&15)); }
static inline int mapOpen(int i){ return (S.mapOpen>>i)&1; }
static inline void setMapOpen(int i){ S.mapOpen|=(unsigned char)(1u<<i); }

/* toast */
static const char* toastTxt=0; static float toastT=0;

/* ---------------- audio ---------------- */
#define SR 22050
static AudioStream au;
static float musPhase=0;
static int curMus=0, musStep=0;
static volatile float sfxV=1.0f, musV=0.28f;

typedef struct { float f; int wv; float dur; float t; float vol; int on; } Sfx;
#define NSFX 12
static Sfx sx[NSFX];
static int sxHead=0;

static float sfxToneFreq_(int semi){ return 440.0f*powf(2.0f,semi/12.0f); }
static float tone(float tp,int wv){
    switch(wv){
        case 0: return sinf(2*PI*tp);
        case 1: return 2*(tp-floorf(tp+0.5f));
        default: return (tp-floorf(tp))<0.5f?1.0f:-1.0f;
    }
}
static const short MUS[4][16]={
 {0,4,7,12,7,4,0,-5,0,4,7,12,16,12,7,4},
 {0,3,7,10,7,3,-2,-5,-2,3,7,10,12,10,7,3},
 {0,5,5,7,7,10,12,10,7,7,5,5,3,3,0,0},
 {0,0,7,7,3,3,10,10,5,5,12,12,7,7,3,3}};
static const short BASSN[4]={-24,-27,-29,-22};
static void sfx(float f,int wv,float dur,float vol){
    Sfx*x=&sx[sxHead]; sxHead=(sxHead+1)%NSFX;
    x->f=f; x->wv=wv; x->dur=dur; x->t=0; x->vol=vol; x->on=1;
}

static void auCB(void *buf, unsigned frames){
    short*o=(short*)buf;
    for(unsigned i=0;i<frames;i++){
        float s=0;
        /* music: 16-step pattern per track */
        float spb=0.125f; /* sec per step */
        musPhase+=1.0f/SR;
        if(musPhase>=spb){ musPhase-=spb; musStep=(musStep+1)&15; }
        int m=curMus;
        int semi=MUS[m][musStep];
        if(semi>=0){
            float f=sfxToneFreq_(semi);
            s+=0.5f*musV*(float)sin(2*M_PI*f*musPhase);
            /* bass every 8 */
            if((musStep&7)==0){
                int bs=BASSN[m];
                float bf=sfxToneFreq_(bs-12);
                s+=0.5f*musV*(float)sin(2*M_PI*bf*musPhase)*0.8f;
            }
        }
        /* sfx mixer */
        for(int j=0;j<NSFX;j++){
            Sfx*x=&sx[j];
            if(!x->on)continue;
            x->t+=1.0f/SR;
            float k=x->t/x->dur;
            if(k>=1){x->on=0;continue;}
            float env=1.0f-k;
            s+=x->vol*env*tone(x->f*x->t,x->wv)*0.6f*sfxV;
        }
        s=clampf(s*0.35f,-1,1);
        o[i*2]=(short)(s*32000); o[i*2+1]=(short)(s*32000);
    }
}
/* ---------------- world state: exact-clone of original MAPS applied in 3D ---------------- */
static int curMap=0, skyPhase=0; /* skyPhase 0=day 1=dusk 2=night, advances by score */
static M8 MP;      /* current map data copy */
static const M8* MPX; /* pointer into MAPS8 */

/* world entities */
#define OBMAX 40
#define CBMAX 130
#define PWMAX 8
typedef struct { int kind; Vector3 pos; Vector3 rot; float spin; float len; int dead; } Obs;
typedef struct { Vector3 pos; int dead; float ph; } Coin;
typedef struct { int kind; Vector3 pos; int dead; } Pw; /* 0 shield 1 magnet 2 slow */

static Obs obs[OBMAX]; static int nObs=0;
static Coin coinsA[CBMAX]; static int nCoins=0;
static Pw pws[PWMAX]; static int nPw=0;

/* bird */
typedef struct { Vector3 pos, vel; float flap, wing, tilt; int alive; } Bird;
static Bird bird;
static float spd, dist, score, scoreF;
static float magnetT, shieldT, slowT, coinCombo, comboT;
static int pendingRevive;

/* particles (graded by quality) */
typedef struct { Vector3 p,v; float life,maxLife,size; Color c; int grav; } Part;
static Part parts[PMAX]; static int nParts=0;
static void emit(Vector3 p, Color c, float n, float sp){
    int qmul = S.qual==0?0.4f:(S.qual==1?0.7f:1);
    int cnt=(int)(n*qmul); if(cnt<1)cnt=1;
    for(int i=0;i<cnt && nParts<PMAX;i++){
        parts[nParts].p=p;
        parts[nParts].v=(Vector3){rr(-sp,sp),rr(0,sp),rr(-sp,sp)};
        parts[nParts].maxLife=rr(.45f,.9f); parts[nParts].life=0;
        parts[nParts].size=rr(.12f,.3f)*(S.qual==2?1.0f:(S.qual==1?0.85f:0.7f));
        parts[nParts].c=c; parts[nParts].grav=1; nParts++;
    }
}
static void partsUpdate(float dt){
    for(int i=0;i<nParts;i++){
        Part*x=&parts[i]; x->life+=dt;
        if(x->life>=x->maxLife){ *x=parts[nParts-1]; nParts--; i--; continue; }
        x->p.x+=x->v.x*dt; x->p.y+=x->v.y*dt; x->p.z+=x->v.z*dt;
        if(x->grav) x->v.y-=14*dt;
    }
}
static void partsDraw(){
    if(S.qual==0) return;
    for(int i=0;i<nParts;i++){
        Part*x=&parts[i];
        float k=1-x->life/x->maxLife;
        DrawSphere(x->p, x->size*k, Ua((unsigned)(x->c.r<<16|x->c.g<<8|x->c.b),(int)(255*k)));
    }
}
/* map ambient VFX */
static float effT=0;
static void mapEffUpdate(float dt, Vector3 bir){
    effT+=dt;
    float acc = S.qual==0?0.15f:(S.qual==1?0.5f:1.0f);
    if(!MP.eff||!MP.eff[0]) return;
    if(effT>0.06f){
        effT=0;
        if(!strcmp(MP.eff,"ember")) { Vector3 p={bir.x+rr(-8,8),GROUNDY+rr(0.2f,3),bir.z+rr(6,12)}; emit(p,U(0xff7a3a),1.0f*acc*2,1.2f); parts[nParts-1].grav=0; }
        else if(!strcmp(MP.eff,"ocean")){ Vector3 p={bir.x+rr(-10,10),rr(GROUNDY,0),bir.z+rr(8,14)}; emit(p,U(0x8ff2e0),0.8f*acc*2,0.6f); parts[nParts-1].grav=0; }
        else if(!strcmp(MP.eff,"space")){ if((rand()%3)==0){ Vector3 p={bir.x+rr(-14,14),rr(-2,12),bir.z+rr(10,20)}; emit(p,WHT,0.5f*acc*2,0.3f); parts[nParts-1].grav=0; } }
        else if(!strcmp(MP.eff,"jungle")){ if((rand()%2)==0){ Vector3 p={bir.x+rr(-10,10),rr(2,10),bir.z+rr(8,14)}; emit(p,U(0x5fbf4f),0.6f*acc*2,0.8f); parts[nParts-1].grav=1; } }
        else if(!strcmp(MP.eff,"storm")){ Vector3 p={bir.x+rr(-10,10),rr(4,12),bir.z+rr(8,14)}; emit(p,U(0xaab2d0),1.2f*acc*2,2.5f); parts[nParts-1].grav=1; }
    }
}
static void partsEffAir(float dt){
    for(int i=0;i<nParts;i++) if(!parts[i].grav) parts[i].v.y+=2.5f*dt; /* embers rise */
    (void)dt;
}

/* sky/fog per map + phase lerp */
static Color skyTop, skyMid, skyBot, fogC;
static void applyMapColors(){
    MPX=&MAPS8[curMap]; MP=*MPX;
    int ph=skyPhase; if(ph<0)ph=0; if(ph>2)ph=2;
    Color c0,c1,c2;
    c0=U(MP.sky[ph][0]); c1=U(MP.sky[ph][1]); c2=U(MP.sky[ph][2]);
    skyTop=c0; skyMid=c1; skyBot=c2;
    fogC=U(MP.fog[ph]);
}
/* gradient sky drawn as big vertical gradient via 3 quads (cheap, no shader) */
static void drawSkyGrad(){
    Color a=LC(skyTop,skyMid,0.0f), b=LC(skyMid,skyBot,0.0f);
    /* full-screen gradient: draw 3 vertical strips in screen space before 3D */
    int H=GetScreenHeight(), W=GetScreenWidth();
    DrawRectangle(0,0,W,H/2,a);
    DrawRectangle(0,H/2,W,H/2,LC(a,b,0.85f));
}

/* original GROUND=-4.5 CEIL=7.2 world bounds used in play physics */
static inline float clampPlay(float y){ return clampf(y,GROUNDY,CEILY2); }

/* ---------------- obstacles (kind 0 pillar 1 slider 2 rotor 3 ring) ---------------- */
static void spawnObs(float z){
    if(nObs>=OBMAX) return;
    Obs*o=&obs[nObs++];
    memset(o,0,sizeof(*o));
    o->kind=ri(0, curMap<3?1:3);
    o->pos=(Vector3){rr(-3.5,3.5),rr(GROUNDY+2,CEILY2-1),z};
    if(o->kind==0){ o->len=rr(2.5,5.5); o->pos.y=clampPlay(rr(GROUNDY+2,CEILY2-2)); }
    else if(o->kind==1){ o->len=rr(2,6); o->pos.x=rr(-3,3); o->spin=rr(0,PI*2); }
    else if(o->kind==2){ o->len=2.2f; o->spin=rr(0,PI*2); }
    else if(o->kind==3){ o->len=1.9f; /* ring */ }
}
static void spawnCoins(float z){
    int n=ri(3,6);
    float cy=clampPlay(rr(GROUNDY+1.5f,CEILY2-1)), cx=rr(-3,3);
    for(int i=0;i<n && nCoins<CBMAX;i++){
        coinsA[nCoins].pos=(Vector3){cx+rr(-0.4f,0.4f),cy+((i%2)?0.5f:-0.5f)*0.4f,z+i*1.2f};
        coinsA[nCoins].dead=0; coinsA[nCoins].ph=rr(0,PI*2); nCoins++;
    }
}
static void spawnPw(float z){
    if(nPw>=PWMAX) return;
    Pw*w=&pws[nPw++];
    w->kind=ri(0,2); w->dead=0;
    w->pos=(Vector3){rr(-3,3),clampPlay(rr(GROUNDY+2,CEILY2-1.5f)),z};
}

/* world generation driver */
static float nextObsZ=24;
static void updateWorld(float dt){
    float sp=spd*(MP.sp>0?MP.sp:1);
    if(slowT>0){ sp*=0.55f; slowT-=dt; }
    if(shieldT>0) shieldT-=dt;
    if(magnetT>0) magnetT-=dt;
    if(comboT>0){ comboT-=dt; if(comboT<=0) coinCombo=0; }
    bird.pos.z-=sp*dt;
    dist+=sp*dt;
    scoreF=dist/OP*MP.gr;
    score=scoreF;
    /* skyPhase advance: every 15 score-loop day→dusk→night→(loop) */
    int np=(int)(score/15.0f)%(3*15);
    skyPhase=(int)(score/15.0f);
    skyPhase=skyPhase%3;
    (void)np;

    /* spawn ahead */
    while(nextObsZ>bird.pos.z-40){
        spawnObs(nextObsZ);
        if(ri(0,9)<7) spawnCoins(nextObsZ+rr(2,4));
        if(ri(0,9)<1) spawnPw(nextObsZ+rr(3,5));
        nextObsZ-=OP;
    }
    /* despawn behind */
    for(int i=0;i<nObs;i++){
        if(obs[i].pos.z>bird.pos.z+8){ obs[i]=obs[nObs-1]; nObs--; i--; }
        else if(obs[i].kind==1){ obs[i].spin+=dt*1.6f; obs[i].pos.x=obs[i].pos.x; }
        else if(obs[i].kind==2){ obs[i].spin+=dt*2.2f; }
    }
    for(int i=0;i<nCoins;i++){
        if(coinsA[i].pos.z>bird.pos.z+6){ coinsA[i]=coinsA[nCoins-1]; nCoins--; i--; continue; }
        coinsA[i].ph+=dt*6;
        if(magnetT>0){
            float dzz=coinsA[i].pos.z-bird.pos.z, dxx=coinsA[i].pos.x-bird.pos.x, dyy=coinsA[i].pos.y-bird.pos.y;
            if(dzz*dzz+dxx*dxx+dyy*dyy<36){
                coinsA[i].pos.x=lerpf(coinsA[i].pos.x,bird.pos.x,dt*8);
                coinsA[i].pos.y=lerpf(coinsA[i].pos.y,bird.pos.y,dt*8);
                coinsA[i].pos.z=lerpf(coinsA[i].pos.z,bird.pos.z,dt*8);
            }
        }
    }
    for(int i=0;i<nPw;i++){
        if(pws[i].pos.z>bird.pos.z+6){ pws[i]=pws[nPw-1]; nPw--; i--; }
    }
    mapEffUpdate(dt,bird.pos);
    partsUpdate(dt);
    partsEffAir(dt);
}
static float steerPrevX=0;
static void updateBird(float dt){
    bird.vel.y-=G*dt;
    bird.pos.y+=bird.vel.y*dt;
    if(bird.pos.y<GROUNDY){ bird.pos.y=GROUNDY; bird.vel.y=0; }
    if(bird.pos.y>CEILY2){ bird.pos.y=CEILY2; if(bird.vel.y>0) bird.vel.y=0; }
    bird.flap=lerpf(bird.flap,0,dt*7);
    bird.tilt=clampf(bird.vel.y*0.045f,-0.6f,0.6f);
}

/* ---------------- collisions (original: bubble 0.95 radius near-miss body 0.55) ---------------- */
static int hitTest(){
    float R=BR;
    /* shield bubble absorbs one hit then poofs */
    Vector3 b=bird.pos;
    for(int i=0;i<nObs;i++){
        Obs*o=&obs[i];
        float dz=fabsf(o->pos.z-b.z);
        if(dz>2.2f) continue;
        if(o->kind==0){ /* pillar: vertical box */
            float dx=fabsf(o->pos.x-b.x);
            float dymin=o->pos.y-o->len/2, dymax=o->pos.y+o->len/2;
            if(dx<0.9f+R && b.y>dymin-R && b.y<dymax+R) return i;
        } else if(o->kind==1){ /* horizontal slider */
            float dx=fabsf(o->pos.x+cosf(o->spin)*1.2f-b.x);
            if(dx<1.4f+R && fabsf(o->pos.y-b.y)<0.8f+R) return i;
        } else if(o->kind==2){ /* rotor arms */
            float cx2=b.x-o->pos.x, cy2=b.y-o->pos.y;
            float rr2=cx2*cx2+cy2*cy2;
            if(rr2<(2.2f+R)*(2.2f+R) && rr2>(0.4f-R)*(0.4f-R)) return i;
        } else { /* ring: pass through center or hit rim */
            float cx2=b.x-o->pos.x, cy2=b.y-o->pos.y;
            float rd=sqrtf(cx2*cx2+cy2*cy2);
            if(rd<1.9f-0.6f-R || rd>1.9f+R) return i;
            if(rd<1.9f-0.6f-R+0.001f){/* inner pass */}
        }
    }
    for(int i=0;i<nCoins;i++){
        Coin*c=&coinsA[i];
        if(c->dead) continue;
        float dx=c->pos.x-b.x, dy=c->pos.y-b.y, dz=c->pos.z-b.z;
        if(dx*dx+dy*dy+dz*dz<1.0f){
            c->dead=1;
            S.coins++; S.totCoins++;
            coinCombo++; comboT=2.2f;
            if(coinCombo>=3){ S.coins+=3; emit(c->pos,AURUM,6,2); coinCombo=0;
                sfx(880,0,.18f,.5f); sfx(1320,0,.22f,.4f);
            } else sfx(1046,0,.11f,.45f);
            emit(c->pos,AURUM,5,2.5f);
        }
    }
    for(int i=0;i<nPw;i++){
        Pw*w=&pws[i]; if(w->dead)continue;
        float dx=w->pos.x-b.x, dy=w->pos.y-b.y, dz=w->pos.z-b.z;
        if(dx*dx+dy*dy+dz*dz<1.44f){
            w->dead=1;
            if(w->kind==0){ shieldT=8; toastTxt=A_1_SHIELD; toastT=2; }
            else if(w->kind==1){ magnetT=7; toastTxt=A_1_MAGNET; toastT=2; }
            else { slowT=5; toastTxt=A_1_SLOW; toastT=2; }
            sfx(660,0,.3f,.5f); sfx(990,0,.35f,.4f);
            emit(w->pos,TEAL,14,3);
        }
    }
    return -1;
}
/* ---------------- 3D rendering (original colors from MAPS8) ---------------- */
#include "rlgl.h"
static Camera3D cam={0};
static float shakeAmp=0;
static Font arF; static int arLoaded=0;
static int uiW=0, uiH=0;

/* LOD helper: quality-based segment counts */
static inline int lodSeg(){ return S.qual==0?6:(S.qual==1?10:16); }
static inline int lodSegS(){ return S.qual==0?4:(S.qual==1?6:8); }
static void DrawCyl0(Vector3 p,float r,float h,int seg,Color c){ DrawCylinder(p,r,0.0f,h,seg,c); }
static float rxF(){ return rr(0,PI*2); }

/* skin access with original formatting */
static inline int skinQ(int i){ return SKS[i].q; }
static inline int gated(int i, int mapBest){
    if(SKS[i].m>0 && SKS[i].m < NMAPS) return !(S.mapBest[SKS[i].m]>=SKS[i].mg) ;
    return 0;
}

/* draw full bird like original makeBird(): body/belly/eyes3/cheeks/tailfins/crown/feet/wings + 5 acc */
static void drawBirdSkin(Vector3 pos, int s, float flap, float tiltZ){
    const SKN*sk=&SKS[s];
    Color cb=U(sk->b), co=U(sk->o), cl=U(sk->l), ck=U(sk->k);
    if(sk->e) { /* emissive-ish: brighten body */ cb=LC(cb,WHT,0.12f); }
    lodSeg(); lodSegS();
    rlPushMatrix();
    rlTranslatef(pos.x,pos.y,pos.z);
    rlRotatef(tiltZ*57.3f,0,0,1);
    /* body */
    DrawSphere((Vector3){0,0,0},0.55f,cb);
    /* belly (lighter, offset down) */
    if(S.qual>=1) DrawSphere((Vector3){0,-0.18f,0.06f},0.42f,cl);
    /* head+eyes (side offsets) */
    DrawSphere((Vector3){0,0.22f,-0.12f},0.42f,cb);
    DrawSphere((Vector3){0.18f,0.3f,-0.4f},0.1f,WHT);   /* eye white */
    DrawSphere((Vector3){0.18f,0.3f,-0.46f},0.05f,INK); /* pupil */
    if(S.qual>=1) DrawSphere((Vector3){-0.18f,0.3f,-0.4f},0.1f,WHT), DrawSphere((Vector3){-0.18f,0.3f,-0.46f},0.05f,INK);
    /* beak: 2 cones (top, bottom) */
    rlPushMatrix();
    rlTranslatef(0,0.24f,-0.62f); rlRotatef(-90,1,0,0);
    DrawCylinderEx((Vector3){0,0,0},(Vector3){0,0,-0.3f},0.02f,0.14f,4,ck);
    rlPopMatrix();
    rlPushMatrix();
    rlTranslatef(0,0.1f,-0.58f); rlRotatef(-90,1,0,0);
    DrawCylinderEx((Vector3){0,0,0},(Vector3){0,0,-0.22f},0.02f,0.11f,4,co);
    rlPopMatrix();
    /* wings (2 side, flapping around x-axis) */
    for(int sd=-1;sd<=1;sd+=2){
        rlPushMatrix();
        rlTranslatef(sd*0.32f,0.05f,0);
        rlRotatef(sd*(flap*70.0f-20.0f),0,0,1);
        DrawCube((Vector3){sd*0.38f,0,0},0.75f,0.06f,0.42f,co);
        rlPopMatrix();
    }
    /* tail fin (3 tiny cubes fanned) */
    for(int i=-1;i<=1;i++){
        DrawCube((Vector3){0,0.1f+0.1f*i,0.5f},0.08f,0.06f,0.4f,co);
    }
    /* crest w/ 3 circles top */
    DrawSphere((Vector3){0,0.5f,-0.05f},0.09f,ck);
    if(S.qual>=1) DrawSphere((Vector3){0.12f,0.58f,-0.05f},0.07f,ck), DrawSphere((Vector3){-0.12f,0.58f,-0.05f},0.07f,ck);
    /* feet (2 tiny cylinders down) */
    if(S.qual>=1) DrawCylinder((Vector3){0.12f,-0.4f,0},0.03f,0.03f,0.3f,4,ORG);
    if(S.qual>=1) DrawCylinder((Vector3){-0.12f,-0.4f,0},0.03f,0.03f,0.3f,4,ORG);
    /* accessories: 0 none 1 crown 2 bandana 3 antenna 4 halo */
    if(sk->acc==1){ /* gold crown */
        DrawCylinder((Vector3){0,0.62f,-0.05f},0.3f,0.34f,0.14f,8,AURUM);
        for(int i=0;i<3;i++){
            float ang=(float)i*2.09f+rxF();
            DrawCyl0((Vector3){cosf(ang)*0.25f,0.78f,-0.05f+sinf(ang)*0.25f},0.1f,0.2f,6,AURUM);
        }
    } else if(sk->acc==2){ /* red bandana */
        DrawCylinder((Vector3){0,0.3f,0},0.44f,0.44f,0.08f,10,(Color){200,30,30,255});
    } else if(sk->acc==3){ /* antenna + orb */
        DrawCylinder((Vector3){0,0.6f,0},0.02f,0.02f,0.4f,4,INK);
        DrawSphere((Vector3){0,0.82f,0},0.12f,(Color){80,220,255,255});
    } else if(sk->acc==4){ /* halo torus */
        rlPushMatrix();
        rlTranslatef(0,0.78f,0); rlRotatef(90,1,0,0);
        { DrawCylinder((Vector3){0,0,0},0.38f,0.38f,0.06f,10,AURUM); DrawCylinder((Vector3){0,0,0},0.2f,0.2f,0.08f,10,CREAM); }
        rlPopMatrix();
    }
    rlPopMatrix();
    /* shield bubble visual */
    if(shieldT>0 && shieldT<90){
        DrawSphereWires(pos,0.95f,lodSegS(),lodSegS(),Ua(0x6fd8ff,155));
    }
}

/* obstacles in current map colors */
static void drawObs(Obs*o){
    Color p1=U(MP.pA), p2=U(MP.pB); lodSeg();
    if(o->kind==0){
        DrawCylinder(o->pos,0.9f,0.9f,o->len,lodSeg(),p1);
        DrawCylinder((Vector3){o->pos.x,o->pos.y,o->pos.z},0.7f,1.1f,0.2f,lodSeg(),p2);
    } else if(o->kind==1){
        Vector3 pp={o->pos.x+cosf(o->spin)*1.2f,o->pos.y,o->pos.z};
        DrawCube(pp,1.8f,0.7f,0.7f,p1);
        DrawCubeWires(pp,1.8f,0.7f,0.7f,p2);
    } else if(o->kind==2){
        rlPushMatrix();
        rlTranslatef(o->pos.x,o->pos.y,o->pos.z);
        rlRotatef(o->spin*57.3f,0,0,1);
        DrawCube((Vector3){0,0,0},4.4f,0.35f,0.35f,p1);
        DrawCube((Vector3){0,0,0},0.35f,4.4f,0.35f,p2);
        rlPopMatrix();
        DrawSphere(o->pos,0.42f,p2);
    } else {
        rlPushMatrix();
        rlTranslatef(o->pos.x,o->pos.y,o->pos.z);
        rlRotatef(90,1,0,0);
        { DrawCylinder((Vector3){0,0,0},2.1f,2.1f,0.18f,lodSeg(),p1); DrawCylinder((Vector3){0,0,0},1.7f,1.7f,0.2f,lodSeg(),p2); }
        rlPopMatrix();
    }
}

/* coins torus gold + powerups */
static void drawCoin(Coin*c){
    rlPushMatrix();
    rlTranslatef(c->pos.x,c->pos.y,c->pos.z);
    rlRotatef(c->ph*57.3f,0,1,0);
    rlRotatef(90,1,0,0);
    lodSeg();
    DrawCylinder((Vector3){0,0,0},0.4f,0.4f,0.1f,lodSeg(),AURUM);
    DrawCylinder((Vector3){0,0,0},0.2f,0.2f,0.12f,lodSeg(),CREAM);
    rlPopMatrix();
}
static void drawPw(Pw*w){
    Color cc = w->kind==0?(Color){111,216,255,255}:(w->kind==1?(Color){255,80,180,255}:(Color){180,120,255,255});
    DrawSphere(w->pos,0.42f,cc);
    if(S.qual>=1) DrawSphereWires(w->pos,0.6f,4,6,WHT);
}

/* terrain per-map: ground slabs + stripes + hills + mountains (colors from MAPS8) */
#define NHILLS 10
#define NMTS   8
static float hillX[NHILLS],hillZ[NHILLS],hillS[NHILLS];
static float mtX[NMTS],mtZ[NMTS],mtS[NMTS];
static void terrainInit(){
    for(int i=0;i<NHILLS;i++){ hillX[i]=rr(-26,26); hillZ[i]=-i*18-6; hillS[i]=rr(2.4,5.2); }
    for(int i=0;i<NMTS;i++){ mtX[i]=rr(-30,30)+(i%2?15:-15); mtZ[i]=-i*24-12; mtS[i]=rr(3.5,7.5); }
}
static void terrainFollow(float bz){ /* recycle hills/mtns behind -> ahead */
    for(int i=0;i<NHILLS;i++) if(hillZ[i]>bz+15) hillZ[i]-=NHILLS*18;
    for(int i=0;i<NMTS;i++)   if(mtZ[i]>bz+20) mtZ[i]-=NMTS*24;
}
static void drawTerrain(float bz){
    lodSeg();
    Color g1=U(MP.gA), g2=U(MP.gB), f1=U(MP.fA), f2=U(MP.fB), e2=U(MP.eB);
    Color m1=U(MP.mtA), m2=U(MP.mtB), h1=U(MP.hA), h2=U(MP.hB);
    /* ground plane: big alternated cubes stretching z */
    float zStart=floorf(bz/4)*4;
    for(int i=-3;i<12;i++){
        float z=zStart-i*4;
        Color cc=((((int)((z)/4)+ (curMap? (int)z:0))&1)? g1:g2);
        DrawCube((Vector3){0,GROUNDY-0.25f,z},56,0.5f,4.1f,cc);
        /* stripe detail (fewer at low quality) */
        if(S.qual>=1 && (i&1)==0) DrawCube((Vector3){0,GROUNDY-0.02f,z},56,0.06f,0.14f,e2);
    }
    /* hills (spheres), mountains (cones) */
    for(int i=0;i<NHILLS;i++){
        if(fabsf(hillZ[i]-bz)>60) continue;
        DrawSphere((Vector3){hillX[i],GROUNDY+hillS[i]*0.18f,hillZ[i]},hillS[i],(i&1)?h1:h2);
    }
    for(int i=0;i<NMTS;i++){
        if(fabsf(mtZ[i]-bz)>85) continue;
        DrawCyl0((Vector3){mtX[i],GROUNDY+mtS[i]+2,mtZ[i]},mtS[i],mtS[i]*1.6f,lodSegS(),(i&1)?m1:m2);
        if(S.qual>=1){ /* snow cap */
            DrawCyl0((Vector3){mtX[i],GROUNDY+mtS[i]*1.45f+2,mtZ[i]},mtS[i]*0.3f,mtS[i]*0.55f,lodSegS(),WHT);
        }
    }
    /* decoration cubes along edges */
    lodSegS();
    for(int i=0;i<6;i++){
        float z=floorf(bz/9)*9-i*9;
        float sd=(i&1)?8:-8;
        DrawCube((Vector3){sd,GROUNDY+0.8f,z},1.1f,1.6f,1.1f,(i&1)?f1:f2);
    }
}

/* fog handling */

/* 3D scene */
static void drawWorld3D(){
    cam.fovy=60; cam.projection=CAMERA_PERSPECTIVE;
    /* camera shake (set on hit/death, decays) */
    shakeAmp*=0.9f; if(shakeAmp<0.02f)shakeAmp=0;
    float shx=shakeAmp*0.5f*sinf((float)GetTime()*91.0f);
    float shy=shakeAmp*0.5f*cosf((float)GetTime()*77.0f);
    cam.position=(Vector3){bird.pos.x*0.35f+shx,bird.pos.y+3.4f+shy,bird.pos.z+8.5f};
    cam.target=(Vector3){bird.pos.x*0.5f,bird.pos.y+0.9f,bird.pos.z-6};
    BeginMode3D(cam);
    drawTerrain(bird.pos.z);
    terrainFollow(bird.pos.z);
    /* cull: only draw objects within view frustum-ish z window */
    for(int i=0;i<nObs;i++){
        float dz=bird.pos.z-obs[i].pos.z;
        if(dz>-10&&dz<110) drawObs(&obs[i]);
    }
    lodSegS();
    for(int i=0;i<nCoins;i++){
        if(coinsA[i].dead) continue;
        float dz=bird.pos.z-coinsA[i].pos.z;
        if(dz>-6&&dz<95) drawCoin(&coinsA[i]);
    }
    for(int i=0;i<nPw;i++){
        if(pws[i].dead) continue;
        float dz=bird.pos.z-pws[i].pos.z;
        if(dz>-6&&dz<95) drawPw(&pws[i]);
    }
    partsDraw();
    if(bird.alive||pendingRevive) drawBirdSkin(bird.pos,S.skin,bird.flap,bird.tilt);
    EndMode3D();
}
/* ---------------- UI: buttons, RTL text, panels ---------------- */
static int state=ST_SPLASH;
static float stateT=0, splashT=0;
static int shop_page=0;
static int wipeArmed=0;
typedef struct { int id; int x,y,w,h; } Btn;
#define NBMAX 48
static Btn btns[NBMAX]; static int nBtns=0;
static Vector2 tPos={0,0}; static int tDown=0, tJust=0;

static void addBtn(int id,int x,int y,int w,int h){ if(nBtns<NBMAX){ btns[nBtns].id=id; btns[nBtns].x=x; btns[nBtns].y=y; btns[nBtns].w=w; btns[nBtns].h=h; nBtns++; } }
static int hitBtn(Vector2 p){
    for(int i=nBtns-1;i>=0;i--){
        Btn*b=&btns[i];
        if(p.x>=b->x&&p.x<=b->x+b->w&&p.y>=b->y&&p.y<=b->y+b->h) return b->id;
    }
    return B_NONE;
}
/* rounded button draw with gradient + shadow (calls move to UI draw phase) */
static void uiBtn(int id,int x,int y,int w,int h,Color base,Color shad,const char*label,float fs){
    addBtn(id,x,y,w,h);
    DrawRectangleRounded((Rectangle){x,y+3,w,h},0.32f,8,shad);
    DrawRectangleRounded((Rectangle){x,y,w,h},0.32f,8,base);
    int tw=(int)MeasureTextEx(arF,label,fs,1.0f).x;
    DrawTextEx(arF,label,(Vector2){x+w/2-tw/2,y+h/2-fs/2},fs,1.0f,WHT);
}

/* toast (toastTxt/toastT defined earlier in file for gameplay use) */

/* title center */
static void centerTitle(const char*t,float fs,Color c){
    int w=(int)MeasureTextEx(arF,t,fs,1.0f).x;
    DrawTextEx(arF,t,(Vector2){uiW/2-w/2,uiH*0.08f},fs,1.0f,c);
}
/* panel */
static void panel(Rectangle r, Color fill, Color bord){
    DrawRectangleRounded(r,0.14f,10,bord);
    DrawRectangleRounded((Rectangle){r.x+4,r.y+4,r.width-8,r.height-8},0.14f,10,fill);
}

/* ---------------- missions (5 slots, escalating tiers of 5) ---------------- */
static void msnNew(int slot,int tier){
    int kind=ri(0,3);
    int base=5+tier*5;
    S.msn[slot*3+0]=kind==0?base+ri(0,15):(kind==1?base*2+ri(0,20):(kind==2?ri(1,2+tier):base/2+ri(0,5)));
    S.msn[slot*3+1]=kind;
    S.msn[slot*3+2]=0;
    S.msnDone[slot]=0;
}
static void msnInitAll(){ int tier=S.runs/3; for(int i=0;i<NMsn;i++) if(S.msn[i*3+1]==0&&S.msn[i*3+0]==0) msnNew(i,tier); }
static int msnGoal(int i){ return S.msn[i*3+0]; }
static int msnKind(int i){ return S.msn[i*3+1]; }
static int msnProg(int i){ return S.msn[i*3+2]; }
static void msnBump(int kind,int amt){
    for(int i=0;i<NMsn;i++){
        if(msnKind(i)==kind && !S.msnDone[i]){
            S.msn[i*3+2]+=amt;
            if(msnProg(i)>=msnGoal(i)) S.msn[i*3+2]=msnGoal(i);
        }
    }
}
static void msnReward(int i, int tier){
    /* reward coins + every 5th mission gives key */
    int rew=20+tier*10;
    S.coins+=rew;
    if((S.runs+ i)%5==0){ S.keys++; toastTxt=A_1_THRONE; toastT=2.5f; }
    else { toastTxt=A_1_CLAIM; toastT=2; }
    msnNew(i,S.runs/3+1);
    svSave();
}
/* text of mission (EN fallback mirrored to shaped AR when needed) */
static const char* msnLabel(int i){
    if(S.lang==1){
        static char b[80];
        int k=msnKind(i);
        const char*nm=k==0?"score":k==1?"coins":k==2?"runs":"combo";
        snprintf(b,sizeof b,"%s >= %d  [%d/%d]",nm,msnGoal(i),msnProg(i),msnGoal(i));
        return b;
    }
    return A_1_INPROG;
}

/* ---------------- daily chest ---------------- */
static int dailyAvailable(){ return dayDiff(S.lastDay,todayInt())>=1; }
static void dailyOpen(){
    if(!dailyAvailable()) return;
    S.lastDay=todayInt();
    S.dailyStreak = (dayDiff(S.lastDay-1,todayInt())==1) ? S.dailyStreak+1 : 1;
    if(S.dailyStreak>7) S.dailyStreak=7;
    S.keys+=1;                 /* key (original: daily gives key progression) */
    S.coins+=30+S.dailyStreak*10;
    toastTxt=A_1_DAILYGOT; toastT=2.6f;
    svSave();
}

/* ---------------- map unlock gates (original MAPU) ---------------- */
static int mapGateOk(int i){ return S.mapBest[0]>=MAPU_G[i] || S.keys>=MAPU_K[i]; }
static void tryOpenMap(int i){
    if(mapGateOk(i)){
        if(S.mapBest[0]<MAPU_G[i]) S.keys-=MAPU_K[i];  /* paid with keys */
        setMapOpen(i); sfx(880,0,.3f,.6f); toastTxt=A_1_UNLOCKED; toastT=2.2f;
        svSave();
    } else { toastTxt=A_1_NEEDKEYS; toastT=2.2f; sfx(220,1,.2f,.5f); }
}

/* game over logic */
static int lastScore=0, countUp=0, medalKind=0;
static void die(){
    if(shieldT>0){ shieldT=0; bird.vel.y=6; shakeAmp=.4f; sfx(160,1,.25f,.6f); emit(bird.pos,WHT,18,4); return; }
    bird.alive=0; pendingRevive=1; shakeAmp=1; sfx(140,1,.45f,.7f); emit(bird.pos,AURUM,24,5);
}
static void finishRun(){
    S.runs++;
    int sc=lastScore;
    if(sc>S.best) S.best=sc;
    if(sc>S.mapBest[curMap]) S.mapBest[curMap]=sc;
    msnBump(0,sc);
    msnBump(1,S.coins); /* note: coins added this run */
    msnBump(2,1);
    medalKind = sc>=100?1:(sc>=50?2:(sc>=20?3:0));
    pendingRevive=0;
    svSave();
}
static void doRevive(){
    if(S.coins<REV_COST) { toastTxt=A_1_NOCOINS; toastT=2; return; }
    S.coins-=REV_COST;
    pendingRevive=0; bird.alive=1;
    bird.vel=(Vector3){0,4,0}; bird.pos.y=clampPlay(bird.pos.y);
    shieldT=3;
    svSave(); sfx(880,0,.3f,.6f);
}
static void startRun(){
    nObs=0;nCoins=0;nPw=0;nParts=0;
    nextObsZ=24;
    bird.alive=1; bird.pos=(Vector3){0,2,0}; bird.vel=(Vector3){0,5,0};
    dist=0;score=0;scoreF=0;spd=SPD0;
    shieldT=0;magnetT=0;slowT=0;coinCombo=0;comboT=0;
    skyPhase=0; applyMapColors();
    steerPrevX=tPos.x;
    state=ST_READY;
    sfx(523,0,.15f,.4f);
}

/* steering + flap input */
static void pollPlayInput(){
    if(tJust){
        /* flank: any tap flaps; if it lands on pause button, pause instead */
        int id=hitBtn(tPos);
        if(id==B_PAUSE){ state=ST_PAUSE; sfx(300,0,.15f,.4f); return; }
        if(tPos.y<uiH*0.14f && tPos.x>uiW-180) { state=ST_PAUSE; return; }
        if(tPos.x!=steerPrevX){
            float dx=(tPos.x-steerPrevX)/uiW*18.0f*(S.sens/100.0f);
            bird.pos.x=clampf(bird.pos.x+dx,-4.2f,4.2f);
            steerPrevX=tPos.x;
        }
        bird.vel.y=FLAPV; bird.flap=1; sfx(500,0,.08f,.28f);
        emit((Vector3){bird.pos.x,bird.pos.y-0.4f,bird.pos.z+0.3f},CREAM,3,1.5f);
    }
    if(!tDown){ /* reset anchor on release so next touch doesn't jump */
        steerPrevX=tPos.x;
    }
}
/* menu / screens common: tap handled by dispatchBtn(id) */
static void uiMenu();
static void uiWorlds();
static void uiShop();
static void uiMsn();
static void uiSet();
static void uiOver();
static void uiPause();
static void uiReady();
static void uiDaily();
/* forward decl dispatch */
static void dispatchBtn(int id);
static const char* mapDisplName(int i){ return S.lang==1?MAPS8[i].en:MAPN8[i]; }

/* HUD */
static void drawHUD(){
    char b[24];
    DrawRectangleRounded((Rectangle){10,10,190,44},0.4f,8,(Color){0,0,0,90});
    DrawTextEx(arF,A_1_COINS,(Vector2){16,16},24,1.0f,AURUM);
    snprintf(b,sizeof b,"%d",S.coins);
    DrawTextEx(arF,b,(Vector2){16+(int)MeasureTextEx(arF,A_1_COINS,24,1).x+8,16},24,1.0f,WHT);
    if(S.keys>0){
        DrawRectangleRounded((Rectangle){210,10,130,44},0.4f,8,(Color){0,0,0,90});
        DrawTextEx(arF,A_1_KEYS,(Vector2){216,16},24,1.0f,U(0x6fd8ff));
        snprintf(b,sizeof b,"%d",S.keys);
        DrawTextEx(arF,b,(Vector2){276+MeasureTextEx(arF,A_1_KEYS,24,1).x,16},24,1.0f,WHT);
    }
    snprintf(b,sizeof b,"%d",(int)score);
    int w=(int)MeasureTextEx(arF,b,44,1).x;
    DrawTextEx(arF,b,(Vector2){uiW/2-w/2,12},44,1.0f,WHT);
    snprintf(b,sizeof b,"%d",S.best);
    w=(int)MeasureTextEx(arF,b,20,1).x;
    DrawTextEx(arF,b,(Vector2){uiW/2-w/2,58},20,1.0f,(Color){255,255,255,140});
    uiBtn(B_PAUSE,uiW-76,8,64,48,ORG,ORGD,A_1_PAUSEBTN,20);
    if(S.showFps){
        DrawRectangleRounded((Rectangle){uiW-150,uiH-40,140,32},0.3f,6,(Color){0,0,0,90});
        snprintf(b,sizeof b,"%d FPS",GetFPS());
        DrawTextEx(arF,b,(Vector2){uiW-140,uiH-36},20,1.0f,U(0x50ff8c));
    }
    /* combo indicator */
    if(coinCombo>=2){
        snprintf(b,sizeof b,"x%d",((int)coinCombo)+1);
        DrawTextEx(arF,b,(Vector2){uiW/2-30,uiH*0.2f},36,1.0f,AURUM);
    }
}

/* actual menu layout function */
static void uiMenu(){
    drawSkyGrad();
    panel((Rectangle){16,20,uiW-32,150},Ua(0x2a1f46,235),ORG);
    int w=(int)MeasureTextEx(arF,A_1_BRAND,60,1).x;
    DrawTextEx(arF,A_1_BRAND,(Vector2){uiW/2-w/2,40},60,1.0f,AURUM);
    w=(int)MeasureTextEx(arF,A_1_SUB,28,1).x;
    DrawTextEx(arF,A_1_SUB,(Vector2){uiW/2-w/2,116},28,1.0f,CREAM);
    /* resource chips */
    char b[32];
    DrawRectangleRounded((Rectangle){16,180,200,48},0.35f,8,(Color){0,0,0,90});
    snprintf(b,sizeof b,"%d",S.coins);
    DrawTextEx(arF,A_1_COINS,(Vector2){26,192},24,1.0f,AURUM);
    DrawTextEx(arF,b,(Vector2){26+MeasureTextEx(arF,A_1_COINS,24,1).x+10,192},24,1.0f,WHT);
    DrawRectangleRounded((Rectangle){226,180,180,48},0.35f,8,(Color){0,0,0,90});
    snprintf(b,sizeof b,"%d",S.keys);
    DrawTextEx(arF,A_1_KEYS,(Vector2){236,192},24,1.0f,U(0x6fd8ff));
    DrawTextEx(arF,b,(Vector2){236+MeasureTextEx(arF,A_1_KEYS,24,1).x+10,192},24,1.0f,WHT);
    /* big menu buttons */
    int bw=uiW-64, bh=64, x=32, y=250, gap=14;
    uiBtn(B_PLAY,x,y,bw,bh,ORG,ORGD,A_1_PLAY,30);
    uiBtn(B_WORLDS,x,y+(bh+gap),bw,bh,TEAL,U(0x13867a),A_1_WORLDS,26);
    uiBtn(B_SHOP,x,y+2*(bh+gap),bw,bh,ROSEC,U(0xe0508a),A_1_SHOP,26);
    uiBtn(B_MSN,x,y+3*(bh+gap),bw/2-8,bh,AURUM,U(0xa8861a),A_1_MISSIONS,24);
    uiBtn(B_SET,x+bw/2+8,y+3*(bh+gap),bw/2-8,bh,INK,U(0x2a1f46),A_1_SETTINGS,24);
    (void)gap;
}
static int gapW(){ return 16; }
static const char* skArName(int i);
static void drawMiniBird(float cx2,float cy2,float r,const SKN*sk);
enum { B_SHOP_P=200, B_SHOP_P2=201 };

static void uiWorlds(){
    drawSkyGrad();
    centerTitle(A_1_WORLDSTITLE,36,AURUM);
    int cols=2, cw=(uiW-48)/cols-gapW(), ch=130, y=80;
    int row=0,col=0;
    char b[128];
    for(int i=0;i<NMAPS;i++){
        int x=24+col*(cw+16);
        int yy=y+row*(ch+14);
                Color cbase=U(MAPS8[i].gA), cb2=U(MAPS8[i].gB);
        DrawRectangleRounded((Rectangle){x,yy,cw,ch},0.16f,8,(Color){0,0,0,90});
        DrawRectangleRounded((Rectangle){x+3,yy+3,cw-6,ch-6},0.16f,8,LC(cbase,cb2,0.5f));
        /* name */
        if(mapOpen(i)){
            int tw=(int)MeasureTextEx(arF,mapDisplName(i),26,1).x;
            DrawTextEx(arF,mapDisplName(i),(Vector2){x+cw/2-tw/2,yy+12},26,1.0f,WHT);
        } else {
            int tw=(int)MeasureTextEx(arF,A_1_LOCKED,26,1).x;
            DrawTextEx(arF,A_1_LOCKED,(Vector2){x+cw/2-tw/2,yy+12},26,1.0f,(Color){255,255,255,140});
        }
        addBtn(mapOpen(i)?(B_MAPSEL+i):(B_MAPOPEN+i-3),x,yy,cw,ch);
        /* subline: best (open) or gate (locked) */
        if(mapOpen(i)){
            snprintf(b,sizeof b,"%s : %d",A_1_BESTIN,S.mapBest[i]);
            int tw=(int)MeasureTextEx(arF,b,20,1).x;
            DrawTextEx(arF,b,(Vector2){x+cw/2-tw/2,yy+48},20,1.0f,CREAM);
        } else {
            snprintf(b,sizeof b,"%s %d  /  %s %d",A_1_UNLOCKSCORE,MAPU_G[i],A_1_UNLOCKKEYS,MAPU_K[i]);
            int tw=(int)MeasureTextEx(arF,b,17,1).x;
            DrawTextEx(arF,b,(Vector2){x+cw/2-tw/2,yy+48},17,1.0f,mapGateOk(i)?U(0x88ff22):U(0xff8f3a));
            if(mapGateOk(i)){
                int tw2=(int)MeasureTextEx(arF,A_1_OPEN,22,1).x;
                DrawTextEx(arF,A_1_OPEN,(Vector2){x+cw/2-tw2/2,yy+84},22,1.0f,WHT);
            }
        }
        col++; if(col>=cols){col=0;row++;}
    }
    /* selected map highlight + back */
    uiBtn(B_BACK,16,uiH-72,110,52,INK,U(0x2a1f46),S.lang==1?"Back":"رجوع",22);
    /* selected label */
    snprintf(b,sizeof b,"%s  [%s]",mapDisplName(curMap),A_1_SELECTED);
    DrawTextEx(arF,b,(Vector2){16+140,uiH-62},22,1.0f,AURUM);
}
static void uiShop(){
    drawSkyGrad();
    centerTitle(A_1_SHOP,34,ROSEC);
    int per=8, start=shop_page*per;
    int cw=(uiW-60)/4, ch=120, y=90;
    char b[96];
    for(int j=0;j<per;j++){
        int i=start+j; if(i>=NSKINS) break;
        int col=i-start, cx2=20+(col%4)*(cw+14), cy2=y+(col/4)*(ch+14);
        const SKN*sk=&SKS[i];
        int isOwn=owned(i), isCur=S.skin==i, isGated=gated(i,0);
        Color base=isCur?AURUM:(isOwn?TEAL:Ua(0x554488,255));
        DrawRectangleRounded((Rectangle){cx2,cy2,cw,ch},0.16f,8,(Color){0,0,0,90});
        DrawRectangleRounded((Rectangle){cx2+3,cy2+3,cw-6,ch-6},0.16f,8,base);
        /* mini bird preview */
        drawMiniBird(cx2+cw/2,cy2+26,cw*0.28f,sk);
        /* name */
        const char*nm=S.lang==1?sk->n:skArName(i);
        int tw=(int)MeasureTextEx(arF,nm,17,1).x;
        DrawTextEx(arF,nm,(Vector2){cx2+cw/2-tw/2,cy2+46},17,1.0f,WHT);
        addBtn(B_SKIN+i,cx2,cy2,cw,ch);
        /* status line */
        if(isGated){
            snprintf(b,sizeof b,"%s %d (%s %d)",A_1_NEEDMAP,SKS[i].mg,MAPN8[sk->m],SKS[i].mg);
        } else if(!isOwn){
            snprintf(b,sizeof b,"%d",sk->p);
        } else if(!isCur){
            snprintf(b,sizeof b,"%s",A_1_SELECT);
        } else snprintf(b,sizeof b,"%s",A_1_SELECTED);
        tw=(int)MeasureTextEx(arF,b,15,1).x;
        DrawTextEx(arF,b,(Vector2){cx2+cw/2-tw/2,cy2+70},15,1.0f,CREAM);
    }
    /* page nav */
    if(shop_page>0) uiBtn(B_SHOP_P,20,uiH-72,64,52,INK,U(0x2a1f46),"<",28);
    if((shop_page+1)*per<NSKINS) uiBtn(B_SHOP_P2,uiW-84,uiH-72,64,52,INK,U(0x2a1f46),">",28);
    /* own count */
    int nown=0; for(int i=0;i<NSKINS;i++) if(owned(i)) nown++;
    snprintf(b,sizeof b,"%d / %d",nown,NSKINS);
    int tw=(int)MeasureTextEx(arF,b,20,1).x;
    DrawTextEx(arF,b,(Vector2){uiW/2-tw/2,uiH-58},20,1.0f,CREAM);
    uiBtn(B_BACK,16,8,110,48,INK,U(0x2a1f46),S.lang==1?"Back":"رجوع",22);
}
/* GLUE: skin-name lookup (order matches SKS), mission-kind labels */

static const char* const skinArNames[35]={
A_1_SK________,A_1_SK_____,A_2_SK_____,A_1_SK_______,A_3_SK_____,A_4_SK_____,A_2_SK________,A_5_SK_____,A_2_SK_______,A_1_SK______,A_3_SK________,A_4_SK________,A_3_SK_______,A_1_SK__________,A_5_SK________,A_2_SK______,A_3_SK______,A_4_SK_______,A_6_SK________,A_1_SK_________,A_4_SK______,A_5_SK_______,A_7_SK________,A_8_SK________,A_6_SK_______,A_9_SK________,A_7_SK_______,A_5_SK______,A_8_SK_______,A_6_SK______,A_10_SK________,A_11_SK________,A_7_SK______,A_9_SK_______,A_10_SK_______
};
#define A_1_MSN0 A_1_MSN_
#define A_1_MSN1 A_2_MSN_
#define A_1_MSN2 A_3_MSN_
#define A_1_MSN3 A_4_MSN_
static const char* msnArLabel(int i);
/* ---------------- Missions screen (5 slots) ---------------- */
static void uiMsn(){
    drawSkyGrad();
    centerTitle(A_1_MISSIONSTITLE,36,AURUM);
    char b[80];
    for(int i=0;i<NMsn;i++){
        int y=90+i*74;
        panel((Rectangle){16,y,uiW-32,64},Ua(0x2a1f46,235),S.msnDone[i]?TEAL:ORG);
        const char*title = S.lang==1?msnLabel(i):msnArLabel(i);
        DrawTextEx(arF,title,(Vector2){28,y+6},22,1.0f,WHT);
        int wpx=(int)((uiW-220)*msnProg(i)*1.0f/msnGoal(i)); if(wpx<0)wpx=0;
        DrawRectangle(28,y+42,uiW-220,10,(Color){0,0,0,90});
        DrawRectangle(28,y+42,wpx,10,S.msnDone[i]?TEAL:AURUM);
        if(!S.msnDone[i]&&msnProg(i)>=msnGoal(i)){
            uiBtn(B_MSNCLAIM+i,uiW-140,y+8,116,46,AURUM,U(0xa8861a),A_1_CLAIM,20);
        }
        snprintf(b,sizeof b,"%d / %d",msnProg(i),msnGoal(i));
        DrawTextEx(arF,b,(Vector2){uiW-150,y+52},18,1.0f,CREAM);
        if(S.msnDone[i]) DrawTextEx(arF,A_1_DONE,(Vector2){40,y+6},0,0,TEAL); /* drawn tiny above */
    }
    uiBtn(B_BACK,16,uiH-72,110,52,INK,U(0x2a1f46),S.lang==1?"Back":"رجوع",22);
}
static const char* msnArLabel(int i){
    static char b[80];
    const char*body;
    switch(msnKind(i)){case 0:body=A_1_MSN0;break;case 1:body=A_1_MSN1;break;case 2:body=A_1_MSN2;break;default:body=A_1_MSN3;}
    snprintf(b,sizeof b,"%s  %d/%d",body,msnProg(i),msnGoal(i));
    return b;
}

/* ---------------- Settings ---------------- */
static const char* setLbl(const char*base,int v){ static char b[80]; snprintf(b,sizeof b,"%s %s",base,v?A_1_ON:A_1_OFF); return b; }
static const char* qualLbl(){ static char b[80]; const char*q=S.qual==0?A_1_QUALITYL:(S.qual==1?A_1_QUALITYM:A_1_QUALITYH); snprintf(b,sizeof b,"%s %s",A_1_QUALITY,q); return b; }
static const char* steerLbl(){ static char b[80]; const char*q=S.sens<=66?A_1_STEERL:(S.sens<=116?A_1_STEERM:A_1_STEERH); snprintf(b,sizeof b,"%d%% %s",S.sens,q); return b; }
static void uiSet(){
    drawSkyGrad();
    centerTitle(A_1_SETTINGSTITLE,34,CREAM);
    int bw=(uiW-48)/2, bh=52, y=70;
    uiBtn(B_SND,16,y,bw,bh,TEAL,U(0x13867a),setLbl(A_1_SOUND,S.sfxV),22);
    uiBtn(B_MUS,32+bw,y,bw,bh,TEAL,U(0x13867a),setLbl(A_1_MUSIC,S.musV),22);
    uiBtn(B_QUAL,16,y+62,bw,bh,ORG,ORGD,qualLbl(),22);
    uiBtn(B_SENP,32+bw,y+62,bw,bh,ORG,ORGD,steerLbl(),22);
    uiBtn(B_SHAKE,16,y+124,bw,bh,INK,U(0x2a1f46),setLbl(A_1_SHAKE,S.shakeOn),22);
    uiBtn(B_FPST,32+bw,y+124,bw,bh,INK,U(0x2a1f46),setLbl(A_1_FPS,S.showFps),22);
    uiBtn(B_LANG,16,y+186,uiW-32,52,ROSEC,U(0xe0508a),S.lang==1?"English / عربي":A_1_LANG,22);
    /* stats */
    panel((Rectangle){16,y+252,uiW-32,110},Ua(0x2a1f46,235),AURUM);
    DrawTextEx(arF,A_1_STATS,(Vector2){28,y+260},22,1.0f,AURUM);
    char b[96];
    int nopen=0; for(int i=0;i<NMAPS;i++) if(mapOpen(i)) nopen++;
    snprintf(b,sizeof b,"%s %d   %s %d   %s %d/8",A_1_BEST,S.best,A_1_RUNS,S.runs,A_1_MAPSOPENED,nopen);
    DrawTextEx(arF,b,(Vector2){28,y+290},20,1.0f,WHT);
    snprintf(b,sizeof b,"%s %lld   %s %d   %s %d",A_1_EARNED,S.totCoins,A_1_COINS,S.coins,A_1_KEYS,S.keys);
    DrawTextEx(arF,b,(Vector2){28,y+322},20,1.0f,WHT);
    if(!wipeArmed) uiBtn(B_WIPE,16,uiH-140,uiW-32,54,(Color){160,60,60,255},(Color){90,30,30,255},A_1_RESET,24);
    else uiBtn(B_WIPE,16,uiH-140,uiW-32,54,(Color){220,60,60,255},(Color){120,30,30,255},A_1_SURE,24);
    uiBtn(B_BACK,16,uiH-72,110,52,INK,U(0x2a1f46),S.lang==1?"Back":"رجوع",22);
}

/* ---------------- Over: count-up + medal + revive 25 ---------------- */
static void uiOver(){
    char b[40];
    centerTitle(A_1_GAMEOVER,40,AURUM);
    if(countUp<lastScore) countUp=(countUp<lastScore-8)?countUp+8:lastScore;
    snprintf(b,sizeof b,"%s %d",A_1_SCORE,countUp);
    int tw=(int)MeasureTextEx(arF,b,44,1).x;
    DrawTextEx(arF,b,(Vector2){uiW/2-tw/2,uiH*0.2f},44,1.0f,WHT);
    const char*med=medalKind==1?A_1_MEDIGOLD:(medalKind==2?A_1_MEDISILV:A_1_MEDIBRON);
    Color mc=medalKind==1?AURUM:(medalKind==2?(Color){210,215,225,255}:(Color){205,127,50,255});
    tw=(int)MeasureTextEx(arF,med,30,1).x;
    DrawTextEx(arF,med,(Vector2){uiW/2-tw/2,uiH*0.28f},30,1.0f,mc);
    if(lastScore>=S.best&&lastScore>0){
        tw=(int)MeasureTextEx(arF,A_1_NEWBEST,28,1).x;
        DrawTextEx(arF,A_1_NEWBEST,(Vector2){uiW/2-tw/2,uiH*0.35f},28,1.0f,U(0x88ff22));
    }
    if(pendingRevive){
        snprintf(b,sizeof b,"%s  %d",A_1_REVIVE,REV_COST);
        uiBtn(B_REVIVE,uiW/2-180,uiH*0.5f,360,74,AURUM,U(0xa8861a),b,26);
        uiBtn(B_GIVEUP,uiW/2-140,uiH*0.5f+90,280,60,INK,U(0x2a1f46),A_1_GIVEUP,24);
    } else {
        uiBtn(B_RETRY,uiW/2-180,uiH*0.5f,360,74,ORG,ORGD,A_1_RETRY,28);
        uiBtn(B_MENU,uiW/2-140,uiH*0.5f+90,280,60,INK,U(0x2a1f46),A_1_MENU,24);
    }
    snprintf(b,sizeof b,"%s %d",A_1_COINS,S.coins);
    tw=(int)MeasureTextEx(arF,b,22,1).x;
    DrawTextEx(arF,b,(Vector2){uiW/2-tw/2,uiH-60},22,1.0f,AURUM);
}

/* ---------------- Pause ---------------- */
static void uiPause(){
    drawSkyGrad();
    centerTitle(A_1_PAUSED,44,WHT);
    uiBtn(B_RESUME,uiW/2-170,uiH*0.35f,340,72,TEAL,U(0x13867a),A_1_RESUME,28);
    uiBtn(B_MENU,uiW/2-170,uiH*0.35f+90,340,68,INK,U(0x2a1f46),A_1_MENU,26);
}

/* ---------------- Ready ---------------- */
static void uiReady(){
    int tw=(int)MeasureTextEx(arF,A_1_READY,34,1).x;
    DrawTextEx(arF,A_1_READY,(Vector2){uiW/2-tw/2,uiH*0.42f},34,1.0f,(Color){255,255,255,200});
    tw=(int)MeasureTextEx(arF,A_1_SWIPE,24,1).x;
    DrawTextEx(arF,A_1_SWIPE,(Vector2){uiW/2-tw/2,uiH*0.42f+50},24,1.0f,(Color){255,255,255,140});
    char b[40]; snprintf(b,sizeof b,"%s",mapDisplName(curMap));
    tw=(int)MeasureTextEx(arF,b,26,1).x;
    DrawRectangleRounded((Rectangle){uiW/2-tw/2-14,14,tw+28,44},0.4f,8,(Color){0,0,0,90});
    DrawTextEx(arF,b,(Vector2){uiW/2-tw/2,22},26,1.0f,AURUM);
    uiBtn(B_PAUSE,uiW-76,8,64,48,ORG,ORGD,A_1_PAUSEBTN,20);
}

/* ---------------- Daily modal ---------------- */
static void uiDaily(){
    DrawRectangle(0,0,uiW,uiH,(Color){0,0,0,140});
    int pw2=(uiW<520?uiW-60:460), ph2=250;
    panel((Rectangle){uiW/2-pw2/2,uiH/2-ph2/2,pw2,ph2},Ua(0x2a1f46,245),AURUM);
    const char*t=A_1_DAILYTITLE;
    int tw=(int)MeasureTextEx(arF,t,30,1).x;
    DrawTextEx(arF,t,(Vector2){uiW/2-tw/2,uiH/2-ph2/2+18},30,1.0f,AURUM);
    const char*body=dailyAvailable()?A_1_DAILYOPEN:A_1_COMEBACK;
    tw=(int)MeasureTextEx(arF,body,24,1).x;
    DrawTextEx(arF,body,(Vector2){uiW/2-tw/2,uiH/2-20},24,1.0f,WHT);
    if(dailyAvailable()) uiBtn(B_CLAIM,uiW/2-110,uiH/2+40,220,64,AURUM,U(0xa8861a),A_1_DAILYOPEN,26);
    uiBtn(B_BACK,uiW/2-90,uiH/2+40+70,180,54,INK,U(0x2a1f46),A_1_MENU,22);
}

/* mini bird for shop */
static void drawMiniBird(float cx2,float cy2,float r,const SKN*sk){
    Color cb=U(sk->b), cl=U(sk->l), ck=U(sk->k);
    DrawSphere((Vector3){cx2,cy2,0},r,cb);
    DrawSphere((Vector3){cx2,cy2-r*0.4f,r*0.76f},r*0.8f,cb);
    DrawSphere((Vector3){cx2,cy2-r*0.18f,r*0.06f},r*0.75f,cl);
    DrawSphere((Vector3){cx2+r*0.34f,cy2-r*0.44f,r*0.72f},r*0.18f,WHT);
    DrawSphere((Vector3){cx2+r*0.34f,cy2-r*0.44f,r*0.63f},r*0.09f,INK);
    DrawCylinderEx((Vector3){cx2,cy2-r*0.38f,r*1.15f},(Vector3){cx2,cy2-r*0.38f,r*1.55f},r*0.04f,r*0.25f,4,ck);
    if(sk->acc==2) DrawCylinder((Vector3){cx2,cy2-r*0.4f,r*0.76f},r*0.82f,r*0.82f,r*0.14f,10,U(0xc81e1e));
    if(sk->acc==4) DrawCylinder((Vector3){cx2,cy2-r*1.4f,0},r*0.6f,r*0.6f,r*0.1f,10,AURUM);
    if(sk->acc==1) DrawCylinder((Vector3){cx2,cy2-r*1.15f,r*0.76f},r*0.5f,r*0.55f,r*0.24f,8,AURUM);
    if(sk->acc==3){ DrawCylinder((Vector3){cx2,cy2-r*1.1f,r*0.76f},r*0.04f,r*0.04f,r*0.7f,4,INK); DrawSphere((Vector3){cx2,cy2-r*1.5f,r*0.76f},r*0.2f,U(0x50dcff)); }
}
static const char* skArName(int i){ return skinArNames[i]; }
static void sfxVcal();
static void musVcal();
/* ---------------- main loop ---------------- */
static void gameInit(void);
static void processJust(void);
static void drawToast(void);
static void drawBtnsFrame(void);
static void pollTouch(void);
static void dispatchBtn(int id){
    if(id>=B_MSNCLAIM){ int slot=id-B_MSNCLAIM; if(slot>=0&&slot<NMsn){ msnReward(slot,S.runs/3); } return; }
    if(id>=B_SKIN){ int si=id-B_SKIN; if(si>=0&&si<NSKINS){ 
        if(gated(si,0)){ toastTxt=A_1_NEEDMAP; toastT=2.2f; return; }
        if(owned(si)) S.skin=si;
        else if(S.coins>=SKS[si].p){ S.coins-=SKS[si].p; setOwned(si); S.skin=si; toastTxt=A_1_CLAIM; toastT=2; }
        else { toastTxt=A_1_NOCOINS; toastT=2; }
        svSave(); sfx(700,0,.12f,.4f);
    } return; }
    if(id>=B_MAPOPEN&&id<B_MAPOPEN+7){ tryOpenMap(id-B_MAPOPEN+3); return; }
    if(id>=B_MAPSEL&&id<B_MAPSEL+8){ curMap=id-B_MAPSEL; applyMapColors(); svSave(); state=ST_READY; startRun(); return; }
    switch(id){
        case B_PLAY: startRun(); break;
        case B_WORLDS: state=ST_WORLDS; break;
        case B_SHOP: state=ST_SHOP; shop_page=0; break;
        case B_MSN: state=ST_MSN; msnInitAll(); break;
        case B_SET: state=ST_SET; wipeArmed=0; break;
        case B_BACK:
            if(state==ST_DAILY||1) state=ST_MENU;
            svSave(); break;
        case B_RESUME: state=ST_PLAY; break;
        case B_MENU: state=ST_MENU; pendingRevive=0; svSave(); break;
        case B_RETRY: finishRun(); startRun(); break;
        case B_REVIVE: doRevive(); break;
        case B_GIVEUP: finishRun(); state=ST_OVER; break;
        case B_SND: S.sfxV=S.sfxV>0?0:100; sfxVcal(); svSave(); break;
        case B_MUS: S.musV=S.musV>0?0:70; musVcal(); svSave(); break;
        case B_QUAL: S.qual=(S.qual+1)%3; svSave(); break;
        case B_SHAKE: S.shakeOn=!S.shakeOn; svSave(); break;
        case B_SENP: S.sens+=50; if(S.sens>150)S.sens=50; svSave(); break;
        case B_FPST: S.showFps=!S.showFps; svSave(); break;
        case B_LANG: S.lang=!S.lang; svSave(); break;
        case B_WIPE:
            if(!wipeArmed) wipeArmed=1;
            else { svWipe(); wipeArmed=0; toastTxt=A_1_CLAIM; toastT=2; }
            break;
        case B_PAUSE: state=ST_PAUSE; break;
        case B_DAILY: state=ST_DAILY; break;
        case B_CLAIM: dailyOpen(); break;
        case B_SHOP_P: if(shop_page>0)shop_page--; break;
        case B_SHOP_P2: shop_page++; break;
        default: break;
    }
}
static void sfxVcal(){ sfxV=S.sfxV/100.0f*0.9f; }
static void musVcal(){ musV=S.musV/100.0f*0.4f; }
static void dailyBumpMenu(){ if(dailyAvailable()) uiBtn(B_DAILY,24,uiH-140,120,48,AURUM,U(0xa8861a),"D",24); }
/* daily icon drawn on menu as small chest button (D chip) */
static void uiMenuV(){ uiMenu(); dailyBumpMenu(); }

static void frame(){
   BeginDrawing();
    ClearBackground(fogC);
    if(!arLoaded){} /* font loaded once in main() */
    int sw=GetScreenWidth(), sh=GetScreenHeight();
    uiW=sw; uiH=sh;
    nBtns=0;
    stateT+=GetFrameTime();
    switch(state){
        case ST_SPLASH:
            splashT+=GetFrameTime();
            DrawRectangle(0,0,uiW,uiH,U(0x2a1f46));
            {
                const char*a=A_1_STUDIO,*b2=A_1_PRESENTS;
                int w1=(int)MeasureTextEx(arF,a,44,1).x, w2=(int)MeasureTextEx(arF,b2,26,1).x;
                float al=clampf((splashT-0.2f)/0.8f,0,1);
                DrawTextEx(arF,a,(Vector2){uiW/2-w1/2,uiH*0.42f},44,1.0f,Ua(0xffd23f,(int)(255*al)));
                DrawTextEx(arF,b2,(Vector2){uiW/2-w2/2,uiH*0.42f+56},26,1.0f,Ua(0xfff6dc,(int)(255*al)));
                if(splashT>2.7f){ state=ST_MENU; }
            }
            break;
        case ST_MENU: uiMenuV(); if(toastT>0)drawToast(); break;
        case ST_WORLDS: drawSkyGrad(); uiWorlds(); break;
        case ST_SHOP: uiShop(); break;
        case ST_MSN: uiMsn(); break;
        case ST_SET: uiSet(); break;
        case ST_DAILY: uiMenuV(); uiDaily(); break;
        case ST_READY:
            drawWorld3D(); uiReady(); drawHUD();
            if(tJust){ state=ST_PLAY; stateT=0; steerPrevX=tPos.x; }
            break;
        case ST_PLAY:
            updateWorld(GetFrameTime());
            updateBird(GetFrameTime());
            { int hi=hitTest();
              if(hi>=0){ die(); state=ST_DYING; stateT=0; } }
            drawWorld3D(); drawHUD(); if(toastT>0)drawToast();
            pollPlayInput();
            break;
        case ST_PAUSE: drawWorld3D(); uiPause(); drawHUD(); break;
        case ST_DYING:
            partsUpdate(GetFrameTime());
            { float dt=GetFrameTime()*0.35f; /* slow-mo */
              bird.vel.y-=G*dt; bird.pos.y+=bird.vel.y*dt; bird.pos.z-=spd*dt*0.35f; }
            bird.tilt=lerpf(bird.tilt,-1.2f,GetFrameTime()*4);
            drawWorld3D();
            if(stateT>0.9f){ lastScore=(int)score; countUp=0; finishRun(); state=ST_OVER; stateT=0; }
            break;
        case ST_OVER: uiOver(); break;
        default: uiMenuV(); break;
    }
    drawBtnsFrame();
    if(toastT>0) drawToast();
EndDrawing();
}
static void drawToast(){
    if(!toastTxt) return;
    int tw=(int)MeasureTextEx(arF,toastTxt,26,1).x;
    DrawRectangleRounded((Rectangle){uiW/2-tw/2-16,uiH-140,tw+32,52},0.3f,8,(Color){0,0,0,180});
    DrawTextEx(arF,toastTxt,(Vector2){uiW/2-tw/2,uiH-128},26,1.0f,WHT);
    toastT-=GetFrameTime(); if(toastT<=0) toastTxt=0;
}
/* buttons need re-draw each frame; addBtn stores, we re-draw in same frame order — unify: */
static void drawBtnsFrame(){ /* buttons already drawn inline during each screen fn; nothing */ }
/* touch poll: use raylib touch (NativeActivity touch mapped automatically) */
static void pollTouch(){
    /* primary pointer */
    if(GetTouchPointCount()>0){
        Vector2 p=GetTouchPosition(0);
        if(!tDown){ tJust=1; steerPrevX=p.x; }
        else tJust=0;
        tPos=p; tDown=1;
    } else { tDown=0; tJust=0; }
    /* mouse fallback (desktop-testing) */
    if(IsMouseButtonPressed(0)){ tJust=1; tPos=GetMousePosition(); steerPrevX=tPos.x; }
    else if(IsMouseButtonDown(0)){ tJust=tJust; tPos=GetMousePosition(); }
}

#if !defined(PLATFORM_ANDROID)
int main(void){
    InitWindow(960,540,"Tipsy Bird Ultra v2");
    gameInit();
    while(!WindowShouldClose()){ pollTouch(); frame(); processJust(); }
    CloseWindow(); return 0;
}
#else
void android_main(struct android_app* app);
void gameInit(void);
int main(int argc,char**argv);
/* raylib 5.5 provides android_main which calls main() */
int main(int argc,char**argv){ (void)argc;(void)argv;
    SetConfigFlags(FLAG_FULLSCREEN_MODE);
    InitWindow(0,0,"Tipsy Bird Ultra");
    gameInit();
    while(!WindowShouldClose()){ pollTouch(); frame(); processJust(); }
    CloseWindow(); return 0;
}
#endif
/* gameInit: font, audio, save, terrain */
static void gameInit(){
    svLoad();
    arF=LoadFontFromMemory(".ttf",(const unsigned char*)AR_FONT_BYTES,AR_FONT_BYTES_LEN,64,(int*)AR_CPS,AR_CPS_N);
    arLoaded=1;
    SetTextureFilter(arF.texture,TEXTURE_FILTER_BILINEAR);
    MPX=&MAPS8[curMap]; MP=*MPX; skyPhase=0; applyMapColors();
    terrainInit();
    InitAudioDevice();
    SetAudioStreamBufferSizeDefault(2048);
    au=LoadAudioStream(SR,16,2);
    SetAudioStreamCallback(au,auCB);
    PlayAudioStream(au);
    msnInitAll();
    sfxVcal(); musVcal();
}
/* tJust is consumed by the frame: translate to dispatch when in UI states */
static void processJust(){
    if(!tJust){ /* also allow tap without just flag for UI (down-edge consumed) */ }
    if(state==ST_PLAY||state==ST_READY||state==ST_SPLASH) { tJust=0; return; }
    int id=hitBtn(tPos);
    if(state==ST_DYING||state==ST_OVER){
        if(tJust){
            if(id==B_NONE&&state==ST_OVER){} /* tap anywhere ok? no: buttons only */
            else dispatchBtn(id);
        }
        tJust=0; return;
    }
    if(tJust){
        if(id!=B_NONE) dispatchBtn(id);
        else if(state==ST_MENU){ state=ST_PLAY; startRun(); } /* tap outside buttons = fly now */
    }
    tJust=0;
}
