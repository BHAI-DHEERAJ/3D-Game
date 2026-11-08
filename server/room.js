import { generateMap, moveWithCollision, raycastWorld, lookVector, PROP_CATALOG, boxOf, boxesOverlap, clamp, dist2D } from '../shared/map.js';
import { PHASE, TIMINGS, RULES, TEAM, TICK_MS } from '../shared/constants.js';
import { NavGrid } from '../shared/nav.js';
import { BotBrain } from './bots.js';

let nextId = 1;

// GAME_SPEED=5 makes rounds 5x shorter - handy for testing / quicker matches.
const SPEED = Math.max(0.1, Number(process.env.GAME_SPEED || 1));
const T = (seconds) => seconds / SPEED;

const BOT_NAMES = [
  'NinjaToaster', 'SilentFridge', 'PotatoKing', 'SneakyLamp', 'GhostCrate',
  'CaptainMop', 'LiquidSofa', 'MegaMug', 'ShadowPlant', 'TurboBin',
];

function pick(arr, rnd = Math.random) { return arr[Math.floor(rnd() * arr.length)]; }

export class Room {
  constructor(io, id) {
    this.io = id === 'main' ? io : io;
    this.id = id;
    this.map = generateMap(1337);
    this.nav = new NavGrid(this.map, 0.45);
    this.players = new Map();
    this.phase = PHASE.LOBBY;
    this.phaseEndsAt = Date.now() + T(TIMINGS.lobbyCountdown) * 1000;
    this.round = 0;
    this.events = [];
    this.botsWanted = 5;          // total players the room tries to keep
    this.botNameIdx = 0;
    this.lastTick = Date.now();
    this.grenades = [];
    this.feed = [];

    const hall = this.map.rooms.find((r) => r.hall);
    this.hunterLock = { x0: hall.x1 - 0.6, x1: hall.x2 + 0.6, z0: hall.z1 - 0.6, z1: hall.z2 + 0.6 };

    this.loop = setInterval(() => this.tick(), TICK_MS);
  }

  // ------------------------------------------------------------------ players
  addPlayer(socket, name) {
    const p = this.makePlayer(socket ? socket.id : `bot${nextId++}`, name, false);
    p.conn = socket || null;
    // joining mid-match? watch this one and drop them into the next round
    if (this.phase !== PHASE.LOBBY && this.phase !== PHASE.OVER) {
      p.alive = false;
      p.spectating = true;
    }
    this.players.set(p.id, p);
    return p;
  }

  addBot(name) {
    const p = this.makePlayer(`bot${nextId++}`, name, true);
    this.players.set(p.id, p);
    return p;
  }

  makePlayer(id, name, isBot) {
    return {
      id, name, bot: isBot, conn: null,
      team: TEAM.PROP, alive: true, hp: RULES.propHealth,
      pos: [0, 0, 0], vel: [0, 0, 0], yaw: 0, pitch: 0, onGround: true,
      propType: null, ammo: RULES.hunterAmmo, fireCd: 0, reloading: false, reloadT: 0,
      grenadeCd: 0, medkits: RULES.medkitCharges, score: 0,
      musicTimer: RULES.musicCycle, musicPlaying: 0,
      kills: 0, movedRecently: 0, lastPos: [0, 0, 0],
      ai: isBot ? new BotBrain(this) : null,
      spawnIndex: 0,
    };
  }

  removePlayer(id) {
    const p = this.players.get(id);
    if (!p) return;
    this.players.delete(id);
    this.broadcast({ type: 'feed', text: `${p.name} left` });
  }

  get humans() { return [...this.players.values()].filter((p) => !p.bot); }

  // ------------------------------------------------------------------ match
  ensureBots() {
    const humans = this.humans.length;
    const want = Math.max(0, this.botsWanted - humans);
    let bots = [...this.players.values()].filter((p) => p.bot);
    while (bots.length > want) { this.players.delete(bots.pop().id); bots = [...this.players.values()].filter((p) => p.bot); }
    while (bots.length < want && this.players.size < RULES.maxPlayers) {
      const name = BOT_NAMES[this.botNameIdx++ % BOT_NAMES.length];
      bots.push(this.addBot(name));
    }
  }

  startMatch() {
    this.round++;
    this.ensureBots();
    const list = [...this.players.values()];
    for (let i = list.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [list[i], list[j]] = [list[j], list[i]];
    }
    // Prefer giving the human a turn on both teams: rotate the role offset.
    const offset = this.round % Math.max(2, list.length);
    const propCount = Math.max(1, Math.min(list.length - 1, Math.round(list.length * RULES.propRatio)));

    const props = [...this.map.propSpawns].sort(() => Math.random() - 0.5);
    const hunters = [...this.map.hunterSpawns].sort(() => Math.random() - 0.5);

    list.forEach((p, i) => {
      const isProp = ((i + offset) % list.length) < propCount;
      p.team = isProp ? TEAM.PROP : TEAM.HUNTER;
      p.alive = true;
      p.spectating = false;
      p.hp = isProp ? RULES.propHealth : RULES.hunterHealth;
      p.propType = null;
      p.ammo = RULES.hunterAmmo;
      p.reloading = false; p.reloadT = 0; p.fireCd = 0;
      p.grenadeCd = 0; p.medkits = RULES.medkitCharges;
      p.score = 0; p.kills = 0;
      p.musicTimer = T(RULES.musicCycle) * (0.4 + Math.random() * 0.6);
      p.musicPlaying = 0;
      p.vel = [0, 0, 0];
      const sp = isProp ? props.pop() : hunters.pop();
      p.pos = [sp[0], 0.02, sp[2]];
      p.lastPos = [...p.pos];
      p.yaw = Math.random() * Math.PI * 2;
      p.pitch = 0;
      if (p.ai) p.ai.reset();
    });

    this.grenades = [];
    this.feed = [];
    this.setPhase(PHASE.PREP);
    this.broadcast({ type: 'feed', text: `Round ${this.round} — ${propCount} props vs ${list.length - propCount} hunters` });
    for (const p of list) {
      if (p.conn) p.conn.emit('matchStart', { round: this.round, team: p.team });
    }
  }

  setPhase(phase) {
    this.phase = phase;
    const dur = T(phase === PHASE.PREP ? TIMINGS.prepTime
      : phase === PHASE.HUNT ? TIMINGS.huntTime
        : phase === PHASE.OVER ? TIMINGS.overTime : TIMINGS.lobbyCountdown);
    this.phaseEndsAt = Date.now() + dur * 1000;
    this.events.push({ type: 'phase', phase });
    if (phase === PHASE.HUNT) this.events.push({ type: 'feed', text: 'Hunters released! Find the props.' });
    if (phase === PHASE.PREP) this.events.push({ type: 'feed', text: 'Hide! Hunters are locked in the hallway.' });
  }

  endMatch(reason) {
    if (this.phase === PHASE.OVER) return;
    const props = [...this.players.values()].filter((p) => p.team === TEAM.PROP);
    const hunters = [...this.players.values()].filter((p) => p.team === TEAM.HUNTER);
    const propScore = props.reduce((s, p) => s + Math.round(p.score), 0);
    const hunterScore = hunters.reduce((s, p) => s + Math.round(p.score), 0);
    const winner = reason === 'caught' ? TEAM.HUNTER : (propScore >= hunterScore ? TEAM.PROP : TEAM.HUNTER);
    this.events.push({ type: 'feed', text: reason === 'caught' ? 'All props eliminated — Hunters win!' : 'Time up!' });
    this.setPhase(PHASE.OVER);
    this.io.to(this.id).emit('matchEnd', {
      winner, propScore, hunterScore, reason,
      board: [...this.players.values()]
        .sort((a, b) => b.score - a.score)
        .map((p) => ({ name: p.name, team: p.team, score: Math.round(p.score), kills: p.kills, alive: p.alive, bot: p.bot })),
    });
  }

  // ------------------------------------------------------------------ tick
  tick() {
    const now = Date.now();
    let dt = (now - this.lastTick) / 1000;
    this.lastTick = now;
    if (dt > 0.25) dt = 0.25;

    if (this.phase === PHASE.LOBBY) {
      this.ensureBots();
      if (this.players.size >= 2 && now >= this.phaseEndsAt) this.startMatch();
    } else if (now >= this.phaseEndsAt) {
      if (this.phase === PHASE.PREP) this.setPhase(PHASE.HUNT);
      else if (this.phase === PHASE.HUNT) this.endMatch('time');
      else if (this.phase === PHASE.OVER) this.startMatch();
    }

    const hunting = this.phase === PHASE.HUNT;

    for (const p of this.players.values()) {
      if (!p.alive) continue;
      p.fireCd = Math.max(0, p.fireCd - dt);
      p.grenadeCd = Math.max(0, p.grenadeCd - dt);
      p.movedRecently = Math.max(0, p.movedRecently - dt);
      if (p.reloading) {
        p.reloadT -= dt;
        if (p.reloadT <= 0) { p.reloading = false; p.ammo = RULES.hunterAmmo; }
      }
      if (p.team === TEAM.PROP && hunting) {
        p.score += RULES.scorePerSecond * dt;
        if (p.musicPlaying > 0) p.musicPlaying -= dt;
        else {
          p.musicTimer -= dt;
          if (p.musicTimer <= 0 && p.propType) {
            p.musicPlaying = T(RULES.musicDuration);
            p.musicTimer = T(RULES.musicCycle);
            this.events.push({ type: 'music', id: p.id, pos: p.pos, on: true });
          }
        }
      }
      if (p.bot) p.ai.update(p, dt);
    }

    this.updateGrenades(dt);

    if (hunting) {
      const aliveProps = [...this.players.values()].filter((p) => p.team === TEAM.PROP && p.alive);
      if (aliveProps.length === 0) this.endMatch('caught');
      const aliveHunters = [...this.players.values()].filter((p) => p.team === TEAM.HUNTER && p.alive);
      if (aliveHunters.length === 0) this.endMatch('time');
    }

    this.broadcastState();
  }

  broadcastState() {
    const players = [];
    for (const p of this.players.values()) {
      players.push({
        id: p.id, name: p.name, team: p.team, bot: p.bot, alive: p.alive,
        hp: Math.round(p.hp), pos: [r2(p.pos[0]), r2(p.pos[1]), r2(p.pos[2])],
        yaw: r2(p.yaw), pitch: r2(p.pitch),
        propType: p.propType, ammo: p.ammo, reloading: p.reloading,
        grenadeCd: Math.round(p.grenadeCd * 10) / 10, medkits: p.medkits,
        score: Math.round(p.score), kills: p.kills,
        music: p.musicPlaying > 0,
        grenadeReady: p.grenadeCd <= 0,
        moving: p.movedRecently > 0.15,
      });
    }
    const payload = {
      t: Date.now(),
      phase: this.phase,
      phaseEndsAt: this.phaseEndsAt,
      players,
      grenades: this.grenades.map((g) => [r2(g.pos[0]), r2(g.pos[1]), r2(g.pos[2])]),
      events: this.events,
    };
    this.events = [];
    this.io.to(this.id).emit('state', payload);
  }

  broadcast(event) { this.events.push(event); }

  // ------------------------------------------------------------------ inputs
  playerSize(p) {
    if (p.team === TEAM.PROP && p.propType) return PROP_CATALOG[p.propType].size;
    return [RULES.playerRadius * 2, RULES.playerHeight, RULES.playerRadius * 2];
  }

  eyePos(p) {
    const size = this.playerSize(p);
    return [p.pos[0], p.pos[1] + Math.min(size[1] - 0.12, RULES.eyeHeight), p.pos[2]];
  }

  handleInput(p, data) {
    if (!p || !p.alive) return;
    if (!Array.isArray(data.pos) || !data.pos.every(Number.isFinite)) return;
    const prev = [...p.pos];
    const next = data.pos;

    // --- sanity: no teleporting, no walking through walls ------------------
    const now = Date.now();
    const since = Math.max(0.02, Math.min(0.25, (now - (p.lastInputAt || now - 50)) / 1000));
    p.lastInputAt = now;
    const maxStep = RULES.moveSpeed * RULES.sprintMul * since * 1.35 + 0.05;
    let dx = next[0] - prev[0], dy = next[1] - prev[1], dz = next[2] - prev[2];
    const d = Math.hypot(dx, dz);
    if (Math.abs(dy) > 12) dy = 0;
    if (d > maxStep) { const k = maxStep / d; dx *= k; dz *= k; }
    const size = this.playerSize(p);
    const res = moveWithCollision(this.map.boxes, prev, size, [dx, dy, dz], RULES.playerRadius);
    p.pos = res.pos;
    p.onGround = res.onGround;

    if (this.phase === PHASE.PREP && p.team === TEAM.HUNTER) this.clampHunter(p);

    if (Math.hypot(p.pos[0] - p.lastPos[0], p.pos[2] - p.lastPos[2]) > 0.45) {
      p.movedRecently = 0.6;
      p.lastPos = [...p.pos];
    }
    if (Number.isFinite(data.yaw)) p.yaw = data.yaw;
    if (Number.isFinite(data.pitch)) p.pitch = clamp(data.pitch, -1.4, 1.4);
  }

  clampHunter(p) {
    const l = this.hunterLock;
    p.pos[0] = clamp(p.pos[0], l.x0, l.x1);
    p.pos[2] = clamp(p.pos[2], l.z0, l.z1);
  }

  /** Server-authoritative raycast for a hunter's shot. */
  handleShoot(p) {
    if (!p || !p.alive || p.team !== TEAM.HUNTER) return;
    if (this.phase !== PHASE.HUNT) return;
    if (p.reloading || p.fireCd > 0) return;
    if (p.ammo <= 0) { this.startReload(p); return; }
    p.ammo--;
    p.fireCd = RULES.fireRate;

    const origin = this.eyePos(p);
    const dir = lookVector(p.yaw, p.pitch);
    const hitPlayer = this.raycastPlayers(p, origin, dir, 70);
    const hitWorld = raycastWorld(this.map.boxes, origin, dir, 70);

    let target = null;
    if (hitPlayer && (!hitWorld || hitPlayer.dist < hitWorld.dist)) target = { kind: 'player', ...hitPlayer };
    else if (hitWorld) target = { kind: 'world', ...hitWorld };

    this.io.to(this.id).emit('shot', { id: p.id, origin: origin.map(r2), dir: dir.map((v) => r2(v)) });

    if (!target) return;

    if (target.kind === 'player') {
      const victim = this.players.get(target.id);
      if (!victim || !victim.alive) return;
      if (victim.team === TEAM.HUNTER) return;           // no friendly fire
      this.damage(victim, RULES.hitDamageProp, p, 'shot');
      p.score += RULES.scorePerHit;
    } else {
      // Shot a real object: the hunter pays for it.
      this.damage(p, RULES.selfDamageOnMiss, null, 'object');
      this.events.push({ type: 'objectHit', id: p.id, at: [r2(origin[0] + dir[0] * target.dist), r2(origin[1] + dir[1] * target.dist), r2(origin[2] + dir[2] * target.dist)] });
    }
  }

  raycastPlayers(shooter, origin, dir, maxDist) {
    let best = null;
    for (const p of this.players.values()) {
      if (!p.alive || p.id === shooter.id) continue;
      const size = this.playerSize(p);
      const box = {
        min: [p.pos[0] - size[0] / 2, p.pos[1], p.pos[2] - size[2] / 2],
        max: [p.pos[0] + size[0] / 2, p.pos[1] + size[1], p.pos[2] + size[2] / 2],
      };
      const t = rayBoxT(origin, dir, box, maxDist);
      if (t !== null && (!best || t < best.dist)) best = { dist: t, id: p.id };
    }
    return best;
  }

  /** Line-of-sight test used by bot AI. */
  hasLOS(from, to) {
    const dx = to[0] - from[0], dy = to[1] - from[1], dz = to[2] - from[2];
    const len = Math.hypot(dx, dy, dz);
    if (len < 0.001) return true;
    const dir = [dx / len, dy / len, dz / len];
    const hit = raycastWorld(this.map.boxes, from, dir, len);
    return !hit || hit.dist >= len - 0.15;
  }

  startReload(p) {
    if (p.reloading || p.ammo === RULES.hunterAmmo) return;
    p.reloading = true;
    p.reloadT = RULES.reloadTime;
    this.events.push({ type: 'reload', id: p.id });
  }

  handleGrenade(p) {
    if (!p || !p.alive || p.team !== TEAM.HUNTER || this.phase !== PHASE.HUNT) return;
    if (p.grenadeCd > 0) return;
    p.grenadeCd = RULES.grenadeCooldown;
    const dir = lookVector(p.yaw, p.pitch);
    const o = this.eyePos(p);
    this.grenades.push({
      owner: p.id,
      pos: [o[0] + dir[0] * 0.5, o[1] + dir[1] * 0.5, o[2] + dir[2] * 0.5],
      vel: [dir[0] * 14 + p.vel[0], dir[1] * 14 + 4, dir[2] * 14 + p.vel[2]],
      fuse: RULES.grenadeFuse,
    });
    this.events.push({ type: 'grenadeThrow', id: p.id });
  }

  updateGrenades(dt) {
    for (let i = this.grenades.length - 1; i >= 0; i--) {
      const g = this.grenades[i];
      g.fuse -= dt;
      g.vel[1] -= RULES.gravity * dt;
      const res = moveWithCollision(this.map.boxes, g.pos, [0.24, 0.24, 0.24],
        [g.vel[0] * dt, g.vel[1] * dt, g.vel[2] * dt], 0.14);
      g.pos = res.pos;
      if (res.onGround) { g.vel[0] *= 0.62; g.vel[2] *= 0.62; g.vel[1] = Math.abs(g.vel[1]) > 1.2 ? -g.vel[1] * 0.32 : 0; }
      if (res.hitWall) { g.vel[0] *= -0.35; g.vel[2] *= -0.35; }
      if (g.fuse <= 0) {
        this.explode(g);
        this.grenades.splice(i, 1);
      }
    }
  }

  explode(g) {
    this.events.push({ type: 'explosion', at: [r2(g.pos[0]), r2(g.pos[1]), r2(g.pos[2])] });
    for (const p of this.players.values()) {
      if (!p.alive) continue;
      const d = Math.hypot(p.pos[0] - g.pos[0], p.pos[1] + 0.5 - g.pos[1], p.pos[2] - g.pos[2]);
      if (d > RULES.grenadeRadius) continue;
      const falloff = 1 - d / RULES.grenadeRadius;
      if (p.team === TEAM.PROP) {
        this.damage(p, RULES.grenadeDamage * falloff, this.players.get(g.owner), 'grenade');
      }
    }
  }

  damage(victim, amount, attacker, cause) {
    if (!victim.alive) return;
    victim.hp -= amount;
    if (attacker && attacker !== victim) {
      this.events.push({ type: 'hitmarker', id: attacker.id });
    }
    if (victim.hp <= 0) {
      victim.hp = 0;
      victim.alive = false;
      victim.propType = null;
      if (attacker && attacker.team === TEAM.HUNTER && victim.team === TEAM.PROP) {
        attacker.kills++;
        attacker.score += Math.round(victim.score) + 25;   // take everything the prop earned
        this.events.push({
          type: 'kill', by: attacker.id, byName: attacker.name, victim: victim.id, victimName: victim.name,
          stolen: Math.round(victim.score),
        });
        this.broadcast({ type: 'feed', text: `${attacker.name} caught ${victim.name} (+${Math.round(victim.score) + 25})` });
      } else {
        this.events.push({ type: 'down', victim: victim.id });
        if (victim.team === TEAM.HUNTER) this.broadcast({ type: 'feed', text: `${victim.name} blew themselves up` });
      }
      // Tell the victim's client who got them.
      if (victim.conn) victim.conn.emit('died', { by: attacker ? attacker.name : null, cause });
    }
  }

  /** Prop morphs into whatever object it is looking at. */
  handleTransform(p) {
    if (!p || !p.alive || p.team !== TEAM.PROP) return null;
    const origin = this.eyePos(p);
    const dir = lookVector(p.yaw, p.pitch);
    const hit = raycastWorld(this.map.boxes, origin, dir, RULES.transformRange);
    if (!hit || hit.itemId == null) return null;
    const item = this.map.items[hit.itemId];
    if (!item) return null;
    const def = PROP_CATALOG[item.type];
    if (!def || !def.hide) return null;
    p.propType = item.type;
    this.events.push({ type: 'morph', id: p.id, propType: p.propType });
    return p.propType;
  }

  handleCycleProp(p, dir = 1) {
    if (!p || !p.alive || p.team !== TEAM.PROP) return null;
    // Free-form morph: pick the nearest hiddable object type around the player.
    const near = this.map.items
      .map((it) => ({ it, d: dist2D([it.x, 0, it.z], p.pos) }))
      .filter((e) => e.d < 9 && PROP_CATALOG[e.it.type].hide)
      .sort((a, b) => a.d - b.d);
    if (!near.length) return null;
    const list = near.slice(0, 6);
    const idx = list.findIndex((e) => e.it.type === p.propType);
    const nextItem = list[(idx + dir + list.length) % list.length];
    p.propType = nextItem.it.type;
    this.events.push({ type: 'morph', id: p.id, propType: p.propType });
    return p.propType;
  }

  handleMedkit(p) {
    if (!p || !p.alive || p.team !== TEAM.PROP) return;
    if (p.medkits <= 0 || p.hp >= RULES.propHealth) return;
    p.medkits--;
    p.hp = Math.min(RULES.propHealth, p.hp + RULES.medkitHeal);
    this.events.push({ type: 'heal', id: p.id });
  }

  /** Bot movement helper: walk `p` toward a world point. */
  stepToward(p, target, dt, speedMul = 1) {
    const dx = target[0] - p.pos[0], dz = target[2] - p.pos[2];
    const len = Math.hypot(dx, dz);
    if (len < 0.001) return true;
    const speed = RULES.moveSpeed * speedMul * (p.team === TEAM.PROP && p.propType ? RULES.propSpeedMul : 1);
    const step = Math.min(speed * dt, len);
    const size = this.playerSize(p);
    const res = moveWithCollision(this.map.boxes, p.pos, size,
      [(dx / len) * step, p.vel[1] * dt, (dz / len) * step], RULES.playerRadius * 0.9);
    const before = [...p.pos];
    p.pos = res.pos;
    p.onGround = res.onGround;
    p.movedRecently = 0.5;
    const wantYaw = Math.atan2(-dx, -dz);
    p.yaw = angleTo(p.yaw, wantYaw, dt * 7);
    if (this.phase === PHASE.PREP && p.team === TEAM.HUNTER) this.clampHunter(p);
    return Math.hypot(p.pos[0] - before[0], p.pos[2] - before[2]) < step * 0.35;  // stuck?
  }

  applyGravity(p, dt) {
    const size = this.playerSize(p);
    const res = moveWithCollision(this.map.boxes, p.pos, size, [0, p.vel[1] * dt, 0], RULES.playerRadius * 0.9);
    p.pos = res.pos;
    p.onGround = res.onGround;
    p.vel[1] = res.onGround ? 0 : p.vel[1] - RULES.gravity * dt;
    if (this.phase === PHASE.PREP && p.team === TEAM.HUNTER) this.clampHunter(p);
  }

  dispose() { clearInterval(this.loop); }
}

function rayBoxT(ro, rd, box, maxDist) {
  let t0 = 0, t1 = maxDist;
  for (let i = 0; i < 3; i++) {
    if (Math.abs(rd[i]) < 1e-8) {
      if (ro[i] < box.min[i] || ro[i] > box.max[i]) return null;
    } else {
      const inv = 1 / rd[i];
      let a = (box.min[i] - ro[i]) * inv, b = (box.max[i] - ro[i]) * inv;
      if (a > b) { const t = a; a = b; b = t; }
      t0 = Math.max(t0, a); t1 = Math.min(t1, b);
      if (t0 > t1) return null;
    }
  }
  return t0;
}

function angleTo(cur, want, maxStep) {
  let diff = ((want - cur + Math.PI) % (Math.PI * 2)) - Math.PI;
  if (diff < -Math.PI) diff += Math.PI * 2;
  return cur + clamp(diff, -maxStep, maxStep);
}

function r2(v) { return Math.round(v * 100) / 100; }
