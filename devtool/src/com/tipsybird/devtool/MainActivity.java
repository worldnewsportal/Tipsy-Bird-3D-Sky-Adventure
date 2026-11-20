package com.tipsybird.devtool;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
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
import java.util.ArrayList;
import java.util.List;

/** Tipsy DevTools — internal companion app for the Tipsy Bird 3D project.
 *  Standalone native APK (no Capacitor/WebView); built via aapt2+javac+d8 in CI. */
public class MainActivity extends Activity {

    static final String REPO = "worldnewsportal/Tipsy-Bird-3D-Sky-Adventure";
    static final String GAME_ID = "800443610";
    static final String PKG = "com.tipsybird.game";

    static final int BG = 0xFF241A3A;
    static final int CARD = 0xFF2F2250;
    static final int ACCENT = 0xFFFF7A2F;
    static final int ACCENT_D = 0xFFC4480F;
    static final int TEAL = 0xFF27C1B0;
    static final int TEAL_D = 0xFF13867A;
    static final int TXT = 0xFFFFFFFF;
    static final int SUB = 0xFFCBC3E3;

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

    LinearLayout root;
    ClipboardManager clip;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        clip = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        setContentView(content());
        fetchBuilds();
        fetchDevReleases();
    }

    View content() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(18), dp(26), dp(18), dp(40));
        sv.addView(outer);

        TextView title = txt("🛠️ Tipsy DevTools", TXT, 26, true);
        title.setGravity(Gravity.CENTER);
        TextView sub = txt("وحدة تحكم المشروع — منتج داخلي لنا فقط", SUB, 13, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(2), 0, dp(8));
        outer.addView(title);
        outer.addView(sub);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        outer.addView(root);

        // ---- CI builds ----
        panel();
        header("🚀 بناءات اللعبة (CI)");
        setText(R_id_builds, "⏳ جارٍ التحميل…", SUB, 12, false);
        // ---- devtool releases ----
        panel();
        header("🧪 إصدارات هذا التطبيق (dev-N)");
        setText(R_id_releases, "⏳ جارٍ التحميل…", SUB, 12, false);
        // ---- project card ----
        panel();
        header("📦 بطاقة المشروع");
        setText(R_id_info,
            "الحزمة: " + PKG +
            "\nGame ID: " + GAME_ID +
            "\nPlacements: Rewarded_Android · Interstitial_Android · Banner_Android" +
            "\nالتوقيع: tools/release.p12 (alias: tipsy) — ثابت بين البناءات" +
            "\nFRANCHISE: قناة العمل PR #1 على الفرع vibe/fully-offline-c73d38",
            TXT, 13, false);
        // ---- publish kit ----
        panel();
        header("🖼️ حزمة النشر");
        setText(R_id_publish,
            "10 صور جاهزة في icons/store: 8 لقطات 1080×1920 + featured 1024×500 + أيقونات 512/256/192. والنصوص العالمية جاهزة للنسخ بالأزرار أدناه.",
            TXT, 13, false);
        row(TEAL, TEAL_D, "📋 انسخ الوصف العربي للنشر", new Runnable() {
            public void run() { copy("Uptodown AR", DESC_AR); }});
        row(TEAL, TEAL_D, "📋 انسخ الوصف الإنجليزي", new Runnable() {
            public void run() { copy("Uptodown EN", DESC_EN); }});
        row(ACCENT, ACCENT_D, "📄 دليل النشر UPTODOWN.md", browse("https://github.com/" + REPO + "/blob/main/docs/UPTODOWN.md"));
        row(ACCENT, ACCENT_D, "🔐 صفحة الخصوصية والأحكام", browse("https://worldnewsportal.github.io/Tipsy-Bird-3D-Sky-Adventure/#privacy"));
        row(ACCENT, ACCENT_D, "⬇️ صفحة إصدارات اللعبة (Releases)", browse("https://github.com/" + REPO + "/releases"));
        row(ACCENT, ACCENT_D, "🩺 سجل العمل (PR #1)", browse("https://github.com/" + REPO + "/pull/1"));
        // ---- changelog + checklist ----
        panel();
        header("🎓 سجل المراحل");
        setText(R_id_log, CHANGELOG, SUB, 12, false);
        panel();
        header("✅ قائمة تحقق النشر");
        setText(R_id_check, CHECKLIST, SUB, 13, false);

        TextView foot = txt("Tipsy Bird 3D — أدوات داخلية · لا تُنشر للمستعمين", SUB, 11, false);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(18), 0, 0);
        outer.addView(foot);
        return sv;
    }

    // ---- view helpers ----
    static final int R_id_builds = 201, R_id_releases = 202, R_id_info = 203, R_id_publish = 204, R_id_log = 205, R_id_check = 206;

    TextView txt(String s, int color, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    void header(String s) {
        TextView h = txt(s, ACCENT, 17, true);
        h.setPadding(0, 0, 0, dp(6));
        add(h);
    }

    void setText(int id, String s, int color, int sp, boolean bold) {
        TextView t = txt(s, color, sp, bold);
        t.setLineSpacing(dp(3), 1f);
        t.setId(id);
        add(t);
    }

    void panel() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(18));
        p.setBackground(g);
        p.setElevation(dp(3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        root.addView(p, lp);
        current = p;
    }

    LinearLayout current;

    void add(View v) {
        current.addView(v);
    }

    void row(int col, int cold, String label, final Runnable action) {
        Button bl = new Button(this);
        bl.setText(label);
        bl.setAllCaps(false);
        bl.setTextColor(TXT);
        bl.setTextSize(15);
        bl.setTypeface(Typeface.DEFAULT_BOLD);
        bl.setPadding(dp(16), dp(10), dp(16), dp(10));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        g.setColor(col);
        bl.setBackground(g);
        bl.setElevation(dp(2));
        bl.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (action != null) action.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        current.addView(bl, lp);
    }

    Runnable browse(final String url) {
        return new Runnable() { public void run() {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignore) {}
        }};
    }

    void copy(String label, String text) {
        clip.setPrimaryClip(new ClipData(label, new String[]{"text/plain"}, new ClipData.Item(text)));
        Toast.makeText(this, "تم النسخ ✓", Toast.LENGTH_SHORT).show();
    }

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ---- networking ----
    static String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "tipsy-devtool");
        c.setRequestProperty("Accept", "application/vnd.github+json");
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line);
        r.close();
        return sb.toString();
    }

    void fetchBuilds() {
        final Activity me = this;
        Thread t = new Thread(new Runnable() { public void run() {
            String out;
            try {
                JSONObject o = new JSONObject(httpGet(
                    "https://api.github.com/repos/" + REPO + "/actions/runs?per_page=6"));
                JSONArray arr = o.getJSONArray("workflow_runs");
                List<String> lines = new ArrayList<String>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject run = arr.getJSONObject(i);
                    int num = run.getInt("run_number");
                    String status = run.optString("status", "?");
                    String conc = run.optString("conclusion", "pending");
                    String branch = run.optString("head_branch", "?");
                    String mark = "success".equals(conc) ? "✅ " : ("failure".equals(conc) ? "❌ " : "⏳ ");
                    lines.add(mark + "بناء #" + num + "  (" + branch + ")  " + status + "/" + conc);
                    if ("success".equals(conc))
                        lines.add("    ↳ APK: github.com/" + REPO + "/releases/download/build-" + num + "/TipsyBird3D.apk");
                }
                out = TextUtils.join("\n", lines);
            } catch (Exception e) { out = "⚠ تعذر الاتصال (إنترنت؟): " + e.getMessage(); }
            final String res = out;
            me.runOnUiThread(new Runnable() { public void run() {
                TextView v = (TextView) findViewById(R_id_builds);
                if (v != null) v.setText(res);
            }});
        }});
        t.setDaemon(true);
        t.start();
    }

    void fetchDevReleases() {
        final Activity me = this;
        Thread t = new Thread(new Runnable() { public void run() {
            String out;
            try {
                JSONArray arr = new JSONArray(httpGet(
                    "https://api.github.com/repos/" + REPO + "/releases?per_page=20"));
                List<String> lines = new ArrayList<String>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject rel = arr.getJSONObject(i);
                    String tag = rel.optString("tag_name", "");
                    if (!tag.startsWith("dev-")) continue;
                    lines.add("📦 " + tag + " — " + rel.optString("name", ""));
                    lines.add("    ↳ التحميل: github.com/" + REPO + "/releases/download/" + tag + "/TipsyBirdDevTools.apk");
                    break;
                }
                out = lines.isEmpty()
                    ? "لا إصدارات dev- بعد — سيظهر أول إصدار فرفعنا تطبيق الأدوات."
                    : TextUtils.join("\n", lines);
            } catch (Exception e) { out = "⚠ تعذر الاتصال (إنترنت؟): " + e.getMessage(); }
            final String res = out;
            me.runOnUiThread(new Runnable() { public void run() {
                TextView v = (TextView) findViewById(R_id_releases);
                if (v != null) v.setText(res);
            }});
        }});
        t.setDaemon(true);
        t.start();
    }
}
