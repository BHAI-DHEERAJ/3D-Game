// Every mesh in the game is generated from primitives here - no external assets.
import * as THREE from 'three';

const C = {
  wood: 0x8a5a33, woodLight: 0xc39258, woodDark: 0x5c3b21,
  fabricRed: 0xd4695c, fabricBlue: 0x4f86b8, fabricGreen: 0x6fae72, fabricSand: 0xdcc79a,
  metal: 0x9aa3ab, dark: 0x2f333a, white: 0xf1f0ec, ceramic: 0xe9eef1,
  leaf: 0x4f9d5d, leafDark: 0x3b7d47, screen: 0x171b21, brass: 0xd9a441,
  pot: 0xb5643f, water: 0x7fc4dd, book1: 0xc85b4a, book2: 0x4a86c8, book3: 0xe0b04a,
};

const matCache = new Map();
export function mat(color, opts = {}) {
  const key = `${color}|${JSON.stringify(opts)}`;
  if (!matCache.has(key)) {
    matCache.set(key, new THREE.MeshLambertMaterial({ color, ...opts }));
  }
  return matCache.get(key);
}

const boxGeo = new THREE.BoxGeometry(1, 1, 1);
const cylGeo = new THREE.CylinderGeometry(0.5, 0.5, 1, 12);
const sphGeo = new THREE.SphereGeometry(0.5, 12, 10);
const coneGeo = new THREE.ConeGeometry(0.5, 1, 12);
const icoGeo = new THREE.IcosahedronGeometry(0.5, 0);

function box(w, h, d, color, x = 0, y = 0, z = 0) {
  const m = new THREE.Mesh(boxGeo, mat(color));
  m.scale.set(w, h, d); m.position.set(x, y, z);
  return m;
}
function cyl(r, h, color, x = 0, y = 0, z = 0, rt = r, rb = r, seg = 12) {
  const shared = seg === 12 && rt === r && rb === r;
  const g = shared ? cylGeo : new THREE.CylinderGeometry(rt, rb, h, seg);
  const m = new THREE.Mesh(g, mat(color));
  if (shared) m.scale.set(r * 2, h, r * 2);   // shared geo: radius .5 / height 1
  m.position.set(x, y, z);
  return m;
}
function sphere(r, color, x = 0, y = 0, z = 0) {
  const m = new THREE.Mesh(sphGeo, mat(color));
  m.scale.set(r * 2, r * 2, r * 2); m.position.set(x, y, z);
  return m;
}
function cone(r, h, color, x = 0, y = 0, z = 0) {
  const m = new THREE.Mesh(coneGeo, mat(color));
  m.scale.set(r * 2, h, r * 2); m.position.set(x, y, z);
  return m;
}
function leafy(r, color, x = 0, y = 0, z = 0, s = 1) {
  const m = new THREE.Mesh(icoGeo, mat(color, { flatShading: true }));
  m.scale.set(r * 2 * s, r * 2.2 * s, r * 2 * s);
  m.position.set(x, y, z);
  m.rotation.set(Math.random(), Math.random(), Math.random());
  return m;
}

// ---------------------------------------------------------------------------
// Object catalogue models. Each returns a Group whose feet sit at y = 0.
// ---------------------------------------------------------------------------
const BUILDERS = {
  chair(g) {
    g.add(box(0.5, 0.07, 0.5, C.wood, 0, 0.45, 0));
    g.add(box(0.5, 0.5, 0.07, C.wood, 0, 0.72, -0.21));
    for (const [dx, dz] of [[-0.2, -0.2], [0.2, -0.2], [-0.2, 0.2], [0.2, 0.2]]) {
      g.add(box(0.07, 0.45, 0.07, C.woodDark, dx, 0.225, dz));
    }
  },
  stool(g) {
    g.add(cyl(0.22, 0.06, C.woodLight, 0, 0.5, 0));
    for (let i = 0; i < 3; i++) {
      const a = (i / 3) * Math.PI * 2;
      g.add(box(0.05, 0.5, 0.05, C.woodDark, Math.cos(a) * 0.15, 0.25, Math.sin(a) * 0.15));
    }
  },
  crate(g) {
    g.add(box(0.8, 0.8, 0.8, C.wood, 0, 0.4, 0));
    g.add(box(0.84, 0.09, 0.09, C.woodDark, 0, 0.14, 0.4));
    g.add(box(0.84, 0.09, 0.09, C.woodDark, 0, 0.68, 0.4));
    g.add(box(0.84, 0.09, 0.09, C.woodDark, 0, 0.14, -0.4));
    g.add(box(0.84, 0.09, 0.09, C.woodDark, 0, 0.68, -0.4));
  },
  plant(g) {
    g.add(cyl(0.28, 0.42, C.pot, 0, 0.21, 0, 0.34, 0.22, 14));
    g.add(cyl(0.3, 0.06, 0x8c4a2f, 0, 0.44, 0, 0.34, 0.34, 14));
    g.add(box(0.06, 0.5, 0.06, 0x4a7a3a, 0, 0.7, 0));
    g.add(leafy(0.26, C.leaf, 0, 0.98, 0, 1.05));
    g.add(leafy(0.2, C.leafDark, 0.18, 0.88, 0.06, 0.8));
    g.add(leafy(0.18, C.leaf, -0.16, 0.9, -0.08, 0.75));
  },
  bin(g) {
    g.add(cyl(0.26, 0.72, C.metal, 0, 0.36, 0, 0.3, 0.22, 14));
    g.add(cyl(0.28, 0.05, 0x6f777e, 0, 0.75, 0, 0.32, 0.32, 14));
  },
  lamp(g) {
    g.add(cyl(0.22, 0.05, C.dark, 0, 0.025, 0, 0.24, 0.24, 14));
    g.add(box(0.05, 1.2, 0.05, C.dark, 0, 0.62, 0));
    const shade = new THREE.Mesh(new THREE.CylinderGeometry(0.16, 0.28, 0.34, 14, 1, true),
      new THREE.MeshLambertMaterial({ color: 0xf6e2b0, side: THREE.DoubleSide }));
    shade.position.set(0, 1.4, 0);
    g.add(shade);
    const bulb = sphere(0.09, 0xfff0c0, 0, 1.36, 0);
    g.add(bulb);
  },
  barrel(g) {
    g.add(cyl(0.36, 1.0, C.wood, 0, 0.5, 0, 0.34, 0.34, 14));
    g.add(cyl(0.38, 0.07, C.metal, 0, 0.24, 0, 0.38, 0.38, 14));
    g.add(cyl(0.38, 0.07, C.metal, 0, 0.76, 0, 0.38, 0.38, 14));
  },
  toilet(g) {
    g.add(box(0.5, 0.5, 0.28, C.ceramic, 0, 0.25, -0.24));
    g.add(cyl(0.24, 0.4, C.ceramic, 0, 0.2, 0.1, 0.26, 0.2, 14));
    g.add(cyl(0.26, 0.07, C.white, 0, 0.42, 0.1, 0.28, 0.28, 14));
    g.add(box(0.14, 0.06, 0.1, C.metal, 0, 0.36, -0.36));
  },
  sidetable(g) {
    g.add(box(0.9, 0.07, 0.6, C.woodLight, 0, 0.66, 0));
    for (const [dx, dz] of [[-0.38, -0.24], [0.38, -0.24], [-0.38, 0.24], [0.38, 0.24]]) {
      g.add(box(0.06, 0.63, 0.06, C.woodDark, dx, 0.315, dz));
    }
    g.add(box(0.7, 0.02, 0.4, C.wood, 0, 0.2, 0));
  },
  tv(g) {
    g.add(box(0.5, 0.06, 0.24, C.dark, 0, 0.03, 0));
    g.add(box(0.1, 0.36, 0.08, C.dark, 0, 0.2, 0));
    g.add(box(1.28, 0.74, 0.08, C.dark, 0, 0.78, 0));
    g.add(box(1.16, 0.62, 0.02, C.screen, 0, 0.78, 0.05));
  },
  vase(g) {
    g.add(cyl(0.2, 0.7, 0x7fb8c9, 0, 0.35, 0, 0.12, 0.2, 14));
    g.add(cyl(0.13, 0.06, C.leaf, 0, 0.7, 0, 0.2, 0.13, 14));
  },
  cup(g) {
    g.add(cyl(0.12, 0.28, C.white, 0, 0.14, 0, 0.13, 0.1, 14));
    const handle = new THREE.Mesh(new THREE.TorusGeometry(0.07, 0.022, 6, 10), mat(C.white));
    handle.position.set(0.14, 0.16, 0);
    g.add(handle);
  },
  bookshelf(g) {
    g.add(box(1.08, 1.9, 0.4, C.wood, 0, 0.95, 0));
    g.add(box(1.0, 1.78, 0.02, 0x3a2716, 0, 0.95, 0.2));
    for (let i = 0; i < 4; i++) {
      const y = 0.28 + i * 0.45;
      g.add(box(1.0, 0.05, 0.34, C.woodLight, 0, y, 0));
      let x = -0.44;
      let k = 0;
      while (x < 0.42) {
        const w = 0.06 + ((i * 7 + k * 3) % 4) * 0.015;
        const h = 0.24 + ((i + k) % 3) * 0.03;
        const col = [C.book1, C.book2, C.book3, 0x8f6fb0][(i + k) % 4];
        g.add(box(w, h, 0.26, col, x + w / 2, y + h / 2 + 0.03, 0.02));
        x += w + 0.012;
        k++;
      }
    }
  },
  sofa(g) {
    g.add(box(2.2, 0.34, 0.95, C.fabricBlue, 0, 0.2, 0));
    g.add(box(2.2, 0.6, 0.28, C.fabricBlue, 0, 0.62, -0.34));
    g.add(box(0.28, 0.5, 0.95, C.fabricBlue, -0.96, 0.48, 0));
    g.add(box(0.28, 0.5, 0.95, C.fabricBlue, 0.96, 0.48, 0));
    g.add(box(0.9, 0.16, 0.78, 0x6ea2cc, -0.5, 0.45, 0.05));
    g.add(box(0.9, 0.16, 0.78, 0x6ea2cc, 0.5, 0.45, 0.05));
    for (const [dx, dz] of [[-0.95, -0.38], [0.95, -0.38], [-0.95, 0.38], [0.95, 0.38]]) {
      g.add(box(0.1, 0.06, 0.1, C.woodDark, dx, 0.03, dz));
    }
  },
  bed(g) {
    g.add(box(2.05, 0.3, 1.6, C.wood, 0, 0.16, 0));
    g.add(box(1.95, 0.22, 1.5, C.ceramic, 0, 0.4, 0.03));
    g.add(box(1.95, 0.12, 0.5, 0x6ea2cc, 0, 0.53, -0.48));
    g.add(box(0.75, 0.18, 0.45, C.white, -0.55, 0.6, -0.48));
    g.add(box(0.75, 0.18, 0.45, C.white, 0.55, 0.6, -0.48));
    g.add(box(2.05, 0.7, 0.12, C.woodDark, 0, 0.42, -0.8));
  },
  fridge(g) {
    g.add(box(0.9, 1.9, 0.8, 0xdfe3e6, 0, 0.95, 0));
    g.add(box(0.02, 1.84, 0.02, 0xb6bdc2, 0.46, 0.95, 0.4));
    g.add(box(0.05, 0.5, 0.05, C.metal, 0.34, 1.34, 0.43));
    g.add(box(0.05, 0.4, 0.05, C.metal, 0.34, 0.62, 0.43));
  },
  counter(g) {
    g.add(box(1.9, 0.9, 0.7, 0xcfd5d8, 0, 0.45, 0));
    g.add(box(2.0, 0.08, 0.76, 0x59636b, 0, 0.94, 0));
    g.add(box(0.86, 0.7, 0.02, 0xb9c0c5, -0.45, 0.45, 0.36));
    g.add(box(0.86, 0.7, 0.02, 0xb9c0c5, 0.45, 0.45, 0.36));
    g.add(box(0.3, 0.04, 0.04, C.metal, -0.28, 0.62, 0.38));
    g.add(box(0.3, 0.04, 0.04, C.metal, 0.28, 0.62, 0.38));
  },
  table(g) {
    g.add(box(1.9, 0.08, 1.1, C.woodLight, 0, 0.74, 0));
    for (const [dx, dz] of [[-0.82, -0.44], [0.82, -0.44], [-0.82, 0.44], [0.82, 0.44]]) {
      g.add(box(0.09, 0.7, 0.09, C.wood, dx, 0.35, dz));
    }
    g.add(box(0.3, 0.24, 0.3, C.fabricRed, 0.4, 0.9, 0));
  },
  cabinet(g) {
    g.add(box(1.25, 2.0, 0.6, C.wood, 0, 1.0, 0));
    g.add(box(0.58, 1.9, 0.03, C.woodLight, -0.31, 1.0, 0.3));
    g.add(box(0.58, 1.9, 0.03, C.woodLight, 0.31, 1.0, 0.3));
    g.add(box(0.06, 0.3, 0.04, C.brass, -0.06, 1.0, 0.33));
    g.add(box(0.06, 0.3, 0.04, C.brass, 0.06, 1.0, 0.33));
  },
  sink(g) {
    g.add(cyl(0.15, 0.6, C.white, 0, 0.3, -0.06, 0.17, 0.19, 12));
    g.add(cyl(0.3, 0.24, C.white, 0, 0.7, 0, 0.32, 0.26, 14));
    g.add(box(0.05, 0.24, 0.05, C.metal, 0, 0.88, -0.14));
    g.add(box(0.18, 0.04, 0.04, C.metal, 0, 0.98, -0.08));
  },
  washer(g) {
    g.add(box(0.72, 0.92, 0.72, C.white, 0, 0.46, 0));
    const door = cyl(0.2, 0.05, C.water, 0, 0.48, 0.36, 0.22, 0.22, 16);
    door.rotation.x = Math.PI / 2;
    g.add(door);
    g.add(box(0.5, 0.1, 0.03, 0xcfd5d8, 0, 0.8, 0.37));
    g.add(box(0.04, 0.04, 0.02, 0x6fae72, -0.18, 0.8, 0.38));
  },
  rug(g) {
    g.add(box(2.6, 0.03, 1.8, 0xb4666a, 0, 0.015, 0));
    g.add(box(2.2, 0.032, 1.4, 0xcfd5d8, 0, 0.016, 0));
    g.add(box(1.8, 0.034, 1.0, 0x8f6fb0, 0, 0.017, 0));
  },
};

export function buildObject(type) {
  const g = new THREE.Group();
  (BUILDERS[type] || BUILDERS.crate)(g);
  g.userData.type = type;
  return g;
}

// ---------------------------------------------------------------------------
// Characters
// ---------------------------------------------------------------------------
export function buildCharacter(team) {
  const g = new THREE.Group();
  const shirt = team === 'hunter' ? 0x37527a : 0x3f8f7a;
  const pants = team === 'hunter' ? 0x26364f : 0x2c6257;
  const skin = 0xe0b48c;

  const legL = box(0.2, 0.62, 0.24, pants, -0.14, 0.31, 0);
  const legR = box(0.2, 0.62, 0.24, pants, 0.14, 0.31, 0);
  g.add(legL, legR);
  g.add(box(0.52, 0.6, 0.3, shirt, 0, 0.92, 0));
  const armL = box(0.14, 0.52, 0.16, shirt, -0.33, 0.94, 0);
  const armR = box(0.14, 0.52, 0.16, shirt, 0.33, 0.94, 0);
  g.add(armL, armR);
  g.add(box(0.16, 0.12, 0.18, skin, -0.33, 0.63, 0));
  g.add(box(0.16, 0.12, 0.18, skin, 0.33, 0.63, 0));
  g.add(box(0.34, 0.34, 0.32, skin, 0, 1.4, 0));
  g.add(box(0.36, 0.12, 0.34, team === 'hunter' ? 0x1d2430 : 0x2b5f52, 0, 1.58, 0));
  g.add(box(0.1, 0.1, 0.06, 0x1b1f26, -0.09, 1.38, 0.17));
  g.add(box(0.1, 0.1, 0.06, 0x1b1f26, 0.09, 1.38, 0.17));

  g.userData.parts = { legL, legR, armL, armR };
  return g;
}

/** First-person weapon view-model (attached to the camera). */
export function buildViewModel() {
  const g = new THREE.Group();
  g.add(box(0.09, 0.12, 0.5, 0x3a4048, 0, -0.02, -0.28));
  g.add(box(0.07, 0.07, 0.34, 0x20252c, 0, 0.02, -0.66));
  g.add(box(0.14, 0.22, 0.1, 0x2b3138, 0, -0.17, -0.12));
  g.add(box(0.06, 0.1, 0.16, 0x20252c, 0, 0.08, -0.2));
  g.add(box(0.05, 0.05, 0.05, 0xd9a441, 0, 0.06, -0.86));
  const flash = new THREE.Mesh(new THREE.SphereGeometry(0.09, 8, 6),
    new THREE.MeshBasicMaterial({ color: 0xffd489, transparent: true, opacity: 0 }));
  flash.position.set(0, 0.02, -0.86);
  g.add(flash);
  g.userData.flash = flash;
  return g;
}

export function buildGrenade() {
  const g = new THREE.Group();
  g.add(sphere(0.13, 0x4a5a3a, 0, 0, 0));
  g.add(box(0.05, 0.09, 0.05, 0x2f333a, 0, 0.15, 0));
  return g;
}

/** Soft blob shadow used instead of real-time shadow maps. */
export function buildBlob(radius = 0.5) {
  const geo = new THREE.CircleGeometry(radius, 16);
  const m = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({
    color: 0x000000, transparent: true, opacity: 0.22, depthWrite: false,
  }));
  m.rotation.x = -Math.PI / 2;
  m.position.y = 0.02;
  m.renderOrder = 1;
  return m;
}
