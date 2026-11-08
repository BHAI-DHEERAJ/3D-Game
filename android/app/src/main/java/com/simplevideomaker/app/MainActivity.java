package com.simplevideomaker.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple Video Maker — native Android photo/video editor for first-time smartphone users.
 * No WebView: the preview, the text/stickers and the exported movie are all drawn by the app
 * itself (Canvas + MediaCodec + MediaMuxer).
 */
public class MainActivity extends Activity {

    private static final int REQ_PICK = 1001;
    private static final int REQ_PERM = 1002;

    private static final int ACCENT = 0xFFE23E3E;
    private static final int ACCENT_DARK = 0xFFC22F2F;
    private static final int BG = 0xFFFFF6F0;
    private static final int CARD = 0xFFFFFFFF;
    private static final int LINE = 0xFFEADFD7;
    private static final int INK = 0xFF2A2320;
    private static final int MUTED = 0xFF7A6C64;

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final List<Clip> clips = new ArrayList<>();
    private int sel = -1;
    private boolean hindi = false;
    private boolean busy = false;
    private Overlay selOverlay;
    private Uri lastSaved;
    private String lastSavedMime = "video/mp4";

    private LinearLayout root;
    private FrameLayout seat;
    private View emptyPanel;
    private LinearLayout editorPanel;
    private EditorView preview;
    private LinearLayout timelineRow;
    private LinearLayout clipTools;
    private LinearLayout overlayTools;
    private TextView speedLabel;

    private AlertDialog busyDialog = null;

    /* ------------------------------------------------------------------ boot */

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQ_PERM);
        }
        buildUi();
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(true);

        root.addView(buildHeader());

        seat = new FrameLayout(this);
        LinearLayout.LayoutParams seatLp = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        seat.setPadding(dp(10), dp(10), dp(10), 0);
        root.addView(seat, seatLp);

        emptyPanel = buildEmptyPanel();
        editorPanel = buildEditorPanel();
        seat.addView(emptyPanel, new FrameLayout.LayoutParams(MATCH, MATCH));
        seat.addView(editorPanel, new FrameLayout.LayoutParams(MATCH, MATCH));

        root.addView(buildActionBar());

        setContentView(root);
        refresh();
    }

    private View buildHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ACCENT, 0xFFFF8A3D});
        bar.setBackground(bg);
        bar.setPadding(dp(14), dp(12), dp(10), dp(12));

        TextView title = new TextView(this);
        title.setText("🎬  " + t("Simple Video Maker", "आसान वीडियो मेकर"));
        title.setTextColor(Color.WHITE);
        title.setTextSize(19f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        bar.addView(title, tlp);

        TextView lang = new TextView(this);
        lang.setText(hindi ? "EN" : "हिं");
        lang.setTextColor(Color.WHITE);
        lang.setTextSize(16f);
        lang.setTypeface(Typeface.DEFAULT_BOLD);
        lang.setGravity(Gravity.CENTER);
        GradientDrawable lb = new GradientDrawable();
        lb.setColor(0x33FFFFFF);
        lb.setCornerRadius(dp(20));
        lb.setStroke(dp(2), 0xB3FFFFFF);
        lang.setBackground(lb);
        lang.setPadding(dp(14), dp(7), dp(14), dp(7));
        lang.setClickable(true);
        lang.setOnClickListener(v -> {
            hindi = !hindi;
            buildUi();
        });
        bar.addView(lang);
        return bar;
    }

    /* ---------------------------------------------------------------- empty */

    private View buildEmptyPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(8), dp(8), dp(8), dp(20));

        TextView hero = new TextView(this);
        hero.setText("👵🎬");
        hero.setTextSize(52f);
        hero.setGravity(Gravity.CENTER);
        box.addView(hero);

        TextView title = new TextView(this);
        title.setText(t("Make a video in 3 easy steps", "3 आसान कदमों में वीडियो बनाएं"));
        title.setTextSize(21f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(INK);
        title.setGravity(Gravity.CENTER);
        box.addView(title, spaced(dp(6)));

        box.addView(stepCard("1", t("Add your photos and videos", "अपनी फ़ोटो और वीडियो जोड़ें")));
        box.addView(stepCard("2", t("Add text or a sticker", "टेक्स्ट या स्टिकर लगाएं")));
        box.addView(stepCard("3", t("Press “Make Video”", "“वीडियो बनाएं” दबाएं")));

        TextView add = bigButton(t("➕  Add Photos & Videos", "➕  फ़ोटो और वीडियो जोड़ें"),
                ACCENT, Color.WHITE);
        add.setOnClickListener(v -> pickMedia());
        box.addView(add, spaced(dp(18)));

        TextView hint = new TextView(this);
        hint.setText(t("Choose photos or videos from your phone",
                "फ़ोन से फ़ोटो या वीडियो चुनें"));
        hint.setTextColor(MUTED);
        hint.setTextSize(15f);
        hint.setGravity(Gravity.CENTER);
        box.addView(hint, spaced(dp(8)));
        return box;
    }

    private View stepCard(String number, String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(round(CARD, 18, LINE));
        row.setPadding(dp(14), dp(12), dp(14), dp(12));

        TextView n = new TextView(this);
        n.setText(number);
        n.setTextSize(18f);
        n.setTypeface(Typeface.DEFAULT_BOLD);
        n.setTextColor(0xFF6B3D00);
        n.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xFFFFB020);
        n.setBackground(circle);
        row.addView(n, new LinearLayout.LayoutParams(dp(38), dp(38)));

        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(17f);
        label.setTextColor(INK);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setPadding(dp(12), 0, 0, 0);
        row.addView(label, new LinearLayout.LayoutParams(0, WRAP, 1f));
        return row;
    }

    /* --------------------------------------------------------------- editor */

    private LinearLayout buildEditorPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        FrameLayout previewBox = new FrameLayout(this);
        GradientDrawable pb = new GradientDrawable();
        pb.setColor(0xFF1B1512);
        pb.setCornerRadius(dp(18));
        previewBox.setBackground(pb);
        preview = new EditorView(this);
        preview.setListener(new EditorView.Listener() {
            @Override
            public void onOverlayTouched(Overlay o) {
                selOverlay = o;
                updateOverlayTools();
            }
        });
        previewBox.addView(preview, new FrameLayout.LayoutParams(MATCH, MATCH));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(MATCH, 0, 1f);
        panel.addView(previewBox, plp);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(MATCH, dp(84)));
        timelineRow = new LinearLayout(this);
        timelineRow.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(timelineRow, new HorizontalScrollView.LayoutParams(WRAP, MATCH));
        panel.addView(scroll);

        clipTools = new LinearLayout(this);
        clipTools.setOrientation(LinearLayout.HORIZONTAL);
        clipTools.addView(tool("🔤", t("Text", "लिखें"), false, v -> addText()));
        clipTools.addView(tool("😊", t("Sticker", "स्टिकर"), false, v -> addSticker()));
        speedLabel = tool("⏱️", "3s", false, v -> changeDuration());
        clipTools.addView(speedLabel);
        clipTools.addView(tool("⬅️", t("Left", "पीछे"), false, v -> move(-1)));
        clipTools.addView(tool("➡️", t("Right", "आगे"), false, v -> move(1)));
        clipTools.addView(tool("🗑️", t("Remove", "हटाएं"), true, v -> removeClip()));
        panel.addView(clipTools);

        overlayTools = new LinearLayout(this);
        overlayTools.setOrientation(LinearLayout.HORIZONTAL);
        overlayTools.addView(tool("➕", t("Bigger", "बड़ा"), false, v -> resize(1.18f)));
        overlayTools.addView(tool("➖", t("Smaller", "छोटा"), false, v -> resize(1f / 1.18f)));
        overlayTools.addView(tool("🗑️", t("Remove", "हटाएं"), true, v -> removeOverlay()));
        overlayTools.addView(tool("✔️", t("Done", "ठीक है"), false, v -> {
            selOverlay = null;
            updateOverlayTools();
        }));
        overlayTools.setVisibility(View.GONE);
        panel.addView(overlayTools);
        return panel;
    }

    private View buildActionBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(10), dp(8), dp(10), dp(14));
        bar.setBackgroundColor(BG);

        TextView add = bigButton(t("➕  Add", "➕  जोड़ें"), CARD, INK);
        add.setOnClickListener(v -> pickMedia());
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, dp(62), 1f);
        lp1.setMargins(dp(4), 0, dp(4), 0);
        bar.addView(add, lp1);

        TextView make = bigButton(t("🎉  Make Video", "🎉  वीडियो बनाएं"), ACCENT, Color.WHITE);
        make.setOnClickListener(v -> makeVideo());
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, dp(62), 1.35f);
        lp2.setMargins(dp(4), 0, dp(4), 0);
        bar.addView(make, lp2);
        return bar;
    }

    /* --------------------------------------------------------------- helpers */

    private String t(String en, String hi) {
        return hindi ? hi : en;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private LinearLayout.LayoutParams spaced(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, topMargin, 0, 0);
        return lp;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        if (strokeColor != 0) {
            d.setStroke(dp(2), strokeColor);
        }
        return d;
    }

    private TextView bigButton(String label, int background, int textColor) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(18f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(textColor);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(14), dp(14), dp(14), dp(14));
        tv.setBackground(round(background, 18, background == CARD ? LINE : 0));
        tv.setClickable(true);
        return tv;
    }

    private TextView tool(String emoji, String label, boolean danger, View.OnClickListener click) {
        TextView tv = new TextView(this);
        tv.setText(emoji + "\n" + label);
        tv.setTextSize(12f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(danger ? 0xFFB32222 : INK);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(round(CARD, 14, danger ? 0xFFF3CFC7 : LINE));
        tv.setClickable(true);
        tv.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(58), 1f);
        lp.setMargins(dp(2), dp(3), dp(2), dp(3));
        tv.setLayoutParams(lp);
        return tv;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private Clip current() {
        return sel >= 0 && sel < clips.size() ? clips.get(sel) : null;
    }

    /* ---------------------------------------------------------------- refresh */

    private void refresh() {
        boolean has = !clips.isEmpty();
        emptyPanel.setVisibility(has ? View.GONE : View.VISIBLE);
        editorPanel.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) {
            if (sel >= clips.size()) {
                sel = clips.size() - 1;
            }
            if (sel < 0) {
                sel = 0;
            }
        }
        rebuildTimeline();
        updateSpeedLabel();
        updateOverlayTools();
        preview.setClip(current());
        preview.setSelected(selOverlay);
    }

    private void rebuildTimeline() {
        timelineRow.removeAllViews();
        for (int i = 0; i < clips.size(); i++) {
            final int index = i;
            Clip c = clips.get(i);
            FrameLayout item = new FrameLayout(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(70), dp(70));
            lp.setMargins(dp(4), dp(2), dp(4), dp(2));
            item.setLayoutParams(lp);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF111111);
            bg.setCornerRadius(dp(14));
            if (i == sel) {
                bg.setStroke(dp(3), ACCENT);
            }
            item.setBackground(bg);

            if (c.thumb != null) {
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageBitmap(c.thumb);
                item.addView(iv, new FrameLayout.LayoutParams(MATCH, MATCH));
            }

            TextView badge = new TextView(this);
            badge.setText((c.kind == Clip.VIDEO ? "🎥 " : "") + seconds(c.durationMs));
            badge.setTextSize(10f);
            badge.setTextColor(Color.WHITE);
            badge.setBackground(round(0xB3000000, 8, 0));
            badge.setPadding(dp(4), 0, dp(4), dp(1));
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(WRAP, WRAP,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            blp.setMargins(0, 0, 0, dp(3));
            item.addView(badge, blp);

            item.setClickable(true);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (busy) {
                        return;
                    }
                    sel = index;
                    selOverlay = null;
                    refresh();
                }
            });
            timelineRow.addView(item);
        }
    }

    private void updateSpeedLabel() {
        if (speedLabel == null) {
            return;
        }
        Clip c = current();
        speedLabel.setText("⏱️\n" + (c == null ? "—" : seconds(c.durationMs)));
    }

    private void updateOverlayTools() {
        if (overlayTools == null) {
            return;
        }
        overlayTools.setVisibility(selOverlay != null ? View.VISIBLE : View.GONE);
    }

    private String seconds(long ms) {
        long s = Math.round(ms / 1000.0);
        return s < 60 ? s + "s" : (s / 60) + ":" + String.format("%02d", s % 60);
    }

    /* ------------------------------------------------------------- clip edits */

    private void changeDuration() {
        Clip c = current();
        if (c == null) {
            return;
        }
        if (c.kind == Clip.VIDEO) {
            toast(t("A video keeps its own length", "वीडियो की लंबाई वैसी ही रहेगी"));
            return;
        }
        long[] options = {2000, 3000, 5000, 8000};
        int idx = 1;
        for (int i = 0; i < options.length; i++) {
            if (options[i] == c.durationMs) {
                idx = i;
                break;
            }
        }
        c.durationMs = options[(idx + 1) % options.length];
        updateSpeedLabel();
        rebuildTimeline();
    }

    private void move(int delta) {
        int target = sel + delta;
        if (sel < 0 || target < 0 || target >= clips.size()) {
            return;
        }
        Clip a = clips.get(sel);
        clips.set(sel, clips.get(target));
        clips.set(target, a);
        sel = target;
        refresh();
    }

    private void removeClip() {
        if (current() == null) {
            return;
        }
        clips.remove(sel);
        selOverlay = null;
        if (sel >= clips.size()) {
            sel = clips.size() - 1;
        }
        refresh();
    }

    private void resize(float factor) {
        if (selOverlay == null) {
            return;
        }
        selOverlay.size = Math.max(0.03f, Math.min(0.5f, selOverlay.size * factor));
        preview.invalidate();
    }

    private void removeOverlay() {
        Clip c = current();
        if (c == null || selOverlay == null) {
            return;
        }
        c.overlays.remove(selOverlay);
        selOverlay = null;
        updateOverlayTools();
        preview.setSelected(null);
        preview.invalidate();
    }

    /* ------------------------------------------------------------ text dialog */

    private void addText() {
        final Clip c = current();
        if (c == null) {
            toast(t("First add some photos or videos", "पहले कुछ फ़ोटो या वीडियो जोड़ें"));
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(12), dp(18), dp(8));

        final EditText input = new EditText(this);
        input.setHint(t("Type here…", "यहाँ लिखें…"));
        input.setTextSize(19f);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackground(round(CARD, 14, LINE));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        box.addView(input);

        String[] phrases = hindi
                ? new String[]{"मेरे यूट्यूब को सब्सक्राइब करें ❤️", "लाइक और शेयर करें 🙏",
                "जन्मदिन मुबारक 🎂", "नमस्ते 🙏", "धन्यवाद 🙏", "आप सबसे प्यार ❤️"}
                : new String[]{"Subscribe to my YouTube ❤️", "Like & Share 🙏",
                "Happy Birthday 🎂", "नमस्ते 🙏", "Thank you 🙏", "Love you all ❤️"};

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(1);
        for (final String phrase : phrases) {
            TextView chip = new TextView(this);
            chip.setText(phrase);
            chip.setTextSize(15f);
            chip.setTextColor(INK);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setBackground(round(CARD, 14, LINE));
            chip.setPadding(dp(12), dp(10), dp(12), dp(10));
            chip.setClickable(true);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    input.setText(phrase);
                }
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(MATCH, WRAP);
            clp.setMargins(0, dp(6), 0, 0);
            grid.addView(chip, clp);
        }
        box.addView(grid);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(t("Add text", "टेक्स्ट जोड़ें"))
                .setView(scroll(box))
                .setNegativeButton(t("Cancel", "रद्द करें"), null)
                .setPositiveButton(t("Put on video", "वीडियो पर लगाएं"), null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String text = input.getText().toString().trim();
                    if (text.isEmpty()) {
                        toast(t("Please type something first", "पहले कुछ लिखें"));
                        return;
                    }
                    Overlay o = new Overlay(Overlay.TEXT, text, Color.WHITE, 0.5f, 0.8f, 0.062f);
                    c.overlays.add(o);
                    selOverlay = o;
                    dialog.dismiss();
                    updateOverlayTools();
                    preview.setSelected(o);
                    preview.invalidate();
                    rebuildTimeline();
                }));
        dialog.show();
    }

    /* --------------------------------------------------------- sticker dialog */

    private void addSticker() {
        final Clip c = current();
        if (c == null) {
            toast(t("First add some photos or videos", "पहले कुछ फ़ोटो या वीडियो जोड़ें"));
            return;
        }
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(t("Add a sticker", "स्टिकर लगाएं"))
                .setNegativeButton(t("Close", "बंद करें"), null)
                .create();

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setPadding(dp(12), dp(12), dp(12), dp(12));

        Object[][] stickers = {
                {"SUBSCRIBE", 0xFFFF0000, "badge"},
                {"LIKE & SHARE", ACCENT, "badge"},
                {"FOLLOW ME", 0xFF8A2BE2, "badge"},
                {"THANK YOU", 0xFF1E9E55, "badge"},
                {"❤️", 0, "emoji"}, {"😊", 0, "emoji"}, {"👍", 0, "emoji"}, {"⭐", 0, "emoji"},
                {"🎉", 0, "emoji"}, {"🔔", 0, "emoji"}, {"🌸", 0, "emoji"}, {"🙏", 0, "emoji"},
                {"👵", 0, "emoji"}, {"🕉️", 0, "emoji"}, {"💐", 0, "emoji"}, {"🔥", 0, "emoji"}
        };

        for (Object[] s : stickers) {
            final String text = (String) s[0];
            final int color = (Integer) s[1];
            final boolean badge = "badge".equals(s[2]);

            TextView cell = new TextView(this);
            cell.setText(text);
            cell.setGravity(Gravity.CENTER);
            cell.setTypeface(Typeface.DEFAULT_BOLD);
            cell.setTextSize(badge ? 10f : 26f);
            if (badge) {
                cell.setTextColor(Color.WHITE);
                cell.setBackground(round(color, 10, 0));
            } else {
                cell.setTextColor(INK);
                cell.setBackground(round(CARD, 14, LINE));
            }
            cell.setPadding(dp(4), dp(12), dp(4), dp(12));
            cell.setClickable(true);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(66);
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            cell.setLayoutParams(lp);

            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Overlay o = badge
                            ? new Overlay(Overlay.BADGE, text, color, 0.5f, 0.72f, 0.045f)
                            : new Overlay(Overlay.EMOJI, text, Color.WHITE, 0.5f, 0.35f, 0.13f);
                    c.overlays.add(o);
                    selOverlay = o;
                    dialog.dismiss();
                    updateOverlayTools();
                    preview.setSelected(o);
                    preview.invalidate();
                    rebuildTimeline();
                }
            });
            grid.addView(cell);
        }

        dialog.setView(scroll(grid));
        dialog.show();
    }

    private View scroll(View content) {
        ScrollView sc = new ScrollView(this);
        sc.addView(content);
        return sc;
    }

    /* ------------------------------------------------------------ pick & load */

    private void pickMedia() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(Intent.createChooser(intent,
                    t("Choose photos or videos", "फ़ोटो या वीडियो चुनें")), REQ_PICK);
        } catch (Exception e) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.addCategory(Intent.CATEGORY_OPENABLE);
            fallback.setType("*/*");
            fallback.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
            fallback.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            try {
                startActivityForResult(Intent.createChooser(fallback,
                        t("Choose photos or videos", "फ़ोटो या वीडियो चुनें")), REQ_PICK);
            } catch (Exception e2) {
                toast(t("No gallery app found", "कोई गैलरी ऐप नहीं मिला"));
            }
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_PICK) {
            if (res != RESULT_OK || data == null) {
                return;
            }
            final List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    uris.add(data.getClipData().getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty()) {
                for (Uri u : uris) {
                    try {
                        getContentResolver().takePersistableUriPermission(u,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception ignored) {
                    }
                }
                loadClips(uris);
            }
            return;
        }
        super.onActivityResult(req, res, data);
    }

    private void loadClips(final List<Uri> uris) {
        showBusy(t("Adding your photos…", "फ़ोटो जोड़ी जा रही हैं…"));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Clip> loaded = new ArrayList<>();
                for (Uri u : uris) {
                    try {
                        Clip c = loadOne(u);
                        if (c != null) {
                            loaded.add(c);
                        }
                    } catch (Exception ignored) {
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        hideBusy();
                        if (loaded.isEmpty()) {
                            toast(t("Those files could not be opened", "ये फ़ाइलें नहीं खुल सकीं"));
                            return;
                        }
                        clips.addAll(loaded);
                        sel = clips.size() - 1;
                        selOverlay = null;
                        refresh();
                    }
                });
            }
        }).start();
    }

    private Clip loadOne(Uri uri) throws Exception {
        String type = getContentResolver().getType(uri);
        boolean isVideo = type != null && type.startsWith("video/");
        if (type == null) {
            String s = uri.toString().toLowerCase();
            isVideo = s.contains(".mp4") || s.contains(".mov") || s.contains(".webm")
                    || s.contains(".3gp") || s.contains(".mkv") || s.contains(".m4v");
        }

        if (isVideo) {
            Clip c = new Clip(Clip.VIDEO, uri);
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            try {
                mmr.setDataSource(this, uri);
                String dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                String rot = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                c.videoDurationMs = dur == null ? 3000L : Long.parseLong(dur);
                c.videoRotation = rot == null ? 0 : Integer.parseInt(rot);
            } finally {
                mmr.release();
            }
            if (c.videoDurationMs <= 0) {
                c.videoDurationMs = 3000L;
            }
            c.durationMs = Math.max(1000L, Math.min(c.videoDurationMs, 60000L));
            Bitmap first = VideoExporter.firstFrame(this, c);
            if (first == null) {
                first = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888);
                Canvas cv = new Canvas(first);
                cv.drawColor(0xFF332211);
            }
            c.image = first;
            c.thumb = makeThumb(first, c.videoRotation);
            return c;
        }

        Bitmap bmp = decodeImage(uri);
        if (bmp == null) {
            return null;
        }
        Clip c = new Clip(Clip.PHOTO, uri);
        c.image = bmp;
        c.thumb = makeThumb(bmp, 0);
        c.durationMs = 3000L;
        return c;
    }

    private Bitmap decodeImage(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            InputStream in = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(in, null, bounds);
            if (in != null) {
                in.close();
            }
            int sample = 1;
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            while (longest / sample > 1600) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            in = getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(in, null, opts);
            if (in != null) {
                in.close();
            }
            return bmp;
        } catch (Throwable t) {
            return null;
        }
    }

    private Bitmap makeThumb(Bitmap source, int rotation) {
        int size = 160;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(0xFF111111);
        boolean rot = rotation == 90 || rotation == 270;
        float srcW = rot ? source.getHeight() : source.getWidth();
        float srcH = rot ? source.getWidth() : source.getHeight();
        float scale = Math.max(size / srcW, size / srcH);
        float dw = srcW * scale;
        float dh = srcH * scale;

        int save = c.save();
        if (rotation != 0) {
            c.rotate(rotation, size / 2f, size / 2f);
        }
        float boxW = rot ? dh : dw;
        float boxH = rot ? dw : dh;
        android.graphics.RectF dst = new android.graphics.RectF(
                (size - boxW) / 2f, (size - boxH) / 2f,
                (size + boxW) / 2f, (size + boxH) / 2f);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setFilterBitmap(true);
        c.drawBitmap(source, null, dst, p);
        c.restoreToCount(save);
        return out;
    }

    /* ------------------------------------------------------------- busy dialog */

    private void showBusy(String message) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(24), dp(24), dp(24), dp(24));
        ProgressBar bar = new ProgressBar(this);
        bar.setIndeterminate(true);
        box.addView(bar);
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextSize(17f);
        tv.setTextColor(INK);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(10), 0, 0);
        box.addView(tv);
        busyDialog = new AlertDialog.Builder(this)
                .setView(box)
                .setCancelable(false)
                .create();
        busyDialog.show();
    }

    private void hideBusy() {
        if (busyDialog != null) {
            busyDialog.dismiss();
            busyDialog = null;
        }
    }

    /* ---------------------------------------------------------------- export */

    private void makeVideo() {
        if (busy) {
            return;
        }
        if (clips.isEmpty()) {
            toast(t("First add some photos or videos", "पहले कुछ फ़ोटो या वीडियो जोड़ें"));
            return;
        }
        long total = 0;
        for (Clip c : clips) {
            total += c.durationMs;
        }
        if (total > 5 * 60 * 1000L) {
            toast(t("Please keep the movie under 5 minutes", "वीडियो 5 मिनट से छोटा रखें"));
            return;
        }

        busy = true;
        selOverlay = null;
        updateOverlayTools();

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(24), dp(22), dp(24), dp(22));

        TextView title = new TextView(this);
        title.setText(t("Making your video…", "आपका वीडियो बन रहा है…"));
        title.setTextSize(19f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(INK);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        final ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(MATCH, dp(18));
        blp.setMargins(0, dp(14), 0, dp(8));
        box.addView(bar, blp);

        final TextView pct = new TextView(this);
        pct.setText("0%");
        pct.setTextSize(17f);
        pct.setTextColor(MUTED);
        pct.setGravity(Gravity.CENTER);
        box.addView(pct);

        TextView note = new TextView(this);
        note.setText(t("Please wait, do not close the app", "कृपया रुकें, ऐप बंद न करें"));
        note.setTextSize(14f);
        note.setTextColor(MUTED);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(8), 0, 0);
        box.addView(note);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(box)
                .setCancelable(false)
                .create();
        dialog.show();

        final List<Clip> snapshot = new ArrayList<>(clips);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final File temp = new File(getCacheDir(),
                        "movie_" + System.currentTimeMillis() + ".mp4");
                try {
                    VideoExporter.export(MainActivity.this, snapshot, temp,
                            new VideoExporter.Listener() {
                                @Override
                                public void onProgress(final int percent) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            bar.setProgress(percent);
                                            pct.setText(percent + "%");
                                        }
                                    });
                                }
                            });

                    String name = "MyVideo_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",
                            java.util.Locale.US).format(new java.util.Date()) + ".mp4";
                    final Uri uri = MediaSaver.saveToGallery(MainActivity.this, temp, name,
                            "video/mp4");
                    temp.delete();
                    lastSaved = uri;
                    lastSavedMime = "video/mp4";

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dialog.dismiss();
                            busy = false;
                            showResult();
                        }
                    });
                } catch (final Throwable e) {
                    temp.delete();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dialog.dismiss();
                            busy = false;
                            toast(t("Something went wrong: ", "कुछ गड़बड़ हुई: ")
                                    + (e.getMessage() == null ? e.getClass().getSimpleName()
                                    : e.getMessage()));
                        }
                    });
                }
            }
        }).start();
    }

    private void showResult() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(20), dp(18), dp(20), dp(18));

        TextView emoji = new TextView(this);
        emoji.setText("✅");
        emoji.setTextSize(44f);
        emoji.setGravity(Gravity.CENTER);
        box.addView(emoji);

        TextView title = new TextView(this);
        title.setText(t("Video saved!", "वीडियो सेव हो गया!"));
        title.setTextSize(21f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(INK);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        TextView hint = new TextView(this);
        hint.setText(t("You can find it in Gallery → Movies → SimpleVideoMaker",
                "यह Gallery → Movies → SimpleVideoMaker में मिलेगा"));
        hint.setTextSize(15f);
        hint.setTextColor(MUTED);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(6), 0, dp(10));
        box.addView(hint);

        final AlertDialog[] holder = new AlertDialog[1];

        TextView watch = bigButton(t("▶️  Watch", "▶️  देखें"), CARD, INK);
        watch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                holder[0].dismiss();
                if (lastSaved != null) {
                    MediaSaver.watch(MainActivity.this, lastSaved, lastSavedMime);
                }
            }
        });
        box.addView(watch, spaced(dp(6)));

        TextView share = bigButton(t("📤  Share", "📤  भेजें"), ACCENT, Color.WHITE);
        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (lastSaved != null) {
                    MediaSaver.share(MainActivity.this, lastSaved, lastSavedMime);
                }
            }
        });
        box.addView(share, spaced(dp(8)));

        TextView close = bigButton(t("Close", "बंद करें"), CARD, INK);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                holder[0].dismiss();
            }
        });
        box.addView(close, spaced(dp(8)));

        holder[0] = new AlertDialog.Builder(this).setView(scroll(box)).create();
        holder[0].show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideBusy();
    }
}
