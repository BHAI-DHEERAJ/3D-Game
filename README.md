# Simple Video Maker

A dead-simple photo & video editor made for a grandmother's phone:
**add photos/videos → add text or stickers (like "SUBSCRIBE to my YouTube") → press "Make Video"**.

Built as a mobile web app (`app/`) and packaged into a real Android APK with a tiny native
WebView wrapper (`android/`).

## Give it to Grandma (easiest way)

1. Download **`SimpleVideoMaker-1.0.apk`** from this repo's
   [Actions](../../actions) → latest "Build Android APK" run → *Artifacts*.
2. Send that file to her phone (WhatsApp, USB, Google Drive…).
3. On the phone, tap the APK → *Install* → if asked, allow *"Install unknown apps"* for
   whichever app you opened it from.
4. Open **Simple Video Maker** from the app drawer.

The finished video is saved to **Gallery → Movies → SimpleVideoMaker**, and the
"Share" button can send it straight to WhatsApp.

## Or install it without an APK (no install needed)

Serve the `app/` folder over HTTPS (any static host, e.g. GitHub Pages) and open it in
Chrome on the phone → menu → **Add to Home screen**. It then behaves like an app and
works offline.

## Using the app

| Step | What to do |
|---|---|
| 1 | **Add** → pick photos and videos from the gallery (multi-select works) |
| 2 | Tap a thumbnail, then **Text** (or a quick phrase) / **Sticker** (YouTube subscribe badge, ❤️, 🙏, 🕉️ …) |
| 3 | Drag the text/sticker on the preview to place it, use **Bigger / Smaller** |
| 4 | **Preview** to watch, **Make Video** to save |

Extras: `⬅️ ➡️` reorder clips, `⏱️` changes how long a photo stays on screen,
the `हिं / EN` button switches the whole app to Hindi.

## Building the APK yourself

* **Cloud (what produced the APK here):** GitHub Actions workflow
  `.github/workflows/android.yml` builds a signed release APK on every push to `main`,
  or manually from the *Actions* tab → *Build Android APK* → *Run workflow*.
* **Locally:** install JDK 17 + Android SDK 34, then

  ```bash
  cd android
  gradle assembleRelease      # or: gradle assembleDebug
  # APK -> android/app/build/outputs/apk/release/
  ```

  (Opening the `android/` folder in Android Studio also works.)

The APK is just a 100 % offline WebView around `app/` — the web app folder is embedded
as assets straight from `app/`, so there is only ever one copy of the code to edit.

## Repository layout

```
app/                      the editor itself (HTML/CSS/JS, no dependencies, works offline)
android/                  native wrapper: gallery picker + "save to Gallery" + Share
tools/make_icons.py       regenerates every icon (pure Python, no deps)
.github/workflows/        cloud APK build
```

> The old `3D-Game` scaffold contained no game code, only a one-line README, so it was
> simply replaced by this project.
