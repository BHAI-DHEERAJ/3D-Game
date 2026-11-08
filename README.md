# Simple Video Maker

A dead-simple, **fully native** Android photo & video editor made for a grandmother's phone:

> **add photos/videos → add text or stickers (like "SUBSCRIBE to my YouTube") → press "Make Video"**

Everything happens on the phone: no WebView, no ffmpeg, no internet, no account.
The app decodes the clips with `MediaCodec`, draws each frame with `Canvas` (pictures, text,
emoji / "SUBSCRIBE" badges), re-encodes with `MediaCodec` H.264 and writes the MP4 with
`MediaMuxer` — original video sound is copied into the movie.

APK builds are produced by **GitHub Actions** (`.github/workflows/android.yml`).

## Install it on Grandma's phone

1. Open **[Releases](../../releases)** and download `SimpleVideoMaker-1.0.apk`
   (or grab `public/SimpleVideoMaker-1.0.apk` from the repo, or the *Actions* → latest run → Artifacts).
2. Send the file to her phone (WhatsApp / Drive / USB).
3. Tap the APK → *Install* → allow *"Install unknown apps"* if Android asks.
4. Open **Simple Video Maker** from the app drawer.

Finished movies are saved to **Gallery → Movies → SimpleVideoMaker**, and the
**Share** button sends them straight to WhatsApp. **Watch** plays the result.

## What she can do

| Button | What it does |
|---|---|
| **➕ Add** | pick many photos and videos at once from the gallery |
| **🔤 Text** | type anything, or tap a ready phrase ("Subscribe to my YouTube ❤️", "नमस्ते 🙏", …) |
| **😊 Sticker** | `SUBSCRIBE`, `LIKE & SHARE`, `FOLLOW ME`, `THANK YOU` badges + ❤️ 😊 👍 ⭐ 🎉 🔔 🌸 🙏 👵 🕉️ 💐 🔥 |
| drag on preview | move the text/sticker exactly where she wants it |
| **➕ / ➖** | make the selected text/sticker bigger or smaller |
| **⏱️** | how long a photo stays on screen (2s / 3s / 5s / 8s) |
| **⬅️ ➡️** | reorder clips |
| **🎉 Make Video** | renders a real MP4 (720×1280, 30 fps) into the Gallery |
| **हिं / EN** | switches the whole app between Hindi and English |

Notes: the movie is portrait 9:16 (good for WhatsApp status / YouTube Shorts); clips longer
than 60 s are trimmed to their first minute; sound comes from the video clips.

## Building the APK (GitHub CI)

Any push that touches `android/` or the workflow, or **Actions → Build Android APK → Run workflow**,
produces a signed APK:

* `SimpleVideoMaker-apk` — the APK artifact
* `signing-keystore` — the key used to sign it (keep it if you want future builds to install
  as an *update* instead of needing an uninstall)
* a **GitHub Release** named `apk-latest` with the APK attached
* `public/SimpleVideoMaker-1.0.apk`, committed back to the branch so the file is easy to grab

The runner does: JDK 17 → Android SDK 34 → `gradle assembleRelease` in `android/`.

Locally (Android Studio or CLI):

```bash
cd android
gradle assembleRelease        # APK -> android/app/build/outputs/apk/release/
```

## Project layout

```
android/app/src/main/java/com/simplevideomaker/app/
    MainActivity.java    native UI: timeline, big buttons, text/sticker dialogs, export flow
    EditorView.java      the live preview; drag text/stickers with your finger
    FrameRenderer.java   draws one 720x1280 frame (picture + text + stickers) — shared with the encoder
    VideoExporter.java   MediaCodec decode -> Canvas composite -> H.264 encode -> MediaMuxer
    MediaSaver.java      writes into the Gallery, Share / Watch intents
    Clip.java  Overlay.java
tools/make_icons.py      regenerates the launcher icons (pure Python, no deps)
.github/workflows/       the APK build
```

> The old `3D-Game` scaffold contained no code (a one-line README only), so it was replaced by
> this project.
