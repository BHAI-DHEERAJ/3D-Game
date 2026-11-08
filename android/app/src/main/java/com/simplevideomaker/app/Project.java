package com.simplevideomaker.app;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** A saved draft: clips + text + stickers + background music. */
public class Project {

    public String id;
    public String name;
    public long updatedAt;
    public final List<Clip> clips = new ArrayList<>();

    /** Background music chosen from the phone (may be null). */
    public Uri musicUri;
    public String musicUriString = "";
    public float musicVolume = 0.7f;

    /** Turn the original sound of every clip off (music only). */
    public boolean clipsMuted = false;

    /** Small JPEG preview of the first clip, base64 — shown on the drafts screen. */
    public String thumb;

    /** Output size and quality of the finished movie. */
    public int outWidth = 1920;
    public int outHeight = 1080;
    public int bitrate = 8_000_000;

    public Project() {
        this.id = ProjectStore.newId();
        this.name = "";
        this.updatedAt = System.currentTimeMillis();
    }

    public long totalMs() {
        long t = 0;
        for (Clip c : clips) {
            t += Math.max(200, c.durationMs);
        }
        return t;
    }

    public Clip firstClip() {
        return clips.isEmpty() ? null : clips.get(0);
    }

    public String summary() {
        if (clips.isEmpty()) {
            return "empty";
        }
        return clips.size() + (clips.size() == 1 ? " clip" : " clips") + " · " + seconds(totalMs());
    }

    public static String seconds(long ms) {
        long s = Math.round(ms / 1000.0);
        if (s < 60) {
            return s + "s";
        }
        return (s / 60) + ":" + String.format(java.util.Locale.US, "%02d", s % 60);
    }

    /* ------------------------------------------------------------------- json */

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("updated", updatedAt);
        o.put("music", musicUriString);
        o.put("musicVol", musicVolume);
        o.put("muted", clipsMuted);
        o.put("w", outWidth);
        o.put("h", outHeight);
        o.put("br", bitrate);
        if (thumb != null) {
            o.put("thumb", thumb);
        }
        JSONArray arr = new JSONArray();
        for (Clip c : clips) {
            arr.put(c.toJson());
        }
        o.put("clips", arr);
        return o;
    }

    public static Project fromJson(JSONObject o) throws Exception {
        Project p = new Project();
        p.id = o.optString("id", ProjectStore.newId());
        p.name = o.optString("name", "");
        p.updatedAt = o.optLong("updated", System.currentTimeMillis());
        p.musicUriString = o.optString("music", "");
        p.musicUri = p.musicUriString.isEmpty() ? null : Uri.parse(p.musicUriString);
        p.musicVolume = (float) o.optDouble("musicVol", 0.7);
        p.clipsMuted = o.optBoolean("muted", false);
        p.outWidth = o.optInt("w", 1920);
        p.outHeight = o.optInt("h", 1080);
        p.bitrate = o.optInt("br", 8000000);
        p.thumb = o.optString("thumb", null);
        JSONArray arr = o.optJSONArray("clips");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                p.clips.add(Clip.fromJson(arr.getJSONObject(i)));
            }
        }
        return p;
    }
}
