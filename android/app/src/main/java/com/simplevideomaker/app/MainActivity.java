package com.simplevideomaker.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
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
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple Video Maker — native Android photo/video editor for first-time smartphone users.
 * No WebView: preview, stickers, drafts and the exported movie are all made by the app itself.
 */
public class MainActivity extends Activity {

    private static final int REQ_PICK = 1001;
    private static final int REQ_PERM = 1002;
    private static final int REQ_MUSIC = 1003;

    private static final int ACCENT = 0xFFE23E3E;
    private static final int BG = 0xFFFFF6F0;
    private static final int CARD = 0xFFFFFFFF;
    private static final int LINE = 0xFFEADFD7;
    private static final int INK = 0xFF2A2320;
    private static final int MUTED = 0xFF7A6C64;

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final int SCREEN_HOME = 0;
    private static final int SCREEN_EDITOR = 1;

    private int screen = SCREEN_HOME;
    private boolean hindi = false;
    private boolean busy = false;
    private boolean moreTools = false;

    private Project project;
    private int sel = -1;
    private Overlay selOverlay;
    private Uri lastSaved;
    private final String lastSavedMime = "video/mp4";

    private LinearLayout root;
    private FrameLayout seat;
    private View emptyPanel;
    private LinearLayout editorPanel;
    private EditorView preview;
    private LinearLayout timelineRow;
    private LinearLayout moreRow;
    private LinearLayout overlayTools;
    private TextView speedLabel;
    private TextView nameLabel;

    private AlertDialog busyDialog;

    private interface Picker {
        void onPick(int index);
    }

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
        screen = SCREEN_HOME;
        buildUi();
    }

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(true);

        if (screen == SCREEN_HOME) {
            root.addView(buildHeader(null));
            root.addView(buildHome(), new LinearLayout.LayoutParams(MATCH, 0, 1f));
            root.addView(buildHomeBar());
        } else {
            root.addView(buildHeader(() -> {
                saveCurrent();
                screen = SCREEN_HOME;
                buildUi();
            }));
            seat = new FrameLayout(this);
            LinearLayout.LayoutParams seatLp = new LinearLayout.LayoutParams(MATCH, 0, 1f);
            seat.setPadding(dp(10), dp(10), dp(10), 0);
            root.addView(seat, seatLp);
            emptyPanel = buildEmptyPanel();
            editorPanel = buildEditorPanel();
            seat.addView(emptyPanel, new FrameLayout.LayoutParams(MATCH, MATCH));
            seat.addView(editorPanel, new FrameLayout.LayoutParams(MATCH, MATCH));
            root.addView(buildEditorBar());
        }
        setContentView(root);
        refresh();
    }

    @Override
    public void onBackPressed() {
        if (screen == SCREEN_EDITOR) {
            saveCurrent();
            screen = SCREEN_HOME;
            buildUi();
            return;
        }
        super.onBackPressed();
    }

    /* ---------------------------------------------------------------- header */

    private View buildHeader(Runnable back) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ACCENT, 0xFFFF8A3D}));
        bar.setPadding(dp(10), dp(10), dp(10), dp(10));

        if (back != null) {
            TextView b = new TextView(this);
            b.setText("⬅️");
            b.setTextSize(20f);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(8), dp(2), dp(10), dp(2));
            b.setClickable(true);
            b.setOnClickListener(v -> back.run());
            bar.addView(b);
        }

        nameLabel = new TextView(this);
        nameLabel.setText(back == null
                ? "🎬  " + t("My Videos", "मेरे वीडियो")
                : (project == null ? t("Project", "प्रोजेक्ट") : project.name));
        nameLabel.setTextColor(Color.WHITE);
        nameLabel.setTextSize(back == null ? 19f : 17f);
        nameLabel.setTypeface(Typeface.DEFAULT_BOLD);
        nameLabel.setSingleLine(true);
        nameLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        bar.addView(nameLabel, tlp);
        if (back != null) {
            nameLabel.setClickable(true);
            nameLabel.setOnClickListener(v -> renameProject());
        }

        TextView lang = new TextView(this);
        lang.setText(hindi ? "EN" : "हिं");
        lang.setTextColor(Color.WHITE);
        lang.setTextSize(15f);
        lang.setTypeface(Typeface.DEFAULT_BOLD);
        lang.setGravity(Gravity.CENTER);
        GradientDrawable lb = new GradientDrawable();
        lb.setColor(0x33FFFFFF);
        lb.setCornerRadius(dp(20));
        lb.setStroke(dp(2), 0xB3FFFFFF);
        lang.setBackground(lb);
        lang.setPadding(dp(12), dp(6), dp(12), dp(6));
        lang.setClickable(true);
        lang.setOnClickListener(v -> {
            hindi = !hindi;
            buildUi();
        });
        bar.addView(lang);
        return bar;
    }

    /* ------------------------------------------------------------------ home */

    private View buildHome() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(20));
        scroll.addView(box);

        List<Project> projects = ProjectStore.list(this);

        if (projects.isEmpty()) {
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(8), dp(40), dp(8), dp(20));
            TextView hero = new TextView(this);
            hero.setText("👵🎬");
            hero.setTextSize(52f);
            hero.setGravity(Gravity.CENTER);
            empty.addView(hero);

            TextView title = new TextView(this);
            title.setText(t("Make a video in 3 easy steps", "3 आसान कदमों में वीडियो बनाएं"));
            title.setTextSize(21f);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(INK);
            title.setGravity(Gravity.CENTER);
            empty.addView(title, spaced(dp(8)));

            empty.addView(stepCard("1", t("Add your photos and videos", "अपनी फ़ोटो और वीडियो जोड़ें")));
            empty.addView(stepCard("2", t("Add text, stickers and music",
                    "टेक्स्ट, स्टिकर और संगीत जोड़ें")));
            empty.addView(stepCard("3", t("Press “Make Video”", "“वीडियो बनाएं” दबाएं")));
            box.addView(empty);
        } else {
            TextView title = new TextView(this);
            title.setText(t("Your drafts", "आपके ड्राफ्ट"));
            title.setTextSize(18f);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(MUTED);
            title.setPadding(dp(4), dp(2), 0, dp(8));
            box.addView(title);

            for (final Project p : projects) {
                box.addView(projectCard(p));
            }
        }
        return scroll;
    }

    private View projectCard(final Project p) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(round(CARD, 18, LINE));
        card.setPadding(dp(10), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, dp(5), 0, dp(5));
        card.setLayoutParams(lp);

        FrameLayout thumbBox = new FrameLayout(this);
        GradientDrawable tb = new GradientDrawable();
        tb.setColor(0xFF111111);
        tb.setCornerRadius(dp(12));
        thumbBox.setBackground(tb);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap thumb = decodeBase64(p.thumb);
        if (thumb != null) {
            iv.setImageBitmap(thumb);
        } else {
            iv.setBackgroundColor(0xFF2A2320);
        }
        thumbBox.addView(iv, new FrameLayout.LayoutParams(dp(64), dp(64)));
        card.addView(thumbBox, new LinearLayout.LayoutParams(dp(64), dp(64)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(12), 0, 0, 0);
        TextView name = new TextView(this);
        name.setText(p.name == null || p.name.isEmpty()
                ? t("Untitled video", "बिना नाम") : p.name);
        name.setTextSize(17f);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(INK);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(name);

        TextView meta = new TextView(this);
        meta.setText(p.summary() + (p.musicUri != null
                ? (hindi ? " · 🎵 संगीत" : " · 🎵 music") : ""));
        meta.setTextSize(14f);
        meta.setTextColor(MUTED);
        info.addView(meta);
        card.addView(info, new LinearLayout.LayoutParams(0, WRAP, 1f));

        TextView open = new TextView(this);
        open.setText(t("Open", "खोलें"));
        open.setTextSize(14f);
        open.setTypeface(Typeface.DEFAULT_BOLD);
        open.setTextColor(Color.WHITE);
        open.setGravity(Gravity.CENTER);
        open.setBackground(round(ACCENT, 14, 0));
        open.setPadding(dp(12), dp(9), dp(12), dp(9));
        open.setClickable(true);
        open.setOnClickListener(v -> openProject(p));
        card.addView(open);

        card.setClickable(true);
        card.setOnLongClickListener(v -> {
            projectMenu(p);
            return true;
        });
        return card;
    }

    private View buildHomeBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(dp(12), dp(8), dp(12), dp(14));
        TextView add = bigButton(t("➕  New Video", "➕  नया वीडियो"), ACCENT, Color.WHITE);
        add.setOnClickListener(v -> newProject());
        bar.addView(add, new LinearLayout.LayoutParams(MATCH, dp(62)));
        return bar;
    }

    private void newProject() {
        project = new Project();
        project.name = t("My Video ", "मेरा वीडियो ") + (ProjectStore.list(this).size() + 1);
        sel = -1;
        selOverlay = null;
        screen = SCREEN_EDITOR;
        buildUi();
        pickMedia();
    }

    private void openProject(final Project p) {
        showBusy(t("Opening…", "खोला जा रहा है…"));
        new Thread(() -> {
            final List<String> missing = new ArrayList<>();
            for (Clip c : p.clips) {
                if (c.image != null) {
                    continue;
                }
                try {
                    if (c.isVideo()) {
                        Bitmap first = VideoExporter.firstFrame(this, c);
                        if (first != null) {
                            c.image = first;
                            c.thumb = makeThumb(first, c.videoRotation);
                        }
                    } else {
                        Bitmap bmp = decodeImage(c.uri);
                        if (bmp != null) {
                            c.image = bmp;
                            c.thumb = makeThumb(bmp, 0);
                        }
                    }
                } catch (Throwable ignored) {
                }
                if (c.image == null) {
                    missing.add(c.uriString);
                }
            }
            runOnUiThread(() -> {
                hideBusy();
                if (!missing.isEmpty()) {
                    p.clips.removeIf(c -> c.image == null);
                    toast(t("Some files are no longer available",
                            "कुछ फ़ाइलें अब उपलब्ध नहीं हैं"));
                }
                project = p;
                sel = p.clips.isEmpty() ? -1 : 0;
                selOverlay = null;
                screen = SCREEN_EDITOR;
                buildUi();
            });
        }).start();
    }

    private void projectMenu(final Project p) {
        String[] items = {
                t("Rename", "नाम बदलें"),
                t("Duplicate", "कॉपी बनाएं"),
                t("Delete", "हटाएं")
        };
        new AlertDialog.Builder(this)
                .setTitle(p.name)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        renameOf(p);
                    } else if (which == 1) {
                        ProjectStore.duplicate(this, p);
                        buildUi();
                    } else {
                        confirmDelete(p);
                    }
                })
                .show();
    }

    private void confirmDelete(final Project p) {
        new AlertDialog.Builder(this)
                .setTitle(t("Delete this video?", "यह वीडियो हटाएं?"))
                .setMessage(t("The draft will be removed. Your photos stay in the gallery.",
                        "ड्राफ्ट हट जाएगा। आपकी फ़ोटो गैलरी में सुरक्षित रहेंगी।"))
                .setNegativeButton(t("Cancel", "रद्द करें"), null)
                .setPositiveButton(t("Delete", "हटाएं"), (d, w) -> {
                    ProjectStore.delete(this, p.id);
                    buildUi();
                })
                .show();
    }

    private void renameOf(final Project p) {
        final EditText input = new EditText(this);
        input.setText(p.name);
        input.setSelectAllOnFocus(true);
        input.setTextSize(18f);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(16), dp(8), dp(16), dp(8));
        wrap.addView(input);
        new AlertDialog.Builder(this)
                .setTitle(t("Name of the video", "वीडियो का नाम"))
                .setView(wrap)
                .setNegativeButton(t("Cancel", "रद्द करें"), null)
                .setPositiveButton(t("Save", "सेव"), (d, w) -> {
                    String name = input.getText().toString().trim();
                    p.name = name.isEmpty() ? t("Untitled video", "बिना नाम") : name;
                    ProjectStore.save(this, p);
                    buildUi();
                })
                .show();
    }

    private void renameProject() {
        if (project != null) {
            renameOf(project);
        }
    }

    /* ---------------------------------------------------------------- editor */

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
        title.setText(t("Add photos and videos to begin", "शुरू करने के लिए फ़ोटो और वीडियो जोड़ें"));
        title.setTextSize(19f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(INK);
        title.setGravity(Gravity.CENTER);
        box.addView(title, spaced(dp(8)));

        TextView add = bigButton(t("➕  Add Photos & Videos", "➕  फ़ोटो और वीडियो जोड़ें"),
                ACCENT, Color.WHITE);
        add.setOnClickListener(v -> pickMedia());
        box.addView(add, spaced(dp(18)));
        return box;
    }

    private LinearLayout buildEditorPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);

        FrameLayout previewBox = new FrameLayout(this);
        GradientDrawable pb = new GradientDrawable();
        pb.setColor(0xFF1B1512);
        pb.setCornerRadius(dp(18));
        previewBox.setBackground(pb);
        preview = new EditorView(this);
        preview.setListener(o -> {
            selOverlay = o;
            updateOverlayTools();
        });
        previewBox.addView(preview, new FrameLayout.LayoutParams(MATCH, MATCH));
        panel.addView(previewBox, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(MATCH, dp(84)));
        timelineRow = new LinearLayout(this);
        timelineRow.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(timelineRow, new HorizontalScrollView.LayoutParams(WRAP, MATCH));
        panel.addView(scroll);

        LinearLayout toolsA = new LinearLayout(this);
        toolsA.setOrientation(LinearLayout.HORIZONTAL);
        toolsA.addView(tool("🔤", t("Text", "लिखें"), false, v -> addText()));
        toolsA.addView(tool("😊", t("Sticker", "स्टिकर"), false, v -> addSticker()));
        toolsA.addView(tool("🎵", t("Music", "संगीत"), false, v -> musicDialog()));
        toolsA.addView(tool("✨", t("Effect", "इफ़ेक्ट"), false, v -> transitionDialog()));
        toolsA.addView(tool("🎨", t("Look", "रंग"), false, v -> filterDialog()));
        toolsA.addView(tool("⋯", t("More", "और"), false, v -> {
            moreTools = !moreTools;
            moreRow.setVisibility(moreTools ? View.VISIBLE : View.GONE);
        }));
        panel.addView(toolsA);

        moreRow = new LinearLayout(this);
        moreRow.setOrientation(LinearLayout.VERTICAL);
        moreRow.setVisibility(View.GONE);

        LinearLayout toolsB = new LinearLayout(this);
        toolsB.setOrientation(LinearLayout.HORIZONTAL);
        toolsB.addView(tool("⏩", t("Speed", "गति"), false, v -> speedDialog()));
        toolsB.addView(tool("✂️", t("Split", "काटें"), false, v -> splitClip()));
        speedLabel = tool("⏱️", "3s", false, v -> changeDuration());
        toolsB.addView(speedLabel);
        toolsB.addView(tool("⬅️", t("Left", "पीछे"), false, v -> move(-1)));
        toolsB.addView(tool("➡️", t("Right", "आगे"), false, v -> move(1)));
        toolsB.addView(tool("🔀", t("Copy", "कॉपी"), false, v -> duplicateClip()));
        moreRow.addView(toolsB);

        LinearLayout toolsC = new LinearLayout(this);
        toolsC.setOrientation(LinearLayout.HORIZONTAL);
        toolsC.addView(tool("🔊", t("Sound", "आवाज़"), false, v -> toggleSound()));
        toolsC.addView(tool("🗑️", t("Remove", "हटाएं"), true, v -> removeClip()));
        toolsC.addView(tool("✔️", t("Save", "सेव"), false, v -> {
            saveCurrent();
            toast(t("Draft saved ✔️", "ड्राफ्ट सेव हो गया ✔️"));
        }));
        moreRow.addView(toolsC);
        panel.addView(moreRow);

        overlayTools = new LinearLayout(this);
        overlayTools.setOrientation(LinearLayout.HORIZONTAL);
        overlayTools.addView(tool("➕", t("Bigger", "बड़ा"), false, v -> resize(1.18f)));
        overlayTools.addView(tool("➖", t("Smaller", "छोटा"), false, v -> resize(1f / 1.18f)));
        overlayTools.addView(tool("✨", t("Glow", "चमक"), false, v -> toggleGlow()));
        overlayTools.addView(tool("🗑️", t("Remove", "हटाएं"), true, v -> removeOverlay()));
        overlayTools.addView(tool("✔️", t("Done", "ठीक है"), false, v -> {
            selOverlay = null;
            updateOverlayTools();
        }));
        overlayTools.setVisibility(View.GONE);
        panel.addView(overlayTools);
        return panel;
    }

    private View buildEditorBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(8), dp(8), dp(8), dp(14));
        bar.setBackgroundColor(BG);

        TextView add = bigButton(t("➕", "➕"), CARD, INK);
        add.setOnClickListener(v -> pickMedia());
        bar.addView(add, weighted(0.7f));

        TextView play = bigButton(t("▶️  Preview", "▶️  देखें"), CARD, INK);
        play.setOnClickListener(v -> previewVideo());
        bar.addView(play, weighted(1.15f));

        TextView make = bigButton(t("🎉  Make Video", "🎉  वीडियो बनाएं"), ACCENT, Color.WHITE);
        make.setOnClickListener(v -> makeVideo());
        bar.addView(make, weighted(1.25f));
        return bar;
    }

    private LinearLayout.LayoutParams weighted(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(62), weight);
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
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
        tv.setTextSize(16.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(textColor);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(10), dp(12), dp(10), dp(12));
        tv.setBackground(round(background, 18, background == CARD ? LINE : 0));
        tv.setClickable(true);
        return tv;
    }

    private TextView tool(String emoji, String label, boolean danger, View.OnClickListener click) {
        TextView tv = new TextView(this);
        tv.setText(emoji + "\n" + label);
        tv.setTextSize(11.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(danger ? 0xFFB32222 : INK);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(round(CARD, 14, danger ? 0xFFF3CFC7 : LINE));
        tv.setClickable(true);
        tv.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(56), 1f);
        lp.setMargins(dp(2), dp(3), dp(2), dp(3));
        tv.setLayoutParams(lp);
        return tv;
    }

    private View stepCard(String number, String text) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(round(CARD, 18, LINE));
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, dp(6), 0, 0);
        row.setLayoutParams(lp);

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
        label.setTextSize(16.5f);
        label.setTextColor(INK);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setPadding(dp(12), 0, 0, 0);
        row.addView(label, new LinearLayout.LayoutParams(0, WRAP, 1f));
        return row;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    private Clip current() {
        return project != null && sel >= 0 && sel < project.clips.size()
                ? project.clips.get(sel) : null;
    }

    private Bitmap decodeBase64(String b64) {
        if (b64 == null || b64.isEmpty()) {
            return null;
        }
        try {
            byte[] bytes = Base64.decode(b64, Base64.NO_WRAP);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Throwable t) {
            return null;
        }
    }

    /* ---------------------------------------------------------------- refresh */

    private void refresh() {
        if (screen != SCREEN_EDITOR || project == null) {
            return;
        }
        boolean has = !project.clips.isEmpty();
        emptyPanel.setVisibility(has ? View.GONE : View.VISIBLE);
        editorPanel.setVisibility(has ? View.VISIBLE : View.GONE);
        if (has) {
            if (sel >= project.clips.size()) {
                sel = project.clips.size() - 1;
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
        if (nameLabel != null) {
            nameLabel.setText(project.name);
        }
    }

    private void rebuildTimeline() {
        timelineRow.removeAllViews();
        if (project == null) {
            return;
        }
        for (int i = 0; i < project.clips.size(); i++) {
            final int index = i;
            Clip c = project.clips.get(i);
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

            LinearLayout tags = new LinearLayout(this);
            tags.setOrientation(LinearLayout.HORIZONTAL);
            FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(WRAP, WRAP,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            tlp.setMargins(0, 0, 0, dp(3));
            TextView badge = new TextView(this);
            badge.setText((c.isVideo() ? "🎥" : "🖼") + Project.seconds(c.durationMs));
            badge.setTextSize(10f);
            badge.setTextColor(Color.WHITE);
            badge.setBackground(round(0xB3000000, 8, 0));
            badge.setPadding(dp(4), 0, dp(4), dp(1));
            tags.addView(badge);
            if (c.transition != Clip.T_NONE) {
                TextView tr = new TextView(this);
                tr.setText("✨");
                tr.setTextSize(10f);
                tr.setPadding(dp(3), 0, 0, 0);
                tags.addView(tr);
            }
            item.addView(tags, tlp);

            item.setClickable(true);
            item.setOnClickListener(v -> {
                if (busy) {
                    return;
                }
                sel = index;
                selOverlay = null;
                refresh();
            });
            timelineRow.addView(item);
        }
    }

    private void updateSpeedLabel() {
        if (speedLabel == null) {
            return;
        }
        Clip c = current();
        speedLabel.setText("⏱️\n" + (c == null ? "—" : Project.seconds(c.durationMs)));
    }

    private void updateOverlayTools() {
        if (overlayTools == null) {
            return;
        }
        overlayTools.setVisibility(selOverlay != null ? View.VISIBLE : View.GONE);
    }

    private void saveCurrent() {
        if (project == null) {
            return;
        }
        if (project.clips.isEmpty() && project.musicUri == null) {
            ProjectStore.delete(this, project.id);
            return;
        }
        ProjectStore.save(this, project);
    }

    /* ------------------------------------------------------------ clip edits */

    private void changeDuration() {
        Clip c = current();
        if (c == null) {
            return;
        }
        if (c.isVideo()) {
            toast(t("Use Speed to change a video's length", "वीडियो की लंबाई गति से बदलें"));
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
        saveCurrent();
    }

    private void move(int delta) {
        if (project == null) {
            return;
        }
        int target = sel + delta;
        if (sel < 0 || target < 0 || target >= project.clips.size()) {
            return;
        }
        Clip a = project.clips.get(sel);
        project.clips.set(sel, project.clips.get(target));
        project.clips.set(target, a);
        sel = target;
        refresh();
        saveCurrent();
    }

    private void removeClip() {
        if (current() == null) {
            return;
        }
        project.clips.remove(sel);
        selOverlay = null;
        if (sel >= project.clips.size()) {
            sel = project.clips.size() - 1;
        }
        refresh();
        saveCurrent();
    }

    private void duplicateClip() {
        Clip c = current();
        if (c == null) {
            return;
        }
        try {
            Clip copy = Clip.fromJson(c.toJson());
            copy.image = c.image;
            copy.thumb = c.thumb;
            project.clips.add(sel + 1, copy);
            sel = sel + 1;
            refresh();
            saveCurrent();
        } catch (Throwable ignored) {
        }
    }

    private void splitClip() {
        Clip c = current();
        if (c == null) {
            return;
        }
        try {
            long sourceEnd = c.sourceEndMs();
            Clip second = Clip.fromJson(c.toJson());
            second.thumb = c.thumb;
            long half = Math.max(400, c.durationMs / 2);
            if (c.isVideo()) {
                long sourceHalf = Math.round(half * c.speed);
                c.durationMs = half;
                second.startMs = c.startMs + sourceHalf;
                second.durationMs = Math.max(400,
                        Math.round((sourceEnd - second.startMs) / c.speed));
                second.image = null;
            } else {
                c.durationMs = half;
                second.durationMs = Math.max(400, c.durationMs);
                second.image = c.image;
            }
            project.clips.add(sel + 1, second);
            refresh();
            saveCurrent();
            toast(t("Clip split into two ✂️", "क्लिप दो हिस्सों में कट गई ✂️"));
            if (second.image == null) {
                loadClipAsync(second);
            }
        } catch (Throwable ignored) {
        }
    }

    private void loadClipAsync(final Clip c) {
        new Thread(() -> {
            try {
                Bitmap first = VideoExporter.firstFrame(this, c);
                if (first != null) {
                    c.image = first;
                    c.thumb = makeThumb(first, c.videoRotation);
                    runOnUiThread(this::refresh);
                }
            } catch (Throwable ignored) {
            }
        }).start();
    }

    private void resize(float factor) {
        if (selOverlay == null) {
            return;
        }
        selOverlay.size = Math.max(0.03f, Math.min(0.5f, selOverlay.size * factor));
        preview.invalidate();
    }

    private void toggleGlow() {
        if (selOverlay == null) {
            return;
        }
        selOverlay.glow = !selOverlay.glow;
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
        saveCurrent();
    }

    private void toggleSound() {
        Clip c = current();
        if (c == null) {
            return;
        }
        if (!c.isVideo()) {
            toast(t("Photos have no sound", "फ़ोटो में आवाज़ नहीं होती"));
            return;
        }
        c.volume = c.volume > 0.01f ? 0f : 1f;
        toast(c.volume > 0 ? t("Sound on 🔊", "आवाज़ चालू 🔊") : t("Sound off 🔇", "आवाज़ बंद 🔇"));
        saveCurrent();
    }

    /* ---------------------------------------------------------------- dialogs */

    private void choose(String title, String[] labels, int selected, Picker picker) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));

        ScrollView sc = new ScrollView(this);
        sc.addView(box);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(sc)
                .setNegativeButton(t("Cancel", "रद्द करें"), null)
                .create();

        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView row = new TextView(this);
            row.setText((i == selected ? "✅  " : "      ") + labels[i]);
            row.setTextSize(17f);
            row.setTextColor(INK);
            row.setTypeface(Typeface.DEFAULT_BOLD);
            row.setBackground(round(i == selected ? 0xFFFFEDE6 : CARD, 14, LINE));
            row.setPadding(dp(14), dp(14), dp(14), dp(14));
            row.setClickable(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
            lp.setMargins(0, dp(4), 0, dp(4));
            row.setLayoutParams(lp);
            row.setOnClickListener(v -> {
                dialog.dismiss();
                picker.onPick(index);
            });
            box.addView(row);
        }
        dialog.show();
    }

    private void filterDialog() {
        Clip c = current();
        if (c == null) {
            missingClipsWarning();
            return;
        }
        String[] labels = {
                t("None", "कोई नहीं"), t("Warm", "गरम"), t("Cool", "ठंडा"),
                t("Black & White", "काला-सफ़ेद"), t("Vivid", "चटक"), t("Sepia", "पुराना")
        };
        choose(t("Choose a look", "रंग चुनें"), labels, c.filter, index -> {
            c.filter = index;
            preview.invalidate();
            rebuildTimeline();
            saveCurrent();
        });
    }

    private void transitionDialog() {
        Clip c = current();
        if (c == null) {
            missingClipsWarning();
            return;
        }
        if (sel == 0) {
            toast(t("Effects work from the second clip on",
                    "इफ़ेक्ट दूसरी क्लिप से लगते हैं"));
            return;
        }
        String[] labels = {
                t("None", "कोई नहीं"), t("Fade", "धुंधला"), t("Slide", "सरकना"),
                t("Zoom", "ज़ूम"), t("Circle", "गोल")
        };
        choose(t("How should this clip appear?", "यह क्लिप कैसे आए?"), labels, c.transition,
                index -> {
                    c.transition = index;
                    rebuildTimeline();
                    saveCurrent();
                });
    }

    private void speedDialog() {
        Clip c = current();
        if (c == null) {
            missingClipsWarning();
            return;
        }
        if (!c.isVideo()) {
            changeDuration();
            return;
        }
        float[] speeds = {0.5f, 1f, 1.5f, 2f};
        String[] labels = {"0.5× " + t("slow", "धीमा"), "1× " + t("normal", "सामान्य"),
                "1.5× " + t("fast", "तेज़"), "2× " + t("very fast", "बहुत तेज़")};
        int selected = 1;
        for (int i = 0; i < speeds.length; i++) {
            if (Math.abs(speeds[i] - c.speed) < 0.01f) {
                selected = i;
            }
        }
        choose(t("Video speed", "वीडियो की गति"), labels, selected, index -> {
            c.speed = speeds[index];
            long span = Math.max(400, c.sourceDurationMs - c.startMs);
            c.durationMs = Math.max(400, Math.round(span / c.speed));
            updateSpeedLabel();
            rebuildTimeline();
            saveCurrent();
        });
    }

    private void musicDialog() {
        if (project == null) {
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(t("Music", "संगीत"));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));

        TextView current = new TextView(this);
        current.setText(project.musicUri == null
                ? t("No music yet", "अभी कोई संगीत नहीं")
                : t("Music added 🎵", "संगीत जुड़ गया 🎵"));
        current.setTextSize(16f);
        current.setTextColor(MUTED);
        box.addView(current);

        TextView pick = bigButton(t("🎵  Choose music from phone", "🎵  फ़ोन से संगीत चुनें"),
                CARD, INK);
        final AlertDialog[] holder = new AlertDialog[1];
        pick.setOnClickListener(v -> {
            if (holder[0] != null) {
                holder[0].dismiss();
            }
            pickMusic();
        });
        box.addView(pick, spaced(dp(10)));

        TextView volLabel = new TextView(this);
        volLabel.setText(t("Music volume", "संगीत की आवाज़"));
        volLabel.setTextSize(15f);
        volLabel.setTextColor(MUTED);
        box.addView(volLabel, spaced(dp(14)));

        SeekBar bar = new SeekBar(this);
        bar.setMax(100);
        bar.setProgress((int) (project.musicVolume * 100));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                project.musicVolume = Math.max(0.05f, progress / 100f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveCurrent();
            }
        });
        box.addView(bar);

        if (project.musicUri != null) {
            TextView remove = bigButton(t("Remove music", "संगीत हटाएं"), CARD, 0xFFB32222);
            remove.setOnClickListener(v -> {
                project.musicUri = null;
                project.musicUriString = "";
                saveCurrent();
                if (holder[0] != null) {
                    holder[0].dismiss();
                }
                toast(t("Music removed", "संगीत हटा दिया"));
            });
            box.addView(remove, spaced(dp(10)));
        }

        ScrollView sc = new ScrollView(this);
        sc.addView(box);
        holder[0] = b.setView(sc).setNegativeButton(t("Close", "बंद करें"), null).create();
        holder[0].show();
    }

    private void pickMusic() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        try {
            startActivityForResult(Intent.createChooser(intent,
                    t("Choose music", "संगीत चुनें")), REQ_MUSIC);
        } catch (Exception e) {
            toast(t("No music app found", "कोई संगीत ऐप नहीं मिला"));
        }
    }

    private void missingClipsWarning() {
        toast(t("First add some photos or videos", "पहले कुछ फ़ोटो या वीडियो जोड़ें"));
    }

    /* ------------------------------------------------------------ text dialog */

    private void addText() {
        final Clip c = current();
        if (c == null) {
            missingClipsWarning();
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
            chip.setOnClickListener(v -> input.setText(phrase));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(MATCH, WRAP);
            clp.setMargins(0, dp(6), 0, 0);
            grid.addView(chip, clp);
        }
        box.addView(grid);

        TextView colorLabel = new TextView(this);
        colorLabel.setText(t("Colour", "रंग"));
        colorLabel.setTextSize(15f);
        colorLabel.setTextColor(MUTED);
        box.addView(colorLabel, spaced(dp(12)));

        final int[] colors = {Color.WHITE, 0xFFFFE44D, 0xFFFF5252, 0xFF64F0A0, 0xFF9AD8FF};
        final int[] chosen = {Color.WHITE};
        LinearLayout palette = new LinearLayout(this);
        palette.setOrientation(LinearLayout.HORIZONTAL);
        for (final int col : colors) {
            TextView dot = new TextView(this);
            dot.setText("");
            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(col);
            gd.setStroke(dp(2), LINE);
            dot.setBackground(gd);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(38), dp(38));
            dlp.setMargins(dp(4), dp(6), dp(10), dp(6));
            dot.setLayoutParams(dlp);
            dot.setClickable(true);
            dot.setOnClickListener(v -> {
                chosen[0] = col;
                for (int i = 0; i < palette.getChildCount(); i++) {
                    View child = palette.getChildAt(i);
                    GradientDrawable g = new GradientDrawable();
                    g.setShape(GradientDrawable.OVAL);
                    g.setColor(colors[i]);
                    g.setStroke(dp(i == indexOf(colors, col) ? 4 : 2),
                            i == indexOf(colors, col) ? INK : LINE);
                    child.setBackground(g);
                }
            });
            palette.addView(dot);
        }
        box.addView(palette);

        final boolean[] glow = {false};
        TextView glowBtn = bigButton(t("✨  Add glow", "✨  चमक जोड़ें"), CARD, INK);
        glowBtn.setOnClickListener(v -> {
            glow[0] = !glow[0];
            glowBtn.setBackground(round(glow[0] ? 0xFFFFEDE6 : CARD, 18, LINE));
            glowBtn.setText(glow[0] ? t("✨  Glow on", "✨  चमक चालू") : t("✨  Add glow", "✨  चमक जोड़ें"));
        });
        box.addView(glowBtn, spaced(dp(10)));

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
                    Overlay o = new Overlay(Overlay.TEXT, text, chosen[0], 0.5f, 0.78f, 0.06f);
                    o.glow = glow[0];
                    c.overlays.add(o);
                    selOverlay = o;
                    dialog.dismiss();
                    updateOverlayTools();
                    preview.setSelected(o);
                    preview.invalidate();
                    rebuildTimeline();
                    saveCurrent();
                }));
        dialog.show();
    }

    private int indexOf(int[] values, int value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                return i;
            }
        }
        return -1;
    }

    /* --------------------------------------------------------- sticker dialog */

    private void addSticker() {
        final Clip c = current();
        if (c == null) {
            missingClipsWarning();
            return;
        }
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(t("Add a sticker", "स्टिकर लगाएं"))
                .setNegativeButton(t("Close", "बंद करें"), null)
                .create();

        ScrollView sc = new ScrollView(this);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(12), dp(10), dp(12), dp(12));
        sc.addView(column);

        TextView badgesTitle = new TextView(this);
        badgesTitle.setText(t("Buttons", "बैज"));
        badgesTitle.setTextSize(14f);
        badgesTitle.setTextColor(MUTED);
        column.addView(badgesTitle);

        GridLayout badges = new GridLayout(this);
        badges.setColumnCount(2);
        Object[][] badgeData = {
                {"SUBSCRIBE", 0xFFFF0000},
                {"LIKE & SHARE", ACCENT},
                {"FOLLOW ME", 0xFF8A2BE2},
                {"THANK YOU", 0xFF1E9E55},
                {"BELL 🔔", 0xFF1E88E5},
                {"MY VLOG", 0xFFEF6C00}
        };
        for (Object[] b : badgeData) {
            final String text = (String) b[0];
            final int color = (Integer) b[1];
            TextView cell = new TextView(this);
            cell.setText(text);
            cell.setTextColor(Color.WHITE);
            cell.setTextSize(12f);
            cell.setTypeface(Typeface.DEFAULT_BOLD);
            cell.setGravity(Gravity.CENTER);
            cell.setBackground(round(color, 12, 0));
            cell.setPadding(dp(6), dp(14), dp(6), dp(14));
            cell.setClickable(true);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(58);
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(dp(3), dp(4), dp(3), dp(4));
            cell.setLayoutParams(lp);
            cell.setOnClickListener(v -> {
                addStickerTo(c, Overlay.BADGE, text, color);
                dialog.dismiss();
            });
            badges.addView(cell);
        }
        column.addView(badges);

        TextView emojiTitle = new TextView(this);
        emojiTitle.setText(t("Stickers", "स्टिकर"));
        emojiTitle.setTextSize(14f);
        emojiTitle.setTextColor(MUTED);
        column.addView(emojiTitle, spaced(dp(12)));

        GridLayout emojis = new GridLayout(this);
        emojis.setColumnCount(5);
        String[] list = {
                "🦋", "🌸", "🌺", "🌻", "🌈", "⭐", "🌟", "✨", "💫", "🔆",
                "❤️", "💖", "💐", "🎀", "🎉", "😊", "🥰", "👍", "🙏", "👵",
                "🕉️", "🪔", "🦚", "🍀", "🎂", "🔥", "🔔", "😍", "🤗", "💫"
        };
        for (final String e : list) {
            TextView cell = new TextView(this);
            cell.setText(e);
            cell.setTextSize(24f);
            cell.setGravity(Gravity.CENTER);
            cell.setBackground(round(CARD, 14, LINE));
            cell.setClickable(true);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(56);
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            cell.setLayoutParams(lp);
            cell.setOnClickListener(v -> {
                addStickerTo(c, Overlay.EMOJI, e, Color.WHITE);
                dialog.dismiss();
            });
            emojis.addView(cell);
        }
        column.addView(emojis);

        dialog.setView(sc);
        dialog.show();
    }

    private void addStickerTo(Clip c, int type, String text, int color) {
        Overlay o = type == Overlay.BADGE
                ? new Overlay(Overlay.BADGE, text, color, 0.5f, 0.7f, 0.045f)
                : new Overlay(Overlay.EMOJI, text, Color.WHITE, 0.5f, 0.35f, 0.13f);
        c.overlays.add(o);
        selOverlay = o;
        updateOverlayTools();
        preview.setSelected(o);
        preview.invalidate();
        rebuildTimeline();
        saveCurrent();
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
        if (req == REQ_MUSIC) {
            if (res == RESULT_OK && data != null && data.getData() != null
                    && project != null) {
                try {
                    getContentResolver().takePersistableUriPermission(data.getData(),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }
                project.musicUri = data.getData();
                project.musicUriString = data.getData().toString();
                saveCurrent();
                toast(t("Music added 🎵", "संगीत जुड़ गया 🎵"));
            }
            return;
        }
        if (req == REQ_PICK) {
            if (res != RESULT_OK || data == null || project == null) {
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
        new Thread(() -> {
            final List<Clip> loaded = new ArrayList<>();
            for (Uri u : uris) {
                try {
                    Clip c = loadOne(u);
                    if (c != null) {
                        loaded.add(c);
                    }
                } catch (Throwable ignored) {
                }
            }
            runOnUiThread(() -> {
                hideBusy();
                if (loaded.isEmpty()) {
                    toast(t("Those files could not be opened", "ये फ़ाइलें नहीं खुल सकीं"));
                    return;
                }
                if (project == null) {
                    project = new Project();
                }
                project.clips.addAll(loaded);
                sel = project.clips.size() - loaded.size();
                selOverlay = null;
                refresh();
                saveCurrent();
            });
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
                c.sourceDurationMs = dur == null ? 3000L : Long.parseLong(dur);
                c.videoRotation = rot == null ? 0 : Integer.parseInt(rot);
            } finally {
                mmr.release();
            }
            if (c.sourceDurationMs <= 0) {
                c.sourceDurationMs = 3000L;
            }
            c.startMs = 0;
            c.durationMs = Math.max(1000L, Math.min(c.sourceDurationMs, 60000L));
            Bitmap first = VideoExporter.firstFrame(this, c);
            if (first == null) {
                first = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888);
                new Canvas(first).drawColor(0xFF332211);
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
        c.sourceDurationMs = 3000L;
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
        RectF dst = new RectF((size - boxW) / 2f, (size - boxH) / 2f,
                (size + boxW) / 2f, (size + boxH) / 2f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        busyDialog = new AlertDialog.Builder(this).setView(box).setCancelable(false).create();
        busyDialog.show();
    }

    private void hideBusy() {
        if (busyDialog != null) {
            busyDialog.dismiss();
            busyDialog = null;
        }
    }

    /* ---------------------------------------------------------------- preview */

    private void previewVideo() {
        if (busy) {
            return;
        }
        if (project == null || project.clips.isEmpty()) {
            missingClipsWarning();
            return;
        }
        busy = true;
        final boolean[] cancelled = {false};

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(0xFF0D0A09);
        final ImageView view = new ImageView(this);
        view.setAdjustViewBounds(true);
        view.setMinimumHeight(dp(320));
        box.addView(view, new LinearLayout.LayoutParams(MATCH, dp(420)));

        TextView stop = bigButton(t("⏹  Stop", "⏹  रोकें"), CARD, INK);
        box.addView(stop, spaced(dp(8)));

        final AlertDialog dialog = new AlertDialog.Builder(this).setView(box)
                .setCancelable(true).create();
        stop.setOnClickListener(v -> {
            cancelled[0] = true;
            dialog.dismiss();
        });
        dialog.setOnDismissListener(d -> cancelled[0] = true);
        dialog.show();

        final long[] lastPost = {0};
        final long[] startWall = {0};
        new Thread(() -> {
            try {
                VideoExporter.renderFrames(MainActivity.this,
                        new ArrayList<>(project.clips),
                        new VideoExporter.FrameSink() {
                            @Override
                            public boolean onFrame(Bitmap composed, long ptsUs) {
                                if (cancelled[0]) {
                                    return false;
                                }
                                long now = System.currentTimeMillis();
                                if (startWall[0] == 0) {
                                    startWall[0] = now;
                                }
                                // keep the preview at real speed
                                long target = startWall[0] + ptsUs / 1000L;
                                long wait = target - now;
                                if (wait > 0 && wait < 400) {
                                    try {
                                        Thread.sleep(wait);
                                    } catch (InterruptedException ignored) {
                                    }
                                }
                                if (now - lastPost[0] >= 60) {
                                    lastPost[0] = now;
                                    final Bitmap copy = composed.copy(
                                            Bitmap.Config.ARGB_8888, false);
                                    runOnUiThread(() -> {
                                        if (!cancelled[0]) {
                                            view.setImageBitmap(copy);
                                        }
                                    });
                                }
                                return true;
                            }
                        }, null);
            } catch (Throwable ignored) {
            } finally {
                runOnUiThread(() -> {
                    busy = false;
                    if (!cancelled[0]) {
                        dialog.dismiss();
                    }
                });
            }
        }).start();
    }

    /* ---------------------------------------------------------------- export */

    private void makeVideo() {
        if (busy) {
            return;
        }
        if (project == null || project.clips.isEmpty()) {
            missingClipsWarning();
            return;
        }
        if (project.totalMs() > 5 * 60 * 1000L) {
            toast(t("Please keep the video under 5 minutes", "वीडियो 5 मिनट से छोटा रखें"));
            return;
        }
        busy = true;
        selOverlay = null;
        updateOverlayTools();
        saveCurrent();

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

        final ProgressBar bar = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
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

        final AlertDialog dialog = new AlertDialog.Builder(this).setView(box)
                .setCancelable(false).create();
        dialog.show();

        final Project snapshot = project;
        new Thread(() -> {
            final File temp = new File(getCacheDir(), "movie_" + System.currentTimeMillis() + ".mp4");
            try {
                VideoExporter.export(MainActivity.this, snapshot, temp, percent -> runOnUiThread(() -> {
                    bar.setProgress(percent);
                    pct.setText(percent + "%");
                }));

                String name = "MyVideo_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",
                        java.util.Locale.US).format(new java.util.Date()) + ".mp4";
                final Uri uri = MediaSaver.saveToGallery(MainActivity.this, temp, name, "video/mp4");
                temp.delete();
                lastSaved = uri;
                runOnUiThread(() -> {
                    dialog.dismiss();
                    busy = false;
                    showResult();
                });
            } catch (final Throwable e) {
                temp.delete();
                runOnUiThread(() -> {
                    dialog.dismiss();
                    busy = false;
                    toast(t("Something went wrong: ", "कुछ गड़बड़ हुई: ")
                            + (e.getMessage() == null ? e.getClass().getSimpleName()
                            : e.getMessage()));
                });
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
        watch.setOnClickListener(v -> {
            holder[0].dismiss();
            if (lastSaved != null) {
                MediaSaver.watch(MainActivity.this, lastSaved, lastSavedMime);
            }
        });
        box.addView(watch, spaced(dp(6)));

        TextView share = bigButton(t("📤  Share", "📤  भेजें"), ACCENT, Color.WHITE);
        share.setOnClickListener(v -> {
            if (lastSaved != null) {
                MediaSaver.share(MainActivity.this, lastSaved, lastSavedMime);
            }
        });
        box.addView(share, spaced(dp(8)));

        TextView close = bigButton(t("Close", "बंद करें"), CARD, INK);
        close.setOnClickListener(v -> holder[0].dismiss());
        box.addView(close, spaced(dp(8)));

        holder[0] = new AlertDialog.Builder(this).setView(scroll(box)).create();
        holder[0].show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideBusy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveCurrent();
    }
}
