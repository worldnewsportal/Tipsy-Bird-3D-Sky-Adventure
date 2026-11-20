/* ============================================================================
   TIPSY BIRD ULTRA — full 3D endless flyer, built from scratch on raylib 5.5
   Native C + NativeActivity (no WebView, no gradle, 100% offline).
   4 biomes, 4 obstacle families, powerups, missions, shop w/ 8 skins,
   synth music + SFX engine, particles, hit-stop / slow-mo / shake.
   ============================================================================ */
#include "raylib.h"
#include "rlgl.h"
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>

/* ------------------------------------------------------------------ config */
#define TPI     3.14159265f
#define SR      44100
#define PMAX    240                  /* particle pool                    */
#define DMAX    40                   /* decor pool                       */
#define SMAX    90                   /* track ground stripes             */
#define OBSN    12                   /* obstacle pool                    */
#define CON     40                   /* coin pool                        */
#define PWN     8                    /* powerup pool                     */
#define OP      6.0f                 /* half playing field width         */
#define SPD0    15.0f
#define SPD1    34.0f
#define G       32.0f
#define FLAPV   10.5f
#define BR      0.55f
#define CEILY   20.0f
#define FLOORY  -1.2f
#define REV_COST 150

static const Color INK   = { 58, 42, 92, 255 };
static const Color CREAM = {255,246,220,255};
static const Color ORG   = {255,122, 47,255};
static const Color TEAL  = { 39,193,176,255};
static const Color AURUM  = {255,201, 77,255};
static const Color ROSEC  = {255, 99,132,255};

/* --------------------------------------------------------------- rng utils */
static unsigned int rs = 0xBEEF1234u;
static unsigned int rnd32(void){ rs^=rs<<13; rs^=rs>>17; rs^=rs<<5; return rs; }
static float rf(void){ return (float)(rnd32()&0xFFFFFF)/16777216.0f; }
static float rr(float a,float b){ return a+(b-a)*rf(); }
static int   ri(int a,int b){ return a+(int)(rf()*(b-a+1))&0x7FFFFFFF; } /* careful use */
static float clampf(float v,float a,float b){ return v<a?a:(v>b?b:v); }
static float lerpf (float a,float b,float t){ return a+(b-a)*t; }
static Vector3 V3(float x,float y,float z){ Vector3 v={x,y,z}; return v; }

static Color LC(Color a, Color b, float t){
    t=clampf(t,0,1);
    Color c={(unsigned char)lerpf(a.r,b.r,t),(unsigned char)lerpf(a.g,b.g,t),
             (unsigned char)lerpf(a.b,b.b,t),(unsigned char)lerpf(a.a,b.a,t)};
    return c;
}
/* fade a color toward fog by distance (-z) */
static Color fogCol(Color c, float z, Color fog){
    float d = clampf((-z)/110.0f,0,1);
    return LC(c, fog, d*0.85f);
}

/* --------------------------------------------------------------------- save */
typedef struct { int type, goal, prog, done; } Msn;
typedef struct {
    unsigned int magic;
    int best, coins, skin;  unsigned short owned;
    int qual, shakeOn, sfxV, musV;
    int runs; long long totCoins;
    Msn msn[3];
} Sv;
static Sv S;
static char savePath[256] = "tipsy_ultra.bin";

static void findSavePath(void){
    char cmd[256]={0};
    FILE *f=fopen("/proc/self/cmdline","rb");
    if(f){ size_t n=fread(cmd,1,255,f); (void)n; fclose(f); }
    char *sp=strchr(cmd,' '); if(sp)*sp=0;
    if(!cmd[0]) strcpy(cmd,"com.tipsybird.raylib");
    char dir[256]; snprintf(dir,sizeof dir,"/data/data/%s/files",cmd);
    mkdir(dir,0777);
    snprintf(savePath,sizeof savePath,"%s/tipsy_ultra.bin",dir);
}
static void saveWrite(void){
    S.magic=0x54425531u;
    FILE *f=fopen(savePath,"wb");
    if(!f) return;
    fwrite(&S,sizeof S,1,f); fclose(f);
}
static void saveRead(void){
    memset(&S,0,sizeof S); S.magic=0;
    FILE *f=fopen(savePath,"rb");
    if(!f) return;
    Sv t; memset(&t,0,sizeof t);
    if(fread(&t,sizeof t,1,f)==1 && t.magic==0x54425531u) S=t;
    fclose(f);
}

/* -------------------------------------------------------------------- audio */
typedef struct { float f0,f1,dur,age,vol; int type, on; } Tone;
#define SFXN 16
static Tone tones[SFXN]; static int toneCur=0;

static const int MUS[4][16]={
 {0,4,7,12,7,4,0,-5, 0,4,7,12,16,12,7,4},
 {0,3,7,10,12,10,7,3, -2,3,7,10,14,12,10,7},
 {0,5,9,12,9,5,0,-3,  0,5,9,12,17,12,9,5},
 {0,7,12,15,12,7,4,0, -1,7,12,19,15,12,7,4}};
static const int BASS[4]={-12,-9,-5,-7};

static float stepT=0; static int mStep=0, mPat=0;
static float melPh=0, bassPh=0;
static AudioStream as;
static int auOK=0;

static float waveForm(int type,float ph){
    switch(type){
    case 0: return sinf(ph);
    case 1: return 0.6366f*asinf(sinf(ph));
    case 2: return sinf(ph)>=0?0.5f:-0.5f;
    }
    return 0;
}
static void tone(int type,float f0,float f1,float dur,float vol){
    Tone *t=&tones[toneCur]; toneCur=(toneCur+1)%SFXN;
    t->f0=f0;t->f1=f1;t->dur=dur;t->age=0;t->vol=vol;t->type=type;t->on=1;
}
static void sfxFlap (void){ tone(1,420,610,0.09f,0.5f); }
static void sfxScore(void){ tone(2,660,660,0.07f,0.35f); }
static void sfxCoin (void){ tone(0,1046,1568,0.12f,0.4f); }
static void sfxHit  (void){ tone(2,300,60,0.5f,0.75f); tone(1,90,40,0.6f,0.6f); }
static void sfxClick(void){ tone(0,520,520,0.05f,0.3f); }
static void sfxPower(void){ tone(0,523,523,0.08f,0.4f); tone(0,659,659,0.08f,0.4f); tone(0,784,784,0.16f,0.4f); }
static void sfxRing (void){ tone(1,784,1175,0.15f,0.45f); }

static void auCB(void *buf, unsigned int frames){
    short *out=(short*)buf;
    float sv=(float)S.sfxV/100.0f, mv=(float)S.musV/100.0f;
    for(unsigned int i=0;i<frames;i++){
        float mix=0;
        /* --- music --- */
        if(mv>0.01f){
            stepT+=1.0f/SR;
            if(stepT>=0.15f){ stepT-=0.15f; mStep=(mStep+1)&15; if(mStep==0) mPat=(mPat+1)&3; }
            int semi=MUS[mPat][mStep];
            float f=220.0f*powf(2.0f,semi/12.0f);
            melPh+=2*TPI*f/SR;
            float env=clampf(1.0f-stepT/0.15f,0,1)*0.8f+0.2f;
            mix+=0.6366f*asinf(sinf(melPh))*env*0.30f;
            if((mStep&7)==0){
                float fb=110.0f*powf(2.0f,BASS[mPat]/12.0f);
                bassPh+=2*TPI*fb/SR;
                mix+=sinf(bassPh)*0.22f;
            }
            mix*=mv;
        }
        /* --- sfx --- */
        if(sv>0.01f){
            for(int k=0;k<SFXN;k++){
                Tone *t=&tones[k];
                if(!t->on) continue;
                t->age+=1.0f/SR;
                if(t->age>=t->dur){ t->on=0; continue; }
                float fr=lerpf(t->f0,t->f1,t->age/t->dur);
                float ph=2*TPI*fr/SR;
                static float phs[SFXN];
                phs[k]+=ph;
                float env=1.0f-t->age/t->dur;
                mix+=waveForm(t->type,phs[k])*env*t->vol*sv;
            }
        }
        if(mix> 1) mix= 1; if(mix<-1) mix=-1;
        short s=(short)(mix*30000.0f);
        *out++=s; *out++=s;
    }
}
static void audioInit(void){
    InitAudioDevice();
    if(!IsAudioDeviceReady()) return;
    SetAudioStreamBufferSizeDefault(2048);
    as=LoadAudioStream(SR,16,2);
    if(as.buffer==0) return;
    SetAudioStreamCallback(as,auCB);
    SetAudioStreamVolume(as,1.0f);
    PlayAudioStream(as);
    auOK=1;
}

/* ---------------------------------------------------------------- particles */
typedef struct { Vector3 p,v; float age,life,size; Color c; int on; } Part;
static Part parts[PMAX]; static int pCur=0;
static void emit(Vector3 p, Vector3 v, float life, float size, Color c, int n){
    for(int i=0;i<n;i++){
        Part *q=&parts[pCur]; pCur=(pCur+1)%PMAX;
        q->p=p; q->v=V3(v.x+rr(-2,2),v.y+rr(-2,2),v.z+rr(-1,1));
        q->age=0;q->life=life*rr(0.6f,1.2f);q->size=size*rr(0.6f,1.3f);
        q->c=c;q->on=1;
    }
}
static void partsUpdate(float dt){
    for(int i=0;i<PMAX;i++){
        Part *q=&parts[i]; if(!q->on) continue;
        q->age+=dt; if(q->age>=q->life){q->on=0;continue;}
        q->p.x+=q->v.x*dt; q->p.y+=q->v.y*dt; q->p.z+=q->v.z*dt;
        q->v.y-=14.0f*dt;
    }
}
static void partsDraw(Color fog){
    for(int i=0;i<PMAX;i++){
        Part *q=&parts[i]; if(!q->on) continue;
        float k=1-q->age/q->life;
        Color c=q->c; c.a=(unsigned char)(255*k);
        DrawCube(q->p,q->size,q->size,q->size,fogCol(c,q->p.z,fog));
    }
}

/* ------------------------------------------------------------------- biomes */
typedef struct { const char*name; Color sky,fog,grnd,deco,acc,pipe; int embers; } Biome;
static const Biome BMS[4]={
 /* MEADOW */ {"Sunny Meadow", {126,207,255,255},{186,227,255,255},{106,190,110,255},{ 60,160, 90,255},{255,214, 92,255},{ 60,175,140,255},0},
 /* DUSK   */ {"Coral Dusk",   {255,140,105,255},{255,190,150,255},{130, 90,140,255},{200,110,120,255},{255,214, 92,255},{184, 72,110,255},0},
 /* NIGHT  */ {"Neon Night",   { 16, 18, 46,255},{ 34, 34, 72,255},{ 26, 30, 58,255},{ 70, 80,150,255},{ 39,193,176,255},{ 39,120,190,255},0},
 /* VOLCANO*/ {"Ember Volcano",{ 46, 18, 22,255},{ 84, 30, 28,255},{ 38, 20, 20,255},{120, 40, 30,255},{255,120, 40,255},{ 60, 34, 36,255},1},
};
static int biome=0; static float colBlend=1, biomeTravel=0;
#define BMLEN 520.0f
static Color cSky,cFog,cGrnd,cDeco,cAcc,cPipe;

static void biomePull(float dt){
    float t=clampf(colBlend,0,1);
    const Biome*b=&BMS[biome];
    cSky =LC(cSky ,b->sky ,clampf(dt*2,0,1));
    cFog =LC(cFog ,b->fog ,clampf(dt*2,0,1));
    cGrnd=LC(cGrnd,b->grnd,clampf(dt*2,0,1));
    cDeco=LC(cDeco,b->deco,clampf(dt*2,0,1));
    cAcc =LC(cAcc ,b->acc ,clampf(dt*2,0,1));
    cPipe=LC(cPipe,b->pipe,clampf(dt*2,0,1));
    (void)t;
}
static void biomeSet(int b){ biome=b%4; }

/* -------------------------------------------------------------------- skins */
typedef struct { const char*n; int price; Color body,belly,beak,wing,acc; } Skin;
static const Skin SK[8]={
 {"Classic",   0,  {255,196, 60,255},{255,236,180,255},{255,122, 47,255},{255,160, 40,255},{255,255,255,255}},
 {"Skyline",   400,{ 90,160,255,255},{200,225,255,255},{255,255,255,255},{ 60,110,220,255},{ 39,193,176,255}},
 {"Topaz",     700,{ 39,193,176,255},{220,255,245,255},{ 26,110,100,255},{ 26,150,140,255},{255,201, 77,255}},
 {"Rosebud",   950,{255, 99,132,255},{255,214,222,255},{255,214, 92,255},{230, 70,100,255},{255,255,255,255}},
 {"Inkwell",   1200,{45, 45, 60,255},{120,120,140,255},{255,122, 47,255},{ 26, 26, 36,255},{ 39,193,176,255}},
 {"Volcano",   1500,{255, 90, 40,255},{255,190,120,255},{ 40, 26, 26,255},{200, 60, 30,255},{255,214, 92,255}},
 {"Frostbite", 1800,{225,245,255,255},{255,255,255,255},{140,190,255,255},{180,215,255,255},{ 60,130,255,255}},
 {"Golden",    2500,{255,201, 77,255},{255,240,200,255},{255,255,255,255},{255,170, 40,255},{255,255,210,255}},
};

/* ----------------------------------------------------------------- entities */
typedef struct {
    int kind;      /* 0 pillars 1 slider 2 rotor 3 ring gate */
    float x,y,z;   /* z is spawn depth (negative, moves to +) */
    float gapY, gapH;
    float amp,ph,sp;   /* slider motion */
    float spin, spinV; /* rotor */
    float r;           /* ring radius */
    int scored, nCoin, coinGot;
} Obs;
typedef struct { float x,y,vy,tx,tilt,wing; int shield; float alive; } Bird;
typedef struct { float x,y,z; int on; } Coin;
typedef struct { int kind; float x,y,z; int on; } Pw;   /* 0 shield 1 magnet 2 slow 3 dbl */
typedef struct { int type; Vector3 p; float s,rot; } Dec;

enum { ST_SPLASH=0, ST_MENU, ST_READY, ST_PLAY, ST_PAUSE, ST_DYING, ST_OVER,
       ST_SHOP, ST_SET, ST_MSN };

static Bird bird;
static Obs obs[OBSN];
static Coin coins[CON];
static Pw pws[PWN];
static Dec dec[DMAX];
static float spd, lastBase, farZ, travel, stopT, slowmo, shake, overT, skyT;
static int score, stage, countN, runCoins, runPerf, streak, curSkin, countT, medalT;
static float shieldT, magT, slowT, dblT, hudPulse, toastT, revived;
static char toastTxt[64]; static Color toastC;
static int state=ST_SPLASH;
static Camera3D cam;

/* buttons (screen space) */
typedef struct { Rectangle r; const char*lb; int id; Color c; } Btn;
#define MAXB 24
static Btn btns[MAXB]; static int nBtns=0;

/* ====================================================== UI primitives ===== */
static void addBtn(float x,float y,float w,float h,const char*lb,int id,Color c){
    if(nBtns>=MAXB) return;
    Btn*b=&btns[nBtns++];
    b->r.x=x;b->r.y=y;b->r.width=w;b->r.height=h;b->lb=lb;b->id=id;b->c=c;
}
static int hitBtn(Vector2 p){
    for(int i=nBtns-1;i>=0;i--)
        if(CheckCollisionPointRec(p,btns[i].r)) return btns[i].id;
    return -1;
}
static void drawBtns(void){
    int fs=GetScreenHeight()/24;
    for(int i=0;i<nBtns;i++){
        Btn*b=&btns[i];
        if(b->c.a==0) continue;
        Rectangle sh=b->r; sh.y+=fs*0.18f;
        DrawRectangleRounded(sh,0.35f,8,LC(b->c,INK,0.55f));
        DrawRectangleRounded(b->r,0.35f,8,b->c);
        DrawRectangleRounded(b->r,0.55f,8,LC(b->c,CREAM,0.22f));
        Vector2 ts=MeasureTextEx(GetFontDefault(),b->lb,(float)fs,fs/10.0f);
        DrawTextEx(GetFontDefault(),b->lb,
            (Vector2){b->r.x+b->r.width/2-ts.x/2,b->r.y+b->r.height/2-ts.y/2},
            (float)fs,fs/10.0f,INK);
    }
}
static void toast(const char*t,Color c){ snprintf(toastTxt,sizeof toastTxt,"%s",t); toastC=c; toastT=2.2f; }

/* touch/mouse unified */
static Vector2 tPos; static int tDownPrev=0, tJust=0, tRel=0; static float tHoldT=0;
static void pollTouch(void){
    int n=GetTouchPointCount(); int d=0; Vector2 p={-1,-1};
    if(n>0){ p=GetTouchPosition(0); d=1; }
    else if(IsMouseButtonDown(MOUSE_BUTTON_LEFT)){ p=GetMousePosition(); d=1; }
    tJust = d&&!tDownPrev; tRel=!d&&tDownPrev;
    if(d) tPos=p; 
    if(tJust) tHoldT=0; else if(d) tHoldT+=GetFrameTime();
    tDownPrev=d;
}

/* ==================================================== mission engine ====== */
static int msnGen=0;
static void msnNew(int slot){
    Msn *m=&S.msn[slot];
    int g=msnGen++;
    m->done=0; m->prog=0;
    int t=(slot+rnd32())%4;
    switch(t){
    case 0: m->type=0; m->goal=10+g*5; break;                 /* score in one run */
    case 1: m->type=1; m->goal=15+g*8; break;                 /* coins total      */
    case 2: m->type=2; m->goal=1+(g%4); break;                /* reach biome N    */
    default:m->type=3; m->goal=2+(g%4); break;                /* fly N runs       */
    }
}
static const char* msnText(int i,char out[96]){
    Msn*m=&S.msn[i];
    switch(m->type){
    case 0: snprintf(out,96,"Score %d in one run",m->goal); break;
    case 1: snprintf(out,96,"Collect %d coins",m->goal); break;
    case 2: snprintf(out,96,"Reach biome %d",m->goal); break;
    default:snprintf(out,96,"Fly %d runs",m->goal); break;
    }
    return out;
}
static int msnReward(int i){ return 60+S.msn[i].goal; }
static void msnCheck(void){
    for(int i=0;i<3;i++){
        Msn*m=&S.msn[i];
        if(m->done||m->prog<m->goal) continue;
        m->done=1;
        int r=msnReward(i);
        S.coins+=r;
        char t[80]; snprintf(t,80,"Mission done! +%d coins",r);
        toast(t,AURUM);
        m->done=2;               /* mark replaceable */
    }
    /* replace finished missions next menu visit */
}
static void msnReplace(void){
    for(int i=0;i<3;i++) if(S.msn[i].done==2) msnNew(i);
}

/* ==================================================== world =============== */
static float groundY(void){ return FLOORY; }

static void spawnObs(float z){
    /* find free slot: z far behind camera (z > 10) */
    for(int i=0;i<OBSN;i++){
        if(obs[i].z>12||obs[i].kind<0){
            Obs*o=&obs[i];
            o->kind=ri(0,3)%4;
            o->z=z; o->scored=0; o->coinGot=0;
            if(o->kind==0||o->kind==1){
                o->x=rr(-OP+2.2f,OP-2.2f);
                o->gapH=clampf(4.6f-score*0.03f,3.0f,4.6f);
                o->gapY=rr(FLOORY+o->gapH/2+0.7f,12.5f);
                o->nCoin=rf()<0.5f?5:0;
                if(o->kind==1){ o->amp=rr(1.0f,2.6f); o->ph=rr(0,TPI*2); o->sp=rr(0.8f,1.6f); }
            }else if(o->kind==2){
                o->x=rr(-OP+2.5f,OP-2.5f);
                o->gapY=rr(2.5f,10.0f);
                o->spin=rr(0,TPI*2); o->spinV=rr(1.2f,2.2f)*(rf()<0.5f?-1:1);
                o->nCoin=4;
            }else{
                o->x=rr(-OP+2.5f,OP-2.5f);
                o->gapY=rr(2.5f,10.5f);
                o->r=2.3f;
                o->nCoin=4;
            }
            return;
        }
    }
}
static void spawnCoins(float z,float cx,float cy,int n){
    for(int j=0;j<CON;j++){
        if(!coins[j].on){
            coins[j].on=1;
            coins[j].x=cx; coins[j].y=cy; coins[j].z=z-j*2.0f;
            n--; if(n<=0) return;
        }
    }
}
static void spawnPw(float z){
    for(int j=0;j<PWN;j++){
        if(!pws[j].on){
            pws[j].on=1;
            pws[j].kind=ri(0,3)%4;
            pws[j].x=rr(-OP+2.5f,OP-2.5f);
            pws[j].y=rr(3,9);
            pws[j].z=z;
            return;
        }
    }
}
static void initRun(void){
    bird.x=0; bird.y=6; bird.vy=0; bird.tx=0; bird.tilt=0; bird.wing=0; bird.shield=0; bird.alive=1;
    for(int i=0;i<OBSN;i++) obs[i].kind=-1;
    for(int i=0;i<CON;i++) coins[i].on=0;
    for(int i=0;i<PWN;i++) pws[i].on=0;
    spd=SPD0; lastBase=-30; farZ=-30;
    score=0; runCoins=0; runPerf=0; streak=0; countN=0; countT=0; medalT=0;
    shieldT=magT=slowT=dblT=0; revived=0;
    stopT=0; slowmo=0; shake=0;
    biomeSet(0);
    travel=0; biomeTravel=0;
    /* first obstacles */
    for(int i=0;i<6;i++) spawnObs(lastBase-16*i);
}
static void decorInit(void){
    for(int i=0;i<DMAX;i++){
        dec[i].type=(int)(rf()*3); if(dec[i].type>2)dec[i].type=2;
        float side=rf()<0.5f?-1:1;
        float xoff=rr(9,34);
        dec[i].p=V3(side*xoff,groundY(),rr(-120,6));
        dec[i].s=rr(0.7f,2.2f);
        dec[i].rot=rr(0,TPI*2);
    }
}
/* speed / difficulty */
static float curSpd(void){
    float base=lerpf(SPD0,SPD1,clampf(score/40.0f,0,1));
    if(slowT>0) base*=0.55f;
    return base*(slowmo>0?0.3f:1.0f);
}

/* --------------------------------------------------------------- collisions */
static void doShake(float amt){ if(S.shakeOn) shake=fmaxf(shake,amt); }
static void die(void){
    if(state!=ST_PLAY) return;
    if(bird.shield||shieldT>0){
        bird.shield=0; shieldT=0;
        doShake(0.35f); sfxClick();
        emit(V3(bird.x,bird.y,0),V3(0,2,0),0.5f,0.3f,TEAL,14);
        toast("Shield saved you!",TEAL);
        /* clear the nearby obstacle */
        for(int i=0;i<OBSN;i++) if(obs[i].kind>=0&&obs[i].z>-4&&obs[i].z<4) obs[i].kind=-1;
        return;
    }
    state=ST_DYING;
    stopT=0.07f; slowmo=0.65f; doShake(0.55f);
    sfxHit();
    emit(V3(bird.x,bird.y,0),V3(0,4,0),0.9f,0.4f,AURUM,26);
    emit(V3(bird.x,bird.y,0),V3(0,2,0),0.7f,0.3f,ROSEC,18);
    if(score>S.best){ S.best=score; }
    S.runs++; S.totCoins+=(long long)runCoins;
    Msn m0bak=S.msn[0];
    (void)m0bak;
    saveWrite();
}
static void finishRun(void){
    state=ST_OVER;
    overT=0; countT=0; medalT=0;
    for(int i=0;i<3;i++){
        Msn*m=&S.msn[i];
        if(m->done) continue;
        if(m->type==0&&score>=m->goal) m->prog=score;
        if(m->type==3) m->prog++;              /* runs fly */
        if(m->type==2){ int pd=biome; if(pd+1>m->prog) m->prog=pd+1; }
    }
    S.coins+=runCoins;
    if(S.totCoins<0) S.totCoins=0;
    saveWrite();
    msnCheck();
}

/* obstacle world-x (sliders move) */
static float obsX(Obs*o,float t){
    if(o->kind==1) return clampf(o->x+o->amp*sinf(o->ph+t*o->sp),-OP+1.5f,OP-1.5f);
    return o->x;
}

/* player update during PLAY */
static void updateBird(float dt,float t){
    /* steering */
    if(tDownPrev&&state==ST_PLAY){
        float sx=(float)GetScreenWidth();
        bird.tx=((tPos.x/sx)*2-1)*OP*0.92f;
    }
    bird.x=lerpf(bird.x,bird.tx,clampf(dt*6,0,1));
    bird.x=clampf(bird.x,-OP+0.8f,OP-0.8f);

    bird.vy-=G*dt;
    if(bird.vy<-22) bird.vy=-22;
    bird.y+=bird.vy*dt;

    /* flap input */
    if(tJust&&state==ST_PLAY){
        bird.vy=FLAPV; sfxFlap(); hudPulse=1;
        emit(V3(bird.x,bird.y-0.4f,0),V3(0,-1,2),0.4f,0.18f,CREAM,3);
    }
    if(IsKeyPressed(KEY_SPACE)){ bird.vy=FLAPV; sfxFlap(); }

    bird.tilt=clampf(bird.vy*0.045f,-0.7f,0.55f);
    bird.wing+=dt*(bird.vy<0?18:10);

    if(bird.y>CEILY){ bird.y=CEILY; bird.vy=fminf(bird.vy,0); }
}

/* collisions + rewards */
static void collide(Obs*o,float t,float dt){
    float bx=bird.x, by=bird.y;
    if(o->kind==0||o->kind==1){
        if(o->z>-1.6f&&o->z<1.6f){
            float ox=obsX(o,t);
            if(fabsf(bx-ox)<1.9f&&
               (by<o->gapY-o->gapH/2||by>o->gapY+o->gapH/2)){ die(); }
        }
        if(!o->scored&&o->z>1.2f){
            o->scored=1; score+=(dblT>0?2:1);
            streak++;
            sfxScore(); hudPulse=1;
        }
        if(o->coinGot==0&&o->nCoin>0&&o->z>2.0f){
            o->coinGot=1; spawnCoins(o->z-12,obsX(o,t),o->gapY,o->nCoin);
        }
    }else if(o->kind==2){ /* rotor */
        if(o->z>-1.2f&&o->z<1.2f){
            float dx=bx-o->x, dy=by-o->gapY;
            /* rotating beam in the XY plane, half-length 3.6 */
            float ca=cosf(o->spin), sa=sinf(o->spin);
            float pxl= dx*ca + dy*sa;   /* along beam */
            float pyl=-dx*sa + dy*ca;   /* across beam */
            if(fabsf(pxl)<3.6f&&fabsf(pyl)<0.55f){ die(); }
        }
        if(!o->scored&&o->z>1.2f){
            o->scored=1; score+=(dblT>0?2:1); sfxScore();
        }
        if(o->coinGot==0&&o->nCoin>0&&o->z>2.0f){
            o->coinGot=1; spawnCoins(o->z-12,o->x,o->gapY+3.0f,o->nCoin);
        }
    }else{ /* ring gate: bonus pass, frame not lethal */
        if(o->z>-1.1f&&o->z<1.1f&&!o->scored){
            float dx=bx-o->x, dy=by-o->gapY;
            float d2=dx*dx+dy*dy;
            if(d2<(o->r-0.55f)*(o->r-0.55f)){
                o->scored=1; runPerf++; hudPulse=1;
                int bonus=3*(dblT>0?2:1);
                score+=bonus;
                toast("PERFECT! +3",TEAL);
                sfxRing();
                emit(V3(o->x,o->gapY,0),V3(0,0,0),0.6f,0.3f,TEAL,12);
            }
        }
    }
}


/* ==================================================== per-frame logic ===== */
static float gT=0;
static int tapUsed=0;
static int wipeArm=0;

static void updateWorld(float dt){
    float t=gT;
    float sdt=dt*(slowmo>0?0.3f:1.0f);
    float sp=curSpd();
    travel+=sp*sdt;
    biomeTravel+=sp*sdt;
    if(biomeTravel>BMLEN){
        biomeTravel=0;
        int nb=(biome+1)%4;
        biomeSet(nb);
        for(int i=0;i<3;i++){ Msn*m=&S.msn[i]; if(!m->done&&m->type==2&&nb+1>m->prog) m->prog=nb+1; }
        toast(BMS[nb].name,cAcc);
        msnCheck();
    }

    /* spawn ahead */
    static float spawnAcc=0;
    int ahead=0; for(int i=0;i<OBSN;i++) if(obs[i].kind>=0&&obs[i].z<-10) ahead++;
    spawnAcc+=sp*sdt;
    if(spawnAcc>16.0f){ spawnAcc=0; if(ahead<6) spawnObs(-140); }

    /* power-ups drop occasionally */
    static float pwAcc=0;
    pwAcc+=sp*sdt;
    if(pwAcc>90.0f){ pwAcc=0; spawnPw(-140); }

    /* move obstacles */
    for(int i=0;i<OBSN;i++){
        if(obs[i].kind<0) continue;
        obs[i].z+=sp*sdt;
        if(obs[i].kind==2) obs[i].spin+=obs[i].spinV*sdt;
        collide(&obs[i],t,sdt);
        if(obs[i].z>16) obs[i].kind=-1;
    }
    /* coins */
    for(int i=0;i<CON;i++){
        if(!coins[i].on) continue;
        coins[i].z+=sp*sdt;
        if(magT>0){
            float dx=bird.x-coins[i].x, dy=bird.y-coins[i].y;
            float d=sqrtf(dx*dx+dy*dy);
            if(d<8.0f&&d>0.01f&&coins[i].z>-2&&coins[i].z<8){
                coins[i].x+=dx/d*16*sdt; coins[i].y+=dy/d*16*sdt;
            }
        }
        if(coins[i].z>-1.8f&&coins[i].z<1.8f){
            float dx=coins[i].x-bird.x, dy=coins[i].y-bird.y;
            if(dx*dx+dy*dy<1.4f){
                coins[i].on=0; runCoins++; hudPulse=1; sfxCoin();
                emit(V3(coins[i].x,coins[i].y,0),V3(0,1,0),0.35f,0.2f,AURUM,4);
                for(int k=0;k<3;k++){ Msn*m=&S.msn[k]; if(!m->done&&m->type==1) m->prog++; }
                msnCheck();
            }
        }
        if(coins[i].z>10) coins[i].on=0;
    }
    /* powerups */
    for(int i=0;i<PWN;i++){
        if(!pws[i].on) continue;
        pws[i].z+=sp*sdt;
        if(pws[i].z>-1.9f&&pws[i].z<1.9f){
            float dx=pws[i].x-bird.x, dy=pws[i].y-bird.y;
            if(dx*dx+dy*dy<2.4f){
                static const char*pn[4]={"SHIELD!","MAGNET!","SLOW-MO!","2X POINTS!"};
                pws[i].on=0; sfxPower(); toast(pn[pws[i].kind],TEAL);
                emit(V3(pws[i].x,pws[i].y,0),V3(0,2,0),0.6f,0.3f,TEAL,14);
                switch(pws[i].kind){
                case 0: shieldT=10; bird.shield=1; break;
                case 1: magT=9; break;
                case 2: slowT=7; break;
                default: dblT=9; break;
                }
            }
        }
        if(pws[i].z>10) pws[i].on=0;
    }
    if(shieldT>0){ shieldT-=dt; if(shieldT<=0) bird.shield=0; }
    if(magT>0)  magT-=dt;
    if(slowT>0) slowT-=dt;
    if(dblT>0)  dblT-=dt;

    /* floor */
    if(state==ST_PLAY&&bird.y<FLOORY+0.5f){ bird.y=FLOORY+0.5f; die(); }

    /* decor scroll */
    for(int i=0;i<DMAX;i++){
        dec[i].p.z+=sp*sdt;
        if(dec[i].p.z>14){
            dec[i].p.z=-rr(100,140);
            dec[i].p.x=(rf()<0.5f?-1:1)*rr(9,34);
            dec[i].type=(int)(rf()*3);
        }
    }
    if(state==ST_PLAY||state==ST_READY) updateBird(dt,t);
}

static void updateCam(void){
    float bob=sinf(gT*2.2f)*0.15f;
    float sx=shake>0?rr(-1,1)*shake*0.7f:0;
    float sy=shake>0?rr(-1,1)*shake*0.7f:0;
    if(state==ST_MENU){
        float a=gT*0.25f;
        cam.position=V3(sinf(a)*13,7.5f+bob,12);
        cam.target  =V3(0,5,-6);
    }else{
        cam.position=V3(bird.x*0.55f+sx,bird.y*0.45f+2.6f+bob+sy,8.5f);
        cam.target  =V3(bird.x*0.78f,bird.y*0.5f,-16);
    }
    cam.up=V3(0,1,0);
    if(shake>0){ shake-=GetFrameTime()*1.4f; if(shake<0)shake=0; }
}

/* ==================================================== drawing (3D) ======== */
static void drawBird(void){
    const Skin*sk=&SK[curSkin];
    rlPushMatrix();
    rlTranslatef(bird.x,bird.y,0);
    rlRotatef(bird.tilt*57.3f,0,0,1);
    float sq=clampf(1-bird.vy*0.012f,0.82f,1.18f);
    rlScalef(fminf(1.0f/sq,1.3f),sq,1.0f);
    /* tail */
    rlPushMatrix(); rlTranslatef(0.55f,0.05f,0.2f); rlRotatef(25,0,1,0);
    DrawCube(V3(0,0,0),0.5f,0.14f,0.3f,sk->wing); rlPopMatrix();
    /* body + belly */
    DrawSphere(V3(0,0,0),BR,sk->body);
    DrawSphere(V3(0,-0.16f,-0.12f),0.38f,sk->belly);
    /* wings */
    float wa=sinf(bird.wing)*0.9f;
    for(int s=-1;s<=1;s+=2){
        rlPushMatrix();
        rlTranslatef(s*0.42f,0.08f,0);
        rlRotatef(wa*s,1,0,0);
        DrawCube(V3(s*0.30f,0,-0.02f),0.62f,0.09f,0.4f,sk->wing);
        rlPopMatrix();
    }
    /* beak */
    rlPushMatrix(); rlTranslatef(0,-0.02f,-0.48f); rlRotatef(90,1,0,0);
    DrawCylinder(V3(0,0,0),0.06f,0.17f,0.34f,10,sk->beak);
    rlPopMatrix();
    /* eyes */
    DrawSphere(V3(0.16f,0.2f,-0.38f),0.13f,WHITE);
    DrawSphere(V3(-0.16f,0.2f,-0.38f),0.13f,WHITE);
    DrawSphere(V3(0.16f,0.2f,-0.48f),0.06f,INK);
    DrawSphere(V3(-0.16f,0.2f,-0.48f),0.06f,INK);
    /* shield bubble */
    if(bird.shield||shieldT>0) DrawSphere(V3(0,0,0),1.05f,LC(TEAL,WHITE,0.35f));
    rlPopMatrix();
}
static void drawObs(Obs*o){
    float t=gT;
    Color pc =fogCol(cPipe,o->z,cFog);
    Color pcd=fogCol(LC(cPipe,INK,0.45f),o->z,cFog);
    Color ac =fogCol(cAcc,o->z,cFog);
    if(o->kind==0||o->kind==1){
        float ox=obsX(o,t);
        float topH =(o->gapY+o->gapH/2)-FLOORY;
        float lowGap=o->gapY-o->gapH/2;
        DrawCube(V3(ox,FLOORY+topH/2,o->z),1.7f,topH,1.7f,pc);
        DrawCube(V3(ox,FLOORY+topH-0.35f,o->z),2.1f,0.7f,2.1f,pcd);
        DrawCube(V3(ox,lowGap/2,o->z),1.7f,-lowGap,1.7f,pc);
        DrawCube(V3(ox,lowGap+0.35f,o->z),2.1f,0.7f,2.1f,pcd);
    }else if(o->kind==2){
        DrawCylinder(V3(o->x,FLOORY-1.0f,o->z),0.4f,0.5f,1.2f,10,pcd);
        rlPushMatrix();
        rlTranslatef(o->x,o->gapY,o->z);
        rlRotatef(o->spin*57.3f,0,0,1);
        DrawCube(V3(0,0,0),7.2f,0.65f,0.65f,pc);
        DrawCube(V3(3.3f,0,0),0.7f,0.9f,0.9f,ac);
        DrawCube(V3(-3.3f,0,0),0.7f,0.9f,0.9f,ac);
        rlPopMatrix();
    }else{
        rlPushMatrix();
        rlTranslatef(o->x,o->gapY,o->z);
        for(int i=0;i<20;i++){
            float a=TPI*2*i/20;
            DrawCube(V3(cosf(a)*o->r,sinf(a)*o->r,0),0.5f,0.5f,0.5f,(i%2)?ac:pc);
        }
        rlPopMatrix();
    }
}
static void drawWorld(void){
    ClearBackground(cSky);
    BeginMode3D(cam);
    DrawPlane(V3(0,FLOORY-0.02f,-40),(Vector2){120,260},cGrnd);
    /* speed stripes */
    {
        float off=fmodf(travel,10.0f);
        for(int i=0;i<16;i++){
            float z=8.0f-i*10.0f+off;
            DrawCube(V3(0,FLOORY-0.01f,z),0.35f,0.05f,4.0f,
                     fogCol(LC(cGrnd,CREAM,0.35f),z,cFog));
        }
    }
    /* decor */
    int decMax=S.qual==0?DMAX/2:(S.qual==1?DMAX-8:DMAX);
    for(int i=0;i<decMax;i++){
        Dec*d=&dec[i];
        Vector3 p=d->p; float s=d->s;
        Color dc =fogCol(cDeco,p.z,cFog);
        Color dcd=fogCol(LC(cDeco,INK,0.5f),p.z,cFog);
        if(d->type==0){
            DrawCylinder(p,0.22f*s,0.30f*s,2.2f*s,7,dcd);
            DrawSphere(V3(p.x,p.y+2.4f*s,p.z),1.1f*s,fogCol(LC(cDeco,CREAM,0.25f),p.z,cFog));
            DrawSphere(V3(p.x+0.5f*s,p.y+1.7f*s,p.z),0.8f*s,dc);
        }else if(d->type==1){
            DrawSphereEx(p,0.9f*s,3,6,dc);
            DrawSphereEx(V3(p.x+0.7f*s,p.y-0.3f,p.z+0.4f),0.55f*s,3,6,dcd);
        }else{
            DrawCylinder(p,0.05f,0.65f*s,3.2f*s,5,fogCol(cAcc,p.z,cFog));
            DrawCylinder(V3(p.x+0.9f*s,p.y,p.z-0.6f),0.05f,0.35f*s,2.1f*s,5,dc);
        }
        if(BMS[biome].embers&&S.qual>0&&(i&3)==0)
            emit(V3(p.x,p.y+1,p.z),V3(0,rr(2,5),1),1.2f,0.16f,ORG,1);
    }
    for(int i=0;i<OBSN;i++) if(obs[i].kind>=0) drawObs(&obs[i]);
    for(int i=0;i<CON;i++){
        if(!coins[i].on) continue;
        float pl=0.30f+0.05f*sinf(gT*6+i);
        DrawSphereEx(V3(coins[i].x,coins[i].y,coins[i].z),pl,4,10,fogCol(AURUM,coins[i].z,cFog));
    }
    for(int i=0;i<PWN;i++){
        if(!pws[i].on) continue;
        static const Color pcol[4]={{39,193,176,255},{255,99,132,255},{90,140,255,255},{255,201,77,255}};
        Color cc=fogCol(pcol[pws[i].kind],pws[i].z,cFog);
        Vector3 q=V3(pws[i].x,pws[i].y+sinf(gT*3)*0.3f,pws[i].z);
        rlPushMatrix(); rlTranslatef(q.x,q.y,q.z); rlRotatef(gT*70,0,1,0);
        DrawCubeWires(V3(0,0,0),0.95f,0.95f,0.95f,cc);
        DrawSphere(V3(0,0,0),0.32f,cc);
        rlPopMatrix();
    }
    partsDraw(cFog);
    if(state!=ST_MENU) drawBird();
    EndMode3D();
}

/* ================================================= 2D screens / HUD ====== */
enum { B_PLAY=1,B_SHOP,B_SET,B_MSN,B_BACK,B_RESUME,B_MENU,B_RETRY,B_REVIVE,
       B_SND,B_MUS,B_QUAL,B_SHAKE,B_WIPE,B_PAUSE,B_SKIN0=100 };

static void centerTitle(const char*t,int y,int size,Color c){
    int W=GetScreenWidth();
    DrawText(t,(W-MeasureText(t,size))/2,y,size,c);
}
static void panel(float cx,float cy,float w,float h){
    Color pc=CREAM; pc.a=235;
    DrawRectangleRounded((Rectangle){cx-w/2,cy-h/2,w,h},0.12f,10,pc);
    Color ed=INK; ed.a=150;
    DrawRectangleRoundedLines((Rectangle){cx-w/2,cy-h/2,w,h},0.12f,10,ed);
}
static void hudBar(void){
    int W=GetScreenWidth(),H=GetScreenHeight();
    int fs=H/18, ss=H/9;
    const char*txt=TextFormat("%d",score);
    int sb=(int)(ss*(1+hudPulse*0.06f));
    DrawText(txt,(W-MeasureText(txt,sb))/2+3,H/12+3,sb,LC(WHITE,INK,0.65f));
    DrawText(txt,(W-MeasureText(txt,sb))/2,H/12,sb,WHITE);
    DrawText(TextFormat("x %d",runCoins),20,H/12,fs,AURUM);
    DrawText(TextFormat("Best %d",S.best),20,H/12+fs*7/4,fs,CREAM);
    struct { float t; Color c; float max; } bts[4]={{shieldT,{39,193,176,255},10},{magT,ROSEC,9},{slowT,{90,140,255,255},7},{dblT,AURUM,9}};
    int k=0;
    for(int i=0;i<4;i++) if(bts[i].t>0){
        float frac=clampf(bts[i].t/bts[i].max,0,1);
        DrawRectangleRounded((Rectangle){20,H/12+fs*7/4+fs+fs*2*k,150*frac,12},0.5f,7,bts[i].c);
        k++;
    }
}
static void uiMenu(void){
    int W=GetScreenWidth(),H=GetScreenHeight();
    int bh=H/8;
    float bw=W*0.55f;
    centerTitle("TIPSY BIRD",(int)(H*0.08f),H/8,AURUM);
    centerTitle("U L T R A",(int)(H*0.08f+H/8+8),H/15,CREAM);
    centerTitle(TextFormat("World: %s",BMS[biome].name),(int)(H*0.08f+H/8+H/16+8),H/30,cAcc);
    addBtn(W/2-bw/2,H*0.42f,bw,bh,"FLY!",B_PLAY,ORG);
    addBtn(W/2-bw/2-bw*0.02f-H*0.14f,H*0.58f,bw*0.48f,bh*0.85f,"Shop",B_SHOP,TEAL);
    addBtn(W/2+bw*0.02f+H*0.14f-bw*0.48f,H*0.58f,bw*0.48f,bh*0.85f,"Missions",B_MSN,AURUM);
    addBtn(W/2-bw/2,H*0.58f+bh+bh*0.12f,bw,bh*0.8f,"Settings",B_SET,INK);
    DrawText(TextFormat("x %d",S.coins),20,20,H/20,AURUM);
    DrawText(TextFormat("Best %d",S.best),20,20+H/20,H/22,CREAM);
}
static void uiReady(void){
    int H=GetScreenHeight();
    centerTitle("Tap to flap",H/2-H/16,H/12,CREAM);
    centerTitle("Hold & drag left / right to steer",H/2+H/18,H/32,LC(CREAM,INK,0.35f));
    hudBar();
}
static void uiPause(void){
    int W=GetScreenWidth(),H=GetScreenHeight();
    panel(W/2,H/2,W*0.5f,H*0.55f);
    centerTitle("PAUSED",H*0.30f,H/10,INK);
    float bw=W*0.34f,bh=H/9;
    addBtn(W/2-bw/2,H*0.42f,bw,bh,"Resume",B_RESUME,TEAL);
    addBtn(W/2-bw/2,H*0.58f,bw,bh,"Quit to menu",B_MENU,INK);
}
static void uiOver(void){
    int W=GetScreenWidth(),H=GetScreenHeight();
    panel(W/2,H*0.40f,W*0.66f,H*0.56f);
    centerTitle("GAME OVER",H*0.17f,H/12,INK);
    if(countT<1){ countT+=GetFrameTime()*1.6f; if(countT>1)countT=1; }
    int shown=(int)lerpf(0,score,countT);
    centerTitle(TextFormat("%d",shown),H*0.25f,H/7,ORG);
    centerTitle(TextFormat("Best %d    Coins +%d",S.best,runCoins),H*0.37f,H/26,LC(INK,CREAM,0.35f));
    if(medalT<1){ medalT+=GetFrameTime()*0.8f; if(medalT>1)medalT=1; }
    const char*med=score>=40?"AURUM MEDAL":score>=25?"SILVER MEDAL":score>=12?"BRONZE MEDAL":0;
    if(med&&medalT>0.5f){
        Color mc=score>=40?AURUM:(score>=25?(Color){200,200,210,255}:(Color){205,127,50,255});
        centerTitle(med,H*0.44f,(int)(H/26*medalT),mc);
    }
    float bw=W*0.27f,bh=H/9;
    int canRev=(revived<0.5f)&&(S.coins>=REV_COST);
    if(canRev) addBtn(W*0.09f,H*0.78f,bw,bh,TextFormat("Revive %dc",REV_COST),B_REVIVE,TEAL);
    addBtn(canRev?W*0.09f+bw+W*0.015f:W*0.37f,H*0.78f,bw,bh,"Retry",B_RETRY,ORG);
    addBtn(W*0.63f+bw*0.02f,H*0.78f,bw,bh,"Menu",B_MENU,INK);
}
static void uiShop(void){
    int W=GetScreenWidth(),H=GetScreenHeight();
    centerTitle("SHOP",H/16,H/12,CREAM);
    const char*cc=TextFormat("x %d",S.coins);
    DrawText(cc,W-MeasureText(cc,H/18)-20,H/14,H/18,AURUM);
    int cols=W>900?4:2;
    float cw=W*0.20f,ch=H*0.30f,gap=W*0.025f;
    float x0=(W-(cw*cols+gap*(cols-1)))/2;
    for(int i=0;i<8;i++){
        int c=i%cols,r=i/cols;
        float x=x0+c*(cw+gap), y=H*0.20f+r*(ch+H*0.04f);
        const Skin*sk=&SK[i];
        int owned=(S.owned>>i)&1;
        int eq=S.skin==i;
        Color card=CREAM; card.a=225;
        DrawRectangleRounded((Rectangle){x,y,cw,ch},0.12f,10,card);
        if(eq) DrawRectangleRoundedLines((Rectangle){x,y,cw,ch},0.12f,10,AURUM);
        float px=x+cw/2,py=y+ch*0.30f;
        DrawCircle((int)px,(int)py,ch*0.13f,sk->body);
        DrawCircle((int)px,(int)(py+ch*0.05f),ch*0.09f,sk->belly);
        DrawCircle((int)(px-ch*0.09f),(int)(py+ch*0.01f),ch*0.045f,sk->beak);
        DrawCircle((int)(px+ch*0.09f),(int)(py+ch*0.01f),ch*0.045f,sk->wing);
        DrawText(sk->n,(int)(px-MeasureText(sk->n,H/40)/2),(int)(y+ch*0.55f),H/40,INK);
        char lb[24];
        if(eq)         strcpy(lb,"EQUIPPED");
        else if(owned) strcpy(lb,"EQUIP");
        else           snprintf(lb,sizeof lb,"%d c",sk->price);
        DrawText(lb,(int)(px-MeasureText(lb,H/42)/2),(int)(y+ch*0.72f),H/42,
                 (owned&&!eq)?TEAL:LC(INK,CREAM,0.35f));
        if(!eq) addBtn(x,y,cw,ch,lb,B_SKIN0+i,BLANK);
    }
    addBtn(20,H-90,140,70,"Back",B_BACK,ORG);
}
static void uiSet(void){
    int W=GetScreenWidth(),H=GetScreenHeight();
    panel(W/2,H/2,W*0.6f,H*0.72f);
    centerTitle("SETTINGS",H*0.18f,H/12,INK);
    float bw=W*0.40f,bh=H/10;
    addBtn(W/2-bw-20,H*0.30f,bw,bh,TextFormat("Sound  %d%%",S.sfxV),B_SND,TEAL);
    addBtn(W/2+20   ,H*0.30f,bw,bh,TextFormat("Music  %d%%",S.musV),B_MUS,TEAL);
    addBtn(W/2-bw-20,H*0.44f,bw,bh,TextFormat("Quality %s",S.qual==2?"HIGH":S.qual==1?"MED":"LOW"),B_QUAL,AURUM);
    addBtn(W/2+20   ,H*0.44f,bw,bh,TextFormat("Shake  %s",S.shakeOn?"ON":"OFF"),B_SHAKE,INK);
    addBtn(W/2-bw/2,H*0.60f,bw,bh,wipeArm?"REALLY RESET?":"Reset Save",B_WIPE,ROSEC);
    centerTitle("Tap Sound / Music to lower by 25%",H*0.75f,H/34,LC(INK,CREAM,0.35f));
    addBtn(20,H-90,140,70,"Back",B_BACK,ORG);
}
static void uiMsn(void){
    int W=GetScreenWidth(),H=GetScreenHeight();
    panel(W/2,H*0.45f,W*0.7f,H*0.62f);
    centerTitle("MISSIONS",H*0.18f,H/12,INK);
    char buf[96];
    float bw=W*0.4f;
    for(int i=0;i<3;i++){
        Msn*m=&S.msn[i];
        Color c=m->done?TEAL:LC(INK,CREAM,0.4f);
        centerTitle(msnText(i,buf),(int)(H*0.28f+i*H*0.09f),H/28,c);
        float frac=clampf((float)m->prog/m->goal,0,1);
        DrawRectangleRounded((Rectangle){W/2-bw/2,H*0.33f+i*H*0.09f,bw,18},0.5f,6,LC(CREAM,INK,0.15f));
        DrawRectangleRounded((Rectangle){W/2-bw/2,H*0.33f+i*H*0.09f,bw*frac,18},0.5f,6,AURUM);
        DrawText(TextFormat("%d/%d  +%dc",m->prog,m->goal,msnReward(i)),
                 (int)(W/2+bw/2+10),(int)(H*0.335f+i*H*0.09f),H/36,c);
    }
    addBtn(20,H-90,140,70,"Back",B_BACK,ORG);
}
static void uiToast(void){
    float a=clampf(toastT,0,1);
    Color c=toastC; c.a=(unsigned char)(255*a);
    centerTitle(toastTxt,(int)(GetScreenHeight()*0.66f),GetScreenHeight()/22,c);
}

/* ================================================ state actions ========== */
static void startGame(void){
    initRun();
    state=ST_READY;
    toast("GO!",ORG);
}
static void goMenu(void){
    initRun();
    state=ST_MENU;
    msnReplace();
    saveWrite();
}
static void doBtn(int id){
    sfxClick();
    if(id>=B_SKIN0&&id<B_SKIN0+8){
        int i=id-B_SKIN0;
        int owned=(S.owned>>i)&1;
        if(owned){ curSkin=i; S.skin=i; toast(TextFormat("Equipped %s",SK[i].n),TEAL); }
        else if(S.coins>=SK[i].price){
            S.coins-=SK[i].price; S.owned|=(unsigned short)(1<<i);
            curSkin=i; S.skin=i;
            toast(TextFormat("Unlocked %s!",SK[i].n),AURUM);
        }else toast("Not enough coins",ROSEC);
        saveWrite(); return;
    }
    switch(id){
    case B_PLAY:   startGame(); break;
    case B_MENU:   goMenu(); wipeArm=0; break;
    case B_BACK:   goMenu(); break;
    case B_SHOP:   state=ST_SHOP; break;
    case B_SET:    state=ST_SET; wipeArm=0; break;
    case B_MSN:    state=ST_MSN; break;
    case B_RESUME: state=ST_PLAY; break;
    case B_RETRY:  startGame(); break;
    case B_PAUSE:  state=ST_PAUSE; break;
    case B_REVIVE:
        if(S.coins>=REV_COST){
            S.coins-=REV_COST; revived=1; saveWrite();
            bird.y=6; bird.vy=0; bird.tx=0; state=ST_PLAY;
            shieldT=5; bird.shield=1;
            toast("Revived!",TEAL);
        }else toast("Not enough coins",ROSEC);
        break;
    case B_SND:  S.sfxV-=25; if(S.sfxV<0)S.sfxV=100; saveWrite(); break;
    case B_MUS:  S.musV-=25; if(S.musV<0)S.musV=100; saveWrite(); break;
    case B_QUAL: S.qual=(S.qual+1)%3; saveWrite(); break;
    case B_SHAKE:S.shakeOn=!S.shakeOn; saveWrite(); break;
    case B_WIPE:
        if(!wipeArm){ wipeArm=1; toast("Tap again to RESET save",ROSEC); }
        else{
            memset(&S,0,sizeof S); S.sfxV=80; S.musV=70; S.shakeOn=1; S.owned=1;
            msnGen=0; for(int i=0;i<3;i++)msnNew(i);
            saveWrite(); wipeArm=0; toast("Save reset",ORG);
        }
        break;
    }
}

/* ================================================ main ==================== */
int main(int argc,char**argv){
    (void)argc;(void)argv;
    findSavePath();
    saveRead();
    curSkin=S.skin<8?S.skin:0;
    int nowned=0; for(int i=0;i<8;i++) if((S.owned>>i)&1) nowned++;
    if(nowned==0) S.owned=1;
    if(S.sfxV<=0&&S.musV<=0&&S.best==0&&S.runs==0){ S.sfxV=80;S.musV=70;S.shakeOn=1; }
    int fresh=1; for(int i=0;i<3;i++) if(S.msn[i].goal>0) fresh=0;
    if(fresh){ for(int k=0;k<3;k++) msnNew(k); }

    SetTraceLogLevel(LOG_WARNING);
    SetConfigFlags(FLAG_MSAA_4X_HINT);
    InitWindow(0,0,"Tipsy Bird Ultra");
    SetTargetFPS(60);
    audioInit();
    cSky=BMS[0].sky;cFog=BMS[0].fog;cGrnd=BMS[0].grnd;
    cDeco=BMS[0].deco;cAcc=BMS[0].acc;cPipe=BMS[0].pipe;
    cam.fovy=62.0f; cam.projection=CAMERA_PERSPECTIVE;
    decorInit();
    initRun();
    state=ST_SPLASH;

    while(!WindowShouldClose()){
        float dt=GetFrameTime();
        if(dt>0.1f)dt=0.05f;
        gT+=dt;
        if(stopT>0){ stopT-=dt; dt=0; }
        if(slowmo>0) slowmo-=dt;
        if(toastT>0) toastT-=dt;
        if(hudPulse>0) hudPulse-=dt*3;

        pollTouch();

        /* taps act on buttons laid out last frame */
        tapUsed=0;
        if(tJust){
            int id=hitBtn(tPos);
            if(id>0){ doBtn(id); tapUsed=1; }
        }
        nBtns=0;

        switch(state){
        case ST_SPLASH:
            if(tJust||IsKeyPressed(KEY_SPACE)) state=ST_MENU;
            break;
        case ST_MENU:
            updateWorld(dt*0.2f);
            break;
        case ST_READY:
            bird.y=6.0f+sinf(gT*2.0f)*0.35f;
            if(tJust&&!tapUsed) state=ST_PLAY;
            break;
        case ST_PLAY:
            updateWorld(dt);
            break;
        case ST_PAUSE: break;
        case ST_DYING:
            overT+=dt;
            bird.vy-=G*dt*0.6f;
            bird.y+=bird.vy*dt*0.6f;
            bird.tilt+=dt*3;
            if(bird.y<FLOORY+0.4f)bird.y=FLOORY+0.4f;
            if(overT>1.4f) finishRun();
            break;
        default: break;
        }
        partsUpdate(dt);
        updateCam();
        biomePull(dt);

        BeginDrawing();
        drawWorld();
        switch(state){
        case ST_SPLASH:
            centerTitle("TIPSY BIRD ULTRA",GetScreenHeight()*0.35f,GetScreenHeight()/10,AURUM);
            centerTitle("a raylib rebuild",GetScreenHeight()*0.50f,GetScreenHeight()/26,CREAM);
            centerTitle("tap to continue",GetScreenHeight()*0.62f,GetScreenHeight()/30,CREAM);
            break;
        case ST_MENU:   uiMenu(); break;
        case ST_READY:  uiReady(); break;
        case ST_PLAY:
            hudBar();
            { int H2=GetScreenHeight(),W2=GetScreenWidth();
              addBtn(W2-H2/8-16,16,H2/8,H2/8,"II",B_PAUSE,CREAM); }
            break;
        case ST_PAUSE:  uiPause(); break;
        case ST_DYING:  hudBar(); break;
        case ST_OVER:   uiOver(); break;
        case ST_SHOP:   uiShop(); break;
        case ST_SET:    uiSet(); break;
        case ST_MSN:    uiMsn(); break;
        default: break;
        }
        drawBtns();
        if(toastT>0) uiToast();
        EndDrawing();
    }
    if(auOK){ StopAudioStream(as); UnloadAudioStream(as); }
    CloseAudioDevice();
    CloseWindow();
    return 0;
}
