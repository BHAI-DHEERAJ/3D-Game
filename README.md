# Hide & Hunt 3D — Hunters vs Props

A 3D **prop-hunt** party game that runs in the browser: **Three.js** client + **Node.js / Socket.IO** server.
Two teams, one apartment, and a lot of suspicious furniture.

> Original implementation inspired by the *Prop Hunt* / hide-and-seek genre (the gameplay style of
> games like *Hide Online*). All code and all art here are generated from scratch — every chair,
> toilet and mug is built out of Three.js primitives, no external models, textures or sound files.

```
Props   (60% of the lobby)  → morph into everyday objects and hide
Hunters (40% of the lobby)  → hunt them down before the 3 minutes run out
```

## Quick start

```bash
npm install
npm start          # http://localhost:3000
```

Open it, type a name, hit **PLAY**. Bots fill the lobby, so it's playable solo — open a second tab
(or share the room name) to play with friends.

## How a round works

| Phase | Length | What happens |
| --- | --- | --- |
| **Starting** | 15s | lobby fills up with bots / friends |
| **Hide!** | 10s | hunters are locked in the hallway; props run off and morph into objects |
| **Hunt** | 3 min | hunters shoot anything that looks out of place |
| **Round over** | 12s | scoreboard, then straight into the next round |

* **Hunters** have 100 HP, 45 rounds and a grenade (12s cooldown).
  Shooting a *real* object costs you **5 HP** — spray and pray will kill you.
  Catching a prop transfers **all of the points that prop had banked**.
* **Props** earn **3 points per second** alive, can morph into any object they look at (`E`),
  shuffle between nearby disguises (`Q` / mouse wheel), and carry 2 medkits (`F`).
* Every ~20 seconds a hidden prop **blasts a music-box tune** that hunters can hear from 26m away.
  When the tune starts, you have ~3 seconds to relocate or you're dead.
* Hunters win by catching every prop. Otherwise props win on points.

## Controls

| | |
| --- | --- |
| `WASD` | move · `Shift` sprint · `Space` jump |
| Mouse | look (click the canvas to capture the pointer) |
| `LMB` | hunters: shoot · props: morph into the object under the crosshair |
| `E` | morph into the object you're aiming at |
| `Q` / wheel | shuffle to a different nearby disguise |
| `F` | medkit (props, ×2) |
| `G` | grenade (hunters, 12s cooldown) |
| `R` | reload · `Tab` scoreboard · `Esc` release mouse |

If the browser blocks pointer lock (e.g. inside an embedded preview), the game falls back to
steering the camera with the cursor — everything else works the same.

## Layout

```
shared/     map.js  (deterministic map generation + AABB collision + raycasts)
            nav.js  (0.5m nav grid + A* used by the bots)
            constants.js (tuning shared by client and server)
server/     index.js  express + Socket.IO gateway
            room.js   authoritative match: phases, damage, hitscan, scoring
            bots.js   bot AI (patrol / detect / shoot, hide / flee / re-morph)
public/     index.html, style.css
            src/models.js  procedural furniture, characters, weapon
            src/world.js   builds the scene from the map data
            src/game.js    camera, prediction, interpolation, effects
            src/audio.js   Web Audio synthesis (no audio files)
            src/hud.js     HUD / scoreboard
test/       client-smoke.js  builds every mesh + checks collision, nav, raycasts
            smoke.js         headless multiplayer round test
```

The server is authoritative for damage, hitscan raycasts and scoring; the client predicts its own
movement and interpolates everyone else ~90ms behind. The map is generated from a seed, so the
client and server derive byte-identical collision geometry from the same data.

## Tuning

* `shared/constants.js` — speeds, damage, phase lengths, ammo, music cycle.
* `GAME_SPEED=6 npm start` — runs rounds 6× faster (handy for testing).
* Bots count is adjustable on the menu screen (1–7).

## Tests

```bash
npm test                 # scene / collision / navigation checks (no browser needed)
GAME_SPEED=12 PORT=3100 npm start &
node test/smoke.js 3100 40    # drives fake clients through several full rounds
```
