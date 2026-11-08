/* Simple Video Maker — a friendly photo/video editor
   Runs as a normal web app, and inside the Android WebView APK. */
(function () {
  'use strict';

  var W = 720, H = 1280;          // render size (portrait 9:16)
  var $ = function (s, r) { return (r || document).querySelector(s); };
  var $$ = function (s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); };

  /* ------------------------------------------------------------------ i18n */
  var STR = {
    en: {
      appName: 'Simple Video Maker',
      welcome: 'Make a video in 3 easy steps',
      step1: 'Add your photos and videos',
      step2: 'Add text or a sticker',
      step3: 'Press "Make Video"',
      addMedia: 'Add Photos & Videos',
      hintPick: 'Choose photos or videos from your phone',
      add: 'Add',
      preview: 'Preview',
      make: 'Make Video',
      text: 'Text',
      sticker: 'Sticker',
      left: 'Left',
      right: 'Right',
      remove: 'Remove',
      bigger: 'Bigger',
      smaller: 'Smaller',
      done: 'Done',
      addText: 'Add Text',
      addTextOnScreen: 'Put text on video',
      addSticker: 'Add Sticker',
      making: 'Making your video…',
      pleaseWait: 'Please wait, do not close the app',
      saved: 'Video saved!',
      savedHint: 'You can find it in your Gallery',
      share: 'Share',
      close: 'Close',
      needPhotos: 'First add some photos or videos',
      needClip: 'Tap a photo below to choose it',
      removed: 'Removed',
      videoFixed: 'Video keeps its own length',
      textEmpty: 'Please type something first',
      savedGallery: 'Saved to your Gallery 🎉',
      downloaded: 'Video saved to Downloads',
      failed: 'Something went wrong. Please try again.',
      playHint: 'Tap to play',
      phrases: ['Subscribe to my YouTube ❤️', 'Like & Share 🙏', 'Happy Birthday 🎂', 'नमस्ते 🙏', 'Love you all ❤️', 'Thank you 🙏']
    },
    hi: {
      appName: 'आसान वीडियो मेकर',
      welcome: '3 आसान कदमों में वीडियो बनाएं',
      step1: 'अपनी फ़ोटो और वीडियो जोड़ें',
      step2: 'टेक्स्ट या स्टिकर लगाएं',
      step3: '“वीडियो बनाएं” दबाएं',
      addMedia: 'फ़ोटो और वीडियो जोड़ें',
      hintPick: 'फ़ोन से फ़ोटो या वीडियो चुनें',
      add: 'जोड़ें',
      preview: 'देखें',
      make: 'वीडियो बनाएं',
      text: 'लिखें',
      sticker: 'स्टिकर',
      left: 'पीछे',
      right: 'आगे',
      remove: 'हटाएं',
      bigger: 'बड़ा',
      smaller: 'छोटा',
      done: 'ठीक है',
      addText: 'टेक्स्ट जोड़ें',
      addTextOnScreen: 'वीडियो पर लिखें',
      addSticker: 'स्टिकर जोड़ें',
      making: 'आपका वीडियो बन रहा है…',
      pleaseWait: 'कृपया रुकें, ऐप बंद न करें',
      saved: 'वीडियो सेव हो गया!',
      savedHint: 'यह आपकी गैलरी में मिलेगा',
      share: 'भेजें',
      close: 'बंद करें',
      needPhotos: 'पहले कुछ फ़ोटो या वीडियो जोड़ें',
      needClip: 'नीचे फ़ोटो पर टैप करें',
      removed: 'हटा दिया',
      videoFixed: 'वीडियो की लंबाई वैसी ही रहेगी',
      textEmpty: 'पहले कुछ लिखें',
      savedGallery: 'गैलरी में सेव हो गया 🎉',
      downloaded: 'वीडियो डाउनलोड में सेव हो गया',
      failed: 'कुछ गड़बड़ हुई। फिर से कोशिश करें।',
      playHint: 'चलाने के लिए टैप करें',
      phrases: ['मेरे यूट्यूब को सब्सक्राइब करें ❤️', 'लाइक और शेयर करें 🙏', 'जन्मदिन मुबारक 🎂', 'नमस्ते 🙏', 'आप सबसे प्यार ❤️', 'धन्यवाद 🙏']
    }
  };
  var lang = localStorage.getItem('svm_lang') || 'en';
  function t(k) { return (STR[lang] && STR[lang][k]) || STR.en[k] || k; }

  /* ----------------------------------------------------------------- state */
  var S = {
    clips: [],
    sel: -1,
    selOverlay: null,
    playing: false,
    exporting: false,
    abort: false
  };

  var stage = $('#stage');
  var ctx = stage.getContext('2d');
  var exportCanvas = document.createElement('canvas');
  exportCanvas.width = W; exportCanvas.height = H;
  var exportCtx = exportCanvas.getContext('2d');
  var fileInput = $('#fileInput');

  var STICKERS = [
    { type: 'badge', text: 'SUBSCRIBE', color: '#FF0000', label: 'YouTube' },
    { type: 'badge', text: 'LIKE & SHARE', color: '#E23E3E', label: 'Like' },
    { type: 'badge', text: 'FOLLOW ME', color: '#8A2BE2', label: 'Follow' },
    { type: 'badge', text: 'THANK YOU', color: '#1E9E55', label: 'Thanks' },
    { type: 'emoji', text: '❤️', label: 'Heart' },
    { type: 'emoji', text: '😊', label: 'Smile' },
    { type: 'emoji', text: '👍', label: 'Thumbs up' },
    { type: 'emoji', text: '⭐', label: 'Star' },
    { type: 'emoji', text: '🎉', label: 'Party' },
    { type: 'emoji', text: '🔔', label: 'Bell' },
    { type: 'emoji', text: '🌸', label: 'Flower' },
    { type: 'emoji', text: '🙏', label: 'Namaste' },
    { type: 'emoji', text: '👵', label: 'Grandma' },
    { type: 'emoji', text: '🕉️', label: 'Om' },
    { type: 'emoji', text: '💐', label: 'Bouquet' },
    { type: 'emoji', text: '🔥', label: 'Fire' }
  ];

  /* ------------------------------------------------------------------ utils */
  function uid() { return Math.random().toString(36).slice(2, 9); }
  function easeOut(x) { return 1 - Math.pow(1 - x, 2); }
  function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }
  function toast(msg, ms) {
    var el = $('#toast');
    el.textContent = msg;
    el.classList.remove('hidden');
    clearTimeout(toast._t);
    toast._t = setTimeout(function () { el.classList.add('hidden'); }, ms || 2200);
  }
  function fmt(sec) {
    var s = Math.round(sec);
    return (s < 60) ? s + 's' : Math.floor(s / 60) + ':' + String(s % 60).padStart(2, '0');
  }
  function bytesToBase64(bytes) {
    var CH = 0x8000, out = '';
    for (var i = 0; i < bytes.length; i += CH) {
      out += String.fromCharCode.apply(null, bytes.subarray(i, i + CH));
    }
    return btoa(out);
  }

  /* -------------------------------------------------------------- language */
  function applyLang() {
    $$('[data-i18n]').forEach(function (el) {
      var k = el.getAttribute('data-i18n');
      if (STR[lang][k] !== undefined) el.textContent = STR[lang][k];
    });
    $('#langBtn').textContent = lang === 'en' ? 'हिं' : 'EN';
    document.documentElement.lang = lang === 'hi' ? 'hi' : 'en';
    $('#textInput').placeholder = lang === 'hi' ? 'यहाँ लिखें…' : 'Type here…';
    buildPhrases();
    updateSpeedLabel();
  }
  $('#langBtn').addEventListener('click', function () {
    lang = lang === 'en' ? 'hi' : 'en';
    localStorage.setItem('svm_lang', lang);
    applyLang();
  });

  function buildPhrases() {
    var wrap = $('#quickPhrases');
    wrap.innerHTML = '';
    t('phrases').forEach(function (p) {
      var b = document.createElement('button');
      b.className = 'chip';
      b.type = 'button';
      b.textContent = p;
      b.addEventListener('click', function () {
        $('#textInput').value = p;
      });
      wrap.appendChild(b);
    });
  }

  /* ------------------------------------------------------------ import media */
  $('#startBtn').addEventListener('click', pickFiles);
  $('#addBtn').addEventListener('click', pickFiles);
  function pickFiles() { fileInput.value = ''; fileInput.click(); }

  fileInput.addEventListener('change', async function () {
    var files = Array.prototype.slice.call(fileInput.files || []);
    if (!files.length) return;
    toast(lang === 'hi' ? 'जोड़ा जा रहा है…' : 'Adding…', 1200);
    for (var i = 0; i < files.length; i++) {
      try {
        var clip = await loadClip(files[i]);
        if (clip) S.clips.push(clip);
      } catch (e) {
        console.warn('skip file', e);
      }
    }
    S.sel = S.clips.length ? S.clips.length - files.length : -1;
    if (S.sel < 0) S.sel = S.clips.length - 1;
    renderAll();
    if (S.clips.length) toast(lang === 'hi' ? 'जुड़ गया ✔️' : 'Added ✔️', 1400);
  });

  function loadClip(file) {
    return new Promise(function (resolve, reject) {
      var isVideo = /^video\//.test(file.type) || /\.(mp4|mov|webm|m4v|3gp|mkv)$/i.test(file.name);
      var url = URL.createObjectURL(file);
      if (isVideo) {
        var v = document.createElement('video');
        v.src = url;
        v.playsInline = true;
        v.setAttribute('playsinline', '');
        v.preload = 'auto';
        v.addEventListener('loadeddata', function () {
          var d = v.duration;
          if (!isFinite(d) || d <= 0) d = 5;
          makeThumb(v, v.videoWidth, v.videoHeight, function (thumb) {
            resolve({
              id: uid(), kind: 'video', video: v, url: url, duration: Math.min(d, 120),
              thumb: thumb, overlays: []
            });
          });
        });
        v.addEventListener('error', reject);
      } else if (/^image\//.test(file.type)) {
        var img = new Image();
        img.onload = function () {
          makeThumb(img, img.naturalWidth, img.naturalHeight, function (thumb) {
            resolve({
              id: uid(), kind: 'photo', image: img, url: url, duration: 3,
              thumb: thumb, overlays: []
            });
          });
        };
        img.onerror = reject;
        img.src = url;
      } else {
        reject(new Error('unsupported'));
      }
    });
  }

  function makeThumb(src, w, h, cb) {
    var c = document.createElement('canvas');
    c.width = 160; c.height = 160;
    var g = c.getContext('2d');
    g.fillStyle = '#111'; g.fillRect(0, 0, 160, 160);
    try {
      var scale = Math.max(160 / (w || 1), 160 / (h || 1));
      var dw = (w || 1) * scale, dh = (h || 1) * scale;
      g.drawImage(src, serial(160, dw), serial(160, dh), dw, dh);
    } catch (e) { /* ignore */ }
    cb(c.toDataURL('image/jpeg', 0.8));
    function serial(outer, inner) { return (outer - inner) / 2; }
  }

  /* --------------------------------------------------------------- rendering */
  function clipSource(clip) {
    return clip.kind === 'video' ? clip.video : clip.image;
  }
  function srcSize(clip) {
    var s = clipSource(clip);
    var w = clip.kind === 'video' ? s.videoWidth : s.naturalWidth;
    var h = clip.kind === 'video' ? s.videoHeight : s.naturalHeight;
    return { w: w || W, h: h || H };
  }

  function drawMedia(g, clip, progress, zoom) {
    var s = clipSource(clip);
    var dim = srcSize(clip);
    if (!dim.w || !dim.h) return;
    var aspect = dim.w / dim.h;
    var Z = zoom || 1;

    // blurred-ish background (cover, dimmed) so tall/wide photos still look full
    var bw = W, bh = H;
    if (aspect > W / H) { bh = H; bw = H * aspect; } else { bw = W; bh = W / aspect; }
    var bs = Math.max(W / bw, H / bh) * Z;
    g.save();
    g.globalAlpha = 0.42;
    if (clip.kind === 'photo') { try { g.filter = 'blur(18px)'; } catch (e) {} }
    try { g.drawImage(s, (W - bw * bs) / 2, (H - bh * bs) / 2, bw * bs, bh * bs); } catch (e) {}
    g.restore();

    // main image (contain)
    var cw, ch;
    if (aspect > W / H) { cw = W; ch = W / aspect; } else { ch = H; cw = H * aspect; }
    cw *= Z; ch *= Z;
    g.save();
    roundedRect(g, (W - cw) / 2, (H - ch) / 2, cw, ch, 18);
    g.clip();
    try { g.drawImage(s, (W - cw) / 2, (H - ch) / 2, cw, ch); } catch (e) {}
    g.restore();
  }

  function roundedRect(g, x, y, w, h, r) {
    r = Math.min(r, w / 2, h / 2);
    g.beginPath();
    g.moveTo(x + r, y);
    g.arcTo(x + w, y, x + w, y + h, r);
    g.arcTo(x + w, y + h, x, y + h, r);
    g.arcTo(x, y + h, x, y, r);
    g.arcTo(x, y, x + w, y, r);
    g.closePath();
  }

  function fontFor(size, emoji) {
    return (emoji ? '600 ' : '900 ') + Math.round(size) + 'px ' +
      (emoji ? '"Apple Color Emoji","Segoe UI Emoji","Noto Color Emoji",sans-serif'
             : 'system-ui,"Noto Sans","Noto Sans Devanagari",Roboto,sans-serif');
  }

  function overlayBounds(g, o) {
    var size = o.size * H;
    if (o.type === 'emoji') return { w: size * 1.15, h: size * 1.2 };
    g.font = fontFor(size, false);
    var tw = g.measureText(o.text).width;
    if (o.type === 'badge') return { w: tw + size * 1.1, h: size * 1.85 };
    return { w: Math.max(tw, size * 0.6), h: size * 1.25 };
  }

  function drawOverlay(g, o) {
    var size = o.size * H;
    var x = o.x * W, y = o.y * H;
    g.save();
    g.textAlign = 'center';
    g.textBaseline = 'middle';
    g.shadowColor = 'rgba(0,0,0,.45)';
    g.shadowBlur = size * 0.22;
    g.shadowOffsetY = size * 0.05;

    if (o.type === 'emoji') {
      g.font = fontFor(size, true);
      g.fillText(o.text, x, y);
    } else if (o.type === 'badge') {
      g.font = fontFor(size, false);
      var tw = g.measureText(o.text).width;
      var bw = tw + size * 1.1, bh = size * 1.55;
      g.shadowColor = 'rgba(0,0,0,.35)';
      g.fillStyle = o.color || '#FF0000';
      roundedRect(g, x - bw / 2, y - bh / 2, bw, bh, bh * 0.32);
      g.fill();
      g.shadowColor = 'transparent';
      g.fillStyle = '#fff';
      g.fillText(o.text, x, y + size * 0.06);
    } else {
      g.font = fontFor(size, false);
      g.lineWidth = size * 0.19;
      g.lineJoin = 'round';
      g.miterLimit = 2;
      g.strokeStyle = 'rgba(0,0,0,.78)';
      g.strokeText(o.text, x, y);
      g.shadowColor = 'transparent';
      g.fillStyle = o.color || '#FFFFFF';
      g.fillText(o.text, x, y);
    }
    g.restore();
  }

  function drawFrame(g, clip, elapsed, opts) {
    opts = opts || {};
    g.save();
    g.fillStyle = '#0d0a09';
    g.fillRect(0, 0, W, H);
    if (clip) {
      var p = clip.duration ? clamp(elapsed / clip.duration, 0, 1) : 0;
      var zoom = 1 + 0.07 * easeOut(p);
      drawMedia(g, clip, p, zoom);
      (clip.overlays || []).forEach(function (o) { drawOverlay(g, o); });
      if (opts.handles && S.selOverlay) {
        var sel = clip.overlays.filter(function (o) { return o.id === S.selOverlay; })[0];
        if (sel) drawHandle(g, sel);
      }
    } else {
      g.fillStyle = '#3a2f2a';
      g.font = '600 34px system-ui,sans-serif';
      g.textAlign = 'center';
      g.fillText(lang === 'hi' ? 'फ़ोटो जोड़ें' : 'Add photos to start', W / 2, H / 2);
    }
    // frame counter dots
    g.restore();
  }

  function drawHandle(g, o) {
    var b = overlayBounds(g, o);
    var x = o.x * W - b.w / 2 - 10, y = o.y * H - b.h / 2 - 10;
    g.save();
    g.setLineDash([12, 10]);
    g.lineWidth = 4;
    g.strokeStyle = 'rgba(0,220,255,.95)';
    roundedRect(g, x, y, b.w + 20, b.h + 20, 16);
    g.stroke();
    g.restore();
  }

  function redraw() {
    var clip = S.clips[S.sel] || null;
    drawFrame(ctx, clip, 0, { handles: !S.playing && !S.exporting });
  }

  /* --------------------------------------------------------------- timeline */
  function renderAll() {
    renderTimeline();
    var has = S.clips.length > 0;
    $('#empty').classList.toggle('hidden', has);
    $('#editor').classList.toggle('hidden', !has);
    $('#actionbar').classList.toggle('hidden', !has);
    if (S.sel >= S.clips.length) S.sel = S.clips.length - 1;
    if (S.sel < 0 && has) S.sel = 0;
    updateSpeedLabel();
    updateOverlayTools();
    fitStage();
    redraw();
  }

  function fitStage() {
    var wrap = stage.parentElement;
    var aw = wrap.clientWidth || 320, ah = wrap.clientHeight || 480;
    var scale = Math.min(aw / W, ah / H);
    stage.style.width = Math.floor(W * scale) + 'px';
    stage.style.height = Math.floor(H * scale) + 'px';
  }

  function renderTimeline() {
    var el = $('#timeline');
    el.innerHTML = '';
    S.clips.forEach(function (clip, i) {
      var b = document.createElement('button');
      b.className = 'clip' + (i === S.sel ? ' sel' : '');
      b.type = 'button';
      var img = document.createElement('img');
      img.src = clip.thumb;
      img.alt = '';
      b.appendChild(img);

      var kind = document.createElement('span');
      kind.className = 'kind';
      kind.textContent = clip.kind === 'video' ? '🎥' : '🖼️';
      b.appendChild(kind);

      var badge = document.createElement('span');
      badge.className = 'badge';
      badge.textContent = fmt(clip.duration);
      b.appendChild(badge);

      if (clip.overlays.length) {
        var dots = document.createElement('span');
        dots.className = 'dots';
        clip.overlays.slice(0, 3).forEach(function () {
          var d = document.createElement('i'); dots.appendChild(d);
        });
        b.appendChild(dots);
      }

      b.addEventListener('click', function () {
        if (S.playing || S.exporting) return;
        S.sel = i;
        S.selOverlay = null;
        renderAll();
      });
      el.appendChild(b);
    });
  }

  function updateSpeedLabel() {
    var clip = S.clips[S.sel];
    var el = $('#speedLabel');
    if (!el) return;
    if (!clip) { el.textContent = '—'; return; }
    el.textContent = fmt(clip.duration);
  }

  /* ------------------------------------------------------------- clip tools */
  $('#clipTools').addEventListener('click', function (e) {
    var btn = e.target.closest('.tool');
    if (!btn) return;
    var act = btn.getAttribute('data-act');
    var clip = S.clips[S.sel];
    if (!clip) return toast(t('needPhotos'));

    if (act === 'text') {
      $('#textInput').value = '';
      openSheet('#textSheet');
      setTimeout(function () { $('#textInput').focus(); }, 120);
    } else if (act === 'sticker') {
      openSheet('#stickerSheet');
    } else if (act === 'speed') {
      if (clip.kind === 'video') return toast(t('videoFixed'));
      var options = [2, 3, 5, 8];
      var idx = options.indexOf(clip.duration);
      clip.duration = options[(idx + 1) % options.length];
      updateSpeedLabel();
      renderTimeline();
      toast(fmt(clip.duration), 900);
    } else if (act === 'left') {
      if (S.sel <= 0) return;
      swap(S.sel, S.sel - 1);
    } else if (act === 'right') {
      if (S.sel >= S.clips.length - 1) return;
      swap(S.sel, S.sel + 1);
    } else if (act === 'delete') {
      S.clips.splice(S.sel, 1);
      S.sel = Math.min(S.sel, S.clips.length - 1);
      S.selOverlay = null;
      renderAll();
      toast(t('removed'), 1100);
    }
  });

  function swap(a, b) {
    var tmp = S.clips[a];
    S.clips[a] = S.clips[b];
    S.clips[b] = tmp;
    S.sel = b;
    renderAll();
  }

  /* ---------------------------------------------------------- overlay tools */
  $('#overlayTools').addEventListener('click', function (e) {
    var btn = e.target.closest('.tool');
    if (!btn) return;
    var act = btn.getAttribute('data-act');
    var clip = S.clips[S.sel];
    var o = clip && clip.overlays.filter(function (x) { return x.id === S.selOverlay; })[0];
    if (!o) return;
    if (act === 'bigger') o.size = clamp(o.size * 1.18, 0.03, 0.5);
    if (act === 'smaller') o.size = clamp(o.size / 1.18, 0.03, 0.5);
    if (act === 'delOverlay') {
      clip.overlays = clip.overlays.filter(function (x) { return x.id !== o.id; });
      S.selOverlay = null;
      renderTimeline();
    }
    if (act === 'doneOverlay') S.selOverlay = null;
    updateOverlayTools();
    redraw();
  });

  function updateOverlayTools() {
    $('#overlayTools').classList.toggle('hidden', !S.selOverlay);
  }

  /* ------------------------------------------------------------ text sheet */
  $('#addTextBtn').addEventListener('click', function () {
    var val = $('#textInput').value.trim();
    if (!val) { toast(t('textEmpty')); return; }
    var clip = S.clips[S.sel];
    if (!clip) return toast(t('needPhotos'));
    var o = {
      id: uid(), type: 'text', text: val,
      x: 0.5, y: 0.8, size: 0.062, color: '#FFFFFF'
    };
    clip.overlays.push(o);
    S.selOverlay = o.id;
    closeSheets();
    renderTimeline();
    updateOverlayTools();
    redraw();
  });

  /* --------------------------------------------------------- sticker sheet */
  function buildStickers() {
    var grid = $('#stickerGrid');
    grid.innerHTML = '';
    STICKERS.forEach(function (st) {
      var b = document.createElement('button');
      b.className = 'sticker-card';
      b.type = 'button';
      var visual = document.createElement('span');
      if (st.type === 'emoji') {
        visual.className = 'emoji';
        visual.textContent = st.text;
      } else {
        visual.className = 'yt';
        visual.textContent = st.text;
        if (st.color === '#8A2BE2' || st.color === '#1E9E55' || st.color === '#E23E3E') {
          visual.style.background = st.color;
        }
      }
      var label = document.createElement('span');
      label.textContent = st.label;
      b.appendChild(visual);
      b.appendChild(label);
      b.addEventListener('click', function () {
        var clip = S.clips[S.sel];
        if (!clip) return toast(t('needPhotos'));
        var o = {
          id: uid(), type: st.type, text: st.text, color: st.color,
          x: 0.5, y: st.type === 'badge' ? 0.72 : 0.35,
          size: st.type === 'badge' ? 0.045 : 0.13
        };
        clip.overlays.push(o);
        S.selOverlay = o.id;
        closeSheets();
        renderTimeline();
        updateOverlayTools();
        redraw();
      });
      grid.appendChild(b);
    });
  }

  /* ------------------------------------------------------------- drag / tap */
  var drag = null;
  function stagePoint(e) {
    var r = stage.getBoundingClientRect();
    var x = (e.clientX - r.left) / r.width;
    var y = (e.clientY - r.top) / r.height;
    return { x: x, y: y };
  }
  function hitTest(px, py) {
    var clip = S.clips[S.sel];
    if (!clip) return null;
    for (var i = clip.overlays.length - 1; i >= 0; i--) {
      var o = clip.overlays[i];
      var b = overlayBounds(ctx, o);
      var cx = o.x * W, cy = o.y * H;
      if (Math.abs(px * W - cx) <= b.w / 2 + 16 && Math.abs(py * H - cy) <= b.h / 2 + 16) return o;
    }
    return null;
  }

  stage.addEventListener('pointerdown', function (e) {
    if (S.playing || S.exporting || !S.clips.length) return;
    var p = stagePoint(e);
    var hit = hitTest(p.x, p.y);
    if (hit) {
      S.selOverlay = hit.id;
      drag = { id: hit.id, dx: hit.x - p.x, dy: hit.y - p.y };
      stage.setPointerCapture(e.pointerId);
      updateOverlayTools();
      redraw();
    } else {
      S.selOverlay = null;
      updateOverlayTools();
      redraw();
    }
  });

  stage.addEventListener('pointermove', function (e) {
    if (!drag) return;
    var clip = S.clips[S.sel];
    if (!clip) return;
    var o = clip.overlays.filter(function (x) { return x.id === drag.id; })[0];
    if (!o) return;
    var p = stagePoint(e);
    o.x = clamp(p.x + drag.dx, 0.02, 0.98);
    o.y = clamp(p.y + drag.dy, 0.03, 0.97);
    redraw();
  });

  function endDrag() { drag = null; }
  stage.addEventListener('pointerup', endDrag);
  stage.addEventListener('pointercancel', endDrag);

  /* ---------------------------------------------------------------- playback */
  function totalDuration() {
    return S.clips.reduce(function (a, c) { return a + c.duration; }, 0);
  }

  async function runTimeline(g, onProgress) {
    var total = totalDuration();
    var done = 0;
    S.abort = false;
    for (var i = 0; i < S.clips.length; i++) {
      if (S.abort) break;
      var clip = S.clips[i];
      if (clip.kind === 'video') {
        try { clip.video.pause(); } catch (e) {}
        try { clip.video.currentTime = 0; } catch (e) {}
        try { await clip.video.play(); } catch (e) {}
      }
      await new Promise(function (resolve) {
        var t0 = performance.now();
        function step() {
          if (S.abort) { finish(); return; }
          var t = (performance.now() - t0) / 1000;
          if (t >= clip.duration) { finish(); return; }
          if (clip.kind === 'video' && clip.video.readyState >= 2) {
            // keep very short clips from ending early: loop them once
            if (clip.video.ended) { try { clip.video.currentTime = 0; clip.video.play(); } catch (e) {} }
          }
          drawFrame(g, clip, t, {});
          if (onProgress) onProgress(done + t, total);
          requestAnimationFrame(step);
        }
        function finish() {
          if (clip.kind === 'video') { try { clip.video.pause(); } catch (e) {} }
          resolve();
        }
        requestAnimationFrame(step);
      });
      done += clip.duration;
      if (onProgress) onProgress(done, total);
    }
    if (onProgress) onProgress(total, total);
  }

  async function playTimeline() {
    if (S.playing || S.exporting || !S.clips.length) return;
    S.playing = true;
    S.selOverlay = null;
    updateOverlayTools();
    selectSheetState(true);
    await runTimeline(ctx, null);
    S.playing = false;
    selectSheetState(false);
    redraw();
  }

  function selectSheetState(busy) {
    $('#previewBtn').disabled = busy;
    $('#makeBtn').disabled = busy;
  }

  $('#previewBtn').addEventListener('click', function () { playTimeline(); });

  /* ------------------------------------------------------------ audio graph */
  var audio = null;
  function ensureAudioGraph() {
    var videos = S.clips.filter(function (c) { return c.kind === 'video'; });
    if (!videos.length) return null;
    if (audio) return audio;
    try {
      var AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return null;
      var ac = new AC();
      var dest = ac.createMediaStreamDestination();
      videos.forEach(function (c) {
        try {
          var src = ac.createMediaElementSource(c.video);
          src.connect(dest);
          src.connect(ac.destination);
        } catch (e) { /* element may already be connected */ }
      });
      audio = { ctx: ac, dest: dest };
      return audio;
    } catch (e) {
      console.warn('audio unavailable', e);
      return null;
    }
  }

  /* ---------------------------------------------------------------- export */
  function pickMime() {
    var candidates = [
      'video/mp4;codecs=avc1.42E01E,mp4a.40.2',
      'video/mp4;codecs=h264',
      'video/mp4',
      'video/webm;codecs=vp9,opus',
      'video/webm;codecs=vp8,opus',
      'video/webm'
    ];
    for (var i = 0; i < candidates.length; i++) {
      if (window.MediaRecorder && MediaRecorder.isTypeSupported(candidates[i])) return candidates[i];
    }
    return '';
  }

  $('#makeBtn').addEventListener('click', makeVideo);

  async function makeVideo() {
    if (S.exporting) return;
    if (!S.clips.length) return toast(t('needPhotos'));
    if (S.playing) { S.abort = true; await new Promise(function (r) { setTimeout(r, 250); }); }

    var mime = pickMime();
    if (!window.MediaRecorder || !mime) {
      toast(t('failed'), 3000);
      return;
    }

    S.exporting = true;
    S.selOverlay = null;
    updateOverlayTools();
    $('#progressBar').style.width = '0%';
    $('#progressText').textContent = '0%';
    openSheet('#progressSheet');
    $('#previewBtn').disabled = true;

    var stream = exportCanvas.captureStream(30);
    var graph = ensureAudioGraph();
    if (graph) {
      try {
        if (graph.ctx.state === 'suspended') await graph.ctx.resume();
        stream.addTrack(graph.dest.stream.getAudioTracks()[0]);
      } catch (e) { console.warn(e); }
    }

    var rec, chunks = [];
    try {
      rec = new MediaRecorder(stream, { mimeType: mime, videoBitsPerSecond: 5000000, audioBitsPerSecond: 128000 });
    } catch (e) {
      try { rec = new MediaRecorder(stream); } catch (e2) {
        S.exporting = false; closeSheets(); toast(t('failed'), 3000); return;
      }
    }
    rec.ondataavailable = function (ev) { if (ev.data && ev.data.size) chunks.push(ev.data); };

    var finished = new Promise(function (resolve) { rec.onstop = resolve; });
    rec.start(1000);

    try {
      await runTimeline(exportCtx, function (cur, total) {
        var pct = Math.round((cur / (total || 1)) * 100);
        $('#progressBar').style.width = pct + '%';
        $('#progressText').textContent = pct + '%';
      });
    } catch (e) {
      console.error(e);
    }

    await new Promise(function (r) { setTimeout(r, 350); });
    try { rec.stop(); } catch (e) {}
    await finished;
    S.exporting = false;
    $('#previewBtn').disabled = false;
    stream.getTracks().forEach(function (tr) { try { tr.stop(); } catch (e) {} });

    var type = rec.mimeType || mime || 'video/webm';
    var blob = new Blob(chunks, { type: type });
    closeSheets();
    if (!blob.size) { toast(t('failed'), 3000); return; }
    await saveVideo(blob);
    redraw();
  }

  /* ----------------------------------------------------------------- saving */
  var lastSavedUri = null;

  async function saveVideo(blob) {
    var isMp4 = /mp4/.test(blob.type);
    var ext = isMp4 ? 'mp4' : 'webm';
    var d = new Date();
    var name = 'MyVideo_' + d.getFullYear() + String(d.getMonth() + 1).padStart(2, '0') +
      String(d.getDate()).padStart(2, '0') + '_' + String(d.getHours()).padStart(2, '0') +
      String(d.getMinutes()).padStart(2, '0') + String(d.getSeconds()).padStart(2, '0') + '.' + ext;

    // 1) Native Android bridge (WebView APK) — saves into the phone Gallery
    if (window.AndroidSaver && typeof window.AndroidSaver.start === 'function') {
      try {
        $('#progressBar').style.width = '0%';
        $('#progressText').textContent = '0%';
        openSheet('#progressSheet');
        var buf = await blob.arrayBuffer();
        var bytes = new Uint8Array(buf);
        var started = JSON.parse(window.AndroidSaver.start(name) || '{"ok":false}');
        if (!started.ok) throw new Error(started.error || 'start failed');
        var CHUNK = 384 * 1024;
        for (var off = 0; off < bytes.length; off += CHUNK) {
          window.AndroidSaver.chunk(bytesToBase64(bytes.subarray(off, off + CHUNK)));
          var pct = Math.round(((off + CHUNK) / bytes.length) * 100);
          $('#progressBar').style.width = Math.min(pct, 100) + '%';
          $('#progressText').textContent = Math.min(pct, 100) + '%';
          await new Promise(function (r) { setTimeout(r, 0); });
        }
        var res = JSON.parse(window.AndroidSaver.finish() || '{"ok":false}');
        if (!res.ok) throw new Error(res.error || 'finish failed');
        lastSavedUri = res.uri || '';
        closeSheets();
        toast(t('savedGallery'), 2600);
        showResult();
        return;
      } catch (e) {
        console.error('native save failed', e);
        closeSheets();
      }
    }

    // 2) Browser / PWA download
    try {
      var url = URL.createObjectURL(blob);
      var a = document.createElement('a');
      a.href = url;
      a.download = name;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      setTimeout(function () { URL.revokeObjectURL(url); }, 60000);
      lastSavedBlob = blob;
      lastSavedName = name;
      toast(t('downloaded'), 2600);
      showResult();
    } catch (e) {
      toast(t('failed'), 3000);
    }
  }

  var lastSavedBlob = null, lastSavedName = null;

  function showResult() {
    var canShareNative = window.AndroidSaver && lastSavedUri;
    var canShareWeb = !!(lastSavedBlob && navigator.canShare &&
      navigator.canShare({ files: [new File([lastSavedBlob], lastSavedName || 'video.mp4', { type: lastSavedBlob.type })] }));
    $('#shareBtn').classList.toggle('hidden', !(canShareNative || canShareWeb));
    openSheet('#resultSheet');
  }

  $('#shareBtn').addEventListener('click', async function () {
    if (window.AndroidSaver && lastSavedUri) {
      try { window.AndroidSaver.share(lastSavedUri); } catch (e) {}
      return;
    }
    if (lastSavedBlob && navigator.canShare) {
      try {
        var f = new File([lastSavedBlob], lastSavedName || 'video.mp4', { type: lastSavedBlob.type });
        await navigator.share({ files: [f], title: 'My video' });
      } catch (e) {}
    }
  });

  /* ----------------------------------------------------------------- sheets */
  function openSheet(sel) { $(sel).classList.remove('hidden'); }
  function closeSheets() { $$('.sheet-backdrop').forEach(function (s) { s.classList.add('hidden'); }); }
  $$('.sheet-backdrop').forEach(function (bd) {
    bd.addEventListener('click', function (e) {
      if (e.target === bd || e.target.hasAttribute('data-close')) closeSheets();
    });
  });

  /* -------------------------------------------------------------- boot / PWA */
  buildStickers();
  applyLang();
  renderAll();

  if ('serviceWorker' in navigator && location.protocol.startsWith('http')) {
    window.addEventListener('load', function () {
      navigator.serviceWorker.register('sw.js').catch(function () {});
    });
  }

  window.addEventListener('resize', fitStage);
  window.addEventListener('orientationchange', function () { setTimeout(fitStage, 250); });
  setTimeout(fitStage, 60);

  window.addEventListener('beforeunload', function (e) {
    if (S.exporting) { e.preventDefault(); e.returnValue = ''; }
  });

  // expose a tiny hook for the Android layer / debugging
  window.SVM = { state: S, makeVideo: makeVideo };
})();
