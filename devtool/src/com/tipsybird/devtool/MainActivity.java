package com.tipsybird.devtool;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Tipsy Tools Pro — multi-page developer companion for the Tipsy Bird 3D project.
 *  Standalone native APK (no WebView/gradle); built via aapt2+javac+d8 in CI. */
public class MainActivity extends Activity {

    static final String REPO = "worldnewsportal/Tipsy-Bird-3D-Sky-Adventure";
    static final String GAME_ID = "800443610";
    static final String PKG = "com.tipsybird.game";
    static final String BRANCH = "vibe/fully-offline-c73d38";
    static final String API = "https://api.github.com/repos/" + REPO;

    static final int BG = 0xFF1C1230, CARD = 0xFF2A1E4A, CARD2 = 0xFF362858;
    static final int ACCENT = 0xFFFF7A2F, ACCENT_D = 0xFFC4480F;
    static final int TEAL = 0xFF27C1B0, TEAL_D = 0xFF13867A;
    static final int GOLD = 0xFFFFC94D, TXT = 0xFFFFFFFF, SUB = 0xFFC6BCDD, DIM = 0xFF8F84AA;
    static final int TERM_BG = 0xFF0C1512, TERM_FG = 0xFF8DF0A8;

    // page ids
    static final int P_HOME = 0, P_BUILDS = 1, P_RELEASES = 2, P_UPDATE = 3, P_CHANNEL = 4,
            P_PUBLISH = 5, P_PROJECT = 6, P_LOG = 7, P_CHECK = 8, P_TERM = 9, P_DEVICE = 10, P_GUIDE = 11;
    static final String[] TITLES = {
        "الرئيسية", "بناءات اللعبة", "إصدارات المطور", "فاحص التحديث", "قناة العمل",
        "حزمة النشر", "بطاقة المشروع", "سجل المراحل", "قائمة التحقق", "وحدة الأوامر",
        "الجهاز", "دليل المبتدئ"
    };
    static final String[] TILE_EMOJI = {"🚀", "🧪", "🔄", "💬", "📦", "🎯", "📜", "✅", "⌨️", "📱", "🎓"};
    static final String[] TILE_LABEL = {"بناءات اللعبة", "إصدارات المطور", "فاحص التحديث", "قناة العمل",
            "حزمة النشر", "بطاقة المشروع", "سجل المراحل", "قائمة التحقق", "وحدة الأوامر", "الجهاز", "دليل المبتدئ"};

static final String DESC_AR =
        "\uD83D\uDC26 Tipsy Bird 3D Sky Adventure — مغامرة سماء بثلاثة أبعاد كاملة!\n\n" +
        "قُد طائرتك عبر 8 عوالم ثلاثية الأبعاد مختلفة جذريًا: \uD83C\uDF0B البركان (حمم وجمرات صاعدة) \uD83C\uDF0A المحيط (طفو وفقاعات) \uD83D\uDE80 الفضاء (نجوم وجاذبية خفيفة) \uD83C\uDF34 الأدغال (عملات وفيرة) ⛈️ العاصفة (رياح سريعة) — بالإضافة إلى 3 خرائط أساسية بأجواء متغيرة (نهار → غروب → ليل).\n\n" +
        "✨ المميزات:\n" +
        "• 8 مابات بفيزياء وسماءات وديكورات فريدة — تُفتح بالتقدم + مفاتيح المابات \uD83D\uDD11\n" +
        "• 35 سكنًا، من بينها 15 سكنًا خاصًا صعب الحصول عليه يعمل في أي ماب\n" +
        "• جرافيكس 3D بثلاثة أطوار: بسيط / متوسط / خارق (تأثيرات سينمائية)\n" +
        "• اقتصاد كامل: عملات، متجر كبير (قدرات دائمة + استهلاكيات)، مهام متجددة للأبد بباقات أقوى\n" +
        "• 3 موسيقيات جميلة تتناوب تلقائيًا + مؤثرات صوتية حية\n" +
        "• مكافآت إعلانية اختيارية تمامًا: عُد من مكان موتك أو خُذ عملات إضافية\n" +
        "• حفظ ذكي مزدوج: تقدمك يُسترجع حتى بعد حذف اللعبة وإعادة تثبيتها\n" +
        "• تعمل 100% بدون إنترنت — الإنترنت فقط للإعلانات الاختيارية\n" +
        "• عربية وإنجليزية\n\n" +
        "حمّلها الآن وحلّق! \uD83D\uDE80";

    static final String DESC_EN =
        "\uD83D\uDC26 Tipsy Bird 3D Sky Adventure — a full 3D sky adventure!\n\n" +
        "Fly your bird through 8 radically different 3D worlds: Volcano, Ocean, Space, Jungle and Storm — plus 3 classic maps with changing skies (day \u2192 sunset \u2192 night).\n\n" +
        "✨ Features:\n" +
        "• 8 maps with unique physics, skies and decor — unlocked by progress + map keys \uD83D\uDD11\n" +
        "• 35 skins including 15 hard per-map collector skins usable on ANY map\n" +
        "• 3D graphics with 3 quality tiers: Basic / Medium / Ultra (cinematic effects)\n" +
        "• Full economy: coins, big shop, endless missions in tougher batches\n" +
        "• 3 beautiful alternating music tracks + live sound effects\n" +
        "• Fully optional rewarded ads: continue from where you fell, or grab bonus coins\n" +
        "• Smart dual save: progress restored even after uninstall + reinstall\n" +
        "• 100% offline — internet only for optional ads\n" +
        "• Arabic & English\n\n" +
        "Download now and flap away! \uD83D\uDE80";

    static final String CHANGELOG =
        "29 — 5 مابات جديدة (بركان/محيط/فضاء/أدغال/عاصفة) + 15 سكن يعمل بأي ماب + مفاتيح \uD83D\uDD11 + بانر دائم + وضع أفقي + لوحة مطوّر داخلية (3 نقرات بالشعار)\n" +
        "28 — تشخيص إعلانات حي لكل نوع + حفظ يفوق حذف التطبيق (Auto Backup)\n" +
        "27 — إصلاح الإحياء بالإعلان (عودة لمكان الموت + حماية 3ث)\n" +
        "26 — صفحة خصوصية/أحكام (gh-pages) + عدة نشر Uptodown\n" +
        "25 — حذف multiplayer كليًا؛ offline كامل، الإنترنت للإعلان فقط\n" +
        "24 — 3 موسيقيات متناوبة + Ultra قوي (rim light + ACES)\n" +
        "23 — إصلاح الخروج مع الإنترنت + i18n نظيف\n" +
        "21-22 — 3 أطوار جرافيكس + فحوص CI دائمة\n" +
        "18-20 — مجسمات 3D أقوى + أزرار SVG + إعلانات online/offline\n" +
        "16-17 — native كامل بلا PC + توقيع ثابت + Artifacts\n" +
        "1-15 — offline + مهام + عملات + متجر + Unity Ads";

    static final String CHECKLIST =
        "☑ APK موقّع من latest release (zipaligned + apksigner)\n" +
        "☑ versionCode متصاعد تلقائيًا (run_number)\n" +
        "☑ سياسة خصوصية + أحكام على gh-pages\n" +
        "☑ أذونات حدًّا ضروريًا (INTERNET + VIBRATE)\n" +
        "☑ أيقونة 512 + 8 لقطات 1080×1920 + Featured 1024×500\n" +
        "☑ وصف عربي/إنجليزي بلا مصطلحات تقنية\n" +
        "☑ تصنيف +3 — الإعلانات معلنة (Unity Ads)\n" +
        "□ بريد تواصل (يدوي منك)\n" +
        "□ تفعيل GitHub Pages (يدوي منك)";

    static final int MATCH = LinearLayout.LayoutParams.MATCH_PARENT;
    static final int WRAP = LinearLayout.LayoutParams.WRAP_CONTENT;

    LinearLayout shellContent, root, cur;
    TextView tvHead, tvStatus, tvBuilds, tvRel, tvUpd, tvFeed, tvTerm, tvDev;
    Button btnBack;
    EditText etPost, etTok, etCmd;
    SharedPreferences prefs;
    ClipboardManager clip;
    int curPage = P_HOME;

    void ui(Runnable r) { runOnUiThread(r); }
    void bg(Runnable r) { Thread t = new Thread(r); t.setDaemon(true); t.start(); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("tipsytool", MODE_PRIVATE);
        clip = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        setContentView(shell());
        show(P_HOME);
    }

    View shell() {
        LinearLayout out = new LinearLayout(this);
        out.setOrientation(LinearLayout.VERTICAL);
        out.setBackgroundColor(BG);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(12), dp(14), dp(12), dp(14));
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF4A3478, BG});
        hg.setCornerRadii(new float[]{0, 0, 0, 0, dp(18), dp(18), 0, 0});
        head.setBackground(hg);
        btnBack = btn("◀", TEAL, new View.OnClickListener() { public void onClick(View v) { show(P_HOME); } });
        btnBack.setPadding(dp(6), dp(4), dp(6), dp(4));
        head.addView(btnBack, new LinearLayout.LayoutParams(WRAP, WRAP));
        tvHead = txt("🛠️ Tipsy Tools Pro", TXT, 19, true);
        tvHead.setPadding(dp(12), 0, 0, 0);
        head.addView(tvHead, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView ver = txt("dev", DIM, 12, false);
        try { PackageInfo me = getPackageManager().getPackageInfo(getPackageName(), 0); ver.setText("dev-" + me.versionCode); } catch (Exception e) {}
        head.addView(ver, new LinearLayout.LayoutParams(WRAP, WRAP));
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        shellContent = new LinearLayout(this);
        shellContent.setOrientation(LinearLayout.VERTICAL);
        shellContent.setPadding(dp(16), dp(14), dp(16), dp(36));
        sv.addView(shellContent, MATCH, WRAP);
        out.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));
        out.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        return out;
    }

    void show(int p) {
        curPage = p;
        tvHead.setText(p == P_HOME ? "🛠️ Tipsy Tools Pro" : TITLES[p]);
        btnBack.setVisibility(p == P_HOME ? View.INVISIBLE : View.VISIBLE);
        shellContent.removeAllViews();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        cur = root;
        shellContent.addView(root, MATCH, WRAP);
        switch (p) {
            case P_BUILDS: pageBuilds(); break;
            case P_RELEASES: pageReleases(); break;
            case P_UPDATE: pageUpdate(); break;
            case P_CHANNEL: pageChannel(); break;
            case P_PUBLISH: pagePublish(); break;
            case P_PROJECT: pageProject(); break;
            case P_LOG: pageLog(); break;
            case P_CHECK: pageCheck(); break;
            case P_TERM: pageTerm(); break;
            case P_DEVICE: pageDevice(); break;
            case P_GUIDE: pageGuide(); break;
            default: pageHome(); break;
        }
    }

    // ---------- 0. Home ----------
    void pageHome() {
        LinearLayout hero = panel();
        TextView h1 = txt("🛠️", GOLD, 32, true); h1.setGravity(Gravity.CENTER);
        TextView h2 = txt("Tipsy Tools Pro", TXT, 22, true); h2.setGravity(Gravity.CENTER);
        TextView h3 = txt("أداة مطوّر احترافية بفكرة بسيطة: قوة Termux بدون شاشة أوامر — كل شيء أزرار وصفحات مرتّبة", SUB, 13, false);
        h3.setGravity(Gravity.CENTER);
        hero.addView(h1, lpm(0, dp(2), 0, dp(2)));
        hero.addView(h2, lpm(0, dp(2), 0, dp(2)));
        hero.addView(h3, lpm(0, dp(4), 0, 0));
        cur = root;
        LinearLayout st = panel();
        st.addView(subtitle("حالة المشروع الآن"));
        st.addView(spacer(4));
        tvStatus = txt("⏳ جارٍ فحص الحالة…", SUB, 13, false);
        st.addView(tvStatus);
        bg(statusFetch());
        cur = root;
        addTiles();
        LinearLayout q = panel();
        q.addView(subtitle("ابدأ من: «فاحص التحديث» لتثبيت أحدث نسخة، و«دليل المبتدئ» لتعلم كل شيء في 6 خطوات."));
    }

    void addTiles() {
        int[] tiles = {P_BUILDS, P_RELEASES, P_UPDATE, P_CHANNEL, P_PUBLISH, P_PROJECT, P_LOG, P_CHECK, P_TERM, P_DEVICE, P_GUIDE};
        for (int k = 0; k < tiles.length; k += 2) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.addView(tile(TILE_EMOJI[tiles[k] - 1], TILE_LABEL[tiles[k] - 1], tiles[k]), wa());
            if (k + 1 < tiles.length) {
                r.addView(tile(TILE_EMOJI[tiles[k + 1] - 1], TILE_LABEL[tiles[k + 1] - 1], tiles[k + 1]), wa());
            } else {
                View f = new View(this);
                r.addView(f, wa());
            }
            root.addView(r, mmp(0, dp(5), 0, dp(5)));
        }
    }

    View tile(final String emoji, final String label, final int page) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD2); g.setCornerRadius(dp(14));
        t.setBackground(g);
        int p = dp(14);
        t.setPadding(p, dp(18), p, dp(18));
        TextView e = txt(emoji, TXT, 24, false); e.setGravity(Gravity.CENTER);
        t.addView(e, MATCH, WRAP);
        t.addView(spacer(6), MATCH, WRAP);
        TextView l = txt(label, SUB, 12, false); l.setGravity(Gravity.CENTER);
        t.addView(l, MATCH, WRAP);
        t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { show(page); } });
        return t;
    }

    Runnable statusFetch() {
        return new Runnable() { public void run() {
            final StringBuilder sb = new StringBuilder();
            try { PackageInfo pi = getPackageManager().getPackageInfo(PKG, 0);
                sb.append("🎮 اللعبة مثبّتة: v").append(pi.versionName).append(" (بناء #").append(pi.versionCode).append(")\n"); }
            catch (Exception e) { sb.append("🎮 اللعبة غير مثبّتة على هذا الجهاز\n"); }
            String latest = "";
            try { latest = new JSONObject(httpGet(API + "/releases/latest")).optString("tag_name"); } catch (Exception e) {}
            sb.append("🏷️ آخر إصدار بالمستودع: ").append(latest.length() == 0 ? "؟" : latest).append("\n");
            try { PackageInfo me = getPackageManager().getPackageInfo(getPackageName(), 0);
                sb.append("🧰 هذه الأداة: dev-").append(me.versionCode); } catch (Exception e) {}
            ui(new Runnable() { public void run() { TextView t = tvStatus; if (t != null) t.setText(sb.toString()); } });
        }};
    }

    // ---------- 1. CI builds (old feature preserved) ----------
    void pageBuilds() {
        LinearLayout c = panel();
        c.addView(subtitle("آخر بناءات automatic CI للعبة — ✅ نجاح، ⏳ قيد التنفيذ، ❌ فشل"));
        c.addView(spacer(6));
        tvBuilds = txt("⏳ جارٍ التحميل…", SUB, 13, false);
        c.addView(tvBuilds);
        c.addView(spacer(10));
        cur = root;
        LinearLayout r = row();
        r.addView(btn("تحديث ↻", TEAL, new View.OnClickListener() { public void onClick(View v) { fetchBuilds(); } }), wa());
        r.addView(btn("تحميل أحدث APK ⬇", ACCENT, new View.OnClickListener() { public void onClick(View v) { download("TipsyBird3D.apk", "TipsyBird3D.apk"); } }), wa());
    }

    void fetchBuilds() {
        ui(new Runnable() { public void run() { TextView t = tvBuilds; if (t != null) t.setText("⏳ جارٍ التحميل…"); } });
        bg(new Runnable() { public void run() {
            try {
                JSONArray arr = new JSONObject(httpGet(API + "/actions/runs?per_page=6")).getJSONArray("workflow_runs");
                final StringBuilder sb = new StringBuilder();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject r = arr.getJSONObject(i);
                    String st = r.optString("status"), con = r.optString("conclusion");
                    String em = "⏳";
                    if ("completed".equals(st)) em = "success".equals(con) ? "✅" : ("skipped".equals(con) ? "⏭" : "❌");
                    sb.append(em).append(' ').append('#').append(r.optInt("run_number")).append(' ')
                      .append("في [").append(r.optString("head_branch")).append("]\n")
                      .append("    ").append(r.optString("created_at")).append("  ").append(st)
                      .append(con.length() == 0 ? "" : "/" + con).append("\n\n");
                }
                ui(new Runnable() { public void run() { TextView t = tvBuilds; if (t != null) t.setText(sb.toString()); } });
            } catch (final Exception e) {
                ui(new Runnable() { public void run() { TextView t = tvBuilds; if (t != null) t.setText("⚠ تعذر الاتصال — تحقق من الإنترنت"); } });
            }
        }});
    }

    // ---------- 2. dev releases (old feature preserved) ----------
    void pageReleases() {
        LinearLayout c = panel();
        c.addView(subtitle("إصدارات هذه الأداة (tags: dev-N) — كل بناء ناجح يُنشر هنا باسم TipsyBirdDevTools.apk"));
        c.addView(spacer(6));
        tvRel = txt("⏳ جارٍ التحميل…", SUB, 13, false);
        c.addView(tvRel);
        c.addView(spacer(10));
        cur = root;
        LinearLayout r = row();
        r.addView(btn("تحديث ↻", TEAL, new View.OnClickListener() { public void onClick(View v) { fetchDevReleases(); } }), wa());
        r.addView(btn("تحميل الأداة ⬇", ACCENT, new View.OnClickListener() { public void onClick(View v) { download("TipsyBirdDevTools.apk", "TipsyBirdDevTools.apk"); } }), wa());
    }

    void fetchDevReleases() {
        ui(new Runnable() { public void run() { TextView t = tvRel; if (t != null) t.setText("⏳ جارٍ التحميل…"); } });
        bg(new Runnable() { public void run() {
            try {
                JSONArray arr = new JSONObject(httpGet(API + "/releases?per_page=30")).getJSONArray("root");
                final StringBuilder sb = new StringBuilder();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject rel = arr.getJSONObject(i);
                    String tag = rel.optString("tag_name");
                    if (!tag.startsWith("dev-")) continue;
                    JSONArray as = rel.getJSONArray("assets");
                    int size = 0;
                    for (int j = 0; j < as.length(); j++) size += as.getJSONObject(j).optInt("size", 0);
                    sb.append("🧰 ").append(tag).append("  (").append(size / 1024).append(" KB)\n").append("    ").append(rel.optString("published_at")).append("\n\n");
                }
                if (sb.length() == 0) sb.append("لا إصدارات بعد");
                ui(new Runnable() { public void run() { TextView t = tvRel; if (t != null) t.setText(sb.toString()); } });
            } catch (final Exception e) {
                ui(new Runnable() { public void run() { TextView t = tvRel; if (t != null) t.setText("⚠ تعذر الاتصال"); } });
            }
        }});
    }

    // ---------- 3. Update checker ----------
    void pageUpdate() {
        LinearLayout c = panel();
        c.addView(subtitle("قارن نسختك المثبّتة مع آخر نسخة بالمستودع، ونزّل التحدtث بضغطة واحدة"));
        c.addView(spacer(8));
        tvUpd = txt("اضغط «فحص الآن»…", SUB, 13, false);
        c.addView(tvUpd);
        c.addView(spacer(12));
        cur = root;
        LinearLayout r1 = row();
        r1.addView(btn("فحص الآن 🔍", TEAL, new View.OnClickListener() { public void onClick(View v) { checkUpdate(); } }), wa());
        r1.addView(btn("فتح اللعبة 🎮", TEAL, new View.OnClickListener() { public void onClick(View v) { openGame(); } }), wa());
        cur = root;
        LinearLayout r2 = row();
        r2.addView(btn("تحميل اللعبة ⬇", ACCENT, new View.OnClickListener() { public void onClick(View v) { download("TipsyBird3D.apk", "TipsyBird3D.apk"); } }), wa());
        r2.addView(btn("تحميل الأداة ⬇", ACCENT, new View.OnClickListener() { public void onClick(View v) { download("TipsyBirdDevTools.apk", "TipsyBirdDevTools.apk"); } }), wa());
        checkUpdate();
    }

    void checkUpdate() {
        ui(new Runnable() { public void run() { TextView t = tvUpd; if (t != null) t.setText("⏳ جارٍ الفحص…"); } });
        bg(new Runnable() { public void run() {
            final StringBuilder sb = new StringBuilder();
            int inst = -1, latestN = -1, devN = -1;
            try { PackageInfo pi = getPackageManager().getPackageInfo(PKG, 0);
                inst = pi.versionCode;
                sb.append("🎮 اللعبة: مثبّتة بناء #").append(inst).append('\n'); }
            catch (Exception e) { sb.append("🎮 اللعبة: غير مثبّتة على هذا الجهاز\n"); }
            try { PackageInfo me = getPackageManager().getPackageInfo(getPackageName(), 0);
                devN = me.versionCode;
                sb.append("🧰 الأداة: مثبّتة dev-").append(devN).append('\n'); }
            catch (Exception e) {}
            try {
                String tag = new JSONObject(httpGet(API + "/releases/latest")).optString("tag_name");
                if (tag.startsWith("build-")) { try { latestN = Integer.parseInt(tag.substring(6)); } catch (Exception e) {} }
                sb.append("🏷️ آخر إصدار للعبة: ").append(tag).append('\n');
            } catch (Exception e) {}
            try {
                JSONArray arr = new JSONObject(httpGet(API + "/releases?per_page=10")).getJSONArray("root");
                for (int i = 0; i < arr.length(); i++) {
                    String tag = arr.getJSONObject(i).optString("tag_name");
                    if (tag.startsWith("dev-")) { try { devN = Integer.parseInt(tag.substring(4)); } catch (Exception e) {} break; }
                }
            } catch (Exception e) {}
            if (devN > 0) sb.append("🧰 آخر بناء للأداة: dev-").append(devN).append('\n');
            if (inst > 0 && latestN > inst) sb.append("\n⬆️ يوجد تحديث جديد للعبة! اضغط «تحميل اللعبة»\n");
            else if (inst > 0 && latestN == inst) sb.append("\n✅ أنت على أحدث نسخة من اللعبة\n");
            if (devN > 0) sb.append("\nملاحظة: إن لم تكن هذه الأداة dev-").append(devN).append(" فاضغط «تحميل الأداة» وثبّت الإصدار الأحدث.");
            ui(new Runnable() { public void run() { TextView t = tvUpd; if (t != null) t.setText(sb.toString()); } });
        }});
    }

    void openGame() {
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(PKG);
            if (i == null) { toast("اللعبة غير مثبّتة"); return; }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) { toast("تعذر الفتح: " + e.getMessage()); }
    }

    // ---------- 4. Team channel (PR #1 + PAT) ----------
    void pageChannel() {
        LinearLayout c = panel();
        c.addView(subtitle("آخر رسائل PR #1 — قناة التواصل الرسمية بيننا"));
        c.addView(spacer(6));
        tvFeed = txt("⏳ جارٍ التحميل…", SUB, 13, false);
        c.addView(tvFeed);
        LinearLayout f = panel();
        f.addView(subtitle("اكتب ردًا وانشره مباشرة (يحتاج رمز PAT بالأسفل)"));
        f.addView(spacer(6));
        etPost = edit(true, "اكتب رسالتك هنا…");
        f.addView(etPost, MATCH, WRAP);
        f.addView(spacer(8));
        cur = root;
        LinearLayout r1 = row();
        r1.addView(btn("إرسال ✈", TEAL, new View.OnClickListener() { public void onClick(View v) { postComment(); } }), wa());
        r1.addView(btn("افتح PR #1 🌐", ACCENT, new View.OnClickListener() { public void onClick(View v) { browse("https://github.com/" + REPO + "/pull/1"); } }), wa());
        LinearLayout p = panel();
        p.addView(subtitle("رمز الوصول الشخصي (PAT) — يُحفظ على جهازك فقط"));
        p.addView(spacer(6));
        etTok = edit(false, "ghp_…");
        p.addView(etTok, MATCH, WRAP);
        p.addView(spacer(8));
        cur = root;
        LinearLayout r2 = row();
        r2.addView(btn("حفظ الرمز 💾", TEAL, new View.OnClickListener() { public void onClick(View v) { savePat(etTok.getText().toString()); } }), wa());
        r2.addView(btn("مسح الرمز 🗑", ACCENT_D, new View.OnClickListener() { public void onClick(View v) { etTok.setText(""); savePat(""); } }), wa());
        LinearLayout h = panel();
        h.addView(subtitle("كيف أحصل على الرمز؟\nGitHub → الإعدادات → Developer settings → Personal access tokens (classic) → Generate new token → فعّل صلاحية repo → انسخه\n\nبما أن token صالح يمكنك الرد على PR #1 وتشغيل بناءات اللعبة والأداة من هذا التطبيق مباشرة."));
        fetchFeed();
    }

    void fetchFeed() {
        ui(new Runnable() { public void run() { TextView t = tvFeed; if (t != null) t.setText("⏳ جارٍ التحميل…"); } });
        bg(new Runnable() { public void run() {
            try {
                JSONArray arr = new JSONObject(httpGet(API + "/issues/1/comments?per_page=5")).getJSONArray("root");
                final StringBuilder sb = new StringBuilder();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject cm = arr.getJSONObject(i);
                    String body = cm.optString("body");
                    if (body.length() > 140) body = body.substring(0, 140) + "…";
                    sb.append("👤 ").append(cm.optJSONObject("user").optString("login")).append(" — ").append(cm.optString("created_at")).append('\n');
                    sb.append(body.replace('\n', ' ')).append("\n\n");
                }
                if (sb.length() == 0) sb.append("لا تعليقات بعد");
                ui(new Runnable() { public void run() { TextView t = tvFeed; if (t != null) t.setText(sb.toString()); } });
            } catch (final Exception e) {
                ui(new Runnable() { public void run() { TextView t = tvFeed; if (t != null) t.setText("⚠ تعذر الاتصال"); } });
            }
        }});
    }

    void postComment() {
        final String text = etPost.getText().toString().trim();
        if (text.length() == 0) { toast("اكتب رسالة أولًا"); return; }
        final String tok = prefs.getString("pat", "");
        if (TextUtils.isEmpty(tok)) { toast("أضف رمز PAT أولًا — انظر الأسفل"); return; }
        toast("⏳ جارٍ النشر…");
        bg(new Runnable() { public void run() {
            try {
                httpPost(API + "/issues/1/comments", new JSONObject().put("body", text).toString(), tok);
                ui(new Runnable() { public void run() {
                    Toast.makeText(MainActivity.this, "تم النشر ✔", Toast.LENGTH_SHORT).show();
                    etPost.setText("");
                    fetchFeed();
                }});
            } catch (final Exception e) {
                ui(new Runnable() { public void run() { Toast.makeText(MainActivity.this, "فشل النشر: " + e.getMessage(), Toast.LENGTH_SHORT).show(); } });
            }
        }});
    }

    // ---------- 5. Publish kit (old features preserved) ----------
    void pagePublish() {
        LinearLayout a = panel();
        a.addView(subtitle("الوصف العربي — انسخه إلى خانة الوصف في متجر Uptodown"));
        a.addView(spacer(8));
        cur = root;
        LinearLayout rA = row();
        rA.addView(btn("📋 نسخ AR", TEAL, new View.OnClickListener() { public void onClick(View v) { copy(DESC_AR); } }), wa());
        rA.addView(btn("📋 نسخ EN", TEAL, new View.OnClickListener() { public void onClick(View v) { copy(DESC_EN); } }), wa());
        LinearLayout d = panel();
        d.addView(subtitle("روابط مفيدة"));
        d.addView(spacer(8));
        cur = root;
        LinearLayout rA2 = row();
        rA2.addView(btn("دليل Uptodown", ACCENT, new View.OnClickListener() { public void onClick(View v) { browse("https://github.com/" + REPO + "/blob/" + BRANCH + "/docs/UPTODOWN.md"); } }), wa());
        rA2.addView(btn("خصوصية", ACCENT, new View.OnClickListener() { public void onClick(View v) { browse("https://worldnewsportal.github.io/Tipsy-Bird-3D-Sky-Adventure/privacy.html"); } }), wa());
        cur = root;
        LinearLayout rA3 = row();
        rA3.addView(btn("الإصدارات", ACCENT, new View.OnClickListener() { public void onClick(View v) { browse("https://github.com/" + REPO + "/releases"); } }), wa());
        rA3.addView(btn("PR #1", ACCENT, new View.OnClickListener() { public void onClick(View v) { browse("https://github.com/" + REPO + "/pull/1"); } }), wa());
        LinearLayout n = panel();
        n.addView(subtitle("صور المتجر جاهزة: icons/store/ — أيقونة 512 + 8 لقطات 1080×1920 + بانر 1024×500\nوقائمة النشر الكاملة في docs/UPTODOWN.md"));
    }

    // ---------- 6. Project card (old feature preserved) ----------
    void pageProject() {
        LinearLayout c = panel();
        kv(c, "الحزمة", PKG);
        kv(c, "Unity Game ID", GAME_ID);
        kv(c, "مواضع الإعلانات", "Rewarded_Android / Interstitial_Android / Banner_Android");
        kv(c, "الفرع", BRANCH);
        kv(c, "التوقيع", "tools/release.p12 — alias tipsy");
        kv(c, "أندرويد", "min 23 (6.0) — target 34");
        kv(c, "ورشات البناء", "android.yml (اللعبة) + devtool.yml (هذه الأداة)");
        kv(c, "الفحوص", "tools/check_i18n.py + محاكيات JSDOM");
    }

    void kv(LinearLayout c, String k, String v) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(5), 0, dp(5));
        TextView a = txt(k, GOLD, 13, true);
        a.setMinWidth(dp(105));
        r.addView(a, new LinearLayout.LayoutParams(WRAP, WRAP));
        TextView b = txt(v, TXT, 13, false);
        r.addView(b, new LinearLayout.LayoutParams(0, WRAP, 1f));
        c.addView(r, MATCH, WRAP);
    }

    // ---------- 7/8. Changelog + checklist (old features preserved) ----------
    void pageLog() {
        LinearLayout c = panel();
        c.addView(body(CHANGELOG));
    }

    void pageCheck() {
        LinearLayout c = panel();
        c.addView(body(CHECKLIST));
        LinearLayout d = panel();
        d.addView(subtitle("البنود المتبقية عليك يدويًا فقط — الباقي مكتمل آليًا في CI"));
    }

    // ---------- 9. Command console (Termux-style, simplified) ----------
    void pageTerm() {
        LinearLayout c = panel();
        GradientDrawable tg = new GradientDrawable();
        tg.setColor(TERM_BG); tg.setCornerRadius(dp(10));
        tvTerm = txt("Tipsy shell v2 — اصنع كل شيء من هنا.\nاكتب help ثم اضغط تشغيل.\n\n", TERM_FG, 12, false);
        tvTerm.setTypeface(Typeface.MONOSPACE);
        tvTerm.setBackground(tg);
        int tp = dp(12);
        tvTerm.setPadding(tp, tp, tp, tp);
        tvTerm.setMinHeight(dp(190));
        c.addView(tvTerm, MATCH, WRAP);
        c.addView(spacer(10));
        etCmd = edit(false, "أمر… مثال: help");
        etCmd.setSingleLine(true);
        c.addView(etCmd, MATCH, WRAP);
        c.addView(spacer(10));
        cur = root;
        LinearLayout rA4 = row();
        rA4.addView(btn("تشغيل ▶", ACCENT, new View.OnClickListener() { public void onClick(View v) { runCmd(); } }), wa());
        rA4.addView(btn("help", TEAL, new View.OnClickListener() { public void onClick(View v) { etCmd.setText("help"); runCmd(); } }), wa());
        rA4.addView(btn("clear", TEAL, new View.OnClickListener() { public void onClick(View v) { runCmd("clear"); } }), wa());
        cur = root;
        LinearLayout rB = row();
        rB.addView(btn("builds", TEAL_D, new View.OnClickListener() { public void onClick(View v) { runCmd("builds"); } }), wa());
        rB.addView(btn("latest", TEAL_D, new View.OnClickListener() { public void onClick(View v) { runCmd("latest"); } }), wa());
        rB.addView(btn("releases", TEAL_D, new View.OnClickListener() { public void onClick(View v) { runCmd("releases"); } }), wa());
        LinearLayout h2 = panel();
        h2.addView(subtitle("الأوامر هنا = إختصار لأوامر Termux (git/curl/gh) — بلا تثبيت ولا كتابة. للقوة الكاملة: dispatch game / dispatch tool بالرمز PAT."));
    }

    void runCmd() {
        String s = etCmd.getText().toString().trim();
        if (s.length() > 0) { etCmd.setText(""); runCmd(s); }
    }

    void runCmd(String cmd) {
        if (cmd == null) return;
        String c = cmd.trim();
        if (c.length() == 0) return;
        out("tipsy@devtool:~$ " + c + "\n");
        if (c.equals("help")) {
            out("الأوامر:\n  help — هذه القائمة\n  clear — تفريغ الشاشة\n  whoami — هوية المشروع\n  status — حالة سريعة\n  builds — آخر 5 بناءات CI\n  latest — آخر إصدار للعبة\n  releases — إصدارات الأداة\n  dispatch game — تشغيل بناء اللعبة (PAT)\n  dispatch tool — تشغيل بناء الأداة (PAT)\n");
        } else if (c.equals("clear")) {
            tvTerm.setText("");
        } else if (c.equals("whoami")) {
            out("developer\nrepo: " + REPO + "\nbranch: " + BRANCH + "\n");
        } else if (c.equals("status")) {
            bg(new Runnable() { public void run() {
                try {
                    JSONArray a = new JSONObject(httpGet(API + "/actions/runs?per_page=1")).getJSONArray("workflow_runs");
                    if (a.length() > 0) { JSONObject r = a.getJSONObject(0);
                        out("آخر بناء: #" + r.optInt("run_number") + " " + r.optString("status") + "/" + r.optString("conclusion") + "\n"); }
                    else out("لا بناءات\n");
                } catch (Exception e) { out("تعذر الاتصال\n"); }
            }});
        } else if (c.equals("builds")) {
            bg(new Runnable() { public void run() {
                try {
                    JSONArray a = new JSONObject(httpGet(API + "/actions/runs?per_page=5")).getJSONArray("workflow_runs");
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < a.length(); i++) { JSONObject r = a.getJSONObject(i);
                        sb.append('#').append(r.optInt("run_number")).append(' ').append(r.optString("status")).append('/').append(r.optString("conclusion")).append('\n'); }
                    out(sb.toString());
                } catch (Exception e) { out("تعذر الاتصال\n"); }
            }});
        } else if (c.equals("latest")) {
            bg(new Runnable() { public void run() {
                try {
                    JSONObject o = new JSONObject(httpGet(API + "/releases/latest"));
                    out(o.optString("tag_name") + " — " + o.optString("published_at") + "\n" + (o.optString("body").replace('\n', ' ').length() > 100 ? o.optString("body").substring(0, 100) + "…\n" : ""));
                } catch (Exception e) { out("تعذر الاتصال\n"); }
            }});
        } else if (c.equals("releases")) {
            bg(new Runnable() { public void run() {
                try {
                    JSONArray a = new JSONObject(httpGet(API + "/releases?per_page=8")).getJSONArray("root");
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < a.length(); i++) sb.append(a.getJSONObject(i).optString("tag_name")).append('\n');
                    out(sb.toString());
                } catch (Exception e) { out("تعذر الاتصال\n"); }
            }});
        } else if (c.equals("dispatch game")) {
            dispatch("android.yml");
        } else if (c.equals("dispatch tool")) {
            dispatch("devtool.yml");
        } else {
            out("command not found: " + c + "\nاكتب help\n");
        }
    }

    void out(final String s) {
        ui(new Runnable() { public void run() {
            TextView t = tvTerm;
            if (t == null) return;
            t.setText(t.getText().toString() + s);
        }});
    }

    void dispatch(final String wf) {
        final String tok = prefs.getString("pat", "");
        if (TextUtils.isEmpty(tok)) { out("✖ لا يوجد PAT — أضفه في صفحة «قناة العمل»\n"); return; }
        out("⏳ تشغيل " + wf + " …\n");
        bg(new Runnable() { public void run() {
            try {
                String payload = "{\"ref\":\"" + BRANCH + "\"}";
                httpPost(API + "/actions/workflows/" + wf + "/dispatches", payload, tok);
                out("✔ تم استقبال طلب التشغيل — افتح صفحة «بناءات اللعبة» بعد دقيقة\n");
            } catch (final Exception e) { out("✖ فشل: " + e.getMessage() + "\n"); }
        }});
    }

    // ---------- 10. Device info ----------
    void pageDevice() {
        LinearLayout c = panel();
        tvDev = txt("⏳ جارٍ القياس…", SUB, 13, false);
        c.addView(tvDev);
        bg(new Runnable() { public void run() {
            final StringBuilder sb = new StringBuilder();
            sb.append("الطراز: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
            sb.append("النظام: Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
            sb.append("أنوية المعالج: ").append(Runtime.getRuntime().availableProcessors()).append('\n');
            try {
                ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(mi);
                sb.append("الرام: ").append(mi.totalMem / 1048576).append(" ميغابايت (متاح ").append(mi.availMem / 1048576).append(")\n");
            } catch (Exception e) { sb.append("الرام: ؟\n"); }
            try {
                StatFs st = new StatFs(Environment.getDataDirectory().getPath());
                sb.append("التخزين: ").append(st.getTotalBytes() / 1073741824).append(" جيغابايت (متاح ").append(st.getAvailableBytes() / 1073741824).append(")\n");
            } catch (Exception e) { sb.append("التخزين: ؟\n"); }
            try {
                BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
                sb.append("البطارية: ").append(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)).append("%\n");
            } catch (Exception e) { sb.append("البطارية: ؟\n"); }
            try {
                DisplayMetrics dm = getResources().getDisplayMetrics();
                sb.append("الشاشة: ").append(dm.widthPixels).append('×').append(dm.heightPixels).append(" (").append(dm.densityDpi).append(" dpi)");
            } catch (Exception e) {}
            ui(new Runnable() { public void run() { TextView t = tvDev; if (t != null) t.setText(sb.toString()); } });
        }});
    }

    // ---------- 11. Beginner guide ----------
    void pageGuide() {
        guideCard("1. ثبّت التطبيق واللعبة", "من «فاحص التحديث» اضغط تحميل — يُنزّل APK إلى مجلد التنزيل. افتحه ووافق على التثبيت من مصادر غير معروفة (مرة واحدة فقط).");
        guideCard("2. راقب البناءات تلقائيًا", "كل تعديل نرفعه يبني تلقائيًا على GitHub Actions. صفحة «بناءات اللعبة» تعرض الحالة، وبعد ✅ اضغط «تحميل أحدث APK».");
        guideCard("3. شغّل بناءً بنفسك", "من «وحدة الأوامر»: اكتب dispatch game (للعبة) أو dispatch tool (لهذه الأداة). يحتاج رمز PAT — الخطوة 5.");
        guideCard("4. حافظ على أحدث نسخة", "«فاحص التحديث» يقارن نسختك المثبّتة مع آخر إصدار بناء-N وينبّهك إن كان هناك أحدث.");
        guideCard("5. احصل على رمز PAT", "GitHub → Settings → Developer settings → Personal access tokens (classic) → Generate new token → فعّل صلاحية repo → انسخه والصقه في «قناة العمل».\nالرمز يُحفظ في هذا التطبيق فقط — لا ترسله لأي أحد.");
        guideCard("6. علاقته بـ Termux", "كل ما يفعله Termux (git clone / curl / gh) — الأداة هنا تنفّذه بأزرار وصفحات. إن أردت قوة أكبر لاحقًا: ثبّت Termux ونفّذ نفس الأوامر التي تعلّمتها هنا للتو.");
        LinearLayout c = panel();
        c.addView(subtitle("تكامل كامل مع القناة: docs/DEVTOOL.md في المستودع يوثّق كل صفحة ومبدأ."));
    }

    void guideCard(String title, String body) {
        LinearLayout c = panel();
        c.addView(subtitle(title));
        c.addView(spacer(4));
        c.addView(txt(body, TXT, 13, false));
        cur = root;
    }

    @Override public void onBackPressed() {
        if (curPage != P_HOME) show(P_HOME);
        else super.onBackPressed();
    }

    // ---------- UI helpers ----------
    TextView txt(String s, int color, float sz, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sz);
        t.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    TextView subtitle(String s) { return txt(s, SUB, 13, false); }
    TextView body(String s) { return txt(s, TXT, 14, false); }
    TextView header(String s) { return txt(s, ACCENT, 16, true); }

    void section(String s) {
        cur.addView(header(s), mmp(dp(4), dp(10), dp(4), dp(2)));
    }

    LinearLayout.LayoutParams lpm(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    LinearLayout.LayoutParams mmp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(l, t, r, b);
        return lp;
    }

    LinearLayout.LayoutParams wa() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        return lp;
    }

    LinearLayout panel() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD); g.setCornerRadius(dp(14));
        c.setBackground(g);
        int p = dp(14);
        c.setPadding(p, p, p, p);
        cur.addView(c, mmp(0, dp(8), 0, dp(8)));
        cur = c;
        return c;
    }

    LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        cur.addView(r, mmp(0, dp(4), dp(-4), dp(4)));
        return r;
    }

    View spacer(int h) {
        View v = new View(this);
        v.setMinimumHeight(dp(h));
        return v;
    }

    Button btn(String s, int color, View.OnClickListener o) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color); g.setCornerRadius(dp(12));
        b.setBackground(g);
        b.setPadding(dp(8), dp(8), dp(8), dp(8));
        b.setOnClickListener(o);
        return b;
    }

    EditText edit(boolean multi, String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(DIM);
        e.setTextColor(TXT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF191028); g.setCornerRadius(dp(10));
        g.setStroke(dp(1), 0xFF5A4880);
        e.setBackground(g);
        int p = dp(10);
        e.setPadding(p, p, p, p);
        if (multi) { e.setMinLines(3); e.setGravity(Gravity.TOP); }
        return e;
    }

    void copy(String text) {
        clip.setPrimaryClip(ClipData.newPlainText("tipsy", text));
        toast("تم النسخ ✔");
    }

    void browse(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { toast("تعذر الفتح: " + e.getMessage()); }
    }

    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    int dp(int d) {
        return (int) (d * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ---------- Downloader ----------
    void download(final String asset, final String save) {
        toast("⏳ جارٍ إيجاد " + save + "…");
        bg(new Runnable() { public void run() {
            try {
                JSONArray rels = new JSONObject(httpGet(API + "/releases?per_page=30")).getJSONArray("root");
                String url = null;
                for (int i = 0; i < rels.length() && url == null; i++) {
                    JSONArray as = rels.getJSONObject(i).getJSONArray("assets");
                    for (int j = 0; j < as.length(); j++) {
                        JSONObject a = as.getJSONObject(j);
                        if (asset.equals(a.optString("name"))) { url = a.optString("browser_download_url"); break; }
                    }
                }
                final String fu = url;
                if (fu == null) { ui(new Runnable() { public void run() { toast("لم أجد " + asset); } }); return; }
                DownloadManager.Request rq = new DownloadManager.Request(Uri.parse(fu));
                rq.setTitle(save);
                rq.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                rq.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, save);
                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                dm.enqueue(rq);
                ui(new Runnable() { public void run() { toast("⬇ بدأ التنزيل — ستجده في مجلد التنزيلات"); } });
            } catch (final Exception e) {
                ui(new Runnable() { public void run() { toast("فشل التنزيل: " + e.getMessage()); } });
            }
        }});
    }

    // ---------- Network ----------
    String httpGet(String s) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(s).openConnection();
        c.setRequestProperty("User-Agent", "tipsy-devtool");
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        int code = c.getResponseCode();
        if (code >= 400) throw new RuntimeException("HTTP " + code);
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        return sb.toString();
    }

    void httpPost(String s, String bd, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(s).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("User-Agent", "tipsy-devtool");
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setDoOutput(true);
        c.getOutputStream().write(bd.getBytes("UTF-8"));
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new RuntimeException("HTTP " + code);
    }

    // ---------- PAT storage ----------
    void savePat(String t) {
        if (t == null) t = "";
        prefs.edit().putString("pat", t.trim()).apply();
        toast(t.trim().length() == 0 ? "تم مسح الرمز" : "تم حفظ الرمز على جهازك ✔");
    }
}
