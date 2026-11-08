// Headless smoke test: joins fake clients into a room and plays a few rounds.
// Usage: GAME_SPEED=10 node server/index.js  (separate terminal / port)
//        node test/smoke.js [port] [seconds]
import { io } from 'socket.io-client';

const PORT = process.argv[2] || 3000;
const SECONDS = Number(process.argv[3] || 30);
const URL = `http://127.0.0.1:${PORT}`;

const clients = [];
let errors = 0;
const seen = { phases: new Set(), kills: 0, morphs: 0, shots: 0, explosions: 0, music: 0, maxSpeedViolation: 0 };

function makeClient(name, isHunterWannabe = false) {
  const socket = io(URL, { transports: ['websocket'], forceNew: true });
  const c = {
    socket, name, me: null, map: null, last: null,
    pos: [0, 0, 0], yaw: 0, pitch: 0, t: 0, dir: Math.random() * Math.PI * 2,
  };
  socket.on('connect_error', (e) => { console.error(`[${name}] connect error`, e.message); errors++; });
  socket.on('joined', (d) => { c.me = d.you; c.map = d.map; console.log(`[${name}] joined as ${d.you} (map: ${d.map.items.length} items, ${d.map.boxes.length} boxes)`); });
  socket.on('matchStart', (d) => console.log(`[${name}] match ${d.round} started`));
  socket.on('matchEnd', (d) => {
    console.log(`[${name}] match over - winner=${d.winner} props=${d.propScore} hunters=${d.hunterScore}`);
    console.log(d.board.map((b) => `   ${b.name.padEnd(14)} ${b.team.padEnd(7)} score=${String(b.score).padStart(4)} kills=${b.kills} ${b.alive ? '' : '(down)'}`).join('\n'));
  });
  socket.on('state', (s) => {
    seen.phases.add(s.phase);
    for (const ev of s.events || []) {
      if (ev.type === 'kill') { seen.kills++; }
      if (ev.type === 'morph') seen.morphs++;
      if (ev.type === 'explosion') seen.explosions++;
      if (ev.type === 'music') seen.music++;
    }
    const me = s.players.find((p) => p.id === c.me);
    c.me_state = me;
    // verify nobody is moving faster than the rules allow
    for (const p of s.players) {
      if (c.prev && c.prev[p.id]) {
        const d = Math.hypot(p.pos[0] - c.prev[p.id][0], p.pos[2] - c.prev[p.id][2]);
        if (d > 0.75) seen.maxSpeedViolation++;
      }
      (c.prev = c.prev || {})[p.id] = p.pos;
    }
    c.state = s;
  });
  socket.on('shot', () => { seen.shots++; });
  clients.push(c);
  socket.emit('join', { name, roomId: 'test', bots: 5 });
  return c;
}

for (let i = 0; i < 3; i++) makeClient(`Tester${i + 1}`);

// --- drive the fake players ------------------------------------------------
const interval = setInterval(() => {
  for (const c of clients) {
    if (!c.me || !c.state) continue;
    const st = c.state.players.find((p) => p.id === c.me);
    if (!st) continue;
    c.t += 0.05;
    if (!st.alive) { c.socket.emit('input', { pos: c.pos, yaw: c.yaw, pitch: 0 }); continue; }

    // wander: turn slowly, walk forward, re-aim at walls
    c.dir += (Math.random() - 0.5) * 0.6;
    const step = 0.12;
    const nx = c.pos[0] + Math.cos(c.dir) * step;
    const nz = c.pos[2] + Math.sin(c.dir) * step;
    const b = c.map.bounds;
    if (nx > b.x0 + 1 && nx < b.x1 - 1 && nz > b.z0 + 1 && nz < b.z1 - 1) { c.pos[0] = nx; c.pos[2] = nz; }
    else c.dir += Math.PI;
    c.yaw = -c.dir - Math.PI / 2;
    c.socket.emit('input', { pos: c.pos, yaw: c.yaw, pitch: Math.random() * 0.2 - 0.1 });

    if (st.team === 'prop') {
      if (Math.random() < 0.05) c.socket.emit('transform');
      if (Math.random() < 0.02) c.socket.emit('cycleProp', { dir: 1 });
      if (Math.random() < 0.01) c.socket.emit('medkit');
    } else {
      if (c.t % 0.5 < 0.05) c.socket.emit('shoot');
      if (Math.random() < 0.01) c.socket.emit('grenade');
      if (st.ammo === 0) c.socket.emit('reload');
    }
  }
}, 50);

setTimeout(() => {
  clearInterval(interval);
  console.log('\n--- results -------------------------------------------');
  console.log('phases seen      :', [...seen.phases].join(', '));
  console.log('shots fired      :', seen.shots);
  console.log('morphs           :', seen.morphs);
  console.log('music triggers   :', seen.music);
  console.log('explosions       :', seen.explosions);
  console.log('kills            :', seen.kills);
  console.log('speed violations :', seen.maxSpeedViolation);
  const last = clients[0]?.state;
  if (last) {
    console.log('players in room  :', last.players.length);
    console.log(last.players.map((p) => `   ${p.name.padEnd(14)} ${p.team.padEnd(7)} hp=${String(p.hp).padStart(3)} ${p.alive ? 'alive' : 'down '} ${p.propType || '-'} pos=${p.pos.map((v) => v.toFixed(1)).join(',')}`).join('\n'));
  }
  console.log('socket errors    :', errors);
  const ok = !errors && seen.shots > 0 && seen.morphs > 0 && seen.kills >= 0;
  console.log(ok ? '\nSMOKE TEST OK' : '\nSMOKE TEST FAILED');
  clients.forEach((c) => c.socket.close());
  process.exit(ok ? 0 : 1);
}, SECONDS * 1000);
