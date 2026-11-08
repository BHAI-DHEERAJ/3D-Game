package com.simplevideomaker.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Drafts live as small JSON files in the app's private storage — no account, no cloud. */
public final class ProjectStore {

    private ProjectStore() {
    }

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static File dir(Context context) {
        File d = new File(context.getFilesDir(), "projects");
        if (!d.exists() && !d.mkdirs()) {
            // fall through; save() will report the failure
        }
        return d;
    }

    private static File file(Context context, String id) {
        return new File(dir(context), id + ".json");
    }

    public static List<Project> list(Context context) {
        List<Project> out = new ArrayList<>();
        File[] files = dir(context).listFiles();
        if (files == null) {
            return out;
        }
        for (File f : files) {
            if (!f.getName().endsWith(".json")) {
                continue;
            }
            try {
                JSONObject o = new JSONObject(read(f));
                out.add(Project.fromJson(o));
            } catch (Exception ignored) {
            }
        }
        Collections.sort(out, new Comparator<Project>() {
            @Override
            public int compare(Project a, Project b) {
                return Long.compare(b.updatedAt, a.updatedAt);
            }
        });
        return out;
    }

    public static void save(Context context, Project project) {
        try {
            project.updatedAt = System.currentTimeMillis();
            updateThumb(project);
            write(file(context, project.id), project.toJson().toString());
        } catch (Exception ignored) {
        }
    }

    public static void delete(Context context, String id) {
        File f = file(context, id);
        if (f.exists()) {
            f.delete();
        }
    }

    public static Project duplicate(Context context, Project source) {
        Project copy = new Project();
        try {
            copy.name = source.name;
            copy.clips.clear();
            for (Clip c : source.clips) {
                Clip n = Clip.fromJson(c.toJson());
                n.image = c.image;
                n.thumb = c.thumb;
                copy.clips.add(n);
            }
            copy.musicUri = source.musicUri;
            copy.musicUriString = source.musicUriString;
            copy.musicVolume = source.musicVolume;
            copy.thumb = source.thumb;
            save(context, copy);
            return copy;
        } catch (Exception e) {
            return copy;
        }
    }

    /* ----------------------------------------------------------------- helpers */

    private static void updateThumb(Project project) {
        try {
            Clip first = project.firstClip();
            if (first == null || first.thumb == null) {
                return;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            first.thumb.compress(Bitmap.CompressFormat.JPEG, 70, out);
            project.thumb = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception ignored) {
        }
    }

    private static String read(File f) throws Exception {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) {
                out.write(buf, 0, r);
            }
            return out.toString("UTF-8");
        } finally {
            in.close();
        }
    }

    private static void write(File f, String text) throws Exception {
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(text.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }
}
