import * as THREE from 'three';
import { buildObject, mat } from './models.js';

const WALL_COLOR = 0xeae4d8;
const WALL_TOP = 0xcfc7b8;

export function buildWorld(scene, map) {
  const group = new THREE.Group();
  scene.add(group);

  // --- ground beyond the building ------------------------------------------
  const ground = new THREE.Mesh(
    new THREE.PlaneGeometry(200, 200),
    new THREE.MeshLambertMaterial({ color: 0x8fa06d }),
  );
  ground.rotation.x = -Math.PI / 2;
  ground.position.y = -0.06;
  group.add(ground);

  // --- floors ---------------------------------------------------------------
  const floorGeo = new THREE.PlaneGeometry(1, 1);
  for (const room of map.rooms) {
    const floor = new THREE.Mesh(floorGeo, new THREE.MeshLambertMaterial({ color: room.floor }));
    floor.rotation.x = -Math.PI / 2;
    floor.scale.set(room.x2 - room.x1, room.z2 - room.z1, 1);
    floor.position.set((room.x1 + room.x2) / 2, 0, (room.z1 + room.z2) / 2);
    group.add(floor);
  }

  // --- walls ----------------------------------------------------------------
  const wallMat = mat(WALL_COLOR);
  const topMat = mat(WALL_TOP);
  const boxGeo = new THREE.BoxGeometry(1, 1, 1);
  for (const b of map.boxes) {
    if (b.kind !== 'wall') continue;
    const sx = b.max[0] - b.min[0], sy = b.max[1] - b.min[1], sz = b.max[2] - b.min[2];
    const m = new THREE.Mesh(boxGeo, wallMat);
    m.scale.set(sx, sy, sz);
    m.position.set((b.min[0] + b.max[0]) / 2, (b.min[1] + b.max[1]) / 2, (b.min[2] + b.max[2]) / 2);
    group.add(m);
    const cap = new THREE.Mesh(boxGeo, topMat);
    cap.scale.set(sx + 0.06, 0.1, sz + 0.06);
    cap.position.set(m.position.x, b.max[1] + 0.05, m.position.z);
    group.add(cap);
  }

  // --- furniture ------------------------------------------------------------
  const itemMeshes = new Map();
  for (const item of map.items) {
    const model = buildObject(item.type);
    model.position.set(item.x, 0, item.z);
    model.rotation.y = (item.rot * Math.PI) / 180;
    group.add(model);
    itemMeshes.set(item.id, model);
  }

  return { group, itemMeshes };
}

export function setupLighting(scene) {
  const hemi = new THREE.HemisphereLight(0xcfe4ff, 0x6d5b43, 0.95);
  scene.add(hemi);

  const sun = new THREE.DirectionalLight(0xfff1d4, 0.85);
  sun.position.set(24, 34, 14);
  scene.add(sun);

  const fill = new THREE.DirectionalLight(0xbcd4ff, 0.28);
  fill.position.set(-18, 20, -22);
  scene.add(fill);

  for (const [x, z, c] of [[-11, -7, 0xffd9a0], [-11, 8, 0xcfe8ff], [12, -7, 0xffd9a0], [16, 9, 0xcfe8ff]]) {
    const p = new THREE.PointLight(c, 0.55, 16, 2);
    p.position.set(x, 2.7, z);
    scene.add(p);
  }
  return { hemi, sun };
}
