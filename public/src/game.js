import * as THREE from 'three';
import {
  moveWithCollision, raycastWorld, lookVector, PROP_CATALOG, clamp,
} from '/shared/map.js';
import { RULES, PHASE, TEAM } from '/shared/constants.js';
import { buildWorld, setupLighting } from './world.js';
import { buildObject, buildCharacter, buildViewModel, buildBlob, buildGrenade } from './models.js';
import { GameAudio } from './audio.js';
import { HUD } from './hud.js';

const UP = new THREE.Vector3(0, 1, 0);

export class Game {
  constructor(canvas) {
    this.canvas = canvas;
    this.hud = new HUD();
    this.audio = new GameAudio();
    this.socket = null;

    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    this.renderer.setClearColor(0x9fd0e8);

    this.scene = new THREE.Scene();
    this.scene.background = new THREE.Color(0x9fd0e8);
    this.scene.fog = new THREE.Fog(0x9fd0e8, 45, 95);
    this.camera = new THREE.PerspectiveCamera(78, 1, 0.05, 250);
    setupLighting(this.scene);

    this.map = null;
    this.boxes = [];
    this.me = null;
    this.myId = null;
    this.remotes = new Map();
    this.effects = [];
    this.grenadeMeshes = new Map();
    this.clockOffset = 0;
    this.phase = PHASE.LOBBY;
    this.phaseEndsAt = 0;
    this.serverScores = { prop: 0, hunter: 0 };
    this.scoreRows = [];
    this.showBoard = false;
    this.lastInputSent = 0;
    this.lastStateAt = performance.now();
    this.musicNearbyUntil = 0;
    this.deathInfo = null;
    this.spectateIdx = 0;
    this.paused = false;

    this.keys = new Set();
    this.mouse = { down: false, sens: 0.0022 };
    this.locked = false;
    this.freeLook = true;      // cursor-steering fallback, off once pointer lock engages
    this.localFireCd = 0;
    this.recoil = 0;
    this.bob = 0;
    this.stepDist = 0;

    this.local = {
      pos: [0, 0, 0], vel: [0, 0, 0], yaw: 0, pitch: 0,
      onGround: true, alive: true, hp: 100, team: TEAM.PROP,
      propType: null, ammo: 45, reloading: false, grenadeCd: 0, medkits: 2, score: 0,
    };

    this.selfModel = new THREE.Group();
    this.scene.add(this.selfModel);
    this.selfType = null;
    this.selfTeam = null;
    this.blob = buildBlob(0.45);
    this.scene.add(this.blob);

    this.vm = buildViewModel();
    this.vm.position.set(0.20, -0.20, -0.42);
    this.camera.add(this.vm);
    this.scene.add(this.camera);

    this.nameplateLayer = document.getElementById('nameplates');
    this.lockHintEl = document.getElementById('lockHint');

    this._bindEvents();
    this.resize();
    this.lastFrame = performance.now();
    this.raf = requestAnimationFrame(this.frame.bind(this));
  }

  // ------------------------------------------------------------------ net
  connect(name, roomId, bots) {
    this.socket = io({ transports: ['websocket', 'polling'], upgrade: true });
    this.socket.on('connect', () => {
      this.socket.emit('join', { name, roomId, bots });
    });
    this.socket.on('joined', (data) => this.onJoined(data));
    this.socket.on('state', (data) => this.onState(data));
    this.socket.on('shot', (data) => this.onShot(data));
    this.socket.on('matchStart', (data) => {
      this.deathInfo = null;
      this.hud.setDeath(null);
      this.showBoard = false;
      this.showRoleBanner(data.team);
    });
    this.socket.on('matchEnd', (data) => this.onMatchEnd(data));
    this.socket.on('died', (data) => this.onDied(data));
    this.socket.on('pongBack', (t) => { this.hud.setPing(Date.now() - t); });
    this.socket.on('disconnect', () => {
      this.hud.addFeed('Disconnected — reload to rejoin');
    });
    setInterval(() => { if (this.socket?.connected) this.socket.emit('pingBack', Date.now()); }, 2000);
  }

  onJoined(data) {
    this.myId = data.you;
    this.map = data.map;
    this.boxes = data.map.boxes;
    const g = buildWorld(this.scene, this.map);
    this.world = g;
    this.hud.show(true);
    this.started = true;
    this.audio.init();
    this.audio.resume();
    this.requestLock();
  }

  onState(s) {
    const now = Date.now();
    const off = s.t - now;
    this.clockOffset = this.clockOffset ? this.clockOffset * 0.9 + off * 0.1 : off;

    this.phase = s.phase;
    this.phaseEndsAt = s.phaseEndsAt;
    this.grenadePos = s.grenades;

    const seen = new Set();
    let propScore = 0, hunterScore = 0;
    const rows = [];

    for (const p of s.players) {
      seen.add(p.id);
      if (p.team === TEAM.PROP) propScore += p.score; else hunterScore += p.score;
      rows.push({ ...p, me: p.id === this.myId });

      if (p.id === this.myId) {
        const m = this.local;
        const wasAlive = m.alive;
        m.team = p.team; m.alive = p.alive; m.hp = p.hp;
        m.propType = p.propType; m.ammo = p.ammo; m.reloading = p.reloading;
        m.grenadeCd = p.grenadeCd ?? 0; m.medkits = p.medkits ?? 0;
        m.score = p.score; m.kills = p.kills ?? 0;
        if (!p.alive && wasAlive) this.audio.death();
        const drift = Math.hypot(m.pos[0] - p.pos[0], m.pos[1] - p.pos[1], m.pos[2] - p.pos[2]);
        if (!m.spawned || drift > 2.5) { m.pos = [...p.pos]; m.vel[1] = 0; m.spawned = true; }
        continue;
      }

      let r = this.remotes.get(p.id);
      if (!r) {
        r = {
          id: p.id, name: p.name, team: p.team, bot: p.bot,
          group: new THREE.Group(), model: null, modelType: null,
          blob: buildBlob(0.45), buffer: [], moved: false,
          label: document.createElement('div'),
        };
        r.label.className = 'nameplate';
        this.scene.add(r.group);
        this.scene.add(r.blob);
        if (this.nameplateLayer) this.nameplateLayer.appendChild(r.label);
        this.remotes.set(p.id, r);
      }
      r.team = p.team; r.name = p.name; r.alive = p.alive; r.hp = p.hp; r.moving = p.moving;
      r.buffer.push({ t: s.t, pos: p.pos, yaw: p.yaw, pitch: p.pitch, propType: p.propType, alive: p.alive });
      if (r.buffer.length > 12) r.buffer.shift();
    }

    for (const [id, r] of [...this.remotes]) {
      if (!seen.has(id)) {
        this.scene.remove(r.group); this.scene.remove(r.blob);
        r.label.remove();
        this.remotes.delete(id);
      }
    }

    this.serverScores = { prop: propScore, hunter: hunterScore };
    this.scoreRows = rows.sort((a, b) => b.score - a.score);
    this.handleEvents(s.events || [], s.t);
    this.lastStateAt = performance.now();
  }

  handleEvents(events, t) {
    for (const e of events) {
      switch (e.type) {
        case 'feed':
          this.hud.addFeed(e.text);
          break;
        case 'hitmarker':
          if (e.id === this.myId) { this.hud.hitmarker(false); this.audio.hitmarker(); }
          break;
        case 'kill':
          if (e.by === this.myId) { this.hud.hitmarker(true); this.audio.kill(); }
          this.hud.addFeed(`${e.byName} caught ${e.victimName}`);
          break;
        case 'objectHit':
          if (e.id === this.myId) this.hud.damageFlash();
          this.puff(e.at, 0xb9b0a0, 0.5);
          this.audio.step(e.at, false);
          break;
        case 'explosion':
          this.explosion(e.at);
          this.audio.explosion(e.at);
          break;
        case 'morph':
          if (e.id !== this.myId) {
            const r = this.remotes.get(e.id);
            if (r && this.local.alive) {
              const d = Math.hypot(r.group.position.x - this.local.pos[0], r.group.position.z - this.local.pos[2]);
              if (d < 18) this.audio.morph([r.group.position.x, r.group.position.y, r.group.position.z]);
            }
          }
          break;
        case 'music': {
          if (this.local.team === TEAM.HUNTER && this.local.alive) {
            const d = Math.hypot(e.pos[0] - this.local.pos[0], e.pos[2] - this.local.pos[2]);
            if (d < RULES.musicHearRadius) {
              this.audio.music(e.pos);
              this.musicNearbyUntil = performance.now() + 3600;
            }
          }
          break;
        }
        case 'reload':
          if (e.id === this.myId) this.audio.reload();
          break;
        case 'heal':
          if (e.id === this.myId) this.audio.beep(true);
          break;
        case 'phase':
          if (e.phase === PHASE.HUNT) this.audio.beep(true);
          else this.audio.beep(false);
          break;
        default:
          break;
      }
    }
  }

  onShot(s) {
    if (s.id === this.myId) return;
    const o = s.origin, d = s.dir;
    const end = [o[0] + d[0] * 60, o[1] + d[1] * 60, o[2] + d[2] * 60];
    const hit = raycastWorld(this.boxes, o, d, 60);
    if (hit) { end[0] = o[0] + d[0] * hit.dist; end[1] = o[1] + d[1] * hit.dist; end[2] = o[2] + d[2] * hit.dist; }
    this.tracer(o, end);
    this.audio.shot(o, false);
  }

  onDied(data) {
    this.deathInfo = data.by
      ? `<span class="big">CAUGHT</span><br>${data.by} got you${data.cause === 'grenade' ? ' with a grenade' : ''}`
      : `<span class="big">DOWN</span><br>${data.cause === 'object' ? 'Too much friendly furniture fire' : 'You are out'}`;
    this.hud.setDeath(this.deathInfo + '<br><small>spectating… press SPACE to cycle view</small>');
  }

  onMatchEnd(data) {
    this.hud.addFeed(`${data.winner === TEAM.HUNTER ? 'HUNTERS' : 'PROPS'} win — ${data.propScore} vs ${data.hunterScore}`);
    this.scoreRows = (data.board || []).map((b) => ({ ...b, me: b.name === this.local.name }));
    this.showBoard = true;
    this.hud.setScoreboard(this.scoreRows, true);
  }

  // ------------------------------------------------------------------ input
  _bindEvents() {
    window.addEventListener('resize', () => this.resize());

    this.canvas.addEventListener('click', () => {
      if (this.started && document.pointerLockElement !== this.canvas) this.requestLock();
    });

    document.addEventListener('pointerlockchange', () => {
      this.locked = document.pointerLockElement === this.canvas;
      if (!this.locked) { this.keys.clear(); this.mouse.down = false; }
    });

    document.addEventListener('mousemove', (e) => {
      if (!this.local.alive || !this.started) return;
      if (this.locked) {
        this.local.yaw -= e.movementX * this.mouse.sens;
        this.local.pitch -= e.movementY * this.mouse.sens;
        this.local.pitch = clamp(this.local.pitch, -1.45, 1.45);
      } else {
        // fallback: steer with the cursor position when pointer lock is blocked
        this.freeLook = true;
        this.mouseX = e.clientX;
        this.mouseY = e.clientY;
      }
    });
    document.addEventListener('pointerlockerror', () => { this.freeLook = true; });

    document.addEventListener('mousedown', (e) => {
      if (!this.locked) return;
      if (e.button === 0) this.mouse.down = true;
    });
    document.addEventListener('mouseup', (e) => { if (e.button === 0) this.mouse.down = false; });
    document.addEventListener('contextmenu', (e) => { if (this.locked) e.preventDefault(); });

    document.addEventListener('wheel', (e) => {
      if (!this.locked || this.local.team !== TEAM.PROP) return;
      this.socket?.emit('cycleProp', { dir: e.deltaY > 0 ? 1 : -1 });
    }, { passive: true });

    window.addEventListener('keydown', (e) => {
      if (e.code === 'Tab') {
        e.preventDefault();
        this.showBoard = true;
        return;
      }
      if (this.keys.has(e.code)) return;
      this.keys.add(e.code);
      if (!this.active || !this.started) return;

      switch (e.code) {
        case 'KeyE': this.socket?.emit('transform'); break;
        case 'KeyQ': this.socket?.emit('cycleProp', { dir: 1 }); break;
        case 'KeyF': this.socket?.emit('medkit'); break;
        case 'KeyR': this.socket?.emit('reload'); break;
        case 'KeyG': this.socket?.emit('grenade'); break;
        case 'Space':
          if (!this.local.alive) this.cycleSpectate();
          break;
        default: break;
      }
    });

    window.addEventListener('keyup', (e) => {
      this.keys.delete(e.code);
      if (e.code === 'Tab') { this.showBoard = false; }
    });

    window.addEventListener('blur', () => { this.keys.clear(); this.mouse.down = false; });
  }

  requestLock() {
    if (!this.canvas.requestPointerLock) { this.freeLook = true; return; }
    try {
      const r = this.canvas.requestPointerLock();
      if (r && typeof r.catch === 'function') r.catch(() => { this.freeLook = true; });
    } catch { this.freeLook = true; }
  }

  /** Cursor-steering fallback for when pointer lock is unavailable. */
  updateFreeLook(dt) {
    if (this.locked || !this.freeLook || !this.started || !this.local.alive) return;
    const cx = window.innerWidth / 2, cy = window.innerHeight / 2;
    const dx = (this.mouseX ?? cx) - cx;
    const dy = (this.mouseY ?? cy) - cy;
    const dead = Math.min(window.innerWidth, window.innerHeight) * 0.12;
    const ax = Math.abs(dx) > dead ? (dx - Math.sign(dx) * dead) : 0;
    const ay = Math.abs(dy) > dead ? (dy - Math.sign(dy) * dead) : 0;
    this.local.yaw -= ax * dt * 0.0035;
    this.local.pitch -= ay * dt * 0.0028;
    this.local.pitch = clamp(this.local.pitch, -1.45, 1.45);
  }

  cycleSpectate() {
    const alive = [...this.remotes.values()].filter((r) => r.alive && r.team === this.local.team);
    if (!alive.length) return;
    this.spectateIdx = (this.spectateIdx + 1) % alive.length;
    this.spectateTarget = alive[this.spectateIdx];
  }

  resize() {
    const w = window.innerWidth, h = window.innerHeight;
    this.renderer.setSize(w, h, false);
    this.camera.aspect = w / h;
    this.camera.updateProjectionMatrix();
  }

  // ------------------------------------------------------------------ loop
  frame(now) {
    this.raf = requestAnimationFrame(this.frame.bind(this));
    const dt = Math.min(0.05, (now - this.lastFrame) / 1000);
    this.lastFrame = now;

    if (this.started) {
      this.updateFreeLook(dt);
      this.updateLocal(dt);
      this.updateRemotes();
      this.updateEffects(dt);
      this.updateCamera(dt);
      this.updateHud(dt);
      this.updateGrenades();
      this.sendInput(now);
    }
    this.renderer.render(this.scene, this.camera);
  }

  // ------------------------------------------------------------------ local
  /** Playable with pointer lock, or with the cursor-steering fallback. */
  get active() { return this.locked || this.freeLook; }

  playerSize() {
    if (this.local.team === TEAM.PROP && this.local.propType) return PROP_CATALOG[this.local.propType].size;
    return [RULES.playerRadius * 2, RULES.playerHeight, RULES.playerRadius * 2];
  }

  eyeHeight() {
    return Math.min(this.playerSize()[1] - 0.12, RULES.eyeHeight);
  }

  updateLocal(dt) {
    const m = this.local;
    if (!m.alive) return;

    const lockedIn = this.phase === PHASE.PREP && m.team === TEAM.HUNTER;
    let fx = 0, fz = 0;
    if (this.active && !lockedIn) {
      if (this.keys.has('KeyW')) fz += 1;
      if (this.keys.has('KeyS')) fz -= 1;
      if (this.keys.has('KeyA')) fx -= 1;
      if (this.keys.has('KeyD')) fx += 1;
    }
    const len = Math.hypot(fx, fz);
    const sprint = this.keys.has('ShiftLeft') || this.keys.has('ShiftRight');
    let speed = RULES.moveSpeed * (sprint ? RULES.sprintMul : 1);
    if (m.team === TEAM.PROP && m.propType) speed *= RULES.propSpeedMul;

    let vx = 0, vz = 0;
    if (len > 0) {
      fx /= len; fz /= len;
      const sinY = Math.sin(m.yaw), cosY = Math.cos(m.yaw);
      // forward = (-sin yaw, -cos yaw)
      vx = (-sinY * fz + cosY * fx) * speed;
      vz = (-cosY * fz - sinY * fx) * speed;
    }
    m.vel[0] = vx; m.vel[2] = vz;

    if (this.active && !lockedIn && this.keys.has('Space') && m.onGround) {
      m.vel[1] = RULES.jumpSpeed;
      m.onGround = false;
    }
    m.vel[1] -= RULES.gravity * dt;
    if (m.vel[1] < -30) m.vel[1] = -30;

    const size = this.playerSize();
    const res = moveWithCollision(this.boxes, m.pos, size,
      [m.vel[0] * dt, m.vel[1] * dt, m.vel[2] * dt], RULES.playerRadius);
    m.pos = res.pos;
    m.onGround = res.onGround;
    if (res.onGround && m.vel[1] < 0) m.vel[1] = 0;

    if (this.phase === PHASE.PREP && m.team === TEAM.HUNTER) {
      const hall = this.map.rooms.find((r) => r.hall);
      m.pos[0] = clamp(m.pos[0], hall.x1 + 0.5, hall.x2 - 0.5);
      m.pos[2] = clamp(m.pos[2], hall.z1 + 0.5, hall.z2 - 0.5);
    }

    // footsteps
    const movedLen = Math.hypot(m.vel[0], m.vel[2]) * dt;
    this.stepDist += movedLen;
    if (this.stepDist > (sprint ? 2.0 : 2.6) && m.onGround) {
      this.stepDist = 0;
      this.audio.step(m.pos, sprint);
    }
    this.bob += movedLen * 3.2;

    // firing
    this.localFireCd -= dt;
    if (m.team === TEAM.HUNTER && m.alive && this.mouse.down && this.active) {
      if (m.reloading) {
        // wait
      } else if (m.ammo <= 0) {
        if (this.localFireCd <= 0) { this.audio.dryFire(); this.socket?.emit('reload'); this.localFireCd = 0.5; }
      } else if (this.localFireCd <= 0) {
        this.socket?.emit('shoot');
        this.localFireCd = RULES.fireRate;
        this.recoil = 1;
        this.audio.shot(null, true);
        this.vmKick();
      }
    } else if (m.team === TEAM.PROP && this.mouse.down && this.active) {
      // left click for props = quick morph into whatever is under the crosshair
      if (this.localFireCd <= 0) { this.socket?.emit('transform'); this.localFireCd = 0.35; }
    }
    this.recoil = Math.max(0, this.recoil - dt * 6);

    // self model (third person for props)
    this.updateSelfModel();
  }

  updateSelfModel() {
    const m = this.local;
    const wantType = m.alive && m.team === TEAM.PROP ? (m.propType || 'human') : null;
    const wantTeam = m.team;
    if (this.selfType !== wantType || this.selfTeam !== wantTeam) {
      this.selfModel.clear();
      if (wantType) {
        const model = wantType === 'human' ? buildCharacter(m.team) : buildObject(wantType);
        this.selfModel.add(model);
      }
      this.selfType = wantType;
      this.selfTeam = wantTeam;
      this.selfPop = 0;
    }
    if (wantType) {
      this.selfModel.position.set(m.pos[0], m.pos[1], m.pos[2]);
      this.selfModel.rotation.y = m.yaw + Math.PI;
      // little "pop" when you morph
      this.selfPop = Math.min(1, (this.selfPop ?? 1) + 0.12);
      const s = 0.55 + 0.45 * Math.min(1, this.selfPop);
      this.selfModel.scale.setScalar(s);
      const size = this.playerSize();
      this.blob.visible = true;
      this.blob.position.set(m.pos[0], m.pos[1] + 0.02, m.pos[2]);
      this.blob.scale.setScalar(Math.max(0.5, Math.max(size[0], size[2]) * 0.9));
    } else {
      this.blob.visible = false;
    }
  }

  sendInput(now) {
    if (now - this.lastInputSent < 45) return;
    this.lastInputSent = now;
    const m = this.local;
    this.socket?.emit('input', {
      pos: [m.pos[0], m.pos[1], m.pos[2]],
      yaw: m.yaw, pitch: m.pitch,
    });
  }

  // ------------------------------------------------------------------ remotes
  updateRemotes() {
    const renderT = Date.now() + this.clockOffset - 90;
    for (const r of this.remotes.values()) {
      const buf = r.buffer;
      if (!buf.length) continue;
      let a = buf[0], b = buf[buf.length - 1];
      for (let i = 0; i < buf.length - 1; i++) {
        if (buf[i].t <= renderT && buf[i + 1].t >= renderT) { a = buf[i]; b = buf[i + 1]; break; }
      }
      const span = b.t - a.t;
      const alpha = span > 0 ? clamp((renderT - a.t) / span, 0, 1) : 1;
      const px = a.pos[0] + (b.pos[0] - a.pos[0]) * alpha;
      const py = a.pos[1] + (b.pos[1] - a.pos[1]) * alpha;
      const pz = a.pos[2] + (b.pos[2] - a.pos[2]) * alpha;
      let dy = b.yaw - a.yaw;
      while (dy > Math.PI) dy -= Math.PI * 2;
      while (dy < -Math.PI) dy += Math.PI * 2;
      const yaw = a.yaw + dy * alpha;

      r.group.position.set(px, py, pz);
      r.group.rotation.y = yaw + Math.PI;
      r.blob.position.set(px, py + 0.02, pz);

      const wantType = r.alive && r.team === TEAM.PROP && b.propType ? b.propType
        : (r.alive ? 'human' : null);
      if (r.modelType !== wantType) {
        r.group.clear();
        if (wantType === 'human') {
          r.model = buildCharacter(r.team);
          r.group.add(r.model);
        } else if (wantType) {
          r.model = buildObject(wantType);
          r.group.add(r.model);
        }
        r.modelType = wantType;
        r.blob.visible = !!wantType;
        if (wantType && wantType !== 'human') {
          r.blob.scale.setScalar(Math.max(0.5, Math.max(PROP_CATALOG[wantType].size[0], PROP_CATALOG[wantType].size[2]) * 0.9));
        } else {
          r.blob.scale.setScalar(1);
        }
      }
      if (!r.alive) { r.group.visible = false; r.blob.visible = false; }
      else { r.group.visible = true; r.blob.visible = r.modelType !== null; }

      // walk animation for human-shaped models
      if (r.model && r.model.userData.parts && r.moving) {
        const t = performance.now() / 140;
        r.model.userData.parts.legL.rotation.x = Math.sin(t) * 0.6;
        r.model.userData.parts.legR.rotation.x = -Math.sin(t) * 0.6;
        r.model.userData.parts.armL.rotation.x = -Math.sin(t) * 0.5;
        r.model.userData.parts.armR.rotation.x = Math.sin(t) * 0.5;
      }

      this.updateNameplate(r);
    }
  }

  updateNameplate(r) {
    if (!this.nameplateLayer) return;
    const show = r.alive && r.team === this.local.team && this.local.alive;
    if (!show) { r.label.style.display = 'none'; return; }
    const v = new THREE.Vector3(r.group.position.x, r.group.position.y + 2.1, r.group.position.z);
    const toCam = v.clone().sub(this.camera.position);
    const dist = toCam.length();
    if (dist > 34) { r.label.style.display = 'none'; return; }
    const dir = lookVector(this.local.yaw, this.local.pitch);
    if (this.local.team === TEAM.HUNTER) {
      // hunters only see plates in front of them
      if (toCam.normalize().dot(new THREE.Vector3(dir[0], dir[1], dir[2])) < 0.25) {
        r.label.style.display = 'none';
        return;
      }
    }
    v.project(this.camera);
    if (v.z > 1) { r.label.style.display = 'none'; return; }
    r.label.style.display = 'block';
    r.label.style.left = `${(v.x * 0.5 + 0.5) * window.innerWidth}px`;
    r.label.style.top = `${(-v.y * 0.5 + 0.5) * window.innerHeight}px`;
    const hp = Math.max(0, Math.round(r.hp));
    r.label.innerHTML = `<span class="nm">${escapeHtml(r.name)}</span>`
      + `<span class="bar"><i style="width:${hp}%"></i></span>`;
  }

  // ------------------------------------------------------------------ camera
  updateCamera(dt) {
    const m = this.local;

    if (!m.alive) {
      // spectator: orbit the person we're watching
      const target = this.spectateTarget && this.spectateTarget.alive
        ? this.spectateTarget
        : [...this.remotes.values()].find((r) => r.alive && r.team === this.local.team);
      this.spectateTarget = target || null;
      this.selfModel.visible = false;
      this.blob.visible = false;
      if (target) {
        const t = performance.now() / 2600;
        this.camera.position.set(
          target.group.position.x + Math.cos(t) * 7,
          target.group.position.y + 4.2,
          target.group.position.z + Math.sin(t) * 7,
        );
        this.camera.lookAt(target.group.position.x, target.group.position.y + 1, target.group.position.z);
      }
      this.vm.visible = false;
      this.audio.setListener(this.camera.position.toArray(), this.getForward());
      return;
    }

    const eye = [m.pos[0], m.pos[1] + this.eyeHeight(), m.pos[2]];
    const fwd = lookVector(m.yaw, m.pitch);

    if (m.team === TEAM.HUNTER) {
      const bob = Math.sin(this.bob) * 0.022 * (Math.hypot(m.vel[0], m.vel[2]) > 0.5 ? 1 : 0);
      this.camera.position.set(eye[0], eye[1] + bob, eye[2]);
      this.camera.rotation.set(0, 0, 0);
      this.camera.rotation.order = 'YXZ';
      this.camera.rotation.y = m.yaw;
      this.camera.rotation.x = m.pitch + this.recoil * 0.035;
      this.camera.rotation.z = Math.sin(this.bob * 0.5) * 0.006;
      this.vm.visible = true;
      this.selfModel.visible = false;
      this.blob.visible = false;
    } else {
      // third person so you can admire your own terrible disguise
      const dist = 4.2;
      const back = new THREE.Vector3(Math.sin(m.yaw) * dist, 0, Math.cos(m.yaw) * dist);
      const target = new THREE.Vector3(m.pos[0], m.pos[1] + 1.1, m.pos[2]);
      const want = target.clone().add(back);
      want.y = m.pos[1] + 2.4 - m.pitch * 2.0;
      const dir = want.clone().sub(target).normalize();
      const hit = raycastWorld(this.boxes, target.toArray(), dir.toArray(), dist);
      const camPos = hit
        ? target.clone().add(dir.multiplyScalar(Math.max(0.9, hit.dist - 0.35)))
        : want;
      this.camera.position.lerp(camPos, Math.min(1, dt * 14));
      this.camera.lookAt(target.x, target.y + 0.25, target.z);
      this.vm.visible = false;
      this.selfModel.visible = true;
    }

    this.audio.setListener(this.camera.position.toArray(), this.getForward());
  }

  getForward() {
    const v = new THREE.Vector3();
    this.camera.getWorldDirection(v);
    return v.toArray();
  }

  vmKick() {
    const flash = this.vm.userData.flash;
    flash.material.opacity = 1;
    this.vm.position.z = -0.36;
    this.vm.rotation.x = 0.09;
    setTimeout(() => { this.vm.position.z = -0.42; this.vm.rotation.x = 0; }, 45);
  }

  // ------------------------------------------------------------------ effects
  tracer(from, to) {
    const a = new THREE.Vector3(...from);
    const b = new THREE.Vector3(...to);
    const mid = a.clone().add(b).multiplyScalar(0.5);
    const len = a.distanceTo(b);
    const geo = new THREE.CylinderGeometry(0.016, 0.016, len, 5, 1, true);
    const mesh = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({
      color: 0xffe6a8, transparent: true, opacity: 0.85, depthWrite: false,
    }));
    mesh.position.copy(mid);
    mesh.quaternion.setFromUnitVectors(UP, b.clone().sub(a).normalize());
    this.scene.add(mesh);
    this.effects.push({ mesh, life: 0.09, max: 0.09, kind: 'fade' });
  }

  puff(at, color = 0xd8d2c4, scale = 1) {
    const m = new THREE.Mesh(new THREE.SphereGeometry(0.12, 8, 6),
      new THREE.MeshBasicMaterial({ color, transparent: true, opacity: 0.9, depthWrite: false }));
    m.position.set(at[0], at[1], at[2]);
    m.scale.setScalar(0.5);
    this.scene.add(m);
    this.effects.push({ mesh: m, life: 0.35, max: 0.35, kind: 'puff', scale });
  }

  explosion(at) {
    const m = new THREE.Mesh(new THREE.SphereGeometry(1, 16, 12),
      new THREE.MeshBasicMaterial({ color: 0xffb44a, transparent: true, opacity: 0.75, depthWrite: false }));
    m.position.set(at[0], at[1], at[2]);
    this.scene.add(m);
    this.effects.push({ mesh: m, life: 0.5, max: 0.5, kind: 'boom' });
    this.puff(at, 0x6b6257, 2.2);
    const light = new THREE.PointLight(0xffa040, 6, 18, 2);
    light.position.set(at[0], at[1], at[2]);
    this.scene.add(light);
    this.effects.push({ mesh: light, life: 0.25, max: 0.25, kind: 'light' });
  }

  updateEffects(dt) {
    for (let i = this.effects.length - 1; i >= 0; i--) {
      const e = this.effects[i];
      e.life -= dt;
      const k = Math.max(0, e.life / e.max);
      if (e.kind === 'puff') {
        e.mesh.scale.setScalar((1 - k) * 2.2 * (e.scale || 1) + 0.3);
        e.mesh.material.opacity = k * 0.8;
      } else if (e.kind === 'boom') {
        e.mesh.scale.setScalar((1 - k) * RULES.grenadeRadius + 0.5);
        e.mesh.material.opacity = k * 0.7;
      } else if (e.kind === 'light') {
        e.mesh.intensity = k * 6;
      } else {
        e.mesh.material.opacity = k * 0.85;
      }
      if (e.life <= 0) {
        this.scene.remove(e.mesh);
        if (e.mesh.geometry) e.mesh.geometry.dispose();
        if (e.mesh.material) e.mesh.material.dispose();
        this.effects.splice(i, 1);
      }
    }
    // muzzle flash decay
    const flash = this.vm.userData.flash;
    if (flash.material.opacity > 0) flash.material.opacity = Math.max(0, flash.material.opacity - dt * 12);
  }

  updateGrenades() {
    if (!this.grenadePos) return;
    const seen = new Set();
    this.grenadePos.forEach((p, i) => {
      seen.add(i);
      let g = this.grenadeMeshes.get(i);
      if (!g) { g = buildGrenade(); this.scene.add(g); this.grenadeMeshes.set(i, g); }
      g.position.set(p[0], p[1], p[2]);
      g.rotation.x += 0.2; g.rotation.z += 0.15;
    });
    for (const [i, g] of [...this.grenadeMeshes]) {
      if (!seen.has(i)) { this.scene.remove(g); this.grenadeMeshes.delete(i); }
    }
  }

  // ------------------------------------------------------------------ hud
  updateHud(dt) {
    const m = this.local;
    const serverNow = Date.now() + this.clockOffset;
    const left = (this.phaseEndsAt - serverNow) / 1000;
    const names = { lobby: 'STARTING', prep: 'HIDE!', hunt: 'HUNT', over: 'ROUND OVER' };
    this.hud.setPhase(names[this.phase] || '—', left);
    this.hud.setScores(this.serverScores.prop, this.serverScores.hunter);
    this.hud.setHealth(m.hp, m.team === TEAM.HUNTER ? RULES.hunterHealth : RULES.propHealth);
    this.hud.setAmmo(m.ammo, RULES.hunterAmmo, m.reloading);
    this.hud.setRole(m.team, m.propType);
    this.hud.setAbilities({
      grenadeReady: m.grenadeCd <= 0,
      grenadeCd: m.grenadeCd,
      medkits: m.medkits,
      isHunter: m.team === TEAM.HUNTER,
    });
    if (!m.alive && !this.deathInfo) {
      this.hud.setDeath('<span class="big">SPECTATING</span><br>you joined mid-round — next one starts in a moment');
    }
    this.hud.showMusic(performance.now() < this.musicNearbyUntil);
    if (this.showBoard !== this.boardShown || (this.showBoard && performance.now() - (this.boardAt || 0) > 250)) {
      this.hud.setScoreboard(this.scoreRows, this.showBoard);
      this.boardShown = this.showBoard;
      this.boardAt = performance.now();
    }
    if (this.lockHintEl) {
      this.lockHintEl.classList.toggle('hidden', !this.started || this.locked);
      if (!this.locked && this.started) {
        this.lockHintEl.textContent = this.freeLook
          ? 'move the mouse to look · click for pointer lock'
          : 'click to capture mouse';
      }
    }

    if (this.phase === PHASE.PREP && m.team === TEAM.HUNTER) {
      this.hud.setPrompt('Locked in the hallway — props are hiding');
    } else if (m.team === TEAM.PROP && m.alive) {
      const hit = this.aimTarget();
      if (hit && hit.hide) {
        this.hud.setPrompt(`<b>E</b> morph into <b>${hit.label}</b> ${m.propType ? ` · <b>Q</b> shuffle` : ''}`);
      } else if (m.propType) {
        this.hud.setPrompt(`Disguised as <b>${m.propType}</b> · <b>Q</b> shuffle · <b>F</b> medkit x${m.medkits}`);
      } else {
        this.hud.setPrompt('<b>E</b> morph into an object you are looking at');
      }
    } else {
      this.hud.setPrompt(null);
    }
  }

  showRoleBanner(team) {
    const el = document.getElementById('roleBanner');
    if (!el) return;
    el.textContent = team === TEAM.HUNTER ? 'YOU ARE A HUNTER' : 'YOU ARE A PROP';
    el.className = team === TEAM.HUNTER ? 'hunter' : 'prop';
    el.classList.remove('hidden');
    clearTimeout(this._bannerT);
    this._bannerT = setTimeout(() => el.classList.add('hidden'), 2200);
  }

  aimTarget() {
    const m = this.local;
    const eye = [m.pos[0], m.pos[1] + this.eyeHeight(), m.pos[2]];
    const dir = lookVector(m.yaw, m.pitch);
    const hit = raycastWorld(this.boxes, eye, dir, RULES.transformRange + 4);
    if (!hit || hit.itemId == null) return null;
    const item = this.map.items[hit.itemId];
    if (!item) return null;
    const def = PROP_CATALOG[item.type];
    if (!def || !def.hide) return null;
    if (hit.dist > RULES.transformRange) return null;
    return { type: item.type, label: def.label, dist: hit.dist };
  }
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
  ));
}
