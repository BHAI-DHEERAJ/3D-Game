// Everything is synthesised with the Web Audio API - no sound files shipped.

export class GameAudio {
  constructor() {
    this.ctx = null;
    this.master = null;
    this.noise = null;
    this.enabled = true;
    this.lastStep = 0;
  }

  init() {
    if (this.ctx) return;
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) { this.enabled = false; return; }
    this.ctx = new AC();
    this.master = this.ctx.createGain();
    this.master.gain.value = 0.75;
    this.master.connect(this.ctx.destination);

    const len = this.ctx.sampleRate * 1.2;
    const buf = this.ctx.createBuffer(1, len, this.ctx.sampleRate);
    const d = buf.getChannelData(0);
    for (let i = 0; i < len; i++) d[i] = Math.random() * 2 - 1;
    this.noise = buf;
  }

  resume() { if (this.ctx && this.ctx.state === 'suspended') this.ctx.resume(); }

  setVolume(v) { if (this.master) this.master.gain.value = v; }

  setListener(pos, forward) {
    if (!this.ctx) return;
    const l = this.ctx.listener;
    if (l.positionX) {
      const t = this.ctx.currentTime;
      l.positionX.setValueAtTime(pos[0], t);
      l.positionY.setValueAtTime(pos[1], t);
      l.positionZ.setValueAtTime(pos[2], t);
      l.forwardX.setValueAtTime(forward[0], t);
      l.forwardY.setValueAtTime(forward[1], t);
      l.forwardZ.setValueAtTime(forward[2], t);
      l.upX.setValueAtTime(0, t); l.upY.setValueAtTime(1, t); l.upZ.setValueAtTime(0, t);
    } else if (l.setPosition) {
      l.setPosition(pos[0], pos[1], pos[2]);
      l.setOrientation(forward[0], forward[1], forward[2], 0, 1, 0);
    }
  }

  panner(pos, refDist = 4, maxDist = 60) {
    const p = this.ctx.createPanner();
    p.panningModel = 'equalpower';
    p.distanceModel = 'inverse';
    p.refDistance = refDist;
    p.maxDistance = maxDist;
    p.rolloffFactor = 1.1;
    if (p.positionX) {
      p.positionX.value = pos[0]; p.positionY.value = pos[1]; p.positionZ.value = pos[2];
    } else {
      p.setPosition(pos[0], pos[1], pos[2]);
    }
    p.connect(this.master);
    return p;
  }

  noiseBurst(dest, { dur = 0.2, gain = 0.5, type = 'lowpass', f0 = 1800, f1 = 300, q = 1 } = {}) {
    const src = this.ctx.createBufferSource();
    src.buffer = this.noise;
    src.loop = true;
    const filter = this.ctx.createBiquadFilter();
    filter.type = type;
    filter.Q.value = q;
    const t = this.ctx.currentTime;
    filter.frequency.setValueAtTime(f0, t);
    filter.frequency.exponentialRampToValueAtTime(Math.max(60, f1), t + dur);
    const g = this.ctx.createGain();
    g.gain.setValueAtTime(gain, t);
    g.gain.exponentialRampToValueAtTime(0.0005, t + dur);
    src.connect(filter); filter.connect(g); g.connect(dest);
    src.start(t);
    src.stop(t + dur + 0.02);
    return { src, g, filter };
  }

  tone(dest, freq, { dur = 0.18, gain = 0.25, type = 'sine', delay = 0, sweepTo = null } = {}) {
    const t = this.ctx.currentTime + delay;
    const o = this.ctx.createOscillator();
    o.type = type;
    o.frequency.setValueAtTime(freq, t);
    if (sweepTo) o.frequency.exponentialRampToValueAtTime(sweepTo, t + dur);
    const g = this.ctx.createGain();
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(gain, t + 0.012);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    o.connect(g); g.connect(dest);
    o.start(t); o.stop(t + dur + 0.03);
    return o;
  }

  // ---------------------------------------------------------------- effects
  shot(pos, near = false) {
    if (!this.ctx || !this.enabled) return;
    const dest = pos ? this.panner(pos, 6, 90) : this.master;
    this.noiseBurst(dest, { dur: 0.16, gain: near ? 0.45 : 0.32, f0: 3200, f1: 220 });
    this.tone(dest, 180, { dur: 0.12, gain: near ? 0.3 : 0.2, type: 'square', sweepTo: 60 });
  }

  dryFire() {
    if (!this.ctx || !this.enabled) return;
    this.tone(this.master, 900, { dur: 0.05, gain: 0.12, type: 'square', sweepTo: 400 });
  }

  reload() {
    if (!this.ctx || !this.enabled) return;
    this.tone(this.master, 260, { dur: 0.06, gain: 0.14, type: 'square' });
    this.tone(this.master, 190, { dur: 0.08, gain: 0.14, type: 'square', delay: 0.22 });
    this.tone(this.master, 320, { dur: 0.05, gain: 0.12, type: 'square', delay: 0.5 });
  }

  hitmarker() {
    if (!this.ctx || !this.enabled) return;
    this.tone(this.master, 1500, { dur: 0.05, gain: 0.16, type: 'square' });
    this.tone(this.master, 2100, { dur: 0.05, gain: 0.1, type: 'square', delay: 0.05 });
  }

  hurt() {
    if (!this.ctx || !this.enabled) return;
    this.tone(this.master, 150, { dur: 0.25, gain: 0.3, type: 'sawtooth', sweepTo: 70 });
  }

  explosion(pos) {
    if (!this.ctx || !this.enabled) return;
    const dest = pos ? this.panner(pos, 8, 120) : this.master;
    this.noiseBurst(dest, { dur: 0.75, gain: 0.75, f0: 1400, f1: 60 });
    this.tone(dest, 90, { dur: 0.6, gain: 0.5, type: 'sine', sweepTo: 32 });
  }

  morph(pos) {
    if (!this.ctx || !this.enabled) return;
    const dest = pos ? this.panner(pos, 4, 40) : this.master;
    this.noiseBurst(dest, { dur: 0.32, gain: 0.28, type: 'bandpass', f0: 300, f1: 2600, q: 3 });
    this.tone(dest, 420, { dur: 0.3, gain: 0.14, type: 'triangle', sweepTo: 880 });
  }

  step(pos, running) {
    if (!this.ctx || !this.enabled) return;
    const now = performance.now();
    if (now - this.lastStep < 180) return;
    this.lastStep = now;
    const dest = pos ? this.panner(pos, 2, 22) : this.master;
    this.noiseBurst(dest, { dur: 0.09, gain: running ? 0.16 : 0.1, f0: 900, f1: 180 });
  }

  beep(high = false) {
    if (!this.ctx || !this.enabled) return;
    this.tone(this.master, high ? 1200 : 660, { dur: 0.14, gain: 0.2, type: 'square' });
  }

  kill() {
    if (!this.ctx || !this.enabled) return;
    [880, 1174, 1568].forEach((f, i) => this.tone(this.master, f, { dur: 0.16, gain: 0.18, type: 'triangle', delay: i * 0.07 }));
  }

  death() {
    if (!this.ctx || !this.enabled) return;
    [520, 390, 260].forEach((f, i) => this.tone(this.master, f, { dur: 0.3, gain: 0.2, type: 'sawtooth', delay: i * 0.12 }));
  }

  /** The prop's forced music box tune - this is what gives them away. */
  music(pos) {
    if (!this.ctx || !this.enabled) return;
    const dest = pos ? this.panner(pos, 5, 70) : this.master;
    const notes = [659, 784, 880, 784, 659, 587, 659];
    notes.forEach((f, i) => {
      this.tone(dest, f, { dur: 0.42, gain: 0.19, type: 'triangle', delay: i * 0.32 });
      this.tone(dest, f * 2, { dur: 0.3, gain: 0.06, type: 'sine', delay: i * 0.32 });
    });
  }
}
