import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import express from 'express';
import { Server } from 'socket.io';
import { Room } from './room.js';
import { PHASE, TIMINGS } from '../shared/constants.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = process.env.PORT || 3000;

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: true, methods: ['GET', 'POST'], credentials: true },
  pingInterval: 10000,
  pingTimeout: 20000,
});

app.use(express.static(path.join(__dirname, '..', 'public')));
app.use('/shared', express.static(path.join(__dirname, '..', 'shared')));
app.use('/vendor', express.static(path.join(__dirname, '..', 'node_modules', 'three', 'build')));

const rooms = new Map();
function getRoom(id) {
  if (!rooms.has(id)) rooms.set(id, new Room(io, id));
  return rooms.get(id);
}
getRoom('main');

io.on('connection', (socket) => {
  let room = null;
  let player = null;

  socket.on('join', ({ name, roomId = 'main', bots } = {}) => {
    room = getRoom(roomId);
    socket.join(room.id);
    if (typeof bots === 'number') room.botsWanted = Math.max(1, Math.min(8, bots));
    player = room.addPlayer(socket, (name || 'Player').slice(0, 16));
    socket.data.player = player;
    socket.data.room = room;
    socket.emit('joined', {
      you: player.id,
      roomId: room.id,
      map: room.map,
      botsWanted: room.botsWanted,
    });
    room.broadcast({ type: 'feed', text: `${player.name} joined` });
  });

  socket.on('input', (data) => {
    if (!player || !room) return;
    room.handleInput(player, data);
  });

  socket.on('shoot', () => { if (player && room) room.handleShoot(player); });
  socket.on('grenade', () => { if (player && room) room.handleGrenade(player); });
  socket.on('reload', () => { if (player && room) room.startReload(player); });
  socket.on('transform', () => { if (player && room) room.handleTransform(player); });
  socket.on('cycleProp', ({ dir } = {}) => { if (player && room) room.handleCycleProp(player, dir || 1); });
  socket.on('medkit', () => { if (player && room) room.handleMedkit(player); });
  socket.on('startNow', () => {
    if (!room) return;
    room.phaseEndsAt = Date.now();
  });
  socket.on('setBots', ({ count } = {}) => {
    if (!room) return;
    room.botsWanted = Math.max(1, Math.min(8, count || 5));
    room.ensureBots();
    io.to(room.id).emit('botsChanged', { count: room.botsWanted });
  });
  socket.on('pingBack', (t) => socket.emit('pongBack', t));
  socket.on('leaveMatch', () => {
    if (!room || !player) return;
    player.alive = false;
    room.broadcast({ type: 'feed', text: `${player.name} is spectating` });
  });

  socket.on('disconnect', () => {
    if (room && player) room.removePlayer(player.id);
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Hide & Hunt server listening on http://0.0.0.0:${PORT}`);
});
