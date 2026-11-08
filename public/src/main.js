import { Game } from './game.js';

const menu = document.getElementById('menu');
const playBtn = document.getElementById('playBtn');
const nameInput = document.getElementById('nameInput');
const roomInput = document.getElementById('roomInput');
const botRange = document.getElementById('botRange');
const botCount = document.getElementById('botCount');

const savedName = localStorage.getItem('hh.name');
if (savedName) nameInput.value = savedName;

botRange.addEventListener('input', () => { botCount.textContent = botRange.value; });
botCount.textContent = botRange.value;

let game = null;

function start() {
  const name = (nameInput.value.trim() || `Player${Math.floor(Math.random() * 900 + 100)}`);
  localStorage.setItem('hh.name', name);
  const roomId = (roomInput.value.trim() || 'main').toLowerCase().replace(/\s+/g, '-');
  const bots = parseInt(botRange.value, 10);

  if (!game) game = new Game(document.getElementById('scene'));
  game.connect(name, roomId, bots);

  menu.classList.add('hidden');
  document.getElementById('hud').classList.remove('hidden');
  const joined = () => { document.getElementById('hud').classList.remove('hidden'); };
  setTimeout(joined, 50);
}

playBtn.addEventListener('click', start);
window.addEventListener('keydown', (e) => {
  if (!menu.classList.contains('hidden') && e.code === 'Enter') start();
});
