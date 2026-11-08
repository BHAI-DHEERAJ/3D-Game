// Builds the whole world + every model in Node (no WebGL needed) to catch
// runtime errors in the client art/scene code.
import * as THREE from 'three';
import { generateMap, PROP_CATALOG, moveWithCollision, raycastWorld, lookVector } from '../shared/map.js';
import { buildWorld, setupLighting } from '../public/src/world.js';
import { buildObject, buildCharacter, buildViewModel, buildGrenade, buildBlob } from '../public/src/models.js';
import { RULES } from '../shared/constants.js';
import { NavGrid } from '../shared/nav.js';

let fail = 0;
const ok = (cond, msg) => { console.log(`${cond ? '  ok  ' : ' FAIL '} ${msg}`); if (!cond) fail++; };

const map = generateMap(1337);
ok(map.items.length > 40, `map generated: ${map.items.length} items, ${map.boxes.length} boxes, ${map.rooms.length} rooms`);

const scene = new THREE.Scene();
const t0 = Date.now();
const world = buildWorld(scene, map);
setupLighting(scene);
ok(true, `world built in ${Date.now() - t0}ms`);

let meshes = 0;
scene.traverse((o) => { if (o.isMesh) meshes++; });
console.log(`       scene contains ${meshes} meshes`);

// every catalogue model builds and roughly matches its declared size
for (const [type, def] of Object.entries(PROP_CATALOG)) {
  const g = buildObject(type);
  const bb = new THREE.Box3().setFromObject(g);
  const size = bb.getSize(new THREE.Vector3());
  const okW = Math.abs(size.x - def.size[0]) < 0.22;
  const okH = Math.abs(size.y - def.size[1]) < 0.45;
  const okD = Math.abs(size.z - def.size[2]) < 0.22;
  ok(okW && okH && okD,
    `model ${type.padEnd(10)} ${size.x.toFixed(2)}x${size.y.toFixed(2)}x${size.z.toFixed(2)} vs def ${def.size.join('x')}`);
}

ok(!!buildCharacter('hunter') && !!buildCharacter('prop'), 'character models build');
ok(!!buildViewModel().userData.flash, 'view model + muzzle flash build');
ok(!!buildGrenade() && !!buildBlob(), 'grenade + blob build');

// --- collision sanity --------------------------------------------------------
const spawns = [...map.hunterSpawns, ...map.propSpawns];
let stuck = 0;
let buried = 0;
for (const sp of spawns) {
  // drop the body from 1.2m and let it fall for a second
  let pos = [sp[0], 1.2, sp[2]];
  let vel = 0;
  let grounded = false;
  for (let i = 0; i < 60; i++) {
    vel -= RULES.gravity * (1 / 60);
    const r = moveWithCollision(map.boxes, pos, [0.84, 1.75, 0.84], [0, vel / 60, 0], RULES.playerRadius);
    pos = r.pos;
    if (r.onGround) { vel = 0; grounded = true; break; }
  }
  if (!grounded || pos[1] > 0.05) { stuck++; console.log('       bad spawn', sp, '->', pos.map((v) => v.toFixed(2)).join(',')); }
  // and it must not be inside a wall either
  for (const b of map.boxes) {
    if (b.kind === 'floor' || b.kind === 'rug') continue;
    if (pos[0] > b.min[0] - 0.3 && pos[0] < b.max[0] + 0.3
      && pos[2] > b.min[2] - 0.3 && pos[2] < b.max[2] + 0.3 && pos[1] < b.max[1] - 0.2) { buried++; break; }
  }
}
ok(stuck === 0, `all ${spawns.length} spawn points drop onto solid ground`);
ok(buried === 0, `no spawn point is inside furniture`);

// navigation: every room centre must be reachable from the hallway spawn
const nav = new NavGrid(map, 0.45);
const start = [1.5, 0, -13];
let reached = 0;
let worstLen = 0;
for (const room of map.rooms) {
  const target = [(room.x1 + room.x2) / 2, 0, (room.z1 + room.z2) / 2];
  const path = nav.findPath(start, target);
  if (path) { reached++; worstLen = Math.max(worstLen, path.length); }
}
ok(reached === map.rooms.length,
  `A* reaches ${reached}/${map.rooms.length} room centres from the hallway (longest path ${worstLen} waypoints)`);

// the path must actually be walkable by a real body
const pathToBedroom = nav.findPath(start, [17, 0, -7]);
let walked = true;
let pos = [...start];
for (const [px, pz] of (pathToBedroom || [])) {
  for (let i = 0; i < 200; i++) {
    const dx = px - pos[0], dz = pz - pos[2];
    const d = Math.hypot(dx, dz);
    if (d < 0.3) break;
    const step = Math.min(0.05, d);
    const res = moveWithCollision(map.boxes, pos, [0.84, 1.75, 0.84], [(dx / d) * step, 0, (dz / d) * step], RULES.playerRadius);
    if (Math.hypot(res.pos[0] - pos[0], res.pos[2] - pos[2]) < 0.001) { walked = false; break; }
    pos = res.pos;
  }
  if (!walked) break;
}
ok(walked && Math.hypot(pos[0] - 17, pos[2] + 7) < 2.5,
  `walking the A* path reaches the bedroom (ended at ${pos[0].toFixed(1)}, ${pos[2].toFixed(1)})`);

// raycast: the hallway is a long corridor, so a straight shot should stop at a wall
const hit = raycastWorld(map.boxes, [1.5, 1.6, 0], lookVector(0, 0), 60);
ok(hit && hit.dist > 10 && hit.dist < 20, `raycast from hallway hits geometry at ${hit ? hit.dist.toFixed(2) : '∞'}m`);

console.log(fail ? `\n${fail} CHECK(S) FAILED` : '\nCLIENT SMOKE OK');
process.exit(fail ? 1 : 0);
