// Deterministic apartment map: same seed => identical geometry on server and client.
// Pure data + math (no Three.js), so Node can run collision / AI / raycasts with it.

export function mulberry32(a) {
  return function () {
    a |= 0; a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

// ---------------------------------------------------------------------------
// Object catalog. size = [width(x), height(y), depth(z)] in metres.
// `hide` = an object a Prop player is allowed to disguise itself as.
// ---------------------------------------------------------------------------
export const PROP_CATALOG = {
  chair:     { label: 'Chair',      size: [0.58, 0.95, 0.58], hide: true },
  stool:     { label: 'Stool',      size: [0.46, 0.55, 0.46], hide: true },
  crate:     { label: 'Crate',      size: [0.82, 0.82, 0.82], hide: true },
  plant:     { label: 'Potted Plant', size: [0.72, 1.25, 0.72], hide: true },
  bin:       { label: 'Trash Bin',  size: [0.52, 0.78, 0.52], hide: true },
  lamp:      { label: 'Floor Lamp', size: [0.5, 1.65, 0.5], hide: true },
  barrel:    { label: 'Barrel',     size: [0.72, 1.0, 0.72], hide: true },
  toilet:    { label: 'Toilet',     size: [0.62, 0.82, 0.78], hide: true },
  sidetable: { label: 'Side Table', size: [0.92, 0.7, 0.62], hide: true },
  tv:        { label: 'TV',         size: [1.3, 0.9, 0.3], hide: true },
  vase:      { label: 'Vase',       size: [0.42, 0.72, 0.42], hide: true },
  cup:       { label: 'Mug',        size: [0.26, 0.3, 0.26], hide: true },
  bookshelf: { label: 'Bookshelf',  size: [1.1, 1.9, 0.42], hide: true },

  sofa:      { label: 'Sofa',       size: [2.2, 0.85, 0.95], hide: false },
  bed:       { label: 'Bed',        size: [2.05, 0.62, 1.6], hide: false },
  fridge:    { label: 'Fridge',     size: [0.9, 1.9, 0.8], hide: false },
  counter:   { label: 'Kitchen Counter', size: [1.9, 0.95, 0.7], hide: false },
  table:     { label: 'Table',      size: [1.9, 0.78, 1.1], hide: false },
  cabinet:   { label: 'Wardrobe',   size: [1.25, 2.0, 0.6], hide: false },
  sink:      { label: 'Sink',       size: [0.75, 0.92, 0.52], hide: false },
  washer:    { label: 'Washing Machine', size: [0.72, 0.92, 0.72], hide: false },
  rug:       { label: 'Rug',        size: [2.6, 0.03, 1.8], hide: false, flat: true },
};

export const HIDABLE_TYPES = Object.keys(PROP_CATALOG).filter((k) => PROP_CATALOG[k].hide);

const ROOMS = [
  { name: 'Living Room', x1: -20, z1: -15, x2: -2, z2: 2, floor: 0xd9c9a8,
    items: { sofa: 2, table: 1, tv: 1, plant: 2, chair: 2, lamp: 1, bookshelf: 1, crate: 1, rug: 1, sidetable: 1, bin: 1 } },
  { name: 'Kitchen', x1: -20, z1: 2, x2: -2, z2: 15, floor: 0xbfc6cc,
    items: { counter: 3, fridge: 1, cabinet: 1, table: 1, chair: 3, bin: 1, barrel: 1, crate: 2, cup: 3, vase: 1, stool: 1 } },
  { name: 'Bedroom', x1: 5, z1: -15, x2: 20, z2: 2, floor: 0xcbbfa6,
    items: { bed: 1, cabinet: 1, sidetable: 2, lamp: 2, bookshelf: 1, plant: 1, chair: 1, crate: 1, rug: 1, stool: 1 } },
  { name: 'Bathroom', x1: 5, z1: 2, x2: 12, z2: 15, floor: 0xc4d3d6,
    items: { toilet: 2, sink: 1, washer: 1, bin: 1, barrel: 1, cup: 1, stool: 1 } },
  { name: 'Study', x1: 12, z1: 2, x2: 20, z2: 15, floor: 0xcdbfab,
    items: { table: 1, chair: 2, bookshelf: 2, crate: 2, plant: 1, lamp: 1, cup: 2, bin: 1, stool: 1 } },
  { name: 'Hallway', x1: -2, z1: -15, x2: 5, z2: 15, floor: 0xbfb6a3, hall: true,
    items: { plant: 2, crate: 1, bin: 1, bookshelf: 1, barrel: 1, stool: 1 } },
];

const WALL_H = 3.0;
const WALL_T = 0.3;

function aabb(cx, cy, cz, sx, sy, sz) {
  return { min: [cx - sx / 2, cy - sy / 2, cz - sz / 2], max: [cx + sx / 2, cy + sy / 2, cz + sz / 2] };
}

// Build a wall along an axis with door gaps punched out of it.
function wallSegments(boxes, horizontal, a, b1, b2, c, doors) {
  // horizontal: wall runs along X at z=c, from x=b1..b2. vertical: along Z at x=c.
  const gaps = doors.map((d) => [d.at - d.w / 2, d.at + d.w / 2]).sort((p, q) => p[0] - q[0]);
  const parts = [];
  let cursor = b1;
  for (const [g0, g1] of gaps) {
    if (g0 > cursor) parts.push([cursor, g0]);
    cursor = Math.max(cursor, g1);
  }
  if (cursor < b2) parts.push([cursor, b2]);
  for (const [p0, p1] of parts) {
    if (p1 - p0 < 0.05) continue;
    const mid = (p0 + p1) / 2;
    const len = p1 - p0;
    boxes.push(horizontal
      ? Object.assign(aabb(mid, WALL_H / 2, c, len, WALL_H, WALL_T), { kind: 'wall' })
      : Object.assign(aabb(c, WALL_H / 2, mid, WALL_T, WALL_H, len), { kind: 'wall' }));
  }
}

export function generateMap(seed = 1337) {
  const rnd = mulberry32(seed);
  const boxes = [];
  const rooms = ROOMS.map((r) => ({ ...r }));

  // --- exterior + interior walls -----------------------------------------
  const X0 = -20, X1 = 20, Z0 = -15, Z1 = 15;
  // Solid floor slab so "standing" is a real collision, not just a y<0 clamp.
  boxes.push(Object.assign(aabb((X0 + X1) / 2, -0.5, (Z0 + Z1) / 2, (X1 - X0) + 8, 1, (Z1 - Z0) + 8), { kind: 'floor' }));
  wallSegments(boxes, true, 0, X0, X1, Z0, []);          // north
  wallSegments(boxes, true, 0, X0, X1, Z1, []);          // south
  wallSegments(boxes, false, 0, Z0, Z1, X0, []);         // west
  wallSegments(boxes, false, 0, Z0, Z1, X1, []);         // east

  // living | hall   (x = -2)
  wallSegments(boxes, false, 0, Z0, 2, -2, [{ at: -4, w: 2.4 }]);
  // kitchen | hall  (x = -2)
  wallSegments(boxes, false, 0, 2, Z1, -2, [{ at: 7.5, w: 2.4 }]);
  // living | kitchen (z = 2, left half)
  wallSegments(boxes, true, 0, X0, -2, 2, [{ at: -8, w: 2.6 }]);
  // bedroom | hall (x = 5)
  wallSegments(boxes, false, 0, Z0, 2, 5, [{ at: -5, w: 2.4 }]);
  // bath/study | hall (x = 5) - two doors
  wallSegments(boxes, false, 0, 2, Z1, 5, [{ at: 5.5, w: 2.2 }, { at: 11.5, w: 2.2 }]);
  // bedroom | study (z = 2, right half)
  wallSegments(boxes, true, 0, 5, X1, 2, [{ at: 14, w: 2.4 }]);
  // bath | study (x = 12)
  wallSegments(boxes, false, 0, 2, Z1, 12, [{ at: 6.5, w: 2.2 }]);

  // --- furniture ----------------------------------------------------------
  const items = [];
  const doors = [
    [-2, -4], [-2, 7.5], [-8, 2], [5, -5], [5, 5.5], [5, 11.5], [14, 2], [12, 6.5],
  ];

  const overlaps = (x, z, sx, sz) => {
    for (const it of items) {
      const d = PROP_CATALOG[it.type];
      const os = Math.abs(it.rot % 180) === 90 ? [d.size[2], d.size[0]] : [d.size[0], d.size[2]];
      if (Math.abs(x - it.x) < (sx + os[0]) / 2 + 0.45 && Math.abs(z - it.z) < (sz + os[1]) / 2 + 0.45) return true;
    }
    return false;
  };
  const nearDoor = (x, z) => doors.some(([dx, dz]) => Math.hypot(x - dx, z - dz) < 2.1);

  for (const room of rooms) {
    for (const [type, count] of Object.entries(room.items)) {
      const def = PROP_CATALOG[type];
      for (let i = 0; i < count; i++) {
        const rot = [0, 90, 180, 270][Math.floor(rnd() * 4)];
        const swap = rot % 180 === 90;
        const sx = swap ? def.size[2] : def.size[0];
        const sz = swap ? def.size[0] : def.size[2];
        let placed = null;
        for (let attempt = 0; attempt < 40; attempt++) {
          const x = room.x1 + 1.0 + rnd() * (room.x2 - room.x1 - 2.0);
          const z = room.z1 + 1.0 + rnd() * (room.z2 - room.z1 - 2.0);
          if (nearDoor(x, z)) continue;
          if (overlaps(x, z, sx, sz)) continue;
          placed = { x, z };
          break;
        }
        if (!placed) continue;
        const item = { id: items.length, type, x: placed.x, z: placed.z, rot, room: room.name, y: 0 };
        items.push(item);
        if (!def.flat) {
          boxes.push(Object.assign(
            aabb(placed.x, def.size[1] / 2, placed.z, sx, def.size[1], sz),
            { kind: 'item', itemId: item.id, itemType: type },
          ));
        } else {
          boxes.push(Object.assign(
            aabb(placed.x, 0.015, placed.z, sx, 0.03, sz),
            { kind: 'rug', itemId: item.id, itemType: type },
          ));
        }
      }
    }
  }

  // --- spawn points -------------------------------------------------------
  // Never spawn anybody inside a wardrobe: nudge any bad point into the clear.
  const isClear = (p) => {
    for (const b of boxes) {
      if (b.kind === 'floor' || b.kind === 'rug') continue;
      if (p[0] > b.min[0] - 0.55 && p[0] < b.max[0] + 0.55
        && p[2] > b.min[2] - 0.55 && p[2] < b.max[2] + 0.55) return false;
    }
    return true;
  };
  const inRect = (p, r) => p[0] > r.x1 + 0.8 && p[0] < r.x2 - 0.8 && p[2] > r.z1 + 0.8 && p[2] < r.z2 - 0.8;
  const nudge = (p, rect) => {
    if (isClear(p)) return p;
    for (let rad = 0.7; rad <= 5; rad += 0.7) {
      for (let a = 0; a < 16; a++) {
        const th = (a / 16) * Math.PI * 2;
        const c = [p[0] + Math.cos(th) * rad, 0, p[2] + Math.sin(th) * rad];
        if (inRect(c, rect) && isClear(c)) return c;
      }
    }
    return null;
  };

  const hallRect = rooms.find((r) => r.hall);
  const hunterSpawns = [];
  for (let i = 0; i < 8; i++) {
    const p = nudge([1.5, 0, -13 + (i % 4) * 2.2 + (i > 3 ? 1.1 : 0)], hallRect);
    if (p) hunterSpawns.push(p);
  }
  const propSpawns = [];
  for (const room of rooms) {
    if (room.hall) continue;
    const cx = (room.x1 + room.x2) / 2, cz = (room.z1 + room.z2) / 2;
    for (let i = 0; i < 4; i++) {
      const a = (i / 4) * Math.PI * 2 + rnd();
      const r = 2.0 + rnd() * 3.0;
      const p = nudge([
        Math.min(room.x2 - 1.2, Math.max(room.x1 + 1.2, cx + Math.cos(a) * r)),
        0,
        Math.min(room.z2 - 1.2, Math.max(room.z1 + 1.2, cz + Math.sin(a) * r)),
      ], room);
      if (p) propSpawns.push(p);
    }
  }

  // --- waypoints for bot navigation (room centres + door mouths) ----------
  const waypoints = [];
  for (const room of rooms) waypoints.push([(room.x1 + room.x2) / 2, 0, (room.z1 + room.z2) / 2]);
  for (const [dx, dz] of doors) waypoints.push([dx, 0, dz]);
  waypoints.push([-11, 0, -7], [-11, 0, 8], [17, 0, -7], [16, 0, 9], [8, 0, 9], [1.5, 0, 0]);

  return {
    seed,
    name: 'Riverside Apartment',
    bounds: { x0: X0, x1: X1, z0: Z0, z1: Z1, wallHeight: WALL_H },
    rooms: rooms.map(({ name, x1, z1, x2, z2, floor, hall }) => ({ name, x1, z1, x2, z2, floor, hall: !!hall })),
    boxes,
    items,
    hunterSpawns,
    propSpawns,
    waypoints,
  };
}

// ---------------------------------------------------------------------------
// Collision helpers
// ---------------------------------------------------------------------------
export function boxOf(center, size) {
  return {
    min: [center[0] - size[0] / 2, center[1] - size[1] / 2, center[2] - size[2] / 2],
    max: [center[0] + size[0] / 2, center[1] + size[1] / 2, center[2] + size[2] / 2],
  };
}

export function boxesOverlap(a, b) {
  return a.min[0] < b.max[0] && a.max[0] > b.min[0]
    && a.min[1] < b.max[1] && a.max[1] > b.min[1]
    && a.min[2] < b.max[2] && a.max[2] > b.min[2];
}

/**
 * Axis-separated swept resolution for an AABB body.
 * pos = feet-centre [x,y,z]; size = [w,h,d]. Mutates a copy and returns it.
 * Also returns `onGround` and the vertical velocity adjustment.
 */
export function moveWithCollision(boxes, pos, size, delta, radius = null) {
  const out = [pos[0], pos[1], pos[2]];
  let onGround = false;
  let hitWall = false;
  const r = radius ?? Math.min(size[0], size[2]) / 2;   // horizontal half-extent
  const h = size[1];

  const collide = (p) => ({
    min: [p[0] - r, p[1], p[2] - r],
    max: [p[0] + r, p[1] + h, p[2] + r],
  });

  // X
  out[0] += delta[0];
  {
    const b = collide(out);
    for (const w of boxes) {
      if (w.kind === 'rug') continue;
      if (b.min[1] >= w.max[1] - 0.02 || b.max[1] <= w.min[1] + 0.02) continue;
      if (!boxesOverlap(b, w)) continue;
      if (delta[0] > 0) out[0] = w.min[0] - r - 0.001;
      else if (delta[0] < 0) out[0] = w.max[0] + r + 0.001;
      hitWall = true;
      b.min[0] = out[0] - r; b.max[0] = out[0] + r;
    }
  }
  // Z
  out[2] += delta[2];
  {
    const b = collide(out);
    for (const w of boxes) {
      if (w.kind === 'rug') continue;
      if (b.min[1] >= w.max[1] - 0.02 || b.max[1] <= w.min[1] + 0.02) continue;
      if (!boxesOverlap(b, w)) continue;
      if (delta[2] > 0) out[2] = w.min[2] - r - 0.001;
      else if (delta[2] < 0) out[2] = w.max[2] + r + 0.001;
      hitWall = true;
      b.min[2] = out[2] - r; b.max[2] = out[2] + r;
    }
  }
  // Y
  out[1] += delta[1];
  {
    const b = collide(out);
    for (const w of boxes) {
      if (w.kind === 'rug') continue;
      if (!boxesOverlap(b, w)) continue;
      if (delta[1] <= 0) { out[1] = w.max[1] + 0.001; onGround = true; }
      else out[1] = w.min[1] - h - 0.001;
      b.min[1] = out[1]; b.max[1] = out[1] + h;
    }
    if (out[1] <= 0.002) { out[1] = 0; onGround = true; }
  }
  return { pos: out, onGround, hitWall };
}

// Ray vs AABB (slab method). Returns entry distance or null.
export function rayBox(ro, rd, box, maxDist = Infinity) {
  let t0 = 0, t1 = maxDist;
  for (let i = 0; i < 3; i++) {
    if (Math.abs(rd[i]) < 1e-8) {
      if (ro[i] < box.min[i] || ro[i] > box.max[i]) return null;
    } else {
      const inv = 1 / rd[i];
      let a = (box.min[i] - ro[i]) * inv;
      let b = (box.max[i] - ro[i]) * inv;
      if (a > b) { const t = a; a = b; b = t; }
      t0 = Math.max(t0, a);
      t1 = Math.min(t1, b);
      if (t0 > t1) return null;
    }
  }
  return t0;
}

// Closest static-geometry hit. Returns { dist, box, itemId } or null.
export function raycastWorld(boxes, ro, rd, maxDist = 60) {
  let best = null;
  for (const b of boxes) {
    if (b.kind === 'rug') continue;
    const t = rayBox(ro, rd, b, maxDist);
    if (t !== null && (!best || t < best.dist)) best = { dist: t, box: b, itemId: b.itemId ?? null };
  }
  return best;
}

export function lookVector(yaw, pitch) {
  const cp = Math.cos(pitch);
  return [-Math.sin(yaw) * cp, Math.sin(pitch), -Math.cos(yaw) * cp];
}

export function clamp(v, a, b) { return v < a ? a : v > b ? b : v; }

export function dist2D(a, b) { return Math.hypot(a[0] - b[0], a[2] - b[2]); }
