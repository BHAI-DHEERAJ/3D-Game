package com.simplevideomaker.app;

import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Puts the finished movie into the phone's Gallery and lets the user share or watch it. */
public final class MediaSaver {

    public static final String FOLDER = "SimpleVideoMaker";

    private MediaSaver() {
    }

    public static Uri saveToGallery(Context ctx, File source, String displayName, String mime)
            throws IOException {
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Video.Media.DISPLAY_NAME, displayName);
        cv.put(MediaStore.Video.Media.MIME_TYPE, mime);
        cv.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000L);

        if (Build.VERSION.SDK_INT >= 29) {
            cv.put(MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/" + FOLDER);
            cv.put(MediaStore.Video.Media.IS_PENDING, 1);
            Uri uri = ctx.getContentResolver()
                    .insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) {
                throw new IOException("cannot create the video file");
            }
            OutputStream out = ctx.getContentResolver().openOutputStream(uri);
            copy(source, out);
            ContentValues done = new ContentValues();
            done.put(MediaStore.Video.Media.IS_PENDING, 0);
            ctx.getContentResolver().update(uri, done, null, null);
            return uri;
        }

        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES), FOLDER);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("cannot create folder");
        }
        File target = new File(dir, displayName);
        copy(source, new FileOutputStream(target));
        cv.put(MediaStore.Video.Media.DATA, target.getAbsolutePath());
        Uri uri = ctx.getContentResolver()
                .insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
        ctx.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                Uri.fromFile(target)));
        return uri != null ? uri : Uri.fromFile(target);
    }

    public static void share(Context ctx, Uri uri, String mime) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(mime);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.setClipData(ClipData.newRawUri("video", uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ctx.startActivity(Intent.createChooser(send, "Share"));
    }

    public static void watch(Context ctx, Uri uri, String mime) {
        Intent view = new Intent(Intent.ACTION_VIEW);
        view.setDataAndType(uri, mime);
        view.setClipData(ClipData.newRawUri("video", uri));
        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            ctx.startActivity(Intent.createChooser(view, "Play"));
        } catch (Exception ignored) {
        }
    }

    private static void copy(File source, OutputStream out) throws IOException {
        if (out == null) {
            throw new IOException("cannot open the destination");
        }
        InputStream in = new FileInputStream(source);
        try {
            byte[] buf = new byte[128 * 1024];
            int read;
            while ((read = in.read(buf)) > 0) {
                out.write(buf, 0, read);
            }
            out.flush();
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
            }
            try {
                out.close();
            } catch (IOException ignored) {
            }
        }
    }
}
