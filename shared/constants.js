// Shared tuning constants used by BOTH the Node server and the browser client.

export const TICK_RATE = 20;                 // server simulation + snapshot rate (Hz)
export const TICK_MS = 1000 / TICK_RATE;

export const PHASE = {
  LOBBY: 'lobby',
  PREP: 'prep',       // hunters locked in spawn, props go hide
  HUNT: 'hunt',       // main phase
  OVER: 'over',
};

export const TIMINGS = {
  lobbyCountdown: 15,   // seconds before a match auto-starts (with bots filling slots)
  prepTime: 10,         // hide phase
  huntTime: 180,        // 3 minutes
  overTime: 12,         // scoreboard before next match
};

export const RULES = {
  maxPlayers: 8,
  propRatio: 0.6,          // ~60% of players are props
  hunterHealth: 100,
  propHealth: 100,
  selfDamageOnMiss: 5,     // shooting a REAL object hurts the hunter
  hitDamageProp: 12,       // per bullet on a disguised prop
  grenadeDamage: 45,
  grenadeRadius: 5.5,
  grenadeCooldown: 12,
  grenadeFuse: 2.0,
  hunterAmmo: 45,
  reloadTime: 2.1,
  fireRate: 0.14,          // seconds between shots
  medkitHeal: 45,
  medkitCharges: 2,
  moveSpeed: 5.4,
  propSpeedMul: 0.92,      // props in disguise are a bit slower
  sprintMul: 1.35,
  jumpSpeed: 6.2,
  gravity: 22,
  eyeHeight: 1.62,
  playerRadius: 0.42,
  playerHeight: 1.75,
  transformRange: 7.0,     // how far a prop can look to copy an object
  musicCycle: 22,          // prop is silent this long...
  musicDuration: 3.5,      // ...then blasts a tune for this long
  musicHearRadius: 26,
  scorePerSecond: 3,       // props earn points while alive
  scorePerHit: 5,
};

export const TEAM = { HUNTER: 'hunter', PROP: 'prop' };
