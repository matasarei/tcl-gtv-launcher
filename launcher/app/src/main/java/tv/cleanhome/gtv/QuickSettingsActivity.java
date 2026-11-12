package tv.cleanhome.gtv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

public class QuickSettingsActivity extends Activity {
    private static final String TAG = "QuickSettings";

    public static final String ACTION_REFRESH_ALBUM = "tv.cleanhome.gtv.ACTION_REFRESH_ALBUM";
    public static final String EXTRA_FOCUS_TILE = "extra_focus_tile";

    static final int WHITE = 0xFFE3E3E3, GREY = 0xFFC4C7C5, DARK_TEXT = 0xFF1B1F23;
    static final int PANEL = 0xFF121B21, TILE = 0xFF1C262C, TILE_FOCUS = 0xFFCCE8FF;

    private View root;
    private View panel;
    private LinearLayout tiles;
    private View allSettings;
    private ImageView allSettingsIcon;
    private Typeface regular, medium;
    private boolean closing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
        setContentView(R.layout.activity_quick_settings);

        regular = font("/system/fonts/Google_Sans-400-100_0-0_0.ttf", Typeface.NORMAL);
        medium = font("/system/fonts/Google_Sans-500-100_0-0_0.ttf", Typeface.BOLD);

        root = findViewById(R.id.quick_root);
        panel = findViewById(R.id.quick_panel);
        tiles = findViewById(R.id.quick_tiles);
        allSettings = findViewById(R.id.all_settings);
        allSettingsIcon = findViewById(R.id.all_settings_icon);

        panel.setBackground(round(PANEL, dp(20)));

        allSettingsIcon.setBackground(oval(TILE));
        allSettingsIcon.setImageTintList(ColorStateList.valueOf(WHITE));
        allSettings.setOnFocusChangeListener((v, has) -> {
            allSettingsIcon.setBackground(oval(has ? TILE_FOCUS : TILE));
            allSettingsIcon.setImageTintList(ColorStateList.valueOf(has ? DARK_TEXT : WHITE));
        });
        allSettings.setOnClickListener(v -> {
            if (start(new Intent(Settings.ACTION_SETTINGS))) {
                dismiss();
            }
        });

        root.setOnClickListener(v -> dismiss());

        buildTiles();

        float away = dp(300);
        panel.setTranslationX(away);
        panel.setAlpha(0f);
        panel.animate().translationX(0).alpha(1f).setDuration(240)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    String target = getIntent() != null ? getIntent().getStringExtra(EXTRA_FOCUS_TILE) : null;
                    if (target != null) {
                        View targetView = tiles.findViewWithTag(target);
                        if (targetView != null) {
                            targetView.requestFocus();
                            return;
                        }
                    }
                    if (tiles.getChildCount() > 0) {
                        View firstRow = tiles.getChildAt(0);
                        if (firstRow instanceof ViewGroup && ((ViewGroup) firstRow).getChildCount() > 0) {
                            ((ViewGroup) firstRow).getChildAt(0).requestFocus();
                        }
                    }
                }).start();
    }

    private void buildTiles() {
        tiles.removeAllViews();
        Object[][] entries = {
                {"Apps", R.drawable.ic_apps, new Intent[]{
                        new Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.device.apps.AllAppsActivity"),
                        new Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.device.apps.AppsActivity"),
                        new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)}},
                {"Accounts", R.drawable.ic_account, new Intent[]{
                        new Intent(Settings.ACTION_SETTINGS),
                        new Intent(Settings.ACTION_ADD_ACCOUNT)}},
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
                {"Refresh album", R.drawable.ic_refresh, (Runnable) () -> sendBroadcast(new Intent(ACTION_REFRESH_ALBUM).setPackage(getPackageName()))},
        };

        LinearLayout row = null;
        for (int i = 0; i < entries.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                tiles.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
            LinearLayout tile = new LinearLayout(this);
            tile.setTag(entries[i][0]);
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
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(8);
            tile.addView(label, lp);

            tile.setOnFocusChangeListener((v, has) -> {
                v.setBackground(round(has ? TILE_FOCUS : TILE, dp(14)));
                icon.setImageTintList(ColorStateList.valueOf(has ? DARK_TEXT : WHITE));
                label.setTextColor(has ? DARK_TEXT : WHITE);
            });

            final Object action = entries[i][2];
            tile.setOnClickListener(v -> {
                if (action instanceof Runnable) {
                    ((Runnable) action).run();
                    dismiss();
                    return;
                }
                for (Intent in : (Intent[]) action) {
                    if (start(in)) {
                        dismiss();
                        return;
                    }
                }
            });

            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, dp(54), 1);
            tp.setMargins(0, 0, i % 2 == 0 ? dp(6) : 0, dp(6));
            if (row != null) {
                row.addView(tile, tp);
                if (i == entries.length - 1 && i % 2 == 0) {
                    row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
                }
            }
        }
    }

    private void dismiss() {
        if (closing) return;
        closing = true;
        panel.animate().translationX(dp(300)).alpha(0f).setDuration(180)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    finish();
                    overridePendingTransition(0, 0);
                }).start();
    }

    @Override
    public void onBackPressed() {
        dismiss();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int code = event.getKeyCode();
            if (code == 4077 || code == 176 || code == KeyEvent.KEYCODE_SETTINGS || code == KeyEvent.KEYCODE_MENU
                    || code == 4016 || code == 288 || code == KeyEvent.KEYCODE_PROFILE_SWITCH) {
                dismiss();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String target = intent != null ? intent.getStringExtra(EXTRA_FOCUS_TILE) : null;
        if (target != null && tiles != null) {
            View targetView = tiles.findViewWithTag(target);
            if (targetView != null && !targetView.isFocused()) {
                targetView.requestFocus();
                return;
            }
        }
        dismiss();
    }

    private boolean start(Intent in) {
        try {
            in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(in);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "Cannot launch " + in + ": " + t);
            return false;
        }
    }

    private TextView caption(String text, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(regular);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private static GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    private static Typeface font(String path, int fallback) {
        try {
            File f = new File(path);
            if (f.exists()) return Typeface.createFromFile(f);
        } catch (Throwable ignored) {}
        return Typeface.defaultFromStyle(fallback);
    }
}
