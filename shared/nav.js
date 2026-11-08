// Coarse navigation grid + A*, used by the bots (server side).
// Cells are 0.5m; a cell is blocked if solid geometry taller than STEP_HEIGHT
// overlaps it (inflated by the body radius).

import { boxesOverlap } from './map.js';

const CELL = 0.5;
const STEP_HEIGHT = 0.55;

export class NavGrid {
  constructor(map, radius = 0.45) {
    this.map = map;
    this.radius = radius;
    const b = map.bounds;
    this.x0 = b.x0; this.z0 = b.z0;
    this.cols = Math.ceil((b.x1 - b.x0) / CELL);
    this.rows = Math.ceil((b.z1 - b.z0) / CELL);
    this.blocked = new Uint8Array(this.cols * this.rows);

    const solids = map.boxes.filter((bx) => bx.kind !== 'rug' && bx.max[1] > STEP_HEIGHT);
    const r = radius;
    for (let j = 0; j < this.rows; j++) {
      for (let i = 0; i < this.cols; i++) {
        const cx = this.x0 + (i + 0.5) * CELL;
        const cz = this.z0 + (j + 0.5) * CELL;
        const cell = { min: [cx - r, 0.2, cz - r], max: [cx + r, 1.6, cz + r] };
        for (const s of solids) {
          if (boxesOverlap(cell, s)) { this.blocked[j * this.cols + i] = 1; break; }
        }
      }
    }
  }

  idx(i, j) { return j * this.cols + i; }
  inBounds(i, j) { return i >= 0 && j >= 0 && i < this.cols && j < this.rows; }
  isBlocked(i, j) { return !this.inBounds(i, j) || this.blocked[this.idx(i, j)] === 1; }

  toCell(x, z) {
    return [Math.floor((x - this.x0) / CELL), Math.floor((z - this.z0) / CELL)];
  }
  toWorld(i, j) {
    return [this.x0 + (i + 0.5) * CELL, this.z0 + (j + 0.5) * CELL];
  }

  /** Nearest walkable cell to a world position (spiral search). */
  nearestFree(x, z, maxRings = 12) {
    let [ci, cj] = this.toCell(x, z);
    if (!this.isBlocked(ci, cj)) return [ci, cj];
    for (let ring = 1; ring <= maxRings; ring++) {
      for (let dj = -ring; dj <= ring; dj++) {
        for (let di = -ring; di <= ring; di++) {
          if (Math.max(Math.abs(di), Math.abs(dj)) !== ring) continue;
          if (!this.isBlocked(ci + di, cj + dj)) return [ci + di, cj + dj];
        }
      }
    }
    return null;
  }

  /** A* returning an array of world-space [x,z] waypoints (excluding start). */
  findPath(from, to) {
    const s = this.nearestFree(from[0], from[2]);
    const g = this.nearestFree(to[0], to[2]);
    if (!s || !g) return null;
    const start = this.idx(s[0], s[1]);
    const goal = this.idx(g[0], g[1]);
    if (start === goal) return [[to[0], to[2]]];

    const n = this.cols * this.rows;
    const came = new Int32Array(n).fill(-1);
    const gScore = new Float32Array(n).fill(Infinity);
    const closed = new Uint8Array(n);
    const heap = new MinHeap();
    gScore[start] = 0;
    heap.push(start, 0);

    const h = (idx) => {
      const i = idx % this.cols, j = (idx - i) / this.cols;
      return Math.hypot(i - g[0], j - g[1]);
    };

    let found = false;
    let guard = 0;
    while (heap.size && guard++ < 20000) {
      const cur = heap.pop();
      if (cur === goal) { found = true; break; }
      if (closed[cur]) continue;
      closed[cur] = 1;
      const ci = cur % this.cols, cj = (cur - ci) / this.cols;
      for (let dj = -1; dj <= 1; dj++) {
        for (let di = -1; di <= 1; di++) {
          if (!di && !dj) continue;
          const ni = ci + di, nj = cj + dj;
          if (this.isBlocked(ni, nj)) continue;
          if (di && dj && (this.isBlocked(ci + di, cj) || this.isBlocked(ci, cj + dj))) continue;
          const nidx = this.idx(ni, nj);
          if (closed[nidx]) continue;
          const step = (di && dj) ? 1.4142 : 1;
          const tentative = gScore[cur] + step;
          if (tentative < gScore[nidx]) {
            gScore[nidx] = tentative;
            came[nidx] = cur;
            heap.push(nidx, tentative + h(nidx));
          }
        }
      }
    }
    if (!found) return null;

    const cells = [];
    let cur = goal;
    while (cur !== -1 && cur !== start) { cells.push(cur); cur = came[cur]; }
    cells.reverse();
    // Simplify: keep only corners.
    const pts = cells.map((idx) => {
      const i = idx % this.cols, j = (idx - i) / this.cols;
      return this.toWorld(i, j);
    });
    const simplified = [];
    for (let k = 0; k < pts.length; k++) {
      if (k === 0 || k === pts.length - 1) { simplified.push(pts[k]); continue; }
      const a = pts[k - 1], b = pts[k], c = pts[k + 1];
      const cross = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
      if (Math.abs(cross) > 1e-6) simplified.push(b);
    }
    simplified.push([to[0], to[2]]);
    return simplified;
  }
}

class MinHeap {
  constructor() { this.a = []; }
  get size() { return this.a.length; }
  push(val, key) {
    const a = this.a;
    a.push({ val, key });
    let i = a.length - 1;
    while (i > 0) {
      const p = (i - 1) >> 1;
      if (a[p].key <= a[i].key) break;
      [a[p], a[i]] = [a[i], a[p]];
      i = p;
    }
  }
  pop() {
    const a = this.a;
    const top = a[0];
    const last = a.pop();
    if (a.length) {
      a[0] = last;
      let i = 0;
      for (;;) {
        const l = 2 * i + 1, r = l + 1;
        let m = i;
        if (l < a.length && a[l].key < a[m].key) m = l;
        if (r < a.length && a[r].key < a[m].key) m = r;
        if (m === i) break;
        [a[m], a[i]] = [a[i], a[m]];
        i = m;
      }
    }
    return top.val;
  }
}
