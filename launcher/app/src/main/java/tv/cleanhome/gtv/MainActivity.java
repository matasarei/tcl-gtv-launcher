package tv.cleanhome.gtv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Google TV-style home: Your apps, Continue watching (other apps' Watch Next rows) and YouTube Subscriptions,
 * over the photos of a Google Photos shared album. Colours and sizes are measured off the stock launcher.
 */
public class MainActivity extends Activity {

    static final String TV = "content://android.media.tv/";
    static final Set<String> WHITE_ICONS = new HashSet<>(java.util.Arrays.asList(
            "com.android.vending", "com.google.android.videos"));
    static final String READ_TV = "android.permission.READ_TV_LISTINGS";
    static final long WEATHER_EVERY_MS = 15 * 60 * 1000L;
    static final String UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    static final int WHITE = 0xFFE3E3E3, GREY = 0xFFC4C7C5, DIM = 0xFF9AA0A6, DARK_TEXT = 0xFF1B1F23;
    // Pills are white at ~11.5% over whatever is behind them, as measured on the stock bar over dark and bright
    // art; the current tab adds ~10% more on top of its pill (~20% in all).
    static final int PILL = 0x1DFFFFFF, PILL_SELECTED = 0x1AFFFFFF, FOCUS = 0xFFE4F3FF, CIRCLE = 0xFF262B2F;
    static final int PANEL = 0xFF121B21, TILE = 0xFF1C262C, TILE_FOCUS = 0xFFCCE8FF, CARD = 0xFF262B2F;

    final ExecutorService io = Executors.newFixedThreadPool(3);
    final Handler ui = new Handler(Looper.getMainLooper());
    // ponytail: 12 MB of decoded posters, enough for three rows of 20 on a 1 GB TV
    final LruCache<String, Bitmap> images = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    public static final String EXTRA_TAB = "extra_tab";
    public static final String TAB_APPS = "apps";

    PackageManager pm;
    Typeface regular, medium;
    TextView weather, tooltip;
    View selectedTab;
    ImageView tabHome, tabApps, btnInputs, btnScreensaver, btnQuick;
    View topBar, navPill, content, quickPanel, allSettings;
    ImageView photoA, photoB, currentPhoto;
    CenterScroll homeScroll;
    ScrollView appsScroll;
    LinearLayout homeRows, appsGrid;
    LinearLayout activeSection;
    final Runnable weatherTick = this::loadWeather;
    final Runnable photoTick = this::advancePhoto;
    List<String> photoUrls = new ArrayList<>();
    int photoIndex;
    int defaultPhotoIndex = 0;
    boolean resumed, photosDimmed;
    private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            loadPhotos();
        }
    };

    // Arrange mode for Your apps, as Google TV's Reorder: OK picks an app up, Left/Right move it, OK drops it.
    LinearLayout arrangeRow;
    boolean okArmed;
    View held, doneButton;
    TextView arrangeHint;

    // Which section and item had focus, so a refresh on return from an app puts it back.
    int focusSection = -1, focusIndex = -1;
    // Last measured spacer heights: a rebuilt Home starts with them, so Your apps doesn't flash at the top.
    int topSpacer, bottomSpacer;

    static class Item {
        String pkg, title, sub, image, intentUri, chip;
        float progress;
        long engaged;
        Runnable action; // a card that does something in the launcher instead of opening an app
    }

    Plex plex;

    /** Scrolls only when told to: MainActivity centres the focused section itself. */
    public static class CenterScroll extends ScrollView {
        public CenterScroll(Context c, AttributeSet a) { super(c, a); }

        @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) { return 0; }

        // ScrollView scrolls a page on Up/Down when nothing in it lies that way, so Up from Your apps scrolled
        // the rows instead of reaching the tabs. Let the normal focus search move focus; onFocusMoved scrolls.
        @Override public boolean executeKeyEvent(KeyEvent e) { return false; }
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        pm = getPackageManager();
        plex = new Plex(this);
        regular = font("/system/fonts/Google_Sans-400-100_0-0_0.ttf", Typeface.NORMAL);
        medium = font("/system/fonts/Google_Sans-500-100_0-0_0.ttf", Typeface.BOLD);

        tabHome = findViewById(R.id.tab_home);
        tabApps = findViewById(R.id.tab_apps);
        btnInputs = findViewById(R.id.btn_inputs);
        btnScreensaver = findViewById(R.id.btn_screensaver);
        btnQuick = findViewById(R.id.btn_quick);
        weather = findViewById(R.id.weather);
        tooltip = findViewById(R.id.tooltip);
        topBar = findViewById(R.id.top_bar);
        navPill = findViewById(R.id.nav_pill);
        content = findViewById(R.id.content);
        quickPanel = findViewById(R.id.quick_panel);
        allSettings = findViewById(R.id.all_settings);
        photoA = findViewById(R.id.photo_a);
        photoB = findViewById(R.id.photo_b);
        homeScroll = findViewById(R.id.home_scroll);
        appsScroll = findViewById(R.id.apps_scroll);
        homeRows = findViewById(R.id.home_rows);
        appsGrid = findViewById(R.id.apps_grid);

        applyFont(findViewById(R.id.root));
        tooltip.setTypeface(medium);
        navPill.setBackground(round(PILL, dp(22)));
        findViewById(R.id.action_pill).setBackground(round(PILL, dp(22)));
        quickPanel.setBackground(round(PANEL, dp(20)));
        GradientDrawable scrim = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xB30D1013, 0x000D1013, 0x400D1013, 0xE60D1013});
        findViewById(R.id.scrim).setBackground(scrim);

        // Home and Apps switch as focus lands on them, like Google TV.
        tabHome.setOnFocusChangeListener((v, has) -> { if (has) selectTab(tabHome); });
        tabApps.setOnFocusChangeListener((v, has) -> { if (has) selectTab(tabApps); });
        tabHome.setOnClickListener(v -> focusFirstApp());
        tabHome.setNextFocusLeftId(R.id.tab_home);
        tabApps.setNextFocusRightId(R.id.btn_inputs);
        btnInputs.setNextFocusLeftId(R.id.tab_apps);
        btnInputs.setNextFocusRightId(R.id.btn_screensaver);
        btnScreensaver.setNextFocusLeftId(R.id.btn_inputs);
        btnScreensaver.setNextFocusRightId(R.id.btn_quick);
        btnQuick.setNextFocusLeftId(R.id.btn_screensaver);
        btnQuick.setNextFocusRightId(R.id.btn_quick);
        btnInputs.setOnClickListener(v -> openInputs());
        btnScreensaver.setOnClickListener(v -> startScreensaver());
        btnQuick.setOnClickListener(v -> startActivity(new Intent(this, QuickSettingsActivity.class)));
        buildQuickPanel();
        appsScroll.setOnScrollChangeListener((v, x, y, ox, oy) -> hideRowsUnderBar());

        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((old, now) -> onFocusMoved(now));

        if (getIntent() != null && TAB_APPS.equals(getIntent().getStringExtra(EXTRA_TAB))) {
            selectTab(tabApps);
            tabApps.requestFocus();
        } else {
            selectTab(tabHome);
            tabHome.requestFocus();
        }
        if (checkSelfPermission(READ_TV) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{READ_TV}, 1);
        }
        loadPhotos();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(refreshReceiver, new IntentFilter(QuickSettingsActivity.ACTION_REFRESH_ALBUM), Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerRefreshReceiverLegacy();
            }
        } catch (Throwable ignored) {}
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerRefreshReceiverLegacy() {
        registerReceiver(refreshReceiver, new IntentFilter(QuickSettingsActivity.ACTION_REFRESH_ALBUM));
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        refreshHome();
        buildAppsGrid();
        loadWeather();
        ui.removeCallbacks(photoTick);
        ui.postDelayed(photoTick, photoDelay());
    }

    @Override protected void onPause() {
        super.onPause();
        exitArrange();
        resumed = false;
        ui.removeCallbacks(weatherTick);
        ui.removeCallbacks(photoTick);
        rememberFocus();
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        refreshHome();
    }

    /** Home pressed while already home: back to the top, Your apps along the bottom, focus on Home. */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        exitArrange();
        showQuick(false);
        if (intent != null && TAB_APPS.equals(intent.getStringExtra(EXTRA_TAB))) {
            selectTab(tabApps);
            tabApps.requestFocus();
            return;
        }
        selectTab(tabHome);
        focusSection = -1;
        tabHome.requestFocus();
        homeScroll.smoothScrollTo(0, 0);
    }

    @Override public void onBackPressed() {
        if (arrangeRow != null) exitArrange();
        else if (quickPanel.getVisibility() == View.VISIBLE) showQuick(false);
        else if (selectedTab == tabApps || inside(getCurrentFocus(), homeRows)) tabHome.requestFocus();
        // Otherwise stay: this is Home.
    }

    // ---- focus ----

    void onFocusMoved(View now) {
        styleTabs();
        if (arrangeRow != null) updateHint(now);
        boolean action = now == tabHome || now == tabApps || now == btnInputs || now == btnScreensaver || now == btnQuick;
        if (action) showTooltip(now);
        else tooltip.setVisibility(View.INVISIBLE);
        if (quickPanel.getVisibility() != View.VISIBLE) content.setAlpha(now == btnInputs || now == btnScreensaver || now == btnQuick ? 0.35f : 1f);
        if (now == null || selectedTab != tabHome) return;
        if (inside(now, homeRows)) {
            LinearLayout section = sectionOf(now);
            setActiveSection(section);
            // Rows above the focused one slide under the transparent bar: hide them, as Google TV does.
            int at = homeRows.indexOfChild(section);
            for (int i = 0; i < homeRows.getChildCount(); i++) {
                if (homeRows.getChildAt(i).getTag() instanceof List) fade(homeRows.getChildAt(i), i < at ? 0f : 1f);
            }
            if (section != null) centre(section);
            dimPhotos(true);
        } else if (inside(now, topBar)) {
            setActiveSection(null);
            for (int i = 0; i < homeRows.getChildCount(); i++) fade(homeRows.getChildAt(i), 1f);
            homeScroll.smoothScrollTo(0, 0);
            dimPhotos(false);
        }
    }

    /** Slides the section with focus to the middle of the screen. */
    void centre(View section) {
        // The title grows when its section takes focus; wait for that layout before measuring.
        section.post(() -> {
            int target = section.getTop() + section.getHeight() / 2 - homeScroll.getHeight() / 2;
            homeScroll.smoothScrollTo(0, Math.max(0, target));
        });
    }

    LinearLayout sectionOf(View v) {
        for (Object p = v; p instanceof View; p = ((View) p).getParent()) {
            if (((View) p).getParent() == homeRows) return p instanceof LinearLayout ? (LinearLayout) p : null;
        }
        return null;
    }

    /** The focused section gets a large title and shows its captions, like Google TV's rows. */
    @SuppressWarnings("unchecked")
    void setActiveSection(LinearLayout section) {
        if (section == activeSection) return;
        activeSection = section;
        for (int i = 0; i < homeRows.getChildCount(); i++) {
            View child = homeRows.getChildAt(i);
            if (!(child.getTag() instanceof List)) continue;
            boolean on = child == section;
            TextView label = (TextView) ((ViewGroup) child).getChildAt(0);
            label.setTextSize(on ? 26 : 15);
            label.setTextColor(on ? Color.WHITE : GREY);
            for (View caption : (List<View>) child.getTag()) caption.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        }
    }

    // ---- top bar ----

    void selectTab(View tab) {
        selectedTab = tab;
        homeScroll.setVisibility(tab == tabApps ? View.GONE : View.VISIBLE);
        appsScroll.setVisibility(tab == tabApps ? View.VISIBLE : View.GONE);
        dimPhotos(tab == tabApps);
        styleTabs();
    }

    /** Only the focused tab is lit while focus is on the tabs; the current page shows grey once focus leaves them. */
    void styleTabs() {
        boolean onTabs = inside(getCurrentFocus(), navPill);
        for (ImageView t : new ImageView[]{tabHome, tabApps}) {
            boolean focused = t.isFocused(), selected = t == selectedTab && !onTabs;
            t.setBackground(focused ? oval(FOCUS) : selected ? oval(PILL_SELECTED) : null);
            t.setImageTintList(ColorStateList.valueOf(focused ? DARK_TEXT : WHITE));
        }
        for (ImageView b : new ImageView[]{btnInputs, btnScreensaver, btnQuick}) {
            b.setBackground(b.isFocused() ? oval(FOCUS) : null);
            b.setImageTintList(ColorStateList.valueOf(b.isFocused() ? DARK_TEXT : WHITE));
        }
    }

    void showTooltip(View anchor) {
        tooltip.setText(anchor.getContentDescription());
        tooltip.setVisibility(View.INVISIBLE);
        tooltip.post(() -> {
            int[] at = new int[2];
            anchor.getLocationInWindow(at);
            tooltip.setX(at[0] + anchor.getWidth() / 2f - tooltip.getWidth() / 2f);
            tooltip.setY(at[1] + anchor.getHeight() + dp(14));
            if (anchor.isFocused()) tooltip.setVisibility(View.VISIBLE);
        });
    }

    void openInputs() {
        long now = SystemClock.uptimeMillis();
        if (now - GlobalKeyService.lastInputOpenedTime < 1000) {
            return;
        }
        GlobalKeyService.lastInputOpenedTime = now;

        // 1. Native TCL input sources overlay service (same as TV button)
        try {
            Intent serviceIntent = new Intent("com.tcl.insrc.SHOW_WINDOW");
            serviceIntent.setClassName("com.tcl.tv", "com.tcl.sourcemenu.insrc.ShowWindowService");
            ComponentName cn = startService(serviceIntent);
            if (cn != null) return;
        } catch (Exception ignored) {}

        // 2. Standard Android TV input picker fallback
        Intent[] intents = new Intent[]{
                new Intent("com.android.tv.action.VIEW_INPUTS"),
                new Intent("com.tcl.suspension.VIEW_INPUTS"),
                new Intent().setClassName("com.tcl.suspension", "com.tcl.suspension.ui.main.InputActivity"),
                new Intent().setClassName("com.tcl.tvinput", "com.tcl.tvinput.MainActivity")
        };
        for (Intent intent : intents) {
            if (start(intent)) return;
        }
    }

    void openYouTube() {
        String[] youtubePackages = {
                "com.google.android.youtube.tv",
                "com.liskovsoft.videomanager",
                "com.liskovsoft.smarttubetv.beta",
                "com.liskovsoft.smarttubetv"
        };
        for (String pkg : youtubePackages) {
            Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                if (start(intent)) return;
            }
        }
        try {
            getPackageManager().setApplicationEnabledSetting(
                    "com.google.android.youtube.tv",
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    0);
            Intent intent = pm.getLeanbackLaunchIntentForPackage("com.google.android.youtube.tv");
            if (intent == null) intent = pm.getLaunchIntentForPackage("com.google.android.youtube.tv");
            if (intent != null && start(intent)) return;
        } catch (Exception ignored) {}
    }

    void openLiveTv() {
        Intent intent = pm.getLeanbackLaunchIntentForPackage("com.tcl.tv");
        if (intent == null) intent = pm.getLaunchIntentForPackage("com.tcl.tv");
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            if (start(intent)) return;
        }
        String[] tvActivities = {
                "com.tcl.player.TVActivity",
                "com.tcl.tv.TVActivity"
        };
        for (String act : tvActivities) {
            Intent actIntent = new Intent().setClassName("com.tcl.tv", act);
            actIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (start(actIntent)) return;
        }
    }

    void startScreensaver() {
        // The stock dream (Google's dreamx Backdrop on Google TV), started now, as Settings' "Start now" does.
        Intent dream = new Intent(Intent.ACTION_MAIN)
                .setClassName("com.android.systemui", "com.android.systemui.Somnambulator");
        if (!start(dream)) start(new Intent(Settings.ACTION_DREAM_SETTINGS));
    }

    // ---- quick settings ----

    void buildQuickPanel() {
        ImageView gear = findViewById(R.id.all_settings_icon);
        gear.setBackground(oval(TILE));
        gear.setImageTintList(ColorStateList.valueOf(WHITE));
        allSettings.setOnFocusChangeListener((v, has) -> {
            gear.setBackground(oval(has ? TILE_FOCUS : TILE));
            gear.setImageTintList(ColorStateList.valueOf(has ? DARK_TEXT : WHITE));
        });
        allSettings.setOnClickListener(v -> { if (start(new Intent(Settings.ACTION_SETTINGS))) showQuick(false); });

        LinearLayout tiles = findViewById(R.id.quick_tiles);
        Object[][] entries = {
                {"Apps", R.drawable.ic_apps, new Intent[]{
                        new Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.device.apps.AllAppsActivity"),
                        new Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.device.apps.AppsActivity"),
                        new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)}},
                {"Inputs", R.drawable.ic_input, new Intent[]{new Intent("com.android.tv.action.VIEW_INPUTS")}},
                {"Picture", R.drawable.ic_picture, new Intent[]{new Intent(Settings.ACTION_DISPLAY_SETTINGS)}},
                {"Network", R.drawable.ic_ethernet, new Intent[]{
                        new Intent("com.tcl.settings.action.NETWORKSETTINGS"),
                        new Intent(Settings.ACTION_WIRELESS_SETTINGS)}},
                {"Remotes", R.drawable.ic_bluetooth, new Intent[]{
                        new Intent().setClassName("com.android.tv.settings", "com.tcl.android.tv.settings.accessories.AccessoriesActivity"),
                        new Intent("android.settings.BLUETOOTH_SETTINGS")}},
                {"Accessibility", R.drawable.ic_accessibility, new Intent[]{
                        new Intent("android.settings.ACCESSIBILITY_TV_OEM_LINK"),
                        new Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.oemlink.AccessibilitySettingsActivity"),
                        new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)}},
                {"Screensaver", R.drawable.ic_frame, new Intent[]{new Intent(Settings.ACTION_DREAM_SETTINGS)}},
                {"Refresh album", R.drawable.ic_refresh, (Runnable) this::loadPhotos},
        };
        LinearLayout row = null;
        for (int i = 0; i < entries.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                tiles.addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout tile = new LinearLayout(this);
            tile.setGravity(Gravity.CENTER_VERTICAL);
            tile.setPadding(dp(12), 0, dp(6), 0);
            tile.setFocusable(true);
            tile.setClickable(true);
            tile.setBackground(round(TILE, dp(14)));
            ImageView icon = new ImageView(this);
            icon.setImageResource((Integer) entries[i][1]);
            icon.setImageTintList(ColorStateList.valueOf(WHITE));
            tile.addView(icon, new LinearLayout.LayoutParams(dp(18), dp(18)));
            TextView label = caption((String) entries[i][0], 13, WHITE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.leftMargin = dp(8);
            tile.addView(label, lp);
            tile.setOnFocusChangeListener((v, has) -> {
                v.setBackground(round(has ? TILE_FOCUS : TILE, dp(14)));
                icon.setImageTintList(ColorStateList.valueOf(has ? DARK_TEXT : WHITE));
                label.setTextColor(has ? DARK_TEXT : WHITE);
            });
            final Object action = entries[i][2];
            tile.setOnClickListener(v -> {
                if (action instanceof Runnable) { ((Runnable) action).run(); showQuick(false); return; }
                for (Intent in : (Intent[]) action) if (start(in)) { showQuick(false); return; }
            });
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(54), 1);
            tp.setMargins(0, 0, i % 2 == 0 ? dp(6) : 0, dp(6));
            row.addView(tile, tp);
            if (i == entries.length - 1 && i % 2 == 0) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        }
    }

    /** Slides in from the right edge and dims the rest, like Google TV's panel. */
    void showQuick(boolean show) {
        boolean open = quickPanel.getVisibility() == View.VISIBLE;
        if (show == open) return;
        int block = show ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS;
        ((ViewGroup) topBar).setDescendantFocusability(block);
        ((ViewGroup) content).setDescendantFocusability(block);
        float away = quickPanel.getWidth() > 0 ? quickPanel.getWidth() + dp(16) : dp(300);
        quickPanel.animate().cancel();
        if (show) {
            quickPanel.setVisibility(View.VISIBLE);
            quickPanel.setTranslationX(away);
            quickPanel.setAlpha(0f);
            quickPanel.animate().translationX(0).alpha(1f).setDuration(280)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
            fade(content, 0.3f);
            fade(topBar, 0.3f);
            LinearLayout tiles = findViewById(R.id.quick_tiles);
            View firstTile = tiles.getChildCount() > 0 && ((ViewGroup) tiles.getChildAt(0)).getChildCount() > 0
                    ? ((ViewGroup) tiles.getChildAt(0)).getChildAt(0) : allSettings;
            firstTile.requestFocus();
        } else {
            quickPanel.animate().translationX(away).alpha(0f).setDuration(200)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> quickPanel.setVisibility(View.GONE)).start();
            fade(content, 1f);
            fade(topBar, 1f);
            btnQuick.requestFocus();
        }
    }

    static void fade(View v, float to) {
        v.animate().alpha(to).setDuration(250).start();
    }

    // ---- home rows ----

    void refreshHome() {
        rememberFocus();
        io.execute(() -> {
            List<Item> apps = dockApps();
            List<Item> cw = continueWatching();
            List<Item> subs = youtubeRow(getString(R.string.subscriptions_channel));
            List<Item> forYouFeed = youtubeRow("Recommended for you");
            if (forYouFeed.isEmpty()) forYouFeed = youtubeRow("Recommended"); // YouTube often leaves the first one empty
            List<Item> forYou = forYouFeed;
            List<Item> plexRecent = plexRow();
            ui.post(() -> {
                boolean rowsHadFocus = inside(getCurrentFocus(), homeRows);
                activeSection = null;
                homeRows.removeAllViews();
                View top = new View(this);
                homeRows.addView(top, new LinearLayout.LayoutParams(-1, topSpacer));
                LinearLayout appsSection = addSection(getString(R.string.section_your_apps), apps, true);
                if (!cw.isEmpty()) addSection(getString(R.string.section_continue_watching), cw, false);
                if (!plexRecent.isEmpty()) addSection(getString(R.string.section_recently_added_plex), plexRecent, false);
                if (!forYou.isEmpty()) addSection(getString(R.string.section_for_you_youtube), forYou, false);
                if (!subs.isEmpty()) addSection(getString(R.string.section_subscriptions), subs, false);
                View bottom = new View(this);
                homeRows.addView(bottom, new LinearLayout.LayoutParams(-1, bottomSpacer));
                // On arrival only Your apps shows, along the bottom over the photo; the rest waits below.
                applyHomeSpacer(top, bottom, appsSection, rowsHadFocus);
            });
        });
    }

    /** Sizes the top spacer once the Home content has really been laid out, then sets the first scroll.
     *  While another tab is showing, homeScroll is GONE, so a fresh section measures 0 height and the
     *  spacer would fill the whole screen: the Home looks empty after the uninstall dialog returns here. */
    void applyHomeSpacer(View top, View bottom, LinearLayout appsSection, boolean rowsHadFocus) {
        Runnable[] size = new Runnable[1];
        size[0] = () -> {
            if (!top.isAttachedToWindow()) return; // a later refresh replaced these views
            int h = homeScroll.getHeight(), sh = appsSection.getHeight();
            if (h <= 0 || sh <= 0) {
                // Still hidden: wait for the pass that selectTab's VISIBLE causes, without asking
                // for one — a GONE view never measures, so requesting would loop forever.
                onHomeLayout(size[0]);
                return;
            }
            // Your apps ends at the screen's bottom edge, so the next section's title starts just below it.
            top.getLayoutParams().height = topSpacer = Math.max(0, h - sh);
            bottom.getLayoutParams().height = bottomSpacer = h / 2;
            top.requestLayout();
            bottom.requestLayout();
            onHomeLayout(() -> {
                if (!top.isAttachedToWindow()) return;
                if (rowsHadFocus) restoreFocus();
                else if (!inside(getCurrentFocus(), homeRows)) homeScroll.scrollTo(0, 0);
            });
        };
        afterHomeLayout(size[0]);
    }

    /** Runs once after the home scroll view's next layout pass, so heights and positions are real. */
    void afterHomeLayout(Runnable r) {
        onHomeLayout(r);
        homeScroll.requestLayout();
    }

    /** Runs once after a layout pass of the window; a waiting caller re-registers itself. */
    void onHomeLayout(Runnable r) {
        ViewTreeObserver.OnGlobalLayoutListener[] once = new ViewTreeObserver.OnGlobalLayoutListener[1];
        once[0] = () -> {
            homeScroll.getViewTreeObserver().removeOnGlobalLayoutListener(once[0]);
            r.run();
        };
        homeScroll.getViewTreeObserver().addOnGlobalLayoutListener(once[0]);
    }

    LinearLayout addSection(String title, List<Item> items, boolean circles) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setClipChildren(false);
        section.setClipToPadding(false);
        List<View> captions = new ArrayList<>();
        section.setTag(captions);

        TextView label = caption(title, 15, GREY);
        label.setPadding(dp(58), dp(10), 0, dp(4));
        section.addView(label);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false); // arrange mode's hint spills past the first icon
        scroll.setPadding(dp(48), 0, dp(48), 0);
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setPadding(0, dp(8), 0, dp(4));
        row.setClipToPadding(false); // a focused card grows into this padding; clipping here cut its top off
        for (Item it : items) {
            View v = circles ? appCircle(it, captions) : card(it, captions);
            if (circles) v.setOnLongClickListener(x -> { dockMenu(it.pkg); return true; });
            row.addView(v);
        }
        if (circles) {
            row.addView(addAppButton(captions));
            row.addView(allAppsButton(captions));
            row.addView(reorderButton());
        }
        // Up from the first section returns to the Home tab, not whichever tab sits above (Live would leave Home).
        if (homeRows.getChildCount() <= 1) {
            for (int i = 0; i < row.getChildCount(); i++) row.getChildAt(i).setNextFocusUpId(R.id.tab_home);
        }
        scroll.addView(row);
        section.addView(scroll);
        for (View c : captions) c.setVisibility(View.INVISIBLE);
        homeRows.addView(section);
        return section;
    }

    List<Item> dockApps() {
        List<Item> out = new ArrayList<>();
        for (String pkg : dock()) {
            if (launchIntent(pkg) == null) continue;
            Item it = new Item();
            it.pkg = pkg;
            it.title = label(pkg);
            out.add(it);
        }
        return out;
    }

    /** Other apps' Watch Next rows: only those they mark searchable are visible without ACCESS_ALL_EPG_DATA. */
    List<Item> continueWatching() {
        List<Item> out = new ArrayList<>();
        String[] cols = {"package_name", "title", "episode_title", "season_display_number",
                "episode_display_number", "poster_art_uri", "thumbnail_uri", "intent_uri",
                "last_playback_position_millis", "duration_millis", "last_engagement_time_utc_millis",
                "watch_next_type"};
        long oldest = System.currentTimeMillis()
                - getResources().getInteger(R.integer.continue_max_age_days) * 86_400_000L;
        // No selection: a caller without ACCESS_ALL_EPG_DATA gets a SecurityException when it passes one.
        try (Cursor c = getContentResolver().query(Uri.parse(TV + "watch_next_program"), cols, null, null, null)) {
            if (c == null) return out;
            while (c.moveToNext()) {
                Item it = new Item();
                it.pkg = c.getString(0);
                it.engaged = c.getLong(10);
                int type = c.isNull(11) ? 0 : c.getInt(11);
                if (type == 3 || it.engaged < oldest || launchIntent(it.pkg) == null) continue; // 3 = watchlist
                it.title = c.getString(1);
                if (TextUtils.isEmpty(it.title)) continue;
                String season = c.getString(3), episode = c.getString(4);
                it.sub = label(it.pkg);
                if (!TextUtils.isEmpty(season) && !TextUtils.isEmpty(episode)) it.sub += " · S" + season + " Ep" + episode;
                else if (!TextUtils.isEmpty(c.getString(2))) it.sub += " · " + c.getString(2);
                it.image = !TextUtils.isEmpty(c.getString(6)) ? c.getString(6) : c.getString(5);
                it.intentUri = c.getString(7);
                long pos = c.getLong(8), dur = c.getLong(9);
                it.progress = dur > 0 ? Math.min(1f, (float) pos / dur) : 0f;
                it.chip = it.progress > 0 ? "RESUME" : "WATCH";
                out.add(it);
            }
        } catch (Exception e) {
            return out; // no permission yet
        }
        Collections.sort(out, (a, b) -> Long.compare(b.engaged, a.engaged));
        return dedupe(out);
    }

    /** One of the feeds YouTube publishes as a preview channel (Subscriptions, Recommended for you, Trending);
     *  Continue watching it does not publish. */
    List<Item> youtubeRow(String name) {
        List<Item> out = new ArrayList<>();
        String pkg = getString(R.string.subscriptions_package);
        if (launchIntent(pkg) == null) return out;
        Set<Long> channels = new HashSet<>();
        try (Cursor c = getContentResolver().query(Uri.parse(TV + "channel"),
                new String[]{"_id", "package_name", "display_name"}, null, null, null)) {
            while (c != null && c.moveToNext()) {
                if (pkg.equals(c.getString(1)) && name.equalsIgnoreCase(c.getString(2))) channels.add(c.getLong(0));
            }
        } catch (Exception e) {
            return out;
        }
        if (channels.isEmpty()) return out;
        String[] cols = {"channel_id", "title", "poster_art_uri", "thumbnail_uri", "intent_uri", "author"};
        try (Cursor c = getContentResolver().query(Uri.parse(TV + "preview_program"), cols, null, null, null)) {
            while (c != null && c.moveToNext()) {
                if (!channels.contains(c.getLong(0))) continue;
                Item it = new Item();
                it.pkg = pkg;
                it.title = c.getString(1);
                it.image = !TextUtils.isEmpty(c.getString(3)) ? c.getString(3) : c.getString(2);
                it.intentUri = c.getString(4);
                it.sub = !TextUtils.isEmpty(c.getString(5)) ? "YouTube · " + c.getString(5) : "YouTube";
                if (!TextUtils.isEmpty(it.title)) out.add(it);
            }
        } catch (Exception e) {
            return out;
        }
        return dedupe(out);
    }

    List<Item> dedupe(List<Item> in) {
        Set<String> seen = new HashSet<>();
        List<Item> out = new ArrayList<>();
        int limit = getResources().getInteger(R.integer.row_limit);
        for (Item it : in) {
            if (seen.add(it.pkg + "\n" + it.title)) out.add(it);
            if (out.size() == limit) break;
        }
        return out;
    }

    // ---- Plex: recently added on the owner's server ----

    /** Recently added movies and shows; until Plex is linked, one card that starts the link. */
    List<Item> plexRow() {
        List<Item> out = new ArrayList<>();
        String app = launchIntent(Plex.PLEZY) != null ? Plex.PLEZY : Plex.PLEX_APP;
        if (launchIntent(app) == null) return out;
        if (plex.linked()) return plex.recentlyAdded(getResources().getInteger(R.integer.row_limit));
        Item link = new Item();
        link.pkg = app;
        link.title = getString(R.string.plex_link_title);
        link.sub = "Show what's new in your movies and TV";
        link.action = this::linkPlex;
        out.add(link);
        return out;
    }

    /** Plex's own TV sign-in: show a code, the owner enters it at plex.tv/link, the row fills in. */
    void linkPlex() {
        TextView body = caption(getString(R.string.plex_getting_code), 18, WHITE);
        body.setSingleLine(false);
        body.setPadding(dp(24), dp(16), dp(24), dp(8));
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(getString(R.string.plex_link_dialog_title))
                .setView(body)
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
        io.execute(() -> {
            try {
                String[] pin = plex.newPin();
                ui.post(() -> body.setText(getString(R.string.plex_code_instructions, pin[1])));
                // ponytail: polls every 2 s for the 15 minutes a Plex PIN lives, stops when the dialog closes
                for (int i = 0; i < 450 && dialog.isShowing(); i++) {
                    Thread.sleep(2000);
                    if (plex.pinDone(pin[0])) {
                        boolean found = plex.findServer();
                        ui.post(() -> {
                            dialog.dismiss();
                            if (!found) body.setText(getString(R.string.plex_no_server));
                            refreshHome();
                        });
                        return;
                    }
                }
            } catch (Exception e) {
                ui.post(() -> body.setText(getString(R.string.plex_error_connection)));
            }
        });
    }

    // ---- apps grid ----

    void buildAppsGrid() {
        List<Item> apps = allApps();
        appsGrid.removeAllViews();
        TextView title = caption(getString(R.string.section_your_apps), 15, GREY);
        title.setPadding(dp(8), dp(10), 0, dp(4));
        appsGrid.addView(title);
        int columns = 9;
        LinearLayout row = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % columns == 0) {
                row = new LinearLayout(this);
                row.setClipChildren(false);
                row.setPadding(0, dp(8), 0, dp(4));
        row.setClipToPadding(false); // a focused card grows into this padding; clipping here cut its top off
                appsGrid.addView(row);
            }
            String pkg = apps.get(i).pkg;
            View v = appCircle(apps.get(i), null);
            v.setOnLongClickListener(x -> { uninstall(pkg); return true; });
            row.addView(v);
        }
    }

    List<Item> allApps() {
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<Item> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            Item it = new Item();
            it.pkg = pkg;
            it.title = ri.loadLabel(pm).toString();
            apps.add(it);
        }
        Collections.sort(apps, (a, b) -> a.title.compareToIgnoreCase(b.title));
        return apps;
    }

    // ---- managing Your apps ----

    /** The Your apps order: saved by the menus below, config.xml's dock_apps until the first edit. */
    List<String> dock() {
        String saved = getSharedPreferences("home", MODE_PRIVATE).getString("dock", null);
        List<String> out = new ArrayList<>(saved == null
                ? Arrays.asList(getResources().getStringArray(R.array.dock_apps))
                : Arrays.asList(TextUtils.split(saved, ",")));
        out.removeIf(TextUtils::isEmpty);
        // Plezy replaced the Plex app: it takes Plex's place in an order saved before the switch.
        int plexAt = out.indexOf(Plex.PLEX_APP);
        if (plexAt >= 0 && launchIntent(Plex.PLEX_APP) == null && launchIntent(Plex.PLEZY) != null) {
            out.remove(Plex.PLEZY);
            out.add(Math.min(plexAt, out.size()), Plex.PLEZY);
            out.remove(Plex.PLEX_APP);
        }
        return out;
    }

    void saveDock(List<String> dock) {
        getSharedPreferences("home", MODE_PRIVATE).edit().putString("dock", TextUtils.join(",", dock)).apply();
    }

    /** Hold OK on a Your apps icon: move it or take it off the row. */
    void dockMenu(String pkg) {
        List<String> d = dock();
        d.removeIf(p -> launchIntent(p) == null); // keeps list positions equal to row positions
        int i = d.indexOf(pkg);
        if (i < 0) return;
        String[] options = {getString(R.string.action_move), getString(R.string.action_remove_from_dock)};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(label(pkg))
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // Google TV's Move: arrange mode with this app already picked up.
                        ViewGroup row = appsRow();
                        View box = row == null ? null : row.getChildAt(i);
                        enterArrange();
                        if (box != null && arrangeRow != null) {
                            box.requestFocus();
                            held = box;
                            showHeld();
                        }
                        return;
                    }
                    d.remove(i);
                    saveDock(d);
                    refreshHome();
                    focusIndex = Math.min(i, Math.max(0, d.size() - 1));
                })
                .show();
    }

    /** The Add tile on the Your apps row: pick any app not already on it. */
    void addToDock() {
        List<String> d = dock();
        List<Item> choices = new ArrayList<>();
        for (Item it : allApps()) if (!d.contains(it.pkg)) choices.add(it);

        Dialog dialog = new Dialog(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(round(PANEL, dp(24)));
        root.setPadding(dp(28), dp(24), dp(28), dp(24));

        TextView title = caption(getString(R.string.add_to_dock_title), 20, WHITE);
        title.setTypeface(medium);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.bottomMargin = dp(16);
        root.addView(title, tlp);

        if (choices.isEmpty()) {
            TextView empty = caption(getString(R.string.all_apps_already_added), 15, GREY);
            empty.setPadding(0, dp(12), 0, dp(16));
            root.addView(empty);

            TextView close = caption(getString(R.string.close), 15, WHITE);
            close.setFocusable(true);
            close.setClickable(true);
            close.setBackground(round(TILE, dp(12)));
            close.setPadding(dp(24), dp(10), dp(24), dp(10));
            close.setGravity(Gravity.CENTER);
            close.setOnFocusChangeListener((v, has) -> {
                close.setBackground(round(has ? TILE_FOCUS : TILE, dp(12)));
                close.setTextColor(has ? DARK_TEXT : WHITE);
            });
            close.setOnClickListener(v -> dialog.dismiss());
            root.addView(close, new LinearLayout.LayoutParams(-2, -2));
            dialog.setOnShowListener(di -> close.requestFocus());
        } else {
            ScrollView scroll = new ScrollView(this);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setClipToPadding(false);

            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setClipToPadding(false);
            list.setClipChildren(false);

            View[] firstItem = new View[1];
            for (Item it : choices) {
                LinearLayout itemRow = new LinearLayout(this);
                itemRow.setOrientation(LinearLayout.HORIZONTAL);
                itemRow.setGravity(Gravity.CENTER_VERTICAL);
                itemRow.setFocusable(true);
                itemRow.setClickable(true);
                itemRow.setBackground(round(TILE, dp(12)));
                itemRow.setPadding(dp(16), dp(10), dp(16), dp(10));

                ImageView icon = new ImageView(this);
                icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
                icon.setBackground(oval(CIRCLE));
                icon.setClipToOutline(true);
                icon.setImageDrawable(appIcon(it.pkg, dp(42)));
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(42), dp(42));
                iconLp.setMarginEnd(dp(16));
                itemRow.addView(icon, iconLp);

                TextView name = caption(it.title, 16, WHITE);
                name.setTypeface(medium);
                itemRow.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

                itemRow.setOnFocusChangeListener((v, has) -> {
                    itemRow.setBackground(round(has ? TILE_FOCUS : TILE, dp(12)));
                    name.setTextColor(has ? DARK_TEXT : WHITE);
                    itemRow.animate().scaleX(has ? 1.02f : 1f).scaleY(has ? 1.02f : 1f).setDuration(120).start();
                });

                itemRow.setOnClickListener(v -> {
                    dialog.dismiss();
                    d.removeIf(p -> launchIntent(p) == null);
                    d.add(it.pkg);
                    saveDock(d);
                    refreshHome();
                    focusIndex = d.size() - 1;
                });

                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(dp(480), -2);
                rowLp.bottomMargin = dp(8);
                list.addView(itemRow, rowLp);

                if (firstItem[0] == null) firstItem[0] = itemRow;
            }

            int contentHeight = Math.min(dp(440), choices.size() * dp(68));
            LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(dp(480), contentHeight);
            root.addView(scroll, scrollLp);
            scroll.addView(list);

            dialog.setOnShowListener(di -> {
                if (firstItem[0] != null) firstItem[0].requestFocus();
            });
        }

        dialog.setContentView(root);
        dialog.show();
    }

    // ---- arrange Your apps (Google TV's Reorder) ----

    ViewGroup appsRow() {
        View s = homeRows.getChildCount() > 1 ? homeRows.getChildAt(1) : null;
        return s != null && s.getTag() instanceof List ? rowOf(s) : null;
    }

    static boolean isApp(View v) {
        return v != null && v.getTag() instanceof String && !((String) v.getTag()).isEmpty();
    }

    int appCount() {
        int n = 0;
        while (n < arrangeRow.getChildCount() && isApp(arrangeRow.getChildAt(n))) n++;
        return n;
    }

    /** "Arrange your Apps": the rest of Home dims and locks, a Done button appears under the row. */
    void enterArrange() {
        ViewGroup row = appsRow();
        if (row == null || arrangeRow != null || !isApp(row.getChildAt(0))) return;
        arrangeHint = caption("", 12, GREY);
        arrangeHint.setLetterSpacing(0.04f);
        arrangeHint.setGravity(Gravity.CENTER_HORIZONTAL);
        // Onto an app before the Add, All apps and Reorder tiles lock, so locking them never drops focus.
        View f = getCurrentFocus();
        if (!isApp(f) || f.getParent() != row) row.getChildAt(0).requestFocus();
        arrangeRow = (LinearLayout) row;
        okArmed = false;
        LinearLayout section = (LinearLayout) homeRows.getChildAt(1);
        ((TextView) section.getChildAt(0)).setText(getString(R.string.arrange_your_apps));
        for (int i = 0; i < row.getChildCount(); i++) {
            if (isApp(row.getChildAt(i))) continue;
            row.getChildAt(i).setFocusable(false);
            fade(row.getChildAt(i), 0.3f);
        }
        for (int i = 0; i < homeRows.getChildCount(); i++) {
            View s = homeRows.getChildAt(i);
            if (s == section || !(s instanceof ViewGroup)) continue;
            ((ViewGroup) s).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            fade(s, 0.15f);
        }
        ((ViewGroup) topBar).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        fade(topBar, 0.15f);

        TextView done = caption(getString(R.string.done), 16, WHITE);
        done.setTypeface(medium);
        done.setGravity(Gravity.CENTER);
        done.setPadding(dp(20), 0, dp(20), 0);
        done.setFocusable(true);
        done.setClickable(true);
        done.setBackground(round(PILL, dp(20)));
        done.setOnFocusChangeListener((v, has) -> {
            v.setBackground(round(has ? FOCUS : PILL, dp(20)));
            ((TextView) v).setTextColor(has ? DARK_TEXT : WHITE);
        });
        done.setOnClickListener(v -> exitArrange());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(40));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(28);
        section.addView(done, lp);
        doneButton = done;
        updateHint(getCurrentFocus());
    }

    void exitArrange() {
        if (arrangeRow == null) return;
        if (held != null) drop();
        LinearLayout row = arrangeRow;
        arrangeRow = null;
        for (int i = 0; i < row.getChildCount(); i++) {
            row.getChildAt(i).setFocusable(true);
            fade(row.getChildAt(i), 1f);
        }
        row.getChildAt(row.getChildCount() - 1).requestFocus(); // back on Reorder, as Google TV leaves it
        for (int i = 0; i < homeRows.getChildCount(); i++) {
            View s = homeRows.getChildAt(i);
            if (!(s instanceof ViewGroup)) continue;
            ((ViewGroup) s).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            fade(s, 1f);
        }
        ((ViewGroup) topBar).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        fade(topBar, 1f);
        LinearLayout section = (LinearLayout) homeRows.getChildAt(1);
        ((TextView) section.getChildAt(0)).setText(getString(R.string.section_your_apps));
        section.removeView(doneButton);
        if (arrangeHint.getParent() != null) ((ViewGroup) arrangeHint.getParent()).removeView(arrangeHint);
    }

    /** PRESS TO MOVE under the focused app, PRESS TO CONFIRM under the one being moved. */
    void updateHint(View now) {
        if (arrangeHint == null) return;
        if (arrangeHint.getParent() != null) ((ViewGroup) arrangeHint.getParent()).removeView(arrangeHint);
        if (!isApp(now) || now.getParent() != arrangeRow) return;
        arrangeHint.setText(now == held ? getString(R.string.hint_press_to_confirm) : getString(R.string.hint_press_to_move));
        // Wider than the 91 dp box; the box centres it and doesn't clip, so it spills evenly both ways.
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(150), -2);
        lp.topMargin = dp(6);
        // The row clips at its left edge, so under the first app the hint starts at the icon instead.
        boolean first = arrangeRow.indexOfChild(now) == 0;
        lp.gravity = first ? Gravity.START : Gravity.CENTER_HORIZONTAL;
        arrangeHint.setGravity(first ? Gravity.START : Gravity.CENTER_HORIZONTAL);
        ((ViewGroup) now).addView(arrangeHint, lp);
    }

    void showHeld() {
        for (int i = 0; i < arrangeRow.getChildCount(); i++) arrangeRow.getChildAt(i).setForeground(null);
        if (held != null) {
            int i = arrangeRow.indexOfChild(held);
            ((ImageView) ((ViewGroup) held).getChildAt(0)).setForeground(null);
            held.setForeground(new HeldRing(((ViewGroup) held).getChildAt(0), i > 0, i < appCount() - 1));
        }
        updateHint(getCurrentFocus());
    }

    void moveHeld(int by) {
        int from = arrangeRow.indexOfChild(held), to = from + by;
        if (to < 0 || to >= appCount()) return;
        arrangeRow.removeView(held);
        arrangeRow.addView(held, to);
        held.requestFocus();
        showHeld();
    }

    void drop() {
        View was = held;
        held = null;
        List<String> order = new ArrayList<>();
        for (int i = 0; i < appCount(); i++) order.add((String) arrangeRow.getChildAt(i).getTag());
        saveDock(order);
        showHeld();
        if (was != null && was.isFocused()) was.getOnFocusChangeListener().onFocusChange(was, true);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (GlobalKeyService.isSettingsKey(k)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                startActivity(new Intent(this, QuickSettingsActivity.class));
            }
            return true;
        }
        if (GlobalKeyService.isProfileKey(k)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                Intent intent = new Intent(this, QuickSettingsActivity.class);
                intent.putExtra(QuickSettingsActivity.EXTRA_FOCUS_TILE, "Accounts");
                startActivity(intent);
            }
            return true;
        }
        if (GlobalKeyService.isTvKey(k)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                openLiveTv();
            }
            return true;
        }
        if (GlobalKeyService.isAppsKey(k)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                selectTab(tabApps);
                tabApps.requestFocus();
            }
            return true;
        }
        if (GlobalKeyService.isYouTubeKey(k)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                openYouTube();
            }
            return true;
        }
        if (GlobalKeyService.isListKey(k)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                openLiveTv();
            }
            return true;
        }
        if (GlobalKeyService.isInputKey(k)) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                openInputs();
            }
            return true;
        }
        if (arrangeRow != null) {
            boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
            boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER;
            // The OK that opened arrange mode is released inside it: only a press made here counts.
            if (ok && down) okArmed = true;
            if (ok && !down && !okArmed) return true;
            if (ok && !down) okArmed = false;
            View f = getCurrentFocus();
            if (held != null) {
                if (k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    if (down) moveHeld(k == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                    return true;
                }
                if (k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN) return true;
                if (ok || k == KeyEvent.KEYCODE_BACK) {
                    if (!down) drop(); // on release, so the release can't also click the app
                    return true;
                }
            } else if (ok && isApp(f) && f.getParent() == arrangeRow) {
                if (!down) {
                    held = f;
                    showHeld();
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    /** The app being moved: a grey ring around its icon, with arrows on the sides it can still move to. */
    class HeldRing extends Drawable {
        final View icon;
        final boolean left, right;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        HeldRing(View icon, boolean left, boolean right) {
            this.icon = icon;
            this.left = left;
            this.right = right;
        }

        @Override public void draw(Canvas c) {
            float cx = icon.getLeft() + icon.getWidth() / 2f, cy = icon.getTop() + icon.getHeight() / 2f;
            float r = icon.getWidth() * 0.55f, w = dp(3);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(w);
            paint.setColor(DIM);
            c.drawCircle(cx, cy, r, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            float h = dp(5), tip = dp(5);
            if (left) c.drawPath(arrow(cx - r - w, cy, -tip, h), paint);
            if (right) c.drawPath(arrow(cx + r + w, cy, tip, h), paint);
        }

        Path arrow(float x, float y, float tip, float h) {
            Path p = new Path();
            p.moveTo(x, y - h);
            p.lineTo(x + tip, y);
            p.lineTo(x, y + h);
            p.close();
            return p;
        }

        @Override public void setAlpha(int a) { }

        @Override public void setColorFilter(android.graphics.ColorFilter f) { }

        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** The Reorder tile at the end of Your apps, as on Google TV. */
    View reorderButton() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setFocusable(true);
        box.setClickable(true);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        box.setPadding(dp(8), dp(6), dp(8), 0);
        box.setTag("");
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(10), dp(10), dp(8), dp(10));
        tile.setBackground(round(CIRCLE, dp(16)));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_reorder);
        icon.setImageTintList(ColorStateList.valueOf(WHITE));
        tile.addView(icon, new LinearLayout.LayoutParams(dp(16), dp(16)));
        tile.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1));
        TextView text = caption(getString(R.string.reorder), 13, WHITE);
        tile.addView(text);
        box.addView(tile, new LinearLayout.LayoutParams(dp(75), dp(75)));
        box.setOnFocusChangeListener((v, has) -> {
            tile.setBackground(round(has ? FOCUS : CIRCLE, dp(16)));
            icon.setImageTintList(ColorStateList.valueOf(has ? DARK_TEXT : WHITE));
            text.setTextColor(has ? DARK_TEXT : WHITE);
            tile.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(150).start();
        });
        box.setOnClickListener(v -> enterArrange());
        box.setLayoutParams(new LinearLayout.LayoutParams(dp(91), -2));
        return box;
    }

    /** Android's own uninstall confirmation; the grid and Your apps drop the app on return (onResume). */
    void uninstall(String pkg) {
        start(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
    }

    /** Apps grid rows that have scrolled up under the transparent bar fade out instead of showing through it. */
    void hideRowsUnderBar() {
        int barBottom = dp(90), y = appsScroll.getScrollY();
        for (int i = 0; i < appsGrid.getChildCount(); i++) {
            View row = appsGrid.getChildAt(i);
            boolean under = appsGrid.getTop() + row.getTop() - y < barBottom;
            if (under != (row.getAlpha() < 0.5f)) fade(row, under ? 0f : 1f);
        }
    }

    // ---- tiles ----

    /** A round app icon; its label shows only in the focused section (always in the Apps grid: captions == null). */
    View appCircle(Item it, List<View> captions) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setFocusable(true);
        box.setClickable(true);
        box.setClipChildren(false);
        box.setClipToPadding(false); // the focus scale grows past the padding edge
        box.setPadding(0, dp(6), 0, 0);
        box.setTag(it.pkg); // arrange mode moves the boxes that carry a package

        ImageView icon = new ImageView(this);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        icon.setBackground(oval(CIRCLE));
        icon.setClipToOutline(true);
        icon.setImageDrawable(appIcon(it.pkg, dp(75)));
        box.addView(icon, new LinearLayout.LayoutParams(dp(75), dp(75)));

        TextView label = caption(it.title, 13, GREY);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        box.addView(label, lp);
        if (captions != null) captions.add(label);

        box.setOnFocusChangeListener((v, has) -> {
            icon.setForeground(has ? ring(true) : null);
            icon.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(150).start();
            label.setTextColor(has ? Color.WHITE : GREY);
        });
        box.setOnClickListener(v -> launchPackage(it.pkg));
        box.setLayoutParams(new LinearLayout.LayoutParams(dp(91), -2));
        return box;
    }

    View addAppButton(List<View> captions) {
        Item it = new Item();
        it.title = getString(R.string.add_app);
        it.pkg = "";
        LinearLayout box = (LinearLayout) appCircle(it, captions);
        ImageView icon = (ImageView) box.getChildAt(0);
        icon.setImageResource(R.drawable.ic_add);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setImageTintList(ColorStateList.valueOf(WHITE));
        box.setOnClickListener(v -> addToDock());
        return box;
    }

    View allAppsButton(List<View> captions) {
        Item it = new Item();
        it.title = getString(R.string.all_apps);
        it.pkg = "";
        LinearLayout box = (LinearLayout) appCircle(it, captions);
        ImageView icon = (ImageView) box.getChildAt(0);
        icon.setImageResource(R.drawable.ic_arrow);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setImageTintList(ColorStateList.valueOf(WHITE));
        box.setOnClickListener(v -> { selectTab(tabApps); tabApps.requestFocus(); });
        return box;
    }

    View card(Item it, List<View> captions) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setFocusable(true);
        box.setClickable(true);
        box.setClipChildren(false);
        box.setClipToPadding(false);
        box.setPadding(dp(10), 0, dp(10), 0);

        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(round(CARD, dp(12)));
        frame.setClipToOutline(true);
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        frame.addView(poster, new FrameLayout.LayoutParams(-1, -1));
        if (it.chip != null) {
            TextView chip = caption(it.chip, 11, Color.WHITE);
            chip.setTypeface(medium);
            chip.setLetterSpacing(0.08f);
            chip.setPadding(dp(8), dp(3), dp(8), dp(3));
            chip.setBackground(round(0xB3202124, dp(6)));
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
            cp.setMargins(dp(10), 0, 0, dp(it.progress > 0 ? 16 : 10));
            frame.addView(chip, cp);
        }
        if (it.progress > 0) {
            LinearLayout track = new LinearLayout(this);
            track.setBackground(round(0x66FFFFFF, dp(2)));
            View fill = new View(this);
            fill.setBackground(round(Color.WHITE, dp(2)));
            track.addView(fill, new LinearLayout.LayoutParams(0, -1, it.progress));
            track.addView(new View(this), new LinearLayout.LayoutParams(0, -1, 1f - it.progress));
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, dp(3), Gravity.BOTTOM);
            tp.setMargins(dp(10), 0, dp(10), dp(8));
            frame.addView(track, tp);
        }
        // The focus ring sits in a holder 3dp larger than the poster, so it outlines the thumbnail instead of
        // drawing over (and visually cropping) its edges; negative margins keep the row layout unchanged.
        FrameLayout holder = new FrameLayout(this);
        holder.setClipChildren(false);
        holder.setPadding(dp(3), dp(3), dp(3), dp(3));
        holder.addView(frame, new FrameLayout.LayoutParams(dp(195), dp(110)));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(201), dp(116));
        hp.setMargins(-dp(3), -dp(3), -dp(3), -dp(3));
        box.addView(holder, hp);

        TextView title = caption(it.title, 14, WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(195), -2);
        lp.topMargin = dp(8);
        box.addView(title, lp);
        TextView sub = caption(it.sub, 12, DIM);
        box.addView(sub, new LinearLayout.LayoutParams(dp(195), -2));
        captions.add(title);
        captions.add(sub);

        box.setOnFocusChangeListener((v, has) -> {
            GradientDrawable ring = ring(false);
            ring.setCornerRadius(dp(15)); // the poster's 12dp corners plus the 3dp gap
            holder.setForeground(has ? ring : null);
            holder.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(150).start();
        });
        box.setOnClickListener(v -> openProgram(it));
        loadImage(poster, it.image, dp(195));
        return box;
    }

    TextView caption(String text, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(regular);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    // ---- focus memory ----

    void rememberFocus() {
        View f = getCurrentFocus();
        LinearLayout section = inside(f, homeRows) ? sectionOf(f) : null;
        if (section == null || !(f.getParent() instanceof ViewGroup)) return;
        focusSection = homeRows.indexOfChild(section);
        focusIndex = ((ViewGroup) f.getParent()).indexOfChild(f);
    }

    void restoreFocus() {
        if (selectedTab == tabApps || quickPanel.getVisibility() == View.VISIBLE) return;
        if (focusSection > 0 && focusSection < homeRows.getChildCount()
                && homeRows.getChildAt(focusSection).getTag() instanceof List) {
            ViewGroup row = rowOf(homeRows.getChildAt(focusSection));
            if (row.getChildCount() > 0) {
                row.getChildAt(Math.min(focusIndex, row.getChildCount() - 1)).requestFocus();
                return;
            }
        }
        focusFirstApp();
    }

    static ViewGroup rowOf(View section) {
        return (ViewGroup) ((ViewGroup) ((ViewGroup) section).getChildAt(1)).getChildAt(0);
    }

    void focusFirstApp() {
        if (homeRows.getChildCount() > 1 && homeRows.getChildAt(1).getTag() instanceof List) {
            ViewGroup row = rowOf(homeRows.getChildAt(1));
            if (row.getChildCount() > 0) row.getChildAt(0).requestFocus();
        }
    }

    static boolean inside(View v, View group) {
        for (Object p = v; p instanceof View; p = ((View) p).getParent()) if (p == group) return true;
        return false;
    }

    // ---- launching ----

    Intent launchIntent(String pkg) {
        if (TextUtils.isEmpty(pkg)) return null;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            if (!ai.enabled) return null;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    boolean launchPackage(String pkg) {
        Intent i = launchIntent(pkg);
        return i != null && start(i);
    }

    void openProgram(Item it) {
        if (it.action != null) {
            it.action.run();
            return;
        }
        if (!TextUtils.isEmpty(it.intentUri)) {
            try {
                Intent i = Intent.parseUri(it.intentUri, Intent.URI_INTENT_SCHEME);
                // Many rows carry a plain https link: send it to the app that published the row, not a chooser.
                if (i.getComponent() == null && i.getPackage() == null) i.setPackage(it.pkg);
                if (start(i)) return;
            } catch (Exception ignored) {
                // fall back to the app itself
            }
        }
        launchPackage(it.pkg);
    }

    boolean start(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    String label(String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    /** Adaptive icons are drawn full-bleed into the circle; drawn as-is they come out as a small badge. */
    Drawable appIcon(String pkg, int size) {
        Drawable d;
        try {
            d = pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        // Dark-mode variant of each icon (Plezy has one), so TVs with night mode off match those with it on.
        try {
            int res = pm.getApplicationInfo(pkg, 0).icon;
            Configuration night = new Configuration(getResources().getConfiguration());
            night.uiMode = (night.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | Configuration.UI_MODE_NIGHT_YES;
            Drawable dark = res == 0 ? null : createPackageContext(pkg, 0).createConfigurationContext(night).getDrawable(res);
            if (dark != null) d = dark;
        } catch (Exception ignored) { }
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        boolean adaptive = d instanceof AdaptiveIconDrawable;
        // Google's own apps (and adaptive icons with no background layer) draw their art edge to edge; Google TV
        // shows those as the logo, inset, on a white circle.
        if (WHITE_ICONS.contains(pkg) || (adaptive && ((AdaptiveIconDrawable) d).getBackground() == null)) {
            Drawable art = adaptive ? ((AdaptiveIconDrawable) d).getForeground() : d;
            c.drawColor(Color.WHITE);
            int inset = adaptive ? size / 8 : size / 5;
            art.setBounds(inset, inset, size - inset, size - inset);
            art.draw(c);
            return new BitmapDrawable(getResources(), b);
        }
        if (!adaptive) return d;
        AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
        // Layers are 108 units with the visible 72 in the middle: draw them 1.5x so the middle fills the circle.
        Rect r = new Rect(-size / 4, -size / 4, size + size / 4, size + size / 4);
        for (Drawable layer : new Drawable[]{a.getBackground(), a.getForeground()}) {
            if (layer == null) continue;
            layer.setBounds(r);
            layer.draw(c);
        }
        return new BitmapDrawable(getResources(), b);
    }

    // ---- background photos (a Google Photos shared album or built-in pack) ----

    void advancePhoto() {
        if (!photoUrls.isEmpty()) {
            nextPhoto();
        } else {
            nextDefaultPhoto();
        }
    }

    List<Integer> defaultWallpapers() {
        List<Integer> list = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            int id = getResources().getIdentifier("wallpaper_" + i, "drawable", getPackageName());
            if (id != 0) list.add(id);
            else if (i > 5) break;
        }
        return list;
    }

    void nextDefaultPhoto() {
        ui.removeCallbacks(photoTick);
        List<Integer> wallpapers = defaultWallpapers();
        if (wallpapers.isEmpty()) return;
        int resId = wallpapers.get(defaultPhotoIndex++ % wallpapers.size());
        io.execute(() -> {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b = BitmapFactory.decodeResource(getResources(), resId, opts);
            ui.post(() -> {
                if (b != null) {
                    ImageView in = currentPhoto == photoA ? photoB : photoA;
                    ImageView out = currentPhoto;
                    currentPhoto = in;
                    in.setImageBitmap(b);
                    in.animate().alpha(photoLevel()).setDuration(1500).start();
                    if (out != null) out.animate().alpha(0f).setDuration(1500).start();
                }
                if (resumed) ui.postDelayed(photoTick, photoDelay());
            });
        });
    }

    void loadPhotos() {
        String album = getString(R.string.photos_album_url).trim();
        if (album.isEmpty()) {
            nextDefaultPhoto();
            return;
        }
        ui.removeCallbacks(photosRetry);
        io.execute(() -> {
            try {
                // ponytail: reads the album's public page; no API lists a shared album's photos without sign-in
                String html = get(album);
                Matcher m = Pattern.compile("https://lh3\\.googleusercontent\\.com/pw/[A-Za-z0-9_\\-]+").matcher(html);
                Set<String> urls = new LinkedHashSet<>();
                while (m.find()) urls.add(m.group());
                List<String> list = new ArrayList<>(urls);
                if (list.isEmpty()) { retryPhotosSoon(); return; } // keep the photos we have
                Collections.shuffle(list);
                ui.post(() -> {
                    photoUrls = list;
                    photoIndex = 0;
                    nextPhoto(); // show one right away: the first load, or a manual refresh
                });
            } catch (Exception ignored) {
                retryPhotosSoon(); // offline or the page hiccuped: keep what's showing
            }
        });
    }

    final Runnable photosRetry = this::loadPhotos;

    /** With nothing on screen yet, a failed read is retried in a minute rather than waiting out the hour. */
    void retryPhotosSoon() {
        ui.post(() -> { if (photoUrls.isEmpty()) ui.postDelayed(photosRetry, 60_000L); });
    }

    void nextPhoto() {
        ui.removeCallbacks(photoTick);
        if (photoUrls.isEmpty()) return;
        String url = photoUrls.get(photoIndex++ % photoUrls.size()) + "=w1920-h1080-c";
        io.execute(() -> {
            Bitmap b = decode(url, 1920);
            ui.post(() -> {
                if (b != null) {
                    ImageView in = currentPhoto == photoA ? photoB : photoA;
                    ImageView out = currentPhoto;
                    currentPhoto = in;
                    in.setImageBitmap(b);
                    in.animate().alpha(photoLevel()).setDuration(1500).start();
                    if (out != null) out.animate().alpha(0f).setDuration(1500).start();
                }
                if (resumed) ui.postDelayed(photoTick, photoDelay());
            });
        });
    }

    long photoDelay() {
        return getResources().getInteger(R.integer.photo_seconds) * 1000L;
    }

    /** The photo is full strength behind the bottom row and the bar, and dim once you move into the rows. */
    void dimPhotos(boolean dim) {
        if (photosDimmed == dim) return;
        photosDimmed = dim;
        // The photo fading in, not whichever is brighter mid-crossfade.
        if (currentPhoto != null) currentPhoto.animate().alpha(photoLevel()).setDuration(300).start();
    }

    float photoLevel() {
        return photosDimmed ? 0.25f : 1f;
    }

    // ---- weather (Open-Meteo, no key) ----

    static boolean isFahrenheitCountry(String country) {
        if (country == null) return false;
        country = country.trim().toUpperCase(Locale.US);
        return country.equals("US") || country.equals("USA")
                || country.equals("BS") || country.equals("BZ")
                || country.equals("KY") || country.equals("PW")
                || country.equals("FM") || country.equals("MH")
                || country.equals("LR") || country.equals("VI")
                || country.equals("GU") || country.equals("MP")
                || country.equals("AS") || country.equals("PR");
    }

    static String[] resolveTemperatureUnitAndSymbol(String configuredUnit, String country) {
        if (configuredUnit != null) {
            configuredUnit = configuredUnit.trim().toLowerCase(Locale.US);
        } else {
            configuredUnit = "";
        }
        if ("celsius".equals(configuredUnit) || "c".equals(configuredUnit)) {
            return new String[]{"celsius", "°C"};
        }
        if ("fahrenheit".equals(configuredUnit) || "f".equals(configuredUnit)) {
            return new String[]{"fahrenheit", "°F"};
        }
        if (isFahrenheitCountry(country)) {
            return new String[]{"fahrenheit", "°F"};
        }
        return new String[]{"celsius", "°C"};
    }

    void loadWeather() {
        ui.removeCallbacks(weatherTick);
        String city = getString(R.string.weather_city).trim();
        String configuredUnit = getString(R.string.temperature_unit);
        if (weather.getText().length() == 0) {
            weather.setVisibility(View.GONE);
            findViewById(R.id.weather_divider).setVisibility(View.GONE);
        }
        io.execute(() -> {
            try {
                SharedPreferences prefs = getSharedPreferences("weather", MODE_PRIVATE);
                String key = city.isEmpty() ? "ip" : city;
                boolean stale = city.isEmpty() && (System.currentTimeMillis() - prefs.getLong("at", 0) > 86_400_000L || !prefs.contains("country"));
                if (!key.equals(prefs.getString("key", null)) || stale) {
                    String lat, lon;
                    String country = "";
                    if (city.isEmpty()) {
                        JSONObject here = new JSONObject(get("https://ipapi.co/json/"));
                        lat = here.getString("latitude");
                        lon = here.getString("longitude");
                        country = here.optString("country_code", "");
                    } else {
                        JSONArray hits = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=1&name="
                                + URLEncoder.encode(city, "UTF-8"))).optJSONArray("results");
                        if (hits == null || hits.length() == 0) return;
                        JSONObject hit = hits.getJSONObject(0);
                        lat = hit.getString("latitude");
                        lon = hit.getString("longitude");
                        country = hit.optString("country_code", "");
                    }
                    SharedPreferences.Editor edit = prefs.edit().putString("key", key).putString("lat", lat).putString("lon", lon)
                            .putLong("at", System.currentTimeMillis());
                    if (!country.isEmpty()) edit.putString("country", country);
                    edit.apply();
                }
                String country = prefs.getString("country", "");
                if (country.isEmpty()) {
                    country = Locale.getDefault().getCountry();
                }
                String[] unitAndSymbol = resolveTemperatureUnitAndSymbol(configuredUnit, country);
                String unit = unitAndSymbol[0];
                String symbol = unitAndSymbol[1];

                JSONObject now = new JSONObject(get("https://api.open-meteo.com/v1/forecast?current=temperature_2m,weather_code,is_day"
                        + "&latitude=" + prefs.getString("lat", "") + "&longitude=" + prefs.getString("lon", "")
                        + "&temperature_unit=" + unit)).getJSONObject("current");
                String text = sky(now.getInt("weather_code"), now.optInt("is_day", 1) == 1)
                        + "  " + Math.round(now.getDouble("temperature_2m")) + symbol;
                ui.post(() -> {
                    weather.setText(text);
                    weather.setVisibility(View.VISIBLE);
                    findViewById(R.id.weather_divider).setVisibility(View.VISIBLE);
                });
            } catch (Exception ignored) {
                // offline: keep the last reading
            }
        });
        if (resumed) ui.postDelayed(weatherTick, WEATHER_EVERY_MS); // refresh every 15 min, only while Home has focus
    }

    static String sky(int code, boolean day) {
        if (code == 0) return day ? "☀" : "☾";
        if (code <= 2) return day ? "⛅" : "☁";
        if (code == 3) return "☁";
        if (code <= 48) return "🌫";
        if (code <= 67 || (code >= 80 && code <= 82)) return "🌧";
        if (code <= 77 || code == 85 || code == 86) return "❄";
        return "⛈";
    }

    // ---- images ----

    void loadImage(ImageView v, String uri, int widthPx) {
        v.setTag(uri);
        if (TextUtils.isEmpty(uri)) return;
        Bitmap cached = images.get(uri);
        if (cached != null) { v.setImageBitmap(cached); return; }
        io.execute(() -> {
            Bitmap b = decode(uri, widthPx);
            if (b == null) return;
            images.put(uri, b);
            ui.post(() -> { if (uri.equals(v.getTag())) v.setImageBitmap(b); });
        });
    }

    Bitmap decode(String uri, int widthPx) {
        try {
            byte[] data;
            if (uri.startsWith("http")) {
                data = readAll(open(uri));
            } else {
                try (InputStream in = getContentResolver().openInputStream(Uri.parse(uri))) {
                    data = readAll(in);
                }
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            o.inSampleSize = 1;
            while (o.outWidth / (o.inSampleSize * 2) >= widthPx) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    static InputStream open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", UA);
        return c.getInputStream();
    }

    static String get(String url) throws Exception {
        return new String(readAll(open(url)), "UTF-8");
    }

    static byte[] readAll(InputStream in) throws Exception {
        try (InputStream s = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384];
            for (int n; (n = s.read(buf)) > 0; ) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    // ---- drawing helpers ----

    static Typeface font(String path, int fallbackStyle) {
        try {
            if (new File(path).canRead()) return Typeface.createFromFile(path);
        } catch (RuntimeException ignored) {
            // fall through
        }
        return Typeface.create("sans-serif", fallbackStyle);
    }

    void applyFont(View v) {
        if (v instanceof TextView) ((TextView) v).setTypeface(regular);
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) applyFont(((ViewGroup) v).getChildAt(i));
        }
    }

    int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    GradientDrawable ring(boolean circle) {
        GradientDrawable d = new GradientDrawable();
        if (circle) d.setShape(GradientDrawable.OVAL);
        else d.setCornerRadius(dp(12));
        d.setStroke(dp(3), Color.WHITE);
        return d;
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(refreshReceiver);
        } catch (Throwable ignored) {}
        io.shutdownNow();
    }
}
