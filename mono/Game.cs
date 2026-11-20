using System;
using System.Collections.Generic;
using System.IO;
using Microsoft.Xna.Framework;
using Microsoft.Xna.Framework.Graphics;
using Microsoft.Xna.Framework.Audio;
using Microsoft.Xna.Framework.Input.Touch;

namespace TipsyBird.Mono
{
    struct Btn { public int id, x, y, w, h; public Btn(int i, int x, int y, int w, int h) { this.id = i; this.x = x; this.y = y; this.w = w; this.h = h; } }

    struct Obs { public float x, gy, gh, w; public int kind; public float phase, amp; public bool passed; }
    struct Coin { public float x, y; public bool dead; public float t; }
    struct Power { public float x, y; public int kind; public bool dead; } // 0 shield 1 magnet 2 slow 3 x2
    struct Part { public float x, y, vx, vy, life, max, r; public Color c; }

    class SaveData
    {
        public const uint MAGIC = 0x54424D33; // TBM3
        public uint magic = MAGIC;
        public int best, coins = 25, keys = 1, skin;
        public short[] ownedMask = new short[3];
        public byte mapOpen = 0x07; // first 3 maps free
        public int[] mapBest = new int[8];
        public int qual = 2, shakeOn = 1, sfxV = 100, musV = 70, sens = 100, showFps, lang; // lang 0=ar 1=en
        public int runs, totCoins, comboBest;
        public int[] msn = new int[15];      // goal,kind,prog per slot
        public int[] msnDone = new int[5];
        public int lastDay, dailyStreak;
    }

    public class TipsyGame : Game
    {
        GraphicsDeviceManager gdm; SpriteBatch sb;
        Texture2D white, atlas, circ;
        Dictionary<int, int[]> glyphIdx = new Dictionary<int, int[]>();
        int atlasW, atlasH;

        public static float W = 1280, H = 720;

        enum St { Splash, Menu, Ready, Play, Dying, Over, Worlds, Shop, Msn, Set, Daily, Pause }
        St state = St.Splash; float stateT = 0;
        Random rnd = new Random();

        SaveData S = new SaveData();
        static Data.MapDef MP; static int curMap = 0;
        int mapSel = 0; int shopPage = 0; const int SHOP_PER = 8;
        bool wipeArmed = false;

        float shieldT, magnetT, slowT, x2T, combo, comboT;
        int runCoins;

        List<Obs> obs = new List<Obs>(); List<Coin> coinsA = new List<Coin>(); List<Power> pws = new List<Power>(); List<Part> parts = new List<Part>();
        float birdY = 0, birdV = 0, flapT = 0, worldX = 0, spawnX = 0, birdTilt = 0;
        bool alive = false, pendingRevive = false; int score = 0; float shakeAmp = 0; float deadT = 0;

        float offFar, offMid, offGround, offCloud;

        SoundEffect sfxFlap, sfxCoin, sfxPower, sfxHit, sfxDie, sfxBuy, sfxClaim, sfxUnlock;
        SoundEffectInstance[] musI = new SoundEffectInstance[3]; int musCur = 0;

        List<Btn> btns = new List<Btn>();
        Vector2 tPos = new Vector2(); bool tDown = false; bool tJust = false; float steerPrevX = 0;

        public TipsyGame()
        {
            gdm = new GraphicsDeviceManager(this);
            gdm.IsFullScreen = true;
            gdm.SupportedOrientations = DisplayOrientation.LandscapeLeft | DisplayOrientation.LandscapeRight;
            IsMouseVisible = false;
        }

        protected override void Initialize()
        {
            base.Initialize();
        }

        // ================= ASSETS =================
        Color C(uint hex) { return new Color((int)(hex & 0xFF), (int)((hex >> 8) & 0xFF), (int)((hex >> 16) & 0xFF), (hex >> 24) == 0 ? 255 : (int)(hex >> 24)); }
        Color C(uint hex, byte a) { var c = C(hex); c.A = a; return c; }

        void MakeWhite()
        {
            white = new Texture2D(GraphicsDevice, 1, 1);
            white.SetData(new[] { Color.White });
            circ = new Texture2D(GraphicsDevice, 128, 128);
            var px = new Color[128 * 128];
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++)
                {
                    float dx = x - 63.5f, dy = y - 63.5f; float d = dx * dx + dy * dy;
                    px[y * 128 + x] = d <= 62 * 62 ? Color.White : (d <= 63.9f * 63.9f ? new Color(255, 255, 255, 160) : Color.Transparent);
                }
            circ.SetData(px);
        }

        void LoadAtlas()
        {
            var asm = typeof(TipsyGame).Assembly;
            using (var st = asm.GetManifestResourceStream("TipsyBird.Mono.Resources.font_atlas.png"))
                atlas = Texture2D.FromStream(GraphicsDevice, st);
            atlasW = atlas.Width; atlasH = atlas.Height;
            foreach (var g in Data.Glyphs) glyphIdx[g.cp] = new[] { g.x, g.y, g.w, g.h };
            sb.GraphicsDevice.SamplerStates[0] = SamplerState.LinearClamp;
        }

        byte[] WavPcm(short[] samples)
        {
            int n = samples.Length; var m = new byte[44 + n * 2];
            m[0] = (byte)'R'; m[1] = (byte)'I'; m[2] = (byte)'F'; m[3] = (byte)'F';
            PutI32(m, 4, 36 + n * 2); m[8] = (byte)'W'; m[9] = (byte)'A'; m[10] = (byte)'V'; m[11] = (byte)'E';
            m[12] = (byte)'f'; m[13] = (byte)'m'; m[14] = (byte)'t'; m[15] = (byte)' ';
            PutI32(m, 16, 16); m[20] = 1; m[22] = 1; PutI32(m, 24, 22050); PutI32(m, 28, 44100); m[34] = 16;
            m[36] = (byte)'d'; m[37] = (byte)'a'; m[38] = (byte)'t'; m[39] = (byte)'a'; PutI32(m, 40, n * 2);
            for (int i = 0; i < n; i++) { short v = samples[i]; m[44 + i * 2] = (byte)(v & 255); m[45 + i * 2] = (byte)((v >> 8) & 255); }
            return m;
        }
        void PutI32(byte[] b, int o, int v) { b[o] = (byte)v; b[o + 1] = (byte)(v >> 8); b[o + 2] = (byte)(v >> 16); b[o + 3] = (byte)(v >> 24); }

        const int SR = 22050;
        static float Tone(int semi, int wv, float t, float dur)
        {
            float f = 440f * (float)Math.Pow(2, semi / 12.0);
            float ph = (t * f) % 1f;
            float v = wv == 0 ? (float)Math.Sin(ph * Math.PI * 2) : (wv == 1 ? (2f * ph < 1 ? 1f : -1f) - 0.12f * (float)Math.Sin(ph * Math.PI * 4 * 3) : (ph < 0.5f ? 1f : -1f) * (1 - ph));
            var env = t < dur ? 1f - t / dur : 0f;
            return v * env;
        }

        void BuildSfx()
        {
            sfxFlap = SfxOf(new float[,] { { 8, 0.10f, 0.7f } }, 0.10f, 0);
            sfxCoin = SfxOf(new float[,] { { 12, 0.06f, 0.8f }, { 16, 0.08f, 0.7f } }, 0.14f, 1);
            sfxPower = SfxOf(new float[,] { { 4, 0.07f, 0.7f }, { 8, 0.07f, 0.7f }, { 12, 0.09f, 0.8f } }, 0.23f, 2);
            sfxHit = SfxOf(new float[,] { { -14, 0.16f, 0.9f } }, 0.16f, 3);
            sfxDie = Seq(new[] { 8, 4, 0, -4, -8, -12 }, 0.09f, 1);
            sfxBuy = Seq(new[] { 8, 12, 16, 20 }, 0.06f, 2);
            sfxClaim = Seq(new[] { 4, 8, 12, 16, 12, 16, 20 }, 0.06f, 0);
            sfxUnlock = Seq(new[] { 0, 4, 7, 12, 16, 19, 24 }, 0.07f, 2);

            musI[0] = MusicOf(new[] { 0, 4, 7, 4, 9, 4, 7, 4, 5, 4, 9, 4, 7, 4, 4, 4 }, new[] { -24, 0, -12, 0, -24, 0, -12, 0, -24, 0, -12, 0, -24, 0, -12, 0 }, 132f, 0);
            musI[1] = MusicOf(new[] { 7, 7, 10, 7, 12, 7, 10, 7, 5, 5, 9, 5, 7, 5, 12, 5 }, new[] { -19, 0, -19, 0, -19, 0, -19, 0, -21, 0, -21, 0, -19, 0, -19, 0 }, 118f, 1);
            musI[2] = MusicOf(new[] { 3, 7, 10, 14, 10, 7, 3, 7, 5, 8, 12, 15, 12, 8, 5, 8 }, new[] { -21, 0, -21, 0, -21, 0, -21, 0, -19, 0, -19, 0, -19, 0, -19, 0 }, 140f, 2);
        }

        SoundEffectInstance MusicOf(int[] lead, int[] bass, float bpm, int wv)
        {
            float step = 60f / bpm / 4f; int bars = 4; int nsteps = 16; int total = (int)(step * nsteps * bars * SR); var p = new float[total];
            for (int b = 0; b < bars; b++)
            {
                int barLen = (int)(nsteps * step * SR);
                for (int s = 0; s < nsteps; s++)
                {
                    int st = b * barLen + (int)(s * step * SR); int len = (int)(step * 0.9f * SR);
                    int semi = lead[s] + (b % 2 == 1 ? 3 : 0);
                    for (int i = 0; i < len && st + i < total; i++)
                    {
                        float t = i / (float)SR; float v = Tone(semi, wv == 2 ? 1 : 0, t, len / (float)SR) * (wv==0?0.22f:0.18f);
                        if (s % 4 == 0 && bass[s] != 0) v += Tone(bass[s], 1, t, step * 0.8f) * 0.25f;
                        p[st + i] += v;
                    }
                }
            }
            var pcm = new short[total]; for (int i = 0; i < total; i++) pcm[i] = Clamp16(p[i]);
            try { var se = SoundEffect.FromStream(new MemoryStream(WavPcm(pcm))); var inst = se.CreateInstance(); inst.IsLooped = true; return inst; }
            catch { return null; }
        }

        SoundEffect SfxOf(float[,] spec, float dur, int wv)
        {
            int total = (int)(dur * SR); var p = new float[total];
            for (int k = 0; k < spec.GetLength(0); k++)
            {
                int off = k * (int)(spec[k, 1] * SR); int len = (int)(spec[k, 1] * SR + 1);
                for (int i = 0; i < len && off + i < total; i++) p[off + i] += Tone((int)spec[k, 0], wv, i / (float)SR, len / (float)SR) * spec[k, 2];
            }
            var pcm = new short[total]; for (int i = 0; i < total; i++) pcm[i] = Clamp16(p[i]);
            try { return SoundEffect.FromStream(new MemoryStream(WavPcm(pcm))); } catch { return null; }
        }

        SoundEffect Seq(int[] semis, float each, int wv)
        {
            var spec = new float[semis.Length, 3];
            for (int i = 0; i < semis.Length; i++) { spec[i, 0] = semis[i]; spec[i, 1] = each; spec[i, 2] = 0.8f; }
            return SfxOf(spec, each * semis.Length, wv);
        }

        static short Clamp16(float v) { if (v < -1) v = -1; if (v > 1) v = 1; return (short)(v * 32000); }

        protected override void LoadContent()
        {
            sb = new SpriteBatch(GraphicsDevice);
            MakeWhite(); LoadAtlas(); BuildSfx();
            MP = Data.Maps[curMap];
            svLoad();
            msnInitAll();
        }

        protected override void UnloadContent() { base.UnloadContent(); }

        // ================= SAVE =================
        string SavePath()
        {
            try { return Path.Combine(Android.App.Application.Context.FilesDir.Path, "tipsy_mono.bin"); }
            catch { return Path.Combine(Path.GetTempPath(), "tipsy_mono.bin"); }
        }

        void svLoad()
        {
            try
            {
                var b = File.ReadAllBytes(SavePath());
                if (b.Length < 200) { svDefaults(); return; }
                var m = BitConverter.ToUInt32(b, 0);
                int o = 4;
                if (m != SaveData.MAGIC) { svDefaults(); return; }
                var t = new SaveData();
                t.best = Rd(b, ref o); t.coins = Rd(b, ref o); t.keys = Rd(b, ref o); t.skin = Rd(b, ref o);
                for (int i = 0; i < 3; i++) t.ownedMask[i] = BitConverter.ToInt16(b, o + i * 2);
                o += 6;
                t.mapOpen = b[o]; o += 1;
                for (int i = 0; i < 8; i++) t.mapBest[i] = Rd(b, ref o);
                t.qual = Cl(Rd(b, ref o), 0, 2); t.shakeOn = Rd(b, ref o); t.sfxV = Cl(Rd(b, ref o), 0, 100); t.musV = Cl(Rd(b, ref o), 0, 100);
                t.sens = Cl(Rd(b, ref o), 40, 160); t.showFps = Rd(b, ref o); t.lang = Rd(b, ref o) != 0 ? 1 : 0;
                t.runs = Math.Max(0, Rd(b, ref o)); t.totCoins = Math.Max(0, Rd(b, ref o)); t.comboBest = Rd(b, ref o);
                for (int i = 0; i < 15; i++) t.msn[i] = Rd(b, ref o);
                for (int i = 0; i < 5; i++) t.msnDone[i] = Rd(b, ref o);
                t.lastDay = Rd(b, ref o); t.dailyStreak = Rd(b, ref o);
                t.skin = Cl(t.skin, 0, 34); t.coins = Math.Max(0, t.coins); t.keys = Math.Max(0, t.keys);
                t.mapOpen |= 0x07;
                S = t;
            }
            catch { svDefaults(); }
        }
        int Rd(byte[] b, ref int o) { int v = BitConverter.ToInt32(b, o); o += 4; return v; }
        int Cl(int v, int a, int b2) { return v < a ? a : (v > b2 ? b2 : v); }

        void svSave()
        {
            var l = new List<byte>();
            l.AddRange(BitConverter.GetBytes(SaveData.MAGIC));
            Wr(l, S.best); Wr(l, S.coins); Wr(l, S.keys); Wr(l, S.skin);
            for (int i = 0; i < 3; i++) l.AddRange(BitConverter.GetBytes(S.ownedMask[i]));
            l.Add(S.mapOpen);
            for (int i = 0; i < 8; i++) Wr(l, S.mapBest[i]);
            Wr(l, S.qual); Wr(l, S.shakeOn); Wr(l, S.sfxV); Wr(l, S.musV);
            Wr(l, S.sens); Wr(l, S.showFps); Wr(l, S.lang);
            Wr(l, S.runs); Wr(l, S.totCoins); Wr(l, S.comboBest);
            for (int i = 0; i < 15; i++) Wr(l, S.msn[i]);
            for (int i = 0; i < 5; i++) Wr(l, S.msnDone[i]);
            Wr(l, S.lastDay); Wr(l, S.dailyStreak);
            try { File.WriteAllBytes(SavePath(), l.ToArray()); } catch { }
        }
        void Wr(List<byte> l, int v) { l.AddRange(BitConverter.GetBytes(v)); }
        void svDefaults() { S = new SaveData(); }

        void svWipe() { svDefaults(); svSave(); }

        int todayInt()
        {
            var d = System.DateTime.UtcNow;
            return d.Year * 10000 + d.Month * 100 + d.Day;
        }

        // ============ helpers: owned / gates ============
        bool owned(int i) { return (S.ownedMask[i >> 4] >> (i & 15) & 1) == 1; }
        void setOwned(int i) { S.ownedMask[i >> 4] |= (short)(1 << (i & 15)); }
        bool mapOpen(int i) { return (S.mapOpen >> i & 1) == 1; }
        void setMapOpen(int i) { S.mapOpen |= (byte)(1 << i); }
        bool mapGateOk(int i) { return S.best >= Data.MapGateScore[i] || S.keys >= Data.MapGateKeys[i]; }
        bool skinGateOk(int i) { var sk = Data.Skins[i]; return sk.m == 0 || mapOpen(sk.m - 1) || S.best >= sk.mg; }

        // ============ toast ============
        string toastTxt; float toastT;
        void toast(string t, float d) { toastTxt = t; toastT = d; }

        string G(string k)
        {
            string v; return Data.Str.TryGetValue("s_" + k, out v) ? v : "?";
        }
        string SkinAr(int i) { string v; return Data.Str.TryGetValue("s_sk" + i, out v) ? v : "?"; }
        string MapAr(int i) { string v; return Data.Str.TryGetValue("s_map" + i, out v) ? v : "?"; }

        // ============ missions (5 slots, escalating tiers) ============
        int NMsn = 5;
        int msnGoal(int i) { return S.msn[i * 3]; }
        int msnKind(int i) { return S.msn[i * 3 + 1]; } // 0 score 1 coins 2 runs 3 combo
        int msnProg(int i) { return S.msn[i * 3 + 2]; }
        int msnReward(int i) { return 6 + msnGoal(i) / 4 + S.runs / 6; }

        void msnNew(int slot, int tier)
        {
            int kind = rnd.Next(0, 4);
            int baseG = 5 + tier * 5;
            int goal = kind == 0 ? baseG + rnd.Next(0, 16) : (kind == 1 ? baseG * 2 + rnd.Next(0, 21) : (kind == 2 ? rnd.Next(1, 2 + tier) : baseG / 2 + rnd.Next(0, 6)));
            S.msn[slot * 3] = goal; S.msn[slot * 3 + 1] = kind; S.msn[slot * 3 + 2] = 0;
        }
        void msnInitAll()
        {
            int tier = S.runs / 3;
            for (int i = 0; i < NMsn; i++) if (S.msn[i * 3] == 0 && S.msn[i * 3 + 1] == 0) msnNew(i, tier);
        }
        void msnBump(int kind, int amt)
        {
            for (int i = 0; i < NMsn; i++)
                if (S.msnDone[i] == 0 && msnKind(i) == kind)
                    S.msn[i * 3 + 2] = Math.Min(msnGoal(i), msnProg(i) + amt);
        }
        int msnClaimable() { int n = 0; for (int i = 0; i < NMsn; i++) if (S.msnDone[i] == 0 && msnProg(i) >= msnGoal(i)) n++; return n; }
        void msnClaim(int i)
        {
            if (S.msnDone[i] == 1 || msnProg(i) < msnGoal(i)) return;
            int rw = msnReward(i);
            S.coins += rw;
            sfxUnlock?.Play();
            int tier = S.runs / 3 + 1; int k = msnKind(i); msnNew(i, tier);
            toast(G("dailyGot") + " +" + rw, 2.2f); svSave();
        }

        // ============ daily chest ============
        bool dailyAvailable() { return S.lastDay != todayInt(); }
        void dailyOpen()
        {
            if (!dailyAvailable()) return;
            S.dailyStreak++; S.lastDay = todayInt();
            int coins = 8 + Math.Min(40, S.dailyStreak * 2); S.coins += coins;
            S.keys += 1;
            toast(G("dailyGot") + " +" + coins + " +1", 2.8f);
            sfxUnlock?.Play(); svSave(); state = St.Menu;
        }

        // ================= RUN LIFECYCLE =================
        const float GROUND = 630f, CEIL = 36f;   // screen-space bounds (original GROUND=-4.5/CEIL=7.2 mapped)
        const float GRAV = 1500f, FLAPV = -430f, SPEED0 = 270f;

        void startRun()
        {
            alive = true; pendingRevive = false; score = 0; runCoins = 0; combo = 0; comboT = 0;
            shieldT = magnetT = slowT = x2T = 0;
            obs.Clear(); coinsA.Clear(); pws.Clear(); parts.Clear();
            birdY = 330; birdV = 0; birdTilt = 0; worldX = 0; spawnX = 700;
            deadT = 0; shakeAmp = 0;
            offFar = offMid = offGround = offCloud = 0;
            state = St.Ready; stateT = 0;
            startMusic();
        }

        void startMusic()
        {
            int want = curMap % 3;
            if (want != musCur)
            {
                if (musI[musCur] != null) musI[musCur].Stop();
                musCur = want;
            }
            applyVolumes();
            if (musI[musCur] != null && musI[musCur].State != SoundState.Playing) musI[musCur].Play();
        }
        void stopMusic() { if (musI[musCur] != null) musI[musCur].Stop(); }
        void applyVolumes()
        {
            float mv = S.musV / 100f * 0.6f;
            for (int i = 0; i < 3; i++) if (musI[i] != null) musI[i].Volume = mv;
        }

        float birdXs() { return W * 0.25f; }

        void die()
        {
            if (shieldT > 0) { shieldT = 0; birdV = FLAPV * 0.8f; shakeAmp = 0.5f; sfxHit?.Play(); emit(birdXs(), birdY, 20, C(0xffffff), 5); return; }
            alive = false; pendingRevive = true; shakeAmp = 1; sfxDie?.Play();
            emit(birdXs(), birdY, 26, C(0xffd23f), 5);
            state = St.Dying; stateT = 0;
        }

        void finishRun()
        {
            S.runs++; S.totCoins += runCoins;
            msnBump(2, 1); msnBump(0, score);
            if (score > S.best) S.best = score;
            if (score > S.mapBest[curMap]) S.mapBest[curMap] = score;
            if ((int)combo > S.comboBest) S.comboBest = (int)combo;
            stopMusic();
            svSave();
            state = St.Over; stateT = 0;
        }

        void doRevive()
        {
            if (S.coins < Data.ReviveCost) { toast(G("noCoins"), 2f); return; }
            S.coins -= Data.ReviveCost;
            alive = true; pendingRevive = false; shieldT = 2.5f; sfxPower?.Play();
            obs.RemoveAll(o => o.x < birdXs() + 900);
            state = St.Play; stateT = 0;
            svSave();
        }

        // ================= WORLD GEN + PHYSICS =================
        void genWorld(float dt)
        {
            float speed = (SPEED0 + Math.Min(180, score * 2.2f)) * MP.sp * (slowT > 0 ? 0.55f : 1f);
            worldX += speed * dt;
            offFar += speed * 0.12f * dt; offMid += speed * 0.3f * dt; offGround += speed * dt; offCloud += speed * 0.05f * dt;

            while (spawnX < worldX + W + 200)
            {
                float gy = rnd.Next((int)CEIL + 120, (int)GROUND - 130); float gh = 210 + rnd.Next(0, 60);
                obs.Add(new Obs { x = spawnX, gy = gy, gh = gh, w = 76, kind = score < 6 ? 0 : rnd.Next(0, 4), phase = (float)rnd.NextDouble() * 6.28f, amp = 60 + rnd.Next(0, 70), passed = false });
                if (rnd.NextDouble() < MP.cf + 0.25f)
                {
                    int n = 3 + rnd.Next(0, 4);
                    for (int i = 0; i < n; i++)
                        coinsA.Add(new Coin { x = spawnX + 60 + i * 46, y = gy + gh / 2 + (float)Math.Sin(i / (float)n * Math.PI * 2) * 26, t = 0 });
                }
                if (rnd.Next(0, 9) == 0)
                    pws.Add(new Power { x = spawnX + 64, y = gy + gh / 2 + rnd.Next(-40, 40), kind = rnd.Next(0, 4) });
                spawnX += 520 + rnd.Next(0, 190) - Math.Min(120, score * 12) / 10;
            }
        }

        bool hitObs(Obs o, float bx, float by, float br)
        {
            if (o.kind == 2)
            { // rotor: rotating bar
                float ang = o.phase + worldX * 0.02f;
                float cx = o.x, cy = o.gy + o.gh / 2;
                var a2 = new Vector2(cx - (float)Math.Cos(ang) * 150, cy - (float)Math.Sin(ang) * 150);
                var b2 = new Vector2(cx + (float)Math.Cos(ang) * 150, cy + (float)Math.Sin(ang) * 150);
                return SegCircle(a2, b2, new Vector2(bx, by), br + 16);
            }
            float left = o.x - o.w / 2, right = o.x + o.w / 2;
            if (bx + br > left && bx - br < right)
            {
                if (o.kind == 3)
                { // ring gate: pass through middle
                    if (by < o.gy - 24 || by > o.gy + o.gh + 24) return true;
                }
                else
                {
                    if (by - br < o.gy) return true;
                    if (by + br > o.gy + o.gh) return true;
                }
            }
            return false;
        }

        static bool SegCircle(Vector2 a, Vector2 b, Vector2 p, float r)
        {
            var ab = b - a; var ap = p - a;
            float t = Vector2.Dot(ap, ab) / Math.Max(0.001f, ab.LengthSquared());
            t = Math.Max(0, Math.Min(1, t));
            var c = a + ab * t;
            return (p - c).Length() < r;
        }

        void updateRun(float dt)
        {
            genWorld(dt);

            birdV += GRAV * (slowT > 0 ? 0.65f : 1f) * dt;
            if (birdV > 620) birdV = 620;
            birdY += birdV * dt;
            birdTilt = Math.Max(-0.5f, Math.Min(1.4f, birdV / 900f));
            flapT += dt;

            float bx = birdXs();
            bool dead = false;
            for (int oi = 0; oi < obs.Count; oi++)
            {
                var o = obs[oi];
                if (o.x < bx - 400) { obs[oi] = o; continue; }
                if (!o.passed && o.x < bx - o.w / 2)
                {
                    o.passed = true; score++;
                    if ((S.runs + score) % 11 == 0) { S.keys++; toast(G("throne"), 2f); }
                }
                if (hitObs(o, bx, birdY, 24)) dead = true;
                obs[oi] = o;
            }
            float birdR = 26;
            if (birdY + birdR > GROUND) { birdY = GROUND - birdR; dead = true; }
            if (birdY - birdR < CEIL) { birdY = CEIL + birdR; dead = true; }

            updateCoins(dt, bx);
            for (int pi = 0; pi < pws.Count; pi++)
            {
                var p = pws[pi];
                if (p.dead) continue;
                if (new Vector2(bx - p.x, birdY - p.y).Length() < 38)
                {
                    p.dead = true; sfxPower?.Play();
                    if (p.kind == 0) { shieldT = 8; toast(G("shield"), 2f); }
                    else if (p.kind == 1) { magnetT = 7; toast(G("magnet"), 2f); }
                    else if (p.kind == 2) { slowT = 5; toast(G("slow"), 2f); }
                    else { x2T = 8; toast("X2", 2f); }
                    emit(p.x, p.y, 12, C(0x8ff2e0), 4);
                }
                pws[pi] = p;
            }

            if (dead) die();

            shieldT = dec(shieldT, dt); magnetT = dec(magnetT, dt); slowT = dec(slowT, dt); x2T = dec(x2T, dt);
            if (comboT > 0) { comboT -= dt; if (comboT <= 0) combo = 0; }
            if (shakeAmp > 0.02f) shakeAmp *= 0.9f; else shakeAmp = 0;

            obs.RemoveAll(o => o.x < worldX - 400);
            coinsA.RemoveAll(c => c.dead || c.x < worldX - 400);
            pws.RemoveAll(p => p.dead || p.x < worldX - 400);
            updateParts(dt);
        }

        void updateCoins(float dt, float bx)
        {
            for (int ci = 0; ci < coinsA.Count; ci++)
            {
                var c = coinsA[ci];
                if (c.dead) continue;
                var v = new Vector2(bx - c.x, birdY - c.y);
                float d = v.Length();
                if (magnetT > 0 && d < 260) { c.x += v.X * 4.5f * dt; c.y += v.Y * 4.5f * dt; }
                if (d < 34)
                {
                    c.dead = true;
                    int gain = x2T > 0 ? 2 : 1;
                    runCoins += gain; S.coins += gain; S.totCoins += gain;
                    combo++; comboT = 2.4f; msnBump(1, gain); if (combo >= 5) msnBump(3, 1);
                    sfxCoin?.Play();
                    emit(c.x, c.y, 5, C(0xffd23f), 3);
                }
                coinsA[ci] = c;
            }
        }

        float dec(float v, float dt) { v -= dt; return v < 0 ? 0 : v; }

        void emit(float x, float y, int n, Color c, int kind)
        {
            int q = S.qual == 0 ? n / 2 : (S.qual == 1 ? n * 7 / 10 : n);
            for (int i = 0; i < q; i++)
            {
                float a = (float)rnd.NextDouble() * 6.28f, sp = 40 + (float)rnd.NextDouble() * 160;
                parts.Add(new Part { x = x, y = y, vx = (float)Math.Cos(a) * sp * (kind == 5 ? 0.6f : 1), vy = (float)Math.Sin(a) * sp * (kind == 5 ? 0.6f : 1), life = 0, max = 0.5f + (float)rnd.NextDouble() * 0.5f, r = 3 + (float)rnd.NextDouble() * 5, c = c });
            }
        }

        void updateParts(float dt)
        {
            for (int i = 0; i < parts.Count; i++)
            {
                var p = parts[i]; p.life += dt;
                p.x += p.vx * dt; p.y += p.vy * dt; p.vy += 60 * dt;
                parts[i] = p;
            }
            parts.RemoveAll(p => p.life >= p.max);
            if (parts.Count > 260) parts.RemoveRange(0, parts.Count - 260);
        }

        float ambT = 0;
        void ambient(float dt)
        {
            if (string.IsNullOrEmpty(MP.eff)) return;
            ambT -= dt;
            if (ambT > 0) return;
            ambT = (S.qual == 0 ? 26 : (S.qual == 1 ? 14 : 8)) / 100f;
            if (MP.eff == "ember") parts.Add(new Part { x = worldX + W + 20, y = GROUND - rnd.Next(0, 200), vx = -120, vy = -50 - rnd.Next(0, 60), life = 0, max = 2.5f, r = 2 + rnd.Next(0, 3), c = C(0xffa03a) });
            else if (MP.eff == "ocean") parts.Add(new Part { x = worldX + W + 20, y = CEIL + rnd.Next(0, 500), vx = -180, vy = 20, life = 0, max = 2.2f, r = 2 + rnd.Next(0, 4), c = C(0x8ff2ff, 160) });
            else if (MP.eff == "space") parts.Add(new Part { x = worldX + W + 20, y = CEIL + rnd.Next(0, 500), vx = -260, vy = 0, life = 0, max = 3f, r = 1 + rnd.Next(0, 2), c = C(0xffffff) });
            else if (MP.eff == "jungle") parts.Add(new Part { x = worldX + W + 20, y = CEIL + rnd.Next(0, 300), vx = -100, vy = 80, life = 0, max = 3f, r = 3 + rnd.Next(0, 3), c = C(0x8fe07f, 200) });
            else if (MP.eff == "storm") parts.Add(new Part { x = worldX + W + 20, y = CEIL + rnd.Next(0, 500), vx = -420, vy = 300, life = 0, max = 1.4f, r = 1 + rnd.Next(0, 2), c = C(0xc8d4ff, 200) });
        }

        // ================= DRAW HELPERS =================
        void R(float x, float y, float w, float h, Color c) { sb.Draw(white, new Vector2(x, y), null, c, 0, Vector2.Zero, new Vector2(w, h), SpriteEffects.None, 0); }
        void Circ(float x, float y, float r, Color c)
        {
            if (r <= 0.2f) r = 0.2f;
            sb.Draw(circ, new Vector2(x - r, y - r), null, c, 0, Vector2.Zero, r / 62f, SpriteEffects.None, 0);
        }
        void Rot(float x, float y, float w, float h, float ang, Color c)
        {
            sb.Draw(white, new Vector2(x, y), null, c, ang, new Vector2(0.5f, 0.5f), new Vector2(w, h), SpriteEffects.None, 0);
        }
        Color Faded(Color c, float a) { int al = (int)Math.Clamp(255 * a, 0, 255); return new Color(c.R, c.G, c.B, al); }
        string N(int v) { return v.ToString("#,0"); }
        Color LerpC(Color a, Color b, float t) { return new Color((int)(a.R + (b.R - a.R) * t), (int)(a.G + (b.G - a.G) * t), (int)(a.B + (b.B - a.B) * t), (int)(a.A + (b.A - a.A) * t)); }

        // ---------- text via glyph atlas (shaped Arabic already baked) ----------
        public float MeasureAr(string s, float size)
        {
            float scale = size / 48f; float w2 = 0;
            foreach (var ch in s)
            {
                if (ch == ' ') { w2 += 14 * scale; continue; }
                int[] g; if (glyphIdx.TryGetValue(ch, out g)) w2 += Math.Max(g[2], 8) * scale + 1;
                else w2 += 14 * scale;
            }
            return w2;
        }
        public void DrawAr(string s, float x, float y, float size, Color c, int align) // 0=left 1=center 2=right
        {
            if (string.IsNullOrEmpty(s)) return;
            float scale = size / 48f;
            float w2 = MeasureAr(s, size);
            float cx = align == 0 ? x : (align == 1 ? x - w2 / 2 : x - w2);
            foreach (var ch in s)
            {
                if (ch == ' ') { cx += 14 * scale; continue; }
                int[] g;
                if (glyphIdx.TryGetValue(ch, out g))
                {
                    sb.Draw(atlas, new Vector2(cx, y), new Rectangle(g[0], g[1], Math.Max(g[2], 1), g[3]), c, 0, Vector2.Zero, new Vector2(scale, scale), SpriteEffects.None, 0);
                    cx += Math.Max(g[2], 8) * scale + 1;
                }
                else cx += 14 * scale;
            }
        }

        // ================= WORLD DRAWING =================
        Color SkyTop, SkyMid, SkyBot;

        void skyUpdate()
        {
            float ph = score / 15f; if (ph > 2) ph = 2;
            int p0 = (int)ph; int p1 = Math.Min(2, p0 + 1); float f = ph - p0;
            var m = Data.Maps[curMap];
            uint[] a = GetSky(m, p0), b = GetSky(m, p1);
            SkyTop = LerpC(C(a[0]), C(b[0]), f);
            SkyMid = LerpC(C(a[1]), C(b[1]), f);
            SkyBot = LerpC(C(a[2]), C(b[2]), f);
        }
        uint[] GetSky(Data.MapDef m, int ph)
        {
            switch (ph)
            {
                case 0: return new[] { m.s00, m.s01, m.s02 };
                case 1: return new[] { m.s10, m.s11, m.s12 };
                default: return new[] { m.s20, m.s21, m.s22 };
            }
        }

        void drawSky()
        {
            int bands = 20;
            for (int i = 0; i < bands; i++)
            {
                float t = i / (float)(bands - 1);
                Color c = t < 0.5f ? LerpC(SkyTop, SkyMid, t * 2) : LerpC(SkyMid, SkyBot, (t - 0.5f) * 2);
                R(0, H * (i / (float)bands), W + 2, H / bands + 2, c);
            }
            R(0, GROUND - 150, W + 2, 160, C(Data.Maps[curMap].fog0, 110));
        }

        float hillY(float seed) { return (float)Math.Sin(seed * 0.7) * 0.25f + (float)Math.Sin(seed * 0.25) * 0.6f + (float)Math.Sin(seed * 1.9) * 0.25f; }

        void drawWorld()
        {
            var m = Data.Maps[curMap];
            drawSky();
            drawParts();

            float my = GROUND - 120;
            for (float x = -((offFar) % 420); x < W + 200; x += 210)
                Mtn(x + 60, my + 55, 190, 120, C(m.mtA));
            for (float x = 105 - ((offFar * 1.4f) % 300); x < W + 200; x += 300)
                Mtn(x, my + 85, 140, 85, C(m.mtB));

            for (float x = -((offCloud) % 380); x < W + 200; x += 380)
            {
                float yy = 76 + (float)Math.Sin(x * 0.05) * 26;
                Circ(x, yy, 26, C(m.cA, 200)); Circ(x + 30, yy - 10, 34, C(m.cA, 200)); Circ(x + 66, yy, 24, C(m.cA, 200));
                Circ(x + 190, yy + 60, 20, C(m.cB, 170)); Circ(x + 218, yy + 54, 26, C(m.cB, 170));
            }

            float hy = GROUND - 95;
            for (float x = -((offMid) % 160); x < W + 200; x += 80)
            {
                float bump = hillY((x + offMid) / 160f);
                Circ(x, hy + 40, 70 + bump * 26, C(m.fA));
                Circ(x + 40, hy + 52, 62 + bump * 20, C(m.fB));
            }

            for (float x = -((offMid * 1.6f) % 200); x < W + 200; x += 100)
            {
                float bump = hillY((x + offMid * 1.6f) / 200f);
                Circ(x, GROUND - 40, 84 + bump * 18, C(m.hA));
                Circ(x + 55, GROUND - 28, 70 + bump * 22, C(m.hB));
            }

            int dstep = 260 - Data.Maps[curMap].dc * 14;
            for (float x = -((offGround * 0.98f) % dstep); x < W + 100; x += dstep)
            {
                float sx = x; float seed = (float)Math.Floor((sx + offGround * 0.98f) / dstep);
                var rr = new Random((int)(seed * 977) + curMap * 31);
                int kind = rr.Next(0, 3); float hgt = 26 + rr.Next(0, 26);
                float gy = GROUND + 6;
                if (kind == 0) { Circ(sx, gy - hgt, 16, C(m.eA)); Circ(sx, gy - hgt * 0.6f, 9, C(m.eB)); R(sx - 4, gy - hgt * 0.25f, 8, hgt * 0.25f, C(m.eB, 220)); }
                else if (kind == 1) { Tri(sx, gy - hgt, 26, hgt, C(m.eA)); Circ(sx, gy - hgt * 0.25f, 8, C(m.eB, 180)); }
                else { Circ(sx, gy - 8, 10, C(m.eB, 200)); }
            }

            drawObsAll();
            drawCoins();
            drawPowers();
            drawParts();
            drawGround();
            if (alive || state == St.Ready) drawBird(birdXs(), birdY);
        }

        void Mtn(float x, float y, float w2, float h2, Color c) { Tri(x, y - h2, w2, h2, c); }
        void Tri(float cx, float topY, float w2, float h2, Color c)
        {
            for (int i = 0; i < 26; i++)
            {
                float t = i / 25f;
                float ww = w2 * t / 2f;
                R(cx - ww, topY + h2 * t, ww * 2, h2 / 26f + 1.5f, c);
            }
        }

        void drawGround()
        {
            var m = Data.Maps[curMap];
            for (int i = 0; i < 10; i++)
            {
                float t = i / 9f;
                R(0, GROUND + (H - GROUND + 60) * t, W + 2, (H - GROUND + 60) / 10 + 2, LerpC(C(m.gA), C(m.gB), t));
            }
            R(0, GROUND - 6, W + 2, 8, C(m.gA));
            for (float x = -((offGround) % 130); x < W + 100; x += 130)
                R(x, GROUND + 26, 46, 8, C(m.gB, 120));
        }

        void drawObsAll()
        {
            var m = Data.Maps[curMap];
            foreach (var o in obs)
            {
                float sx = o.x - worldX;
                if (sx < -150 || sx > W + 150) continue;
                float phy = o.kind == 1 ? (float)Math.Sin(worldX * 0.02f + o.phase) * o.amp : 0;
                var topC = C(m.pA); var botC = C(m.pB);
                if (o.kind == 2)
                {
                    float ang = o.phase + worldX * 0.02f;
                    float cx = sx, cy = o.gy + o.gh / 2 - phy;
                    Circ(cx, cy, 18, botC);
                    Rot(cx - 150, cy, 300, 22, ang, C(m.pA));
                    Circ(cx + 150 * (float)Math.Cos(ang), cy + 150 * (float)Math.Sin(ang), 14, botC);
                }
                else if (o.kind == 3)
                {
                    Circ(sx, o.gy + o.gh / 2, o.gh / 2 + 34, C(m.pA));
                    Circ(sx, o.gy + o.gh / 2, o.gh / 2 + 16, C(m.pB));
                    Circ(sx, o.gy + o.gh / 2, o.gh / 2 - 2, SkyBot);
                }
                else
                {
                    R(sx - o.w / 2, CEIL, o.w, o.gy - CEIL + phy, topC);
                    R(sx - o.w / 2 - 8, o.gy - 26 + phy, o.w + 16, 30, botC);
                    R(sx - o.w / 2, o.gy + o.gh + phy, o.w, Math.Max(0, GROUND - o.gy - o.gh - phy), topC);
                    R(sx - o.w / 2 - 8, o.gy + o.gh - 2 + phy, o.w + 16, 30, botC);
                    R(sx - 4, CEIL, 8, o.gy - CEIL, C(0xffffff, 50));
                }
            }
        }
        void DrawRing(float x, float y, float r, float th, Color c)
        {
            for (int i = 0; i < 22; i++)
            {
                float a = i / 22f * 6.2832f;
                Circ(x + (float)Math.Cos(a) * r, y + (float)Math.Sin(a) * r, th, c);
            }
        }

        void drawCoins()
        {
            foreach (var c in coinsA)
            {
                if (c.dead) continue;
                float sx = c.x - worldX;
                if (sx < -60 || sx > W + 60) continue;
                float phy = (float)Math.Sin(worldX * 0.05f + c.x * 0.05f) * 4;
                Circ(sx, c.y + phy, 16, C(0xffd23f));
                Circ(sx, c.y + phy, 11, C(0xfff3b0));
                Circ(sx + 3, c.y + phy + 2, 4, C(0xffffff, 200));
            }
        }

        void drawPowers()
        {
            foreach (var p in pws)
            {
                if (p.dead) continue;
                float sx = p.x - worldX;
                if (sx < -60 || sx > W + 60) continue;
                float floaty = (float)Math.Sin(worldX * 0.06f + p.x) * 6;
                uint col = p.kind == 0 ? 0x88d8ffu : (p.kind == 1 ? 0xff8fd8u : (p.kind == 2 ? 0x9affc0u : 0xffe12au));
                Circ(sx, p.y + floaty, 20, C(0x2a1f46, 230));
                Circ(sx, p.y + floaty, 18, C(col));
                if (p.kind == 0) { Circ(sx, p.y + floaty, 8, C(0xffffff, 80)); DrawRing(sx, p.y + floaty, 10, 3, C(0xffffff)); }
                else if (p.kind == 1) { Circ(sx, p.y + floaty, 3, C(0x8a2a5a)); DrawRing(sx, p.y + floaty, 8, 2, C(0xffffff, 200)); }
                else if (p.kind == 2) { R(sx - 9, p.y + floaty - 2, 18, 4, C(0x0a4a30)); R(sx - 4, p.y + floaty - 2, 4, 8, C(0x0a4a30)); }
                else DrawAr("X2", sx, p.y + floaty - 10, 20, C(0x8a5a00), 1);
            }
        }

        void drawParts()
        {
            foreach (var p in parts)
            {
                float sx = p.x - worldX;
                if (sx < -30 || sx > W + 30) continue;
                float t = p.life / p.max;
                var c = p.c; c.A = (byte)(255 * (1 - t));
                float rr = p.r * (0.6f + 0.4f * (1 - t)) * (S.qual == 0 ? 0.7f : 1);
                Circ(sx, p.y, rr, c);
            }
        }

        void drawBird(float x, float y)
        {
            var sk = Data.Skins[S.skin];
            Color cb = C(sk.b), co = C(sk.o), cl = C(sk.l), ck = C(sk.k);
            if (sk.acc == 4)
            {
                float wag = (float)Math.Sin(flapT * 9) * 4;
                R(x - 34, y - 14 + wag, 20, 38, C(0xc81e1e, 230));
                R(x - 44, y - 6 + wag, 12, 20, C(0xc81e1e, 230));
            }
            if (shieldT > 0 || state == St.Ready)
            {
                float pulse = 1 + (float)Math.Sin(flapT * 8) * 0.06f;
                DrawRing(x, y, 40 * pulse, 4, C(0x88d8ff, 200));
            }
            Circ(x, y, 26, cb);
            Circ(x + 3, y + 6, 17, cl);
            float wa = (float)Math.Sin(flapT * (state == St.Play ? 14 : 6)) * 0.5f;
            Rot(x - 22, y + 2 - (wa * 14), 26, 16, -0.4f + wa, co);
            Rot(x - 22, y + 2 - (wa * 14), 18, 8, -0.4f + wa, cl);
            Rot(x - 30, y + 6, 16, 10, 0.5f, co);
            Circ(x + 13, y - 9, 8, Color.White);
            Circ(x + 15, y - 9, 4, new Color(30, 30, 40));
            Circ(x + 16, y - 11, 1.6f, Color.White);
            Rot(x + 24, y - 2, 14, 10, 0.15f, ck);
            Rot(x + 24, y + 4, 12, 6, 0.15f, C(sk.e & 0xFFFFFF));
            if (sk.acc == 1) { R(x - 12, y - 40, 8, 10, C(0xffd23f)); R(x - 4, y - 44, 8, 14, C(0xffd23f)); R(x + 4, y - 40, 8, 10, C(0xffd23f)); R(x - 12, y - 32, 24, 5, C(0xa8861a)); }
            if (sk.acc == 2) { R(x + 4, y - 12, 20, 8, C(0x1c1e26, 230)); R(x + 26, y - 12, 4, 8, C(0x1c1e26, 230)); }
            if (sk.acc == 3) DrawRing(x, y - 40, 10, 3, C(0xffe12a));
        }

        // ================= UI BUTTONS =================
        Color INK = new Color(58, 42, 92), CREAM = new Color(255, 246, 220), ORG = new Color(255, 122, 47), ORGD = new Color(196, 72, 15), TEAL = new Color(39, 193, 176), AURUM = new Color(255, 210, 63), ROSEC = new Color(255, 126, 182), WHT = Color.White;

        void addBtn(int id, float x, float y, float w, float h)
        {
            btns.Add(new Btn(id, (int)x, (int)y, (int)w, (int)h));
        }
        void Dim(float a) { R(0, 0, W, H, new Color(12, 8, 28, (int)(255 * a))); }

        void uiBtn(int id, float x, float y, float w, float h, Color fill, Color edge, string label, float fs)
        {
            R(x - 3, y - 3, w + 6, h + 6, edge);
            R(x, y, w, h, fill);
            R(x, y, w, h / 2, new Color((byte)Math.Min(255, fill.R + 24), (byte)Math.Min(255, fill.G + 24), (byte)Math.Min(255, fill.B + 24)));
            float tw = label != null ? MeasureAr(label, fs) : 0;
            if (label != null) DrawAr(label, x + w / 2, y + h / 2 - fs / 2, fs, WHT, 1);
            addBtn(id, x, y, w, h);
        }

        void title(string t, float fs, Color c) { DrawAr(t, W / 2, 34, fs, c, 1); R(W / 2 - 90, 78, 180, 3, c); }

        void chip(float x, float yref, string label, int val, Color c)
        {
            string s = label + " " + val;
            float w2 = MeasureAr(s, 24) + 26;
            R(x, 20, w2, 44, C(0x2a1f46, 220));
            R(x, 20, w2, 4, c);
            DrawAr(s, x + 13, 30, 24, CREAM, 0);
        }

        void drawToast()
        {
            if (string.IsNullOrEmpty(toastTxt) || toastT <= 0) return;
            float tw = MeasureAr(toastTxt, 26);
            R(W / 2 - tw / 2 - 16, H - 130, tw + 32, 48, C(0x2a1f46, 230));
            DrawAr(toastTxt, W / 2, H - 122, 26, WHT, 1);
        }

        void drawHud()
        {
            DrawAr(N(score), W / 2, 24, 58, WHT, 1);
            if (score > S.best) DrawAr(G("newBest"), W / 2, 96, 26, AURUM, 1);
            chip(20, 0, G("coins"), S.coins, AURUM);
            chip(170, 0, G("keys"), S.keys, TEAL);
            // powers
            float ty = 90; float[] tts = { shieldT, magnetT, slowT, x2T };
            uint[] cols = { 0x88d8ff, 0xff8fd8, 0x9affc0, 0xffe12a };
            for (int i = 0; i < 4; i++) if (tts[i] > 0) { R(20, ty, 120, 10, C(0x2a1f46, 200)); R(20, ty, 120 * Math.Min(1, tts[i] / 8f), 10, C(cols[i])); ty += 16; }
            // combo
            if (combo >= 3 && comboT > 0) DrawAr("COMBO " + (int)combo, W / 2, 100, 30, C(0xffa03a), 1);
            uiBtn(8, W - 76, 8, 60, 48, ORG, ORGD, G("pauseBtn"), 22);
            if (S.showFps == 1) DrawAr(((int)(1f / Math.Max(0.001f, (float)lastFrameTime))).ToString(), W - 70, H - 40, 22, C(0x8ff2e0), 0);
        }

        // ================= SCREENS =================
        void uiMenu()
        {
            drawWorld(); // live menu background
            float cy = H * 0.30f;
            DrawAr(G("brand"), W / 2, cy - 60, 78, AURUM, 1);
            DrawAr(G("sub"), W / 2, cy + 34, 40, ROSEC, 1);
            chip(20, 0, G("coins"), S.coins, AURUM);
            chip(190, 0, G("keys"), S.keys, TEAL);
            DrawAr(G("best") + " " + S.best, W - 20, 28, 26, CREAM, 2);
            // map chip
            R(W - 20 - MeasureAr(MapAr(curMap), 24) - 26, 70, MeasureAr(MapAr(curMap), 24) + 26, 40, C(0x2a1f46, 200));
            DrawAr(MapAr(curMap), W - 20, 76, 24, TEAL, 2);

            float bw = 320, bx = W / 2 - bw / 2, by = H * 0.52f, gap = 12;
            string[] labels = { G("play"), G("worlds"), G("shop"), G("missions"), G("settings") };
            int[] ids = { 1, 2, 3, 4, 5 };
            Color[] cols = { ORG, TEAL, ROSEC, AURUM, new Color(138, 79, 224) };
            for (int i = 0; i < 5; i++)
            {
                int h = 52;
                uiBtn(ids[i], bx, by, bw, h, cols[i], new Color(cols[i].R / 2, cols[i].G / 2, cols[i].B / 2), labels[i], 28);
                by += h + gap;
            }
            // missions badge
            int mc = msnClaimable();
            if (mc > 0) { Circ(bx + bw - 14, H * 0.52f + 3 * 64 + 12, 15, C(0xff3a3a)); DrawAr(N(mc), bx + bw - 14, H * 0.52f + 3 * 64 + 3, 18, WHT, 1); }
            // daily chest button when available
            if (dailyAvailable())
            {
                float pulse = 1 + (float)Math.Sin(stateT * 5) * 0.25f;
                Circ(W - 60, H - 70, 34 * pulse, AURUM);
                uiBtn(6, W - 100, H - 104, 80, 68, AURUM, new Color(168, 134, 26), "", 20);
            }
            // daily hint text
            if (dailyAvailable()) DrawAr(G("dailyTitle"), W - 100, H - 130, 20, AURUM, 2);
        }

        void uiWorlds()
        {
            drawWorld();
            Dim(0.55f);
            title(G("worldsTitle"), 40, TEAL);
            int cols = 4, cw = (int)(W - 60) / cols, ch = 150;
            for (int i = 0; i < 8; i++)
            {
                int r = i / cols, c = i % cols;
                float x = 30 + c * (cw + 10), y = 110 + r * (ch + 14);
                var m = Data.Maps[i];
                bool open = mapOpen(i);
                R(x, y, cw, ch, open ? C(0x2a1f46, 240) : C(0x1a1430, 240));
                R(x, y, cw, 8, C(m.gA));
                DrawAr(MapAr(i), x + cw / 2, y + 14, 26, WHT, 1);
                if (open)
                {
                    DrawAr(G("bestIn") + " " + S.mapBest[i], x + cw / 2, y + 52, 20, CREAM, 1);
                    if (curMap == i) uiBtn(99, x + cw / 2 - 40, y + ch - 44, 80, 36, TEAL, new Color(18, 106, 96), G("selected"), 17);
                    else uiBtn(30 + i, x + 10, y + ch - 44, cw - 20, 36, ORG, ORGD, G("select"), 17);
                }
                else
                {
                    DrawAr(G("unlockScore") + " " + Data.MapGateScore[i], x + cw / 2, y + 52, 17, CREAM, 1);
                    DrawAr(G("unlockKeys") + " " + Data.MapGateKeys[i], x + cw / 2, y + 74, 17, CREAM, 1);
                    if (mapGateOk(i)) uiBtn(40 + i, x + 10, y + ch - 44, cw - 20, 36, TEAL, new Color(18, 106, 96), G("open"), 18);
                    else
                    {
                        Circ(x + cw / 2, y + ch - 44 + 14, 15, C(0x8a80a8));
                        DrawAr(G("locked"), x + cw / 2, y + ch - 44 + 6, 18, C(0x8a80a8), 1);
                    }
                }
            }
            uiBtn(7, 20, H - 66, 120, 50, INK, INK, G("menu"), 22);
        }

        void uiShop()
        {
            drawWorld();
            Dim(0.55f);
            title(G("shop"), 40, ROSEC);
            int cols = 4, cw = (int)(W - 60) / cols, ch = 165;
            int start = shopPage * SHOP_PER;
            for (int i = start; i < Math.Min(35, start + SHOP_PER); i++)
            {
                int idx = i % 8; // position on page
                int r = idx / cols, c = idx % cols;
                float x = 30 + c * (cw + 10), y = 110 + r * (ch + 14);
                var sk = Data.Skins[i];
                bool own = owned(i) || i == 0;
                R(x, y, cw, ch, own ? C(0x2a1f46, 240) : C(0x1a1430, 240));
                R(x, y, cw, 8, C(sk.b));
                // mini bird
                dBird(x + cw / 2, y + 52, 12, sk);
                DrawAr(SkinAr(i), x + cw / 2, y + 74, 21, WHT, 1);
                if (own)
                {
                    if (S.skin == i) uiBtn(99, x + cw / 2 - 44, y + ch - 42, 88, 34, TEAL, new Color(18, 106, 96), G("selected"), 16);
                    else uiBtn(60 + i, x + 10, y + ch - 42, cw - 20, 34, ORG, ORGD, G("select"), 16);
                }
                else
                {
                    if (sk.q > 0)
                    {
                        DrawAr(G("coins") + " " + sk.p, x + cw / 2, y + 100, 18, AURUM, 1);
                        uiBtn(60 + i, x + 10, y + ch - 42, cw - 20, 34, AURUM, new Color(168, 134, 26), G("buy"), 16);
                    }
                    else
                    {
                        // map-gated skin
                        DrawAr(G("needMap") + " " + sk.m + " - " + sk.mg, x + cw / 2, y + 100, 15, C(0x8a80a8), 1);
                        if (skinGateOk(i)) uiBtn(60 + i, x + 10, y + ch - 42, cw - 20, 34, TEAL, new Color(18, 106, 96), G("open"), 16);
                        else { Circ(x + cw / 2, y + ch - 42 + 14, 13, C(0x8a80a8)); DrawAr(G("locked"), x + cw / 2, y + ch - 42 + 6, 16, C(0x8a80a8), 1); }
                    }
                }
            }
            if (shopPage > 0) uiBtn(95, 20, H - 66, 60, 50, INK, INK, "<", 30);
            if ((shopPage + 1) * SHOP_PER < 35) uiBtn(96, W - 80, H - 66, 60, 50, INK, INK, ">", 30);
            DrawAr(N(shopPage + 1) + " / 5", W / 2, H - 52, 24, CREAM, 1);
            uiBtn(7, 100, H - 66, 110, 50, INK, INK, G("menu"), 22);
        }

        void dBird(float x, float y, float r, Data.SkinDef sk)
        {
            Circ(x, y, r, C(sk.b));
            Circ(x + 1, y + r * 0.25f, r * 0.6f, C(sk.l));
            Circ(x + r * 0.5f, y - r * 0.35f, r * 0.3f, WHT);
            Circ(x + r * 0.58f, y - r * 0.35f, r * 0.15f, new Color(30, 30, 40));
            Rot(x + r * 0.95f, y, r * 0.5f, r * 0.35f, 0.15f, C(sk.k));
            Rot(x - r * 0.25f, y, r * 0.25f, r * 0.6f, 0.6f, C(sk.o));
            if (sk.acc == 1) R(x - r * 0.5f, y - r * 1.5f, r * 0.25f, r * 0.4f, C(0xffd23f));
            if (sk.acc == 2) R(x, y - r * 0.4f, r * 0.8f, r * 0.3f, C(0x1c1e26, 230));
            if (sk.acc == 3) DrawRing(x, y - r * 1.5f, r * 0.4f, r * 0.12f, C(0xffe12a));
        }

        void uiMsn()
        {
            drawWorld();
            Dim(0.55f);
            title(G("missionsTitle"), 40, AURUM);
            string[] kinds = { G("msn0"), G("msn1"), G("msn2"), G("msn3") };
            for (int i = 0; i < NMsn; i++)
            {
                float y = 110 + i * 78;
                bool done = S.msnDone[i] == 1 && msnProg(i) >= msnGoal(i);
                R(30, y, W - 60, 66, C(0x2a1f46, 240));
                R(30, y, 6, 66, done ? TEAL : ORG);
                DrawAr(kinds[msnKind(i)], W - 44, y + 6, 22, WHT, 2);
                DrawAr(G("reward") + " " + msnReward(i), W - 44, y + 36, 18, AURUM, 2);
                float pw2 = (W - 300) * Math.Min(1f, msnProg(i) / (float)Math.Max(1, msnGoal(i)));
                R(60, y + 40, W - 300, 12, new Color(0, 0, 0, 90));
                R(60, y + 40, pw2, 12, done ? TEAL : AURUM);
                DrawAr(N(msnProg(i)) + "/" + N(msnGoal(i)), 44, y + 8, 22, CREAM, 0);
                if (S.msnDone[i] == 0 && msnProg(i) >= msnGoal(i))
                    uiBtn(50 + i, 130, y + 20, 130, 42, TEAL, new Color(18, 106, 96), G("claim"), 20);
                else if (done) DrawAr(G("done"), 160, y + 26, 20, TEAL, 0);
            }
            uiBtn(7, 20, H - 66, 120, 50, INK, INK, G("menu"), 22);
        }

        void uiSet()
        {
            drawWorld();
            Dim(0.55f);
            title(G("settingsTitle"), 40, new Color(138, 79, 224));
            float bw = Math.Min(560, W - 200), bx = W / 2 - bw / 2;
            float by = 110, bh = 52, gap = 12;
            Color off = new Color(74, 64, 110);
            uiBtn(20, bx, by, bw, bh, S.sfxV > 0 ? TEAL : off, INK, G("sound") + (S.sfxV > 0 ? " ON" : " OFF"), 24); by += bh + gap;
            uiBtn(21, bx, by, bw, bh, S.musV > 0 ? TEAL : off, INK, G("music") + (S.musV > 0 ? " ON" : " OFF"), 24); by += bh + gap;
            string ql = G("quality") + ": " + (S.qual == 0 ? G("qualityL") : (S.qual == 1 ? G("qualityM") : G("qualityH")));
            uiBtn(22, bx, by, bw, bh, ORG, ORGD, ql, 24); by += bh + gap;
            uiBtn(23, bx, by, bw, bh, S.shakeOn == 1 ? TEAL : off, INK, G("shake") + (S.shakeOn == 1 ? " ON" : " OFF"), 24); by += bh + gap;
            uiBtn(24, bx, by, bw, bh, S.showFps == 1 ? TEAL : off, INK, G("fps") + (S.showFps == 1 ? " ON" : " OFF"), 24); by += bh + gap;
            uiBtn(25, bx, by, bw, bh, ORG, ORGD, G("lang") + (S.lang == 1 ? " EN" : " AR"), 24); by += bh + gap;
            uiBtn(26, bx, by, bw, bh, wipeArmed ? Color.OrangeRed : new Color(120, 40, 40), INK, wipeArmed ? G("sure") : G("reset"), 24); by += bh + gap * 2;
            // stats
            R(bx, by, bw, 150, C(0x2a1f46, 230));
            DrawAr(G("stats"), bx + bw / 2, by + 6, 22, CREAM, 1);
            DrawAr(G("runs") + " " + S.runs, bx + 14, by + 40, 20, CREAM, 0);
            DrawAr(G("earned") + " " + S.totCoins, bx + 14, by + 66, 20, CREAM, 0);
            int mo = 0; for (int i = 0; i < 8; i++) if (mapOpen(i)) mo++;
            DrawAr(G("mapsOpened") + " " + mo + "/8", bx + 14, by + 92, 20, CREAM, 0);
            DrawAr(G("best") + " " + S.best, bx + 14, by + 118, 20, AURUM, 0);
            uiBtn(7, 20, H - 66, 120, 50, INK, INK, G("menu"), 22);
        }

        void uiDaily()
        {
            drawWorld();
            Dim(0.6f);
            float bw = 460, bh = 300, x = W / 2 - bw / 2, y = H / 2 - bh / 2;
            R(x - 4, y - 4, bw + 8, bh + 8, AURUM);
            R(x, y, bw, bh, C(0x2a1f46));
            DrawAr(G("dailyTitle"), W / 2, y + 24, 36, AURUM, 1);
            // chest
            float cxp = W / 2;
            R(cxp - 46, y + 100, 92, 70, C(0x8a5a1a));
            R(cxp - 46, y + 100, 92, 20, C(0xa8861a));
            R(cxp - 8, y + 128, 16, 18, C(0xffd23f));
            DrawAr("STREAK " + S.dailyStreak + " - " + G("comeBack"), W / 2, y + 190, 18, CREAM, 1);
            uiBtn(27, W / 2 - 110, y + bh - 66, 220, 50, TEAL, new Color(18, 106, 96), G("dailyOpen"), 24);
        }

        void uiPause()
        {
            Dim(0.6f);
            DrawAr(G("paused"), W / 2, H / 2 - 130, 52, WHT, 1);
            uiBtn(9, W / 2 - 130, H / 2 - 40, 260, 56, TEAL, new Color(18, 106, 96), G("resume"), 28);
            uiBtn(11, W / 2 - 130, H / 2 + 40, 260, 56, INK, INK, G("menu"), 28);
        }

        void uiOver()
        {
            Dim(0.68f);
            float ov = Math.Min(1f, stateT * 1.5f);
            DrawAr(G("gameover"), W / 2, H * 0.16f, 48, WHT, 1);
            int shown = (int)Math.Min(score, (stateT - 0.3f) * 60);
            if (shown < 0) shown = 0;
            DrawAr(N(shown), W / 2, H * 0.30f, 74, AURUM, 1);
            DrawAr(G("best") + " " + S.best, W / 2, H * 0.30f + 92, 26, CREAM, 1);
            if (score > S.best - score) { } // no-op
            if (shown >= score && score > 0 && score < S.best)
            {
                if (score >= 30) { Circ(W / 2, H * 0.25f, 26, C(0xffd23f)); DrawAr(G("mediGold"), W / 2, H * 0.30f + 118, 22, C(0xffd23f), 1); }
                else if (score >= 20) { Circ(W / 2, H * 0.25f, 26, C(0xc0c8d8)); DrawAr(G("mediSilv"), W / 2, H * 0.30f + 118, 22, C(0xc0c8d8), 1); }
                else if (score >= 10) { Circ(W / 2, H * 0.25f, 26, C(0xc0783a)); DrawAr(G("mediBron"), W / 2, H * 0.30f + 118, 22, C(0xc0783a), 1); }
            }
            float byy = H * 0.58f;
            if (pendingRevive && S.coins >= Data.ReviveCost)
            {
                uiBtn(12, W / 2 - 130, byy, 260, 58, AURUM, new Color(168, 134, 26), G("revive") + " " + Data.ReviveCost, 26);
                byy += 70;
            }
            uiBtn(10, W / 2 - 130, byy, 260, 58, ORG, ORGD, G("retry"), 28);
            byy += 70;
            uiBtn(11, W / 2 - 130, byy, 260, 58, INK, INK, G("menu"), 28);
            chip(20, 0, G("coins"), S.coins, AURUM);
        }

        void uiReady()
        {
            float pulse = 1 + (float)Math.Sin(stateT * 6) * 0.05f;
            R(0, H / 2 - 60, W, 120, C(0x2a1f46, 150));
            DrawAr(G("ready"), W / 2, H / 2 - 40, (int)(44 * pulse), WHT, 1);
            DrawAr(G("tapStart"), W / 2, H / 2 + 14, 26, CREAM, 1);
        }

        // ================= INPUT + DISPATCH =================
        double lastFrameTime = 0.016;

        void pollTouch()
        {
            tJust = false; tDown = false;
            var tc = TouchPanel.GetState();
            if (tc.Count > 0)
            {
                var t0 = tc[0];
                float sx = t0.Position.X / (GraphicsDevice.Viewport.Height / (float)H);
                float sy = t0.Position.Y / (GraphicsDevice.Viewport.Height / (float)H);
                tPos = new Vector2(sx, sy);
                tDown = t0.State == TouchLocationState.Pressed || t0.State == TouchLocationState.Moved;
                if (t0.State == TouchLocationState.Pressed) tJust = true;
            }
            else
            {
                var ms = Microsoft.Xna.Framework.Input.Mouse.GetState();
                if (ms.LeftButton == Microsoft.Xna.Framework.Input.ButtonState.Pressed)
                {
                    tDown = true;
                    if (!prevMouse) tJust = true;
                    tPos = new Vector2(ms.X / (GraphicsDevice.Viewport.Height / (float)H), ms.Y / (GraphicsDevice.Viewport.Height / (float)H));
                }
                prevMouse = ms.LeftButton == Microsoft.Xna.Framework.Input.ButtonState.Pressed;
            }
        }
        bool prevMouse;

        int hitBtn(Vector2 p)
        {
            for (int i = btns.Count - 1; i >= 0; i--)
            {
                var b = btns[i];
                if (p.X >= b.x && p.X <= b.x + b.w && p.Y >= b.y && p.Y <= b.y + b.h) return b.id;
            }
            return 0;
        }

        void flap()
        {
            birdV = FLAPV; flapSfx();
        }
        void flapSfx() { sfxFlap?.Play(); }

        void dispatchBtn(int id)
        {
            if (id == 0) return;
            if (id == 1) { startRun(); return; }
            if (id == 2) { state = St.Worlds; stateT = 0; return; }
            if (id == 3) { state = St.Shop; stateT = 0; shopPage = 0; return; }
            if (id == 4) { state = St.Msn; stateT = 0; return; }
            if (id == 5) { state = St.Set; stateT = 0; wipeArmed = false; return; }
            if (id == 6) { state = St.Daily; stateT = 0; return; }
            if (id == 7) { state = St.Menu; stateT = 0; return; }
            if (id == 8) { state = St.Pause; stateT = 0; return; }
            if (id == 9) { state = St.Play; stateT = 0; return; }
            if (id == 10) { startRun(); return; }
            if (id == 11) { state = St.Menu; stateT = 0; return; }
            if (id == 12) { doRevive(); return; }
            if (id == 27) { dailyOpen(); return; }
            // worlds
            if (id >= 30 && id < 38)
            {
                int i2 = id - 30;
                if (mapOpen(i2)) { curMap = i2; MP = Data.Maps[curMap]; sfxFlap?.Play(); toast(MapAr(i2), 1.5f); }
                return;
            }
            if (id >= 40 && id < 48)
            {
                int i2 = id - 40;
                if (mapGateOk(i2)) { setMapOpen(i2); curMap = i2; MP = Data.Maps[curMap]; sfxUnlock?.Play(); toast(G("unlocked"), 2.2f); svSave(); }
                else { toast(G("needKeys"), 2f); sfxHit?.Play(); }
                return;
            }
            // missions
            if (id >= 50 && id < 55) { msnClaim(id - 50); return; }
            // skins
            if (id >= 60 && id < 95)
            {
                int i2 = id - 60;
                if (owned(i2) || i2 == 0) { S.skin = i2; sfxFlap?.Play(); svSave(); }
                else
                {
                    var sk = Data.Skins[i2];
                    if (sk.q > 0)
                    {
                        if (S.coins >= sk.p) { S.coins -= sk.p; setOwned(i2); S.skin = i2; sfxBuy?.Play(); toast(G("owned"), 2f); svSave(); }
                        else { toast(G("noCoins"), 2f); sfxHit?.Play(); }
                    }
                    else
                    {
                        if (skinGateOk(i2)) { setOwned(i2); S.skin = i2; sfxUnlock?.Play(); svSave(); }
                        else { toast(G("needMap"), 2f); sfxHit?.Play(); }
                    }
                }
                return;
            }
            if (id == 95) { shopPage = Math.Max(0, shopPage - 1); return; }
            if (id == 96) { if ((shopPage + 1) * SHOP_PER < 35) shopPage++; return; }
            // settings
            if (id == 20) { S.sfxV = S.sfxV > 0 ? 0 : 100; sfxFlap?.Play(); svSave(); return; }
            if (id == 21) { S.musV = S.musV > 0 ? 0 : 70; applyVolumes(); svSave(); return; }
            if (id == 22) { S.qual = (S.qual + 1) % 3; sfxFlap?.Play(); svSave(); return; }
            if (id == 23) { S.shakeOn = S.shakeOn == 1 ? 0 : 1; sfxFlap?.Play(); svSave(); return; }
            if (id == 24) { S.showFps = S.showFps == 1 ? 0 : 1; svSave(); return; }
            if (id == 25) { S.lang = S.lang == 1 ? 0 : 1; svSave(); return; }
            if (id == 26)
            {
                if (!wipeArmed) { wipeArmed = true; toast(G("sure"), 2.5f); }
                else { svWipe(); wipeArmed = false; applyVolumes(); toast(G("done"), 2f); msnInitAll(); }
                return;
            }
        }

        // ================= FRAME LOOP =================
        protected override void Update(GameTime gameTime)
        {
            float dt = (float)gameTime.ElapsedGameTime.TotalSeconds;
            if (dt > 0.06f) dt = 0.06f;
            lastFrameTime = gameTime.ElapsedGameTime.TotalSeconds;
            float vh = GraphicsDevice.Viewport.Height;
            H = 720f; W = GraphicsDevice.Viewport.Width / (vh / 720f);

            pollTouch();
            if (tJust) steerPrevX = tPos.X;
            stateT += dt;
            if (toastT > 0) toastT -= dt;

            if (tJust && state != St.Play && state != St.Ready)
            {
                int id = hitBtn(tPos);
                dispatchBtn(id);
            }
            else if (tJust && state == St.Ready)
            {
                state = St.Play; stateT = 0; flap();
            }
            else if (tDown && state == St.Play)
            {
                // continuous relative steering while dragging
                float dx = (tPos.X - steerPrevX) / W * 900 * (S.sens / 100f);
                for (int i = 0; i < obs.Count; i++) { var ob = obs[i]; ob.x -= dx * dt; obs[i] = ob; }
                for (int i = 0; i < coinsA.Count; i++) { var cA = coinsA[i]; cA.x -= dx * dt; coinsA[i] = cA; }
                for (int i = 0; i < pws.Count; i++) { var p2 = pws[i]; p2.x -= dx * dt; pws[i] = p2; }
                spawnX -= dx * dt;
                steerPrevX = tPos.X;
            }
            if (state == St.Play && !tDown) { }
            lastTapDown = tDown;

            switch (state)
            {
                case St.Splash: if (stateT > 1.9f) { state = St.Menu; stateT = 0; } break;
                case St.Play:
                    updateRun(dt);
                    ambient(dt);
                    skyUpdate();
                    if (tJust) flap(); // tap = flap (also handles multi-tap) — steering uses drag
                    break;
                case St.Dying:
                    deadT += dt;
                    updateParts(dt);
                    if (deadT > 0.8f) finishRun();
                    break;
                case St.Ready: skyUpdate(); flapT += dt; break;
                case St.Menu:
                case St.Worlds:
                case St.Shop:
                case St.Msn:
                case St.Set:
                    ambient(dt); skyUpdate(); flapT += dt;
                    break;
            }

            base.Update(gameTime);
        }
        bool lastTapDown;

        protected override void Draw(GameTime gameTime)
        {
            GraphicsDevice.Clear(C(0x2a1f46));
            sb.Begin(SpriteSortMode.Deferred, BlendState.AlphaBlend);
            btns.Clear();

            switch (state)
            {
                case St.Splash:
                    GraphicsDevice.Clear(new Color(26, 18, 50));
                    float k = Math.Min(1f, stateT / 1.2f);
                    DrawAr(G("studio"), W / 2, H / 2 - 60 * k, 52 * (0.6f + 0.4f * k), Faded(AURUM, k), 1);
                    DrawAr(G("presents"), W / 2, H / 2 + 40, 30, Faded(CREAM, 0.78f * k), 1);
                    break;
                case St.Menu: uiMenu(); break;
                case St.Worlds: uiWorlds(); break;
                case St.Shop: uiShop(); break;
                case St.Msn: uiMsn(); break;
                case St.Set: uiSet(); break;
                case St.Daily: uiDaily(); break;
                case St.Ready:
                    skyUpdate(); drawWorld();
                    break;
                case St.Play:
                    skyUpdate();
                    if (S.shakeOn == 1 && shakeAmp > 0.02f)
                        drawWorldShaken();
                    else drawWorld();
                    break;
                case St.Dying:
                    drawWorld();
                    drawBirdDead();
                    break;
                case St.Over: drawWorld(); drawBirdDead(); uiOver(); break;
                case St.Pause: drawWorld(); uiPause(); break;
            }

            if (state == St.Ready) { drawHudReady(); uiReady(); }
            if (state == St.Play) drawHud();
            if (state == St.Dying) drawHud();
            drawToast();
            sb.End();
            base.Draw(gameTime);
        }

        void drawWorldShaken()
        {
            float shx = shakeAmp * 10 * (float)Math.Sin(stateT * 91);
            float shy = shakeAmp * 10 * (float)Math.Cos(stateT * 77);
            sb.End(); sb.Begin(SpriteSortMode.Deferred, BlendState.AlphaBlend, null, null, null, null, Matrix.CreateTranslation(shx, shy, 0));
            drawWorld();
            sb.End(); sb.Begin(SpriteSortMode.Deferred, BlendState.AlphaBlend);
        }

        void drawBirdDead()
        {
            float y2 = Math.Min(GROUND - 26, birdY + deadT * 400);
            Rot(birdXs(), y2, 46, 40, deadT * 2.4f, C(Data.Skins[S.skin].b));
            Circ(birdXs() - 10, y2 - 10, 6, new Color(30, 30, 40)); // X eye approximation
        }

        void drawHudReady()
        {
            chip(20, 0, G("coins"), S.coins, AURUM);
            chip(190, 0, G("keys"), S.keys, TEAL);
            DrawAr(MapAr(curMap), W - 20, 28, 26, TEAL, 2);
        }
    }
}
