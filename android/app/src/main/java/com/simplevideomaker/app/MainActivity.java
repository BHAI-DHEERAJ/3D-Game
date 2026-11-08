package com.simplevideomaker.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Thin, dependency-free wrapper that loads the offline web editor from
 * assets/index.html and gives it one thing a browser cannot on its own:
 * writing the finished video straight into the phone's Gallery.
 *
 * JS bridge  ->  window.AndroidSaver.start(name) / chunk(base64) / finish() / share(uri)
 */
public class MainActivity extends Activity {

    private static final int REQ_PICK = 1001;
    private static final int REQ_PERM = 1002;

    private WebView web;
    private ValueCallback<Uri[]> pendingFileCallback;
    private final Saver saver = new Saver();

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        setContentView(web, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setTextZoom(100);
        s.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= 26) {
            s.setSafeBrowsingEnabled(false);
        }

        web.setBackgroundColor(0xFFFFF6F0);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (pendingFileCallback != null) {
                    pendingFileCallback.onReceiveValue(null);
                    pendingFileCallback = null;
                }
                pendingFileCallback = callback;
                try {
                    Intent intent = params.createIntent();
                    if (intent == null) {
                        intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("*/*");
                        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                                new String[]{"image/*", "video/*"});
                    }
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    startActivityForResult(Intent.createChooser(intent, "Select photos or videos"),
                            REQ_PICK);
                    return true;
                } catch (Exception e) {
                    pendingFileCallback = null;
                    toast("Could not open the gallery");
                    return false;
                }
            }
        });

        web.addJavascriptInterface(saver, "AndroidSaver");
        web.loadUrl("file:///android_asset/index.html");

        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERM);
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_PICK) {
            Uri[] uris = null;
            if (res == RESULT_OK && data != null) {
                ClipData clip = data.getClipData();
                if (clip != null && clip.getItemCount() > 0) {
                    uris = new Uri[clip.getItemCount()];
                    for (int i = 0; i < clip.getItemCount(); i++) {
                        uris[i] = clip.getItemAt(i).getUri();
                    }
                } else if (data.getData() != null) {
                    uris = new Uri[]{data.getData()};
                }
            }
            if (pendingFileCallback != null) {
                pendingFileCallback.onReceiveValue(uris);
                pendingFileCallback = null;
            }
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public void onBackPressed() {
        // single-page app: back closes the app
        super.onBackPressed();
    }

    private void toast(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /* ------------------------------------------------------------------ saver */
    public class Saver {
        private OutputStream out;
        private Uri uri;
        private File legacyFile;
        private String mime = "video/mp4";
        private String lastError;

        @JavascriptInterface
        public synchronized String start(String name) {
            try {
                out = null;
                uri = null;
                legacyFile = null;
                lastError = null;
                mime = name != null && name.toLowerCase().endsWith(".webm")
                        ? "video/webm" : "video/mp4";

                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Video.Media.DISPLAY_NAME, name);
                cv.put(MediaStore.Video.Media.MIME_TYPE, mime);
                cv.put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000L);

                if (Build.VERSION.SDK_INT >= 29) {
                    cv.put(MediaStore.Video.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_MOVIES + "/SimpleVideoMaker");
                    cv.put(MediaStore.Video.Media.IS_PENDING, 1);
                    uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
                    if (uri == null) return err("Could not create the video file");
                    out = getContentResolver().openOutputStream(uri);
                } else {
                    File dir = new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_MOVIES), "SimpleVideoMaker");
                    if (!dir.exists() && !dir.mkdirs()) return err("Could not create folder");
                    legacyFile = new File(dir, name);
                    cv.put(MediaStore.Video.Media.DATA, legacyFile.getAbsolutePath());
                    uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv);
                    out = new FileOutputStream(legacyFile);
                }
                if (out == null) return err("Could not open the video file");
                return "{\"ok\":true}";
            } catch (Exception e) {
                return err(e.getMessage() == null ? e.toString() : e.getMessage());
            }
        }

        @JavascriptInterface
        public synchronized void chunk(String base64) {
            try {
                if (out != null && base64 != null) {
                    out.write(Base64.decode(base64, Base64.DEFAULT));
                }
            } catch (Exception e) {
                lastError = String.valueOf(e.getMessage());
            }
        }

        @JavascriptInterface
        public synchronized String finish() {
            try {
                if (out != null) {
                    out.flush();
                    out.close();
                    out = null;
                }
                if (lastError != null) return err(lastError);

                if (Build.VERSION.SDK_INT >= 29 && uri != null) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Video.Media.IS_PENDING, 0);
                    getContentResolver().update(uri, cv, null, null);
                } else if (legacyFile != null) {
                    sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                            Uri.fromFile(legacyFile)));
                }
                if (uri == null) return err("Nothing was saved");
                return "{\"ok\":true,\"uri\":\"" + uri + "\"}";
            } catch (Exception e) {
                return err(e.getMessage() == null ? e.toString() : e.getMessage());
            }
        }

        @JavascriptInterface
        public void share(final String uriString) {
            try {
                Uri u = Uri.parse(uriString);
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType(mime);
                i.putExtra(Intent.EXTRA_STREAM, u);
                i.setClipData(ClipData.newRawUri("video", u));
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "Share your video"));
            } catch (Exception e) {
                toast("Could not share the video");
            }
        }

        @JavascriptInterface
        public void message(String msg) {
            toast(msg);
        }

        private String err(String msg) {
            String safe = msg == null ? "unknown error" : msg.replace("\\", " ").replace("\"", " ");
            return "{\"ok\":false,\"error\":\"" + safe + "\"}";
        }
    }
}
