// Simple but believable bot AI: hunters patrol / detect / shoot, props hide / flee / re-morph.

import { PHASE, RULES, TEAM } from '../shared/constants.js';
import { PROP_CATALOG, dist2D } from '../shared/map.js';

export class BotBrain {
  constructor(room) {
    this.room = room;
    this.reset();
  }

  reset() {
    this.path = null;
    this.pathIdx = 0;
    this.goal = null;
    this.repathT = 0;
    this.stuckT = 0;
    this.scanT = Math.random() * 0.4;
    this.thinkT = 0;
    this.target = null;         // player id being chased (hunter) / hunter id feared (prop)
    this.fleeT = 0;
    this.relocateT = 8 + Math.random() * 14;
    this.settleT = 0;
    this.skill = 0.55 + Math.random() * 0.45;
    this.jitter = Math.random() * 6;
    this.accuracy = 0.02 + (1 - this.skill) * 0.11;
  }

  // ------------------------------------------------------------------ helpers
  setGoal(p, target) {
    const path = this.room.nav.findPath(p.pos, target);
    this.path = path && path.length ? path : null;
    this.pathIdx = 0;
    this.goal = target;
    this.stuckT = 0;
  }

  followPath(p, dt, speedMul = 1) {
    if (!this.path) return true;
    if (this.pathIdx >= this.path.length) { this.path = null; return true; }
    const pt = this.path[this.pathIdx];
    if (dist2D(p.pos, [pt[0], 0, pt[1]]) < 0.75) {
      this.pathIdx++;
      return this.pathIdx >= this.path.length ? (this.path = null, true) : false;
    }
    const stuck = this.room.stepToward(p, [pt[0], 0, pt[1]], dt, speedMul);
    if (stuck) this.stuckT += dt; else this.stuckT = 0;
    if (this.stuckT > 0.9) { this.setGoal(p, this.goal || [0, 0, 0]); this.stuckT = 0; }
    return false;
  }

  randomWaypoint(p, awayFrom = null) {
    const wps = this.room.map.waypoints;
    if (!awayFrom) return wps[Math.floor(Math.random() * wps.length)];
    // pick the candidate that maximises distance from the threat (with some noise)
    let best = null, bestScore = -Infinity;
    for (let i = 0; i < 6; i++) {
      const w = wps[Math.floor(Math.random() * wps.length)];
      const score = dist2D(w, awayFrom) + Math.random() * 6;
      if (score > bestScore) { bestScore = score; best = w; }
    }
    return best;
  }

  nearestHunter(p) {
    let best = null, bd = Infinity;
    for (const q of this.room.players.values()) {
      if (q.team !== TEAM.HUNTER || !q.alive) continue;
      const d = dist2D(p.pos, q.pos);
      if (d < bd) { bd = d; best = q; }
    }
    return best ? { hunter: best, dist: bd } : null;
  }

  hiddableNear(p, radius = 12) {
    return this.room.map.items
      .map((it) => ({ it, d: dist2D([it.x, 0, it.z], p.pos) }))
      .filter((e) => e.d < radius && PROP_CATALOG[e.it.type].hide)
      .sort((a, b) => a.d - b.d);
  }

  // ------------------------------------------------------------------ main
  update(p, dt) {
    if (!p.alive) return;
    this.room.applyGravity(p, dt);
    if (p.team === TEAM.HUNTER) this.updateHunter(p, dt);
    else this.updateProp(p, dt);
  }

  // ------------------------------------------------------------------ hunter
  updateHunter(p, dt) {
    const room = this.room;
    if (room.phase === PHASE.PREP) {
      p.yaw += dt * 0.8;                       // impatiently looking around the hallway
      const hall = room.map.rooms.find((r) => r.hall);
      if (!this.path && Math.random() < dt * 0.4) {
        this.setGoal(p, [
          hall.x1 + 1 + Math.random() * (hall.x2 - hall.x1 - 2), 0,
          hall.z1 + 1 + Math.random() * (hall.z2 - hall.z1 - 2),
        ]);
      }
      this.followPath(p, dt, 0.5);
      return;
    }
    if (room.phase !== PHASE.HUNT) return;

    this.scanT -= dt;
    this.repathT -= dt;

    // --- perception ---------------------------------------------------------
    if (this.scanT <= 0) {
      this.scanT = 0.35 + Math.random() * 0.25;
      const eye = room.eyePos(p);
      let found = null;
      for (const q of room.players.values()) {
        if (q.team !== TEAM.PROP || !q.alive) continue;
        const d = dist2D(p.pos, q.pos);
        if (d > 30) continue;
        const target = [q.pos[0], q.pos[1] + room.playerSize(q)[1] * 0.6, q.pos[2]];
        const los = room.hasLOS(eye, target);
        let chance = 0;
        if (q.musicPlaying > 0 && d < RULES.musicHearRadius) chance = 0.92;   // loud tunes carry
        else if (los) {
          if (d < 6) chance = 0.15;
          else if (d < 13) chance = 0.07;
          else if (d < 20) chance = 0.03;
          if (q.movedRecently > 0) chance *= 2.4;             // moving objects are obvious
          if (!q.propType) chance += 0.55;                    // running around undisguised
        }
        if (Math.random() < chance * this.skill) { found = q; break; }
      }
      if (found) {
        this.target = found.id;
        if (!this.goal || dist2D(this.goal, found.pos) > 2.5) this.setGoal(p, [found.pos[0], 0, found.pos[2]]);
      } else if (!this.target) {
        // occasional false positive: shoot a boring object and lose 5 hp, like a real player
        if (Math.random() < 0.02 && p.hp > 55) {
          const items = this.hiddableNear(p, 9);
          if (items.length) {
            const it = items[Math.floor(Math.random() * items.length)].it;
            const dx = it.x - p.pos[0], dz = it.z - p.pos[2];
            p.yaw = Math.atan2(-dx, -dz);
            p.pitch = Math.atan2(0.4 - room.eyePos(p)[1] + p.pos[1], Math.hypot(dx, dz));
            room.handleShoot(p);
          }
        }
      }
    }

    const victim = this.target ? room.players.get(this.target) : null;
    if (!victim || !victim.alive) { this.target = null; }

    // --- combat -------------------------------------------------------------
    if (victim && victim.alive) {
      const d = dist2D(p.pos, victim.pos);
      const eye = room.eyePos(p);
      const aim = [victim.pos[0], victim.pos[1] + room.playerSize(victim)[1] * 0.55, victim.pos[2]];
      const los = room.hasLOS(eye, aim);

      if (d < 24) {
        // aim (with human-ish error)
        const dx = aim[0] - eye[0], dy = aim[1] - eye[1], dz = aim[2] - eye[2];
        const want = Math.atan2(-dx, -dz);
        const err = Math.sin(Date.now() / 400 + this.jitter) * this.accuracy;
        p.yaw = lerpAngle(p.yaw, want + err, Math.min(1, dt * (4 + this.skill * 8)));
        p.pitch = Math.atan2(dy, Math.hypot(dx, dz)) + err * 0.4;
        if (los && p.ammo > 0 && d < 26 && Math.abs(shortestAngle(p.yaw, want)) < 0.14) {
          if (p.grenadeCd <= 0 && d < 9 && d > 2.5 && Math.random() < 0.02) room.handleGrenade(p);
          room.handleShoot(p);
        }
      }
      if (p.ammo <= 0) room.startReload(p);

      // reposition: close the gap, but don't hump the prop
      if (this.repathT <= 0) {
        this.repathT = 0.5;
        if (!this.path || dist2D(this.goal || [0, 0, 0], victim.pos) > 2.5) {
          this.setGoal(p, [victim.pos[0], 0, victim.pos[2]]);
        }
      }
      if (d > 3.2 || !los) this.followPath(p, dt, 1);
      else { p.vel[0] = 0; p.vel[2] = 0; this.path = null; }
      return;
    }

    // --- patrol -------------------------------------------------------------
    if (!this.path) this.setGoal(p, this.randomWaypoint(p));
    const done = this.followPath(p, dt, 0.95);
    if (done) { this.path = null; }
    if (p.ammo <= 0) room.startReload(p);
  }

  // ------------------------------------------------------------------ prop
  updateProp(p, dt) {
    const room = this.room;
    if (room.phase === PHASE.LOBBY || room.phase === PHASE.OVER) return;

    const threat = this.nearestHunter(p);
    const lockedIn = room.phase === PHASE.PREP;
    const threatDist = lockedIn ? 99 : (threat ? threat.dist : 99);

    // medkit
    if (p.hp < 55 && p.medkits > 0) room.handleMedkit(p);

    // panic: a hunter is close (or we're making noise) -> run
    if (threatDist < (p.musicPlaying > 0 ? 16 : 9) || this.fleeT > 0) {
      if (this.fleeT <= 0) {
        this.fleeT = 4.5 + Math.random() * 3;
        const away = threat ? [threat.hunter.pos[0], 0, threat.hunter.pos[2]] : [1.5, 0, 0];
        this.setGoal(p, this.randomWaypoint(p, away));
      }
      this.fleeT -= dt;
      const done = this.followPath(p, dt, 1);
      if (done) { this.fleeT = 0; this.settleT = 0.8; }
      return;
    }

    // settle down, pick (or re-pick) a disguise
    if (!p.propType || this.settleT > 0) {
      this.settleT -= dt;
      if (this.settleT <= 0) {
        const near = this.hiddableNear(p, 14)
          .filter((e) => e.d > 0.6);                 // don't morph on top of the real object
        if (near.length) {
          const pool = near.slice(0, 5);
          const choice = pool[Math.floor(Math.random() * pool.length)];
          p.propType = choice.it.type;
          room.events.push({ type: 'morph', id: p.id, propType: p.propType });
        }
        this.settleT = 0;
      }
    }

    // wander to a new hiding place now and then
    this.relocateT -= dt;
    if (this.relocateT <= 0) {
      this.relocateT = 12 + Math.random() * 18;
      const away = threat ? [threat.hunter.pos[0], 0, threat.hunter.pos[2]] : null;
      this.setGoal(p, this.randomWaypoint(p, away));
    }
    if (this.path) {
      const done = this.followPath(p, dt, 0.8);
      if (done) { this.path = null; this.settleT = 0.6; }
    } else {
      // parked: slowly scan the room like a paranoid lamp
      p.yaw += Math.sin(Date.now() / 1400 + this.jitter) * dt * 0.35;
    }
  }
}

function lerpAngle(cur, want, t) {
  return cur + shortestAngle(cur, want) * t;
}
function shortestAngle(cur, want) {
  let diff = ((want - cur + Math.PI) % (Math.PI * 2)) - Math.PI;
  if (diff < -Math.PI) diff += Math.PI * 2;
  return diff;
}
